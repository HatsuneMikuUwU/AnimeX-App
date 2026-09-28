package com.uwu.animex.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.uwu.animex.AnimeDownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Unduhan disimpan sebagai file utuh (mp4 atau ts) di folder pilihan user lewat Storage Access Framework.
 * Progresif (mp4) diunduh dengan resume via Range; HLS (m3u8) diunduh per segmen lalu digabung.
 */
object Downloads {
    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID = 1001
    private const val PREFS = "downloads"
    private const val KEY_ITEMS = "items"
    private const val KEY_FOLDER = "folder"
    private const val MAX_PARALLEL = 2
    private const val UA = "okhttp/4.12.0"

    enum class Status { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    data class Meta(
        val movieId: String? = null,
        val movieTitle: String? = null,
        val epIndex: String? = null,
        val epTitle: String? = null,
        val image: String? = null,
        val quality: String? = null,
    )

    data class Item(
        val id: String,
        val status: Status,
        val percent: Float,
        val bytes: Long,
        val startTimeMs: Long,
        val url: String,
        val meta: Meta,
        val fileUri: String? = null,
        val segDone: Int = 0,
        val error: String? = null,
    )

    private class Seg(val url: String, val keyUri: String?, val iv: String?, val seq: Long)

    private val gson = Gson()
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()
    private var app: Context? = null
    private var prefs: SharedPreferences? = null
    @Volatile
    private var lastSave = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    var items: Map<String, Item> by mutableStateOf(emptyMap())
        private set

    var folderUri: String? by mutableStateOf(null)
        private set

    fun init(context: Context) {
        if (app != null) return
        val c = context.applicationContext
        app = c
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        folderUri = p.getString(KEY_FOLDER, null)
        val loaded = runCatching { gson.fromJson(p.getString(KEY_ITEMS, null), Array<Item>::class.java)?.toList() }
            .getOrNull().orEmpty()
        items = loaded.associate {
            it.id to if (it.status == Status.DOWNLOADING) it.copy(status = Status.QUEUED) else it
        }
        scope.launch {
            items.values.filter { it.status == Status.COMPLETED }.forEach { d ->
                val exists = d.fileUri?.let {
                    runCatching { DocumentFile.fromSingleUri(c, Uri.parse(it))?.exists() == true }.getOrDefault(false)
                } ?: false
                if (!exists) synchronized(lock) { items = items - d.id }
            }
            persist()
            pump()
        }
    }

    // ---------- Folder ----------

    fun setFolder(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs?.edit()?.putString(KEY_FOLDER, uri.toString())?.apply()
        folderUri = uri.toString()
    }

    fun folderName(context: Context): String? =
        folderUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }

    // ---------- Public queries ----------

    fun item(id: String?): Item? = items[id ?: return null]

    fun completedUrl(id: String?): String? =
        item(id)?.takeIf { it.status == Status.COMPLETED }?.fileUri

    // ---------- Public actions ----------

    fun enqueue(context: Context, epId: String, url: String, meta: Meta) {
        if (app == null) init(context)
        synchronized(lock) {
            val old = items[epId]?.takeIf { it.url == url }
            val next = old?.copy(status = Status.QUEUED, error = null)
                ?: Item(
                    id = epId,
                    status = Status.QUEUED,
                    percent = 0f,
                    bytes = 0L,
                    startTimeMs = System.currentTimeMillis(),
                    url = url,
                    meta = meta,
                )
            items = items + (epId to next)
        }
        persist()
        pump()
    }

    fun pause(context: Context, id: String) {
        update(id, save = true) {
            if (it.status == Status.QUEUED || it.status == Status.DOWNLOADING) it.copy(status = Status.PAUSED) else it
        }
        synchronized(lock) { jobs[id] }?.cancel()
        pump()
    }

    /** Jeda semua unduhan yang antre/berjalan (aksi notifikasi dan timeout foreground service). */
    fun pauseAll() {
        val ids = synchronized(lock) {
            items.values
                .filter { it.status == Status.QUEUED || it.status == Status.DOWNLOADING }
                .map { it.id }
        }
        ids.forEach { id ->
            update(id, save = true) { it.copy(status = Status.PAUSED) }
            synchronized(lock) { jobs[id] }?.cancel()
        }
    }

    fun resume(context: Context, id: String) = requeue(id)

    fun retry(context: Context, id: String) = requeue(id)

    private fun requeue(id: String) {
        update(id, save = true) {
            if (it.status == Status.PAUSED || it.status == Status.FAILED) it.copy(status = Status.QUEUED, error = null) else it
        }
        pump()
    }

    fun remove(context: Context, id: String) {
        val item: Item?
        val job: Job?
        synchronized(lock) {
            item = items[id]
            job = jobs[id]
            items = items - id
        }
        persist()
        job?.cancel()
        scope.launch {
            job?.join()
            item?.fileUri?.let { runCatching { DocumentFile.fromSingleUri(context, Uri.parse(it))?.delete() } }
            pump()
        }
    }

