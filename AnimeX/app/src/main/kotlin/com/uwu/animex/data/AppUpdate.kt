package com.uwu.animex.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.uwu.animex.BuildConfig
import com.uwu.animex.MainActivity
import com.uwu.animex.R
import com.uwu.animex.UpdateCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object AppUpdate {
    private const val TAG = "AppUpdate"
    private const val WORK_NAME = "app_update_check"
    private const val REPO = "HatsuneMikuUwU/AnimeX-App"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=10"
    private const val PREFS = "app_update"
    private const val KEY_SKIPPED = "skipped_version"
    private const val KEY_NOTIFIED = "notified_version"

    const val CHANNEL_ID = "app_update"
    const val NOTIFICATION_ID = 7101
    const val EXTRA_OPEN_UPDATE = "open_app_update"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AnimeX/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/vnd.github+json")
                    .build(),
            )
        }
        .build()

    data class Release(
        val tag: String,
        val name: String,
        val body: String,
        val publishedAt: String,
        val apkUrl: String,
        val apkName: String,
        val sizeBytes: Long,
        val htmlUrl: String = "",
    )

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data class Available(val release: Release, val older: List<Release> = emptyList()) : State()
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
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var appCtx: Context? = null

    fun init(context: Context) {
        appCtx = context.applicationContext
        ensureChannel(context.applicationContext)
    }

    fun scheduleBackgroundCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun skipThisVersion(tag: String) {
        appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_SKIPPED, tag)?.apply()
        _state.value = State.Idle
        cancelNotification()
    }

    fun clearError() {
        if (_state.value is State.Error) _state.value = State.Idle
    }

    private fun skippedVersion(): String? =
        appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_SKIPPED, null)

    private fun notifiedVersion(): String? =
        appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_NOTIFIED, null)

    private fun markNotified(tag: String) {
        appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_NOTIFIED, tag)?.apply()
    }

    fun isNewer(remote: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        fun nums(v: String): List<Int> =
            v.split('.', '-', '_')
                .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
                .ifEmpty { listOf(0) }

        fun core(v: String) = v.trim().removePrefix("v").substringBefore('-')
        fun pre(v: String): String? =
            v.trim().removePrefix("v").substringAfter('-', "").ifBlank { null }

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
            else -> comparePre(pr, pc) > 0
        }
    }

    private fun preTokens(v: String): List<String> =
        Regex("[A-Za-z]+|\\d+").findAll(v).map { it.value }.toList()

    private fun comparePre(a: String, b: String): Int {
        val x = preTokens(a)
        val y = preTokens(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrNull(i) ?: return -1
            val q = y.getOrNull(i) ?: return 1
            val pn = p.toBigIntegerOrNull()
            val qn = q.toBigIntegerOrNull()
            val c = when {
                pn != null && qn != null -> pn.compareTo(qn)
                pn != null -> -1
                qn != null -> 1
                else -> p.compareTo(q, ignoreCase = true)
            }
            if (c != 0) return c
        }
        return 0
    }

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
        } catch (e: Exception) {
            Log.w(TAG, "check failed", e)
            _state.value = State.Idle
        }
    }

    private fun maybeNotify(release: Release) {
        val ctx = appCtx ?: return
        if (release.tag == notifiedVersion()) return
        postNotification(ctx, release)
        markNotified(release.tag)
    }

    private fun fetchReleases(): Pair<Release?, List<Release>> {
        val body = http.newCall(Request.Builder().url(RELEASES_URL).build()).execute().use { r ->
            if (!r.isSuccessful) error("GitHub ${r.code}")
            r.body.string()
        }
        val arr = JsonParser.parseString(body).asJsonArray
        val all = mutableListOf<Release>()
        for (el in arr) {
            val o = el.asJsonObject
            if (o.get("draft")?.asBoolean == true) continue
            parseRelease(o)?.let { all += it }
        }
        val newer = all.filter { isNewer(it.tag) }
        val latest = newer.firstOrNull()
        val older = newer.drop(1)
        return latest to older
    }

    private fun parseRelease(root: com.google.gson.JsonObject): Release? {
        val tag = root.get("tag_name")?.asString?.trim().orEmpty()
        if (tag.isBlank()) return null
        val name = root.get("name")?.asString?.ifBlank { tag } ?: tag
        val notes = root.get("body")?.asString.orEmpty()
        val published = root.get("published_at")?.asString.orEmpty().take(10)
        val assets = root.getAsJsonArray("assets") ?: return null

        val abi = preferredAbi()
        val candidates = mutableListOf<Triple<String, String, Long>>()
        for (el in assets) {
            val o = el.asJsonObject
            val n = o.get("name")?.asString.orEmpty()
            if (!n.endsWith(".apk", ignoreCase = true)) continue
            val url = o.get("browser_download_url")?.asString.orEmpty()
            if (url.isBlank()) continue
            val size = o.get("size")?.asLong ?: 0L
            candidates += Triple(url, n, size)
        }
        if (candidates.isEmpty()) return null

        val pick = candidates.firstOrNull { (_, n, _) ->
            n.contains(abi, ignoreCase = true)
        } ?: candidates.firstOrNull { (_, n, _) ->
            n.contains("universal", ignoreCase = true)
        } ?: candidates.first()

        return Release(
            tag = tag.removePrefix("v"),
            name = name,
            body = notes,
            publishedAt = published,
            apkUrl = pick.first,
            apkName = pick.second,
            sizeBytes = pick.third,
            htmlUrl = root.get("html_url")?.asString?.takeIf { it.isNotBlank() }
                ?: "https://github.com/$REPO/releases/tag/$tag",
        )
    }

    private fun preferredAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.any { it.contains("arm64") } -> "arm64-v8a"
            abis.any { it.contains("armeabi") } -> "armeabi-v7a"
            abis.any { it.contains("x86_64") } -> "x86_64"
            else -> "universal"
        }
    }

    suspend fun download(context: Context, release: Release, older: List<Release> = emptyList()) {
        _state.value = State.Downloading(release, 0f, older)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, release.apkName.ifBlank { "AnimeX-update.apk" })
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
                                done += read
                                if (total > 0) {
                                    val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                    _state.value = State.Downloading(release, p, older)
                                }
                            }
                        }
                    }
                }
                out
            }
            _state.value = State.Ready(release, file, older)
            postReadyNotification(context, release)
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            _state.value = State.Error(e.message ?: "Gagal unduh update")
        }
    }

    fun install(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
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

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Update aplikasi",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifikasi saat ada versi AnimeX baru"
            },
        )
    }

    private fun postNotification(ctx: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            NOTIFICATION_ID,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_UPDATE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Pembaruan AnimeX tersedia")
            .setContentText("Versi ${release.tag} siap dipasang")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Versi ${release.tag} siap dipasang. " +
                            "Ketuk untuk melihat catatan perubahan dan unduh.",
                    ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notif)
    }

    private fun postReadyNotification(ctx: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            NOTIFICATION_ID + 1,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_UPDATE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Update siap dipasang")
            .setContentText("v${release.tag} sudah terunduh. Ketuk untuk install.")
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
}
