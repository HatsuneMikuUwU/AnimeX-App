package com.uwu.animex.data.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.BuildConfig
import com.uwu.animex.MainActivity
import com.uwu.animex.R
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AppUpdate {
    private const val TAG = "AppUpdate"
    private const val REPO = "HatsuneMikuUwU/AnimeX-App"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=10"

    const val CHANNEL_ID = "app_update"
    const val NOTIFICATION_ID = 7101
    const val EXTRA_OPEN_UPDATE = "open_app_update"

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", "AnimeX/${BuildConfig.VERSION_NAME}")
                        .header("Accept", "application/vnd.github+json")
                        .build(),
                )
            }.build()

    data class Release(
        val tag: String,
        val name: String,
        val body: String,
        val publishedAt: String,
        val apkUrl: String,
        val apkName: String,
        val sizeBytes: Long,
        val sha256: String? = null,
        val htmlUrl: String = "",
    )

    sealed class State {
        data object Idle : State()

        data object Checking : State()

        data class Available(
            val release: Release,
            val older: List<Release> = emptyList(),
        ) : State()

        data class Downloading(
            val release: Release,
            val progress: Float,
            val older: List<Release> = emptyList(),
        ) : State()

        data class Ready(
            val release: Release,
            val file: File,
            val older: List<Release> = emptyList(),
        ) : State()

        data class Error(
            val message: String,
        ) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var appCtx: Context? = null

    fun init(context: Context) {
        appCtx = context.applicationContext
        ensureChannel(context.applicationContext)
    }

    fun skipThisVersion(tag: String) {
        appCtx
            ?.getSharedPreferences(PrefKeys.AppUpdate.FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PrefKeys.AppUpdate.SKIPPED, tag)
            ?.apply()
        _state.value = State.Idle
        cancelNotification()
    }

    private fun skippedVersion(): String? =
        appCtx
            ?.getSharedPreferences(PrefKeys.AppUpdate.FILE, Context.MODE_PRIVATE)
            ?.getString(PrefKeys.AppUpdate.SKIPPED, null)

    private fun notifiedVersion(): String? =
        appCtx
            ?.getSharedPreferences(PrefKeys.AppUpdate.FILE, Context.MODE_PRIVATE)
            ?.getString(PrefKeys.AppUpdate.NOTIFIED, null)

    private fun markNotified(tag: String) {
        appCtx
            ?.getSharedPreferences(PrefKeys.AppUpdate.FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PrefKeys.AppUpdate.NOTIFIED, tag)
            ?.apply()
    }

    fun isNewer(
        remote: String,
        current: String = BuildConfig.VERSION_NAME,
    ): Boolean = isReleaseNewer(remote, current)

    suspend fun check() {
        val current = _state.value
        if (current is State.Checking || current is State.Downloading) return
        if (current is State.Ready) return
        _state.value = State.Checking
        try {
            val (latest, older) = withContext(Dispatchers.IO) { fetchReleases() }
            if (latest == null || !isNewer(latest.tag)) {
                _state.value = State.Idle
                return
            }
            if (latest.tag == skippedVersion()) {
                _state.value = State.Idle
                return
            }
            _state.value = State.Available(latest, older)
            maybeNotify(latest)
        } catch (e: CancellationException) {
            _state.value = State.Idle
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "check failed", e)
            _state.value = State.Error(e.toUserMessage())
        }
    }

    private fun maybeNotify(release: Release) {
        val ctx = appCtx ?: return
        if (release.tag == notifiedVersion()) return
        if (postNotification(ctx, release)) markNotified(release.tag)
    }

    private fun fetchReleases(): Pair<Release?, List<Release>> {
        val body =
            http.newCall(Request.Builder().url(RELEASES_URL).build()).execute().use { r ->
                if (!r.isSuccessful) error("GitHub ${r.code}")
                r.body.string()
            }
        return parseGitHubReleasesJson(
            body = body,
            allowPrereleases = NotificationPrefs.state.value.prereleases,
            abi = preferredAbi(),
            currentVersion = BuildConfig.VERSION_NAME,
            repo = REPO,
        )
    }

    private fun preferredAbi(): String = pickPreferredAbi(Build.SUPPORTED_ABIS)

    suspend fun download(
        context: Context,
        release: Release,
        older: List<Release> = emptyList(),
    ) {
        _state.value = State.Downloading(release, 0f, older)
        try {
            val file =
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val safeName =
                        release.apkName
                            .substringAfterLast('/')
                            .replace(Regex("[^A-Za-z0-9._-]"), "_")
                            .ifBlank { "AnimeX-update.apk" }
                    val out = File(dir, safeName)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val req = Request.Builder().url(release.apkUrl).build()
                    http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("Download gagal HTTP ${resp.code}")
                        val body = resp.body
                        val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                        body.byteStream().use { input ->
                            out.outputStream().use { output ->
                                val buf = ByteArray(32 * 1024)
                                var read: Int
                                var done = 0L
                                while (input.read(buf).also { read = it } != -1) {
                                    output.write(buf, 0, read)
                                    digest.update(buf, 0, read)
                                    done += read
                                    if (total > 0) {
                                        val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                        _state.value = State.Downloading(release, p, older)
                                    }
                                }
                            }
                        }
                    }
                    if (release.sizeBytes > 0 && out.length() != release.sizeBytes) {
                        out.delete()
                        error("Ukuran file unduhan tidak sesuai, coba unduh ulang")
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (release.sha256 == null) {
                        out.delete()
                        error("Rilis ini tidak menyertakan checksum, update dibatalkan demi keamanan")
                    }
                    if (!checksumMatches(release.sha256, actual)) {
                        out.delete()
                        error("Checksum file unduhan tidak cocok, unduhan dibatalkan")
                    }
                    out
                }
            _state.value = State.Ready(release, file, older)
            postReadyNotification(context, release)
        } catch (e: CancellationException) {
            _state.value = State.Available(release, older)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            _state.value = State.Error(e.toUserMessage())
        }
    }

    fun install(
        context: Context,
        file: File,
    ) {
        try {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { ri ->
                    context.grantUriPermission(
                        ri.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            _state.value = State.Error(e.message ?: "Gagal buka installer")
        }
    }

    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private fun ensureChannel(ctx: Context) = AppNotifier.createChannels(ctx)

    private fun postNotification(
        ctx: Context,
        release: Release,
    ) = AppNotifier.showAppUpdate(ctx, release.tag)

    private fun postReadyNotification(
        ctx: Context,
        release: Release,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(ctx)
        val open =
            PendingIntent.getActivity(
                ctx,
                NOTIFICATION_ID + 1,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_OPEN_UPDATE, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notif =
            NotificationCompat
                .Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle("Update siap dipasang")
                .setContentText("v${release.tag.removePrefix("v")} sudah terunduh. Ketuk untuk install.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notif)
    }

    fun cancelNotification() {
        val ctx = appCtx ?: return
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
    }

    fun onCacheCleared() {
        if (_state.value is State.Ready) {
            _state.value = State.Idle
            cancelNotification()
        }
    }
}

private data class ApkAsset(
    val url: String,
    val name: String,
    val size: Long,
    val sha256: String?,
)

private val preTokenRegex = Regex("[A-Za-z]+|\\d+")

internal fun isReleaseNewer(
    remote: String,
    current: String,
): Boolean {
    fun nums(v: String): List<Int> =
        v
            .split('.', '-', '_')
            .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
            .ifEmpty { listOf(0) }

    fun core(v: String) = v.trim().removePrefix("v").substringBefore('-')

    fun pre(v: String): String? =
        v
            .trim()
            .removePrefix("v")
            .substringAfter('-', "")
            .ifBlank { null }

    val a = nums(core(remote))
    val b = nums(core(current))
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    val pr = pre(remote)
    val pc = pre(current)
    return when {
        pr == null && pc == null -> false
        pr == null -> true
        pc == null -> false
        else -> comparePreTokens(pr, pc) > 0
    }
}

private fun comparePreTokens(
    a: String,
    b: String,
): Int {
    val x = preTokenRegex.findAll(a).map { it.value }.toList()
    val y = preTokenRegex.findAll(b).map { it.value }.toList()
    for (i in 0 until maxOf(x.size, y.size)) {
        val p = x.getOrNull(i) ?: return -1
        val q = y.getOrNull(i) ?: return 1
        val pn = p.toBigIntegerOrNull()
        val qn = q.toBigIntegerOrNull()
        val c =
            when {
                pn != null && qn != null -> pn.compareTo(qn)
                pn != null -> -1
                qn != null -> 1
                else -> p.compareTo(q, ignoreCase = true)
            }
        if (c != 0) return c
    }
    return 0
}

private fun JsonObject.optString(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }

private fun JsonObject.optBoolean(key: String): Boolean =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } == true

private fun JsonObject.optLong(key: String): Long =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L

private fun JsonObject.optArray(key: String): JsonArray? =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray

internal fun parseGitHubReleasesJson(
    body: String,
    allowPrereleases: Boolean,
    abi: String,
    currentVersion: String,
    repo: String = "HatsuneMikuUwU/AnimeX-App",
): Pair<AppUpdate.Release?, List<AppUpdate.Release>> {
    val parsed = JsonParser.parseString(body)
    if (!parsed.isJsonArray) return null to emptyList()
    val all = mutableListOf<AppUpdate.Release>()
    for (el in parsed.asJsonArray) {
        if (!el.isJsonObject) continue
        val o = el.asJsonObject
        if (o.optBoolean("draft")) continue
        val isPrerelease = o.optBoolean("prerelease") || o.optString("tag_name").orEmpty().contains('-')
        if (isPrerelease && !allowPrereleases) continue
        parseSingleRelease(o, abi, repo)?.let { all += it }
    }
    val newer = all.filter { isReleaseNewer(it.tag, currentVersion) }
    return newer.firstOrNull() to newer.drop(1)
}

private fun parseSingleRelease(
    root: JsonObject,
    abi: String,
    repo: String,
): AppUpdate.Release? {
    val tag = root.optString("tag_name")?.trim().orEmpty()
    if (tag.isBlank()) return null
    val name = root.optString("name")?.ifBlank { tag } ?: tag
    val notes = root.optString("body").orEmpty()
    val published = root.optString("published_at").orEmpty().take(10)
    val assets = root.optArray("assets") ?: return null

    val candidates = mutableListOf<ApkAsset>()
    for (el in assets) {
        if (!el.isJsonObject) continue
        val o = el.asJsonObject
        val n = o.optString("name").orEmpty()
        if (!n.endsWith(".apk", ignoreCase = true)) continue
        val url = o.optString("browser_download_url").orEmpty()
        if (!url.startsWith("https://github.com/$repo/releases/download/")) continue
        val size = o.optLong("size")
        val sha =
            o
                .optString("digest")
                ?.removePrefix("sha256:")
                ?.lowercase()
                ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        candidates += ApkAsset(url, n, size, sha)
    }
    if (candidates.isEmpty()) return null

    val pick =
        candidates.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: candidates.first()

    return AppUpdate.Release(
        tag = tag.removePrefix("v"),
        name = name,
        body = notes,
        publishedAt = published,
        apkUrl = pick.url,
        apkName = pick.name,
        sizeBytes = pick.size,
        sha256 = pick.sha256,
        htmlUrl =
            root.optString("html_url")?.takeIf { it.isNotBlank() }
                ?: "https://github.com/$repo/releases/tag/$tag",
    )
}