    // ---------- Internals ----------

    private fun update(id: String, save: Boolean = false, block: (Item) -> Item) {
        synchronized(lock) {
            val cur = items[id] ?: return
            items = items + (id to block(cur))
        }
        if (save) persist()
    }

    private fun persist() {
        lastSave = System.currentTimeMillis()
        prefs?.edit()?.putString(KEY_ITEMS, gson.toJson(items.values.toList()))?.apply()
    }

    private fun progress(id: String, bytes: Long, percent: Float, segDone: Int? = null) {
        update(id) { it.copy(bytes = bytes, percent = percent, segDone = segDone ?: it.segDone) }
        if (System.currentTimeMillis() - lastSave > 2000) persist()
    }

    private fun pump() {
        val c = app ?: return
        var started = false
        synchronized(lock) {
            val free = MAX_PARALLEL - jobs.size
            if (free <= 0) return@synchronized
            val next = items.values
                .filter { it.status == Status.QUEUED && it.id !in jobs }
                .sortedBy { it.startTimeMs }
                .take(free)
            next.forEach { item ->
                items = items + (item.id to item.copy(status = Status.DOWNLOADING, error = null))
                val job = scope.launch(start = CoroutineStart.LAZY) { run(c, item.id) }
                jobs[item.id] = job
                job.invokeOnCompletion {
                    synchronized(lock) { jobs.remove(item.id) }
                    pump()
                }
                job.start()
                started = true
            }
        }
        if (started) {
            persist()
            // Android 12+ melempar ForegroundServiceStartNotAllowedException (IllegalStateException)
            // kalau app di background; unduhan tetap jalan di proses, hanya tanpa notifikasi foreground.
            try {
                ContextCompat.startForegroundService(c, Intent(c, AnimeDownloadService::class.java))
            } catch (e: IllegalStateException) {
            } catch (e: SecurityException) {
            }
        }
    }

    private suspend fun run(ctx: Context, id: String) {
        try {
            val tree = folderUri ?: error("Folder unduhan belum dipilih")
            val root = DocumentFile.fromTreeUri(ctx, Uri.parse(tree))
            if (root == null || !root.canWrite()) error("Folder unduhan tidak bisa diakses. Pilih ulang folder.")
            val item = items[id] ?: return
            if (".m3u8" in item.url.lowercase()) hls(ctx, id, root) else progressive(ctx, id, root)
            update(id, save = true) { it.copy(status = Status.COMPLETED, percent = 100f, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            update(id, save = true) { it.copy(status = Status.FAILED, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun baseName(item: Item): String {
        val raw = "${item.meta.movieTitle.orEmpty()} - Ep ${item.meta.epIndex.orEmpty()}".trim()
        val clean = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return clean.ifBlank { item.id }
    }

    private fun docFor(ctx: Context, root: DocumentFile, item: Item, mime: String): Pair<DocumentFile, Boolean> {
        val existing = item.fileUri
            ?.let { DocumentFile.fromSingleUri(ctx, Uri.parse(it)) }
            ?.takeIf { it.exists() }
        if (existing != null) return existing to false
        val created = root.createFile(mime, baseName(item)) ?: error("Tidak bisa membuat file di folder unduhan")
        update(item.id, save = true) { it.copy(fileUri = created.uri.toString(), segDone = 0, bytes = 0L) }
        return created to true
    }

    private fun Job.guarded(call: Call, block: () -> Unit) {
        val handle = invokeOnCompletion { call.cancel() }
        try {
            block()
        } finally {
            handle.dispose()
        }
    }

    private fun request(url: String, range: String? = null): Request =
        Request.Builder().url(url).header("User-Agent", UA).apply { if (range != null) header("Range", range) }.build()

    // ---------- Progressive (mp4) ----------

    private suspend fun progressive(ctx: Context, id: String, root: DocumentFile) {
        val job = currentCoroutineContext().job
        val item = items[id] ?: return
        val (doc, fresh) = docFor(ctx, root, item, "video/mp4")
        val have = if (fresh) 0L else doc.length()
        val call = http.newCall(request(item.url, if (have > 0) "bytes=$have-" else null))
        job.guarded(call) {
            call.execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val partial = resp.code == 206
                val start = if (partial) have else 0L
                val len = resp.body.contentLength()
                val total = if (len > 0) start + len else -1L
                val out = ctx.contentResolver.openOutputStream(doc.uri, if (partial) "wa" else "wt")
                    ?: error("Tidak bisa menulis ke folder unduhan")
                out.use { o ->
                    val input = resp.body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    var written = start
                    var lastUi = 0L
                    while (true) {
                        job.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 500) {
                            lastUi = now
                            progress(id, written, if (total > 0) written * 100f / total else -1f)
                        }
                    }
                    o.flush()
                    progress(id, written, if (total > 0) written * 100f / total else -1f)
                    if (total > 0 && written < total) error("Unduhan terputus")
                }
            }
        }
    }

    // ---------- HLS (m3u8) ----------

    private fun attr(line: String, name: String): String? =
        Regex("$name=(\"[^\"]*\"|[^,]*)").find(line)?.groupValues?.get(1)?.trim('"')

    private fun resolve(base: String, ref: String): String =
        base.toHttpUrl().resolve(ref)?.toString() ?: error("URL playlist tidak valid")

    private fun fetchBytes(job: Job, url: String): ByteArray {
        var last: Exception? = null
        repeat(3) {
            job.ensureActive()
            try {
                val call = http.newCall(request(url))
                var data: ByteArray? = null
                job.guarded(call) {
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        data = resp.body.bytes()
                    }
                }
                return data ?: ByteArray(0)
            } catch (e: Exception) {
                job.ensureActive()
                last = e
            }
        }
        throw last ?: IllegalStateException("Gagal mengunduh segmen")
    }

    private suspend fun hls(ctx: Context, id: String, root: DocumentFile) {
        val job = currentCoroutineContext().job
        val item = items[id] ?: return
        var playlistUrl = item.url
        var text = String(fetchBytes(job, playlistUrl))

        if ("#EXT-X-STREAM-INF" in text) {
            val lines = text.lines().map { it.trim() }
            var bestBw = -1L
            var bestUrl: String? = null
            lines.forEachIndexed { i, l ->
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = attr(l, "BANDWIDTH")?.toLongOrNull() ?: 0L
                    val u = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    if (u != null && bw > bestBw) {
                        bestBw = bw
                        bestUrl = u
                    }
                }
            }
            playlistUrl = resolve(playlistUrl, bestUrl ?: error("Playlist tidak valid"))
            text = String(fetchBytes(job, playlistUrl))
        }

        var seq = 0L
        var keyUri: String? = null
        var keyIv: String? = null
        var mapUri: String? = null
        var ended = false
        val segs = ArrayList<Seg>()
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    seq = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-KEY:") -> {
                    when (val method = attr(line, "METHOD")) {
                        "NONE", null -> { keyUri = null; keyIv = null }
                        "AES-128" -> {
                            keyUri = attr(line, "URI")?.let { resolve(playlistUrl, it) }
                            keyIv = attr(line, "IV")
                        }
                        else -> error("Enkripsi $method tidak didukung")
                    }
                }
                line.startsWith("#EXT-X-MAP:") ->
                    mapUri = attr(line, "URI")?.let { resolve(playlistUrl, it) }
                line.startsWith("#EXT-X-BYTERANGE") -> error("Playlist byte-range tidak didukung")
                line.startsWith("#EXT-X-ENDLIST") -> ended = true
                line.isNotEmpty() && !line.startsWith("#") ->
                    segs += Seg(resolve(playlistUrl, line), keyUri, keyIv, seq + segs.size)
            }
        }
        if (!ended) error("Siaran langsung tidak bisa diunduh")
        if (segs.isEmpty()) error("Playlist kosong")

        val (doc, fresh) = if (mapUri != null) {
            docFor(ctx, root, item, "video/mp4")
        } else {
            docFor(ctx, root, item, "video/mp2t")
        }
        val startIdx = if (fresh) 0 else (items[id]?.segDone ?: 0).coerceIn(0, segs.size)
        var written = if (startIdx == 0) 0L else items[id]?.bytes ?: 0L
        val keys = HashMap<String, ByteArray>()

        val out = ctx.contentResolver.openOutputStream(doc.uri, if (startIdx > 0) "wa" else "wt")
            ?: error("Tidak bisa menulis ke folder unduhan")
        out.use { o ->
            if (startIdx == 0 && mapUri != null) {
                val init = fetchBytes(job, mapUri)
                o.write(init)
                written += init.size
            }
            for (i in startIdx until segs.size) {
                job.ensureActive()
                val s = segs[i]
                var data = fetchBytes(job, s.url)
                if (s.keyUri != null) {
                    val key = keys.getOrPut(s.keyUri) { fetchBytes(job, s.keyUri) }
                    val iv = s.iv?.removePrefix("0x")?.removePrefix("0X")?.let { hex ->
                        val padded = hex.padStart(32, '0')
                        ByteArray(16) { idx -> padded.substring(idx * 2, idx * 2 + 2).toInt(16).toByte() }
                    } ?: ByteArray(16).also { b ->
                        var v = s.seq
                        for (k in 15 downTo 8) { b[k] = (v and 0xFF).toByte(); v = v shr 8 }
                    }
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                    data = cipher.doFinal(data)
                }
                o.write(data)
                o.flush()
                written += data.size
                progress(id, written, (i + 1) * 100f / segs.size, segDone = i + 1)
            }
        }
    }
}
