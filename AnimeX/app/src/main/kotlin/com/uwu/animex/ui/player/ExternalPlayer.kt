package com.uwu.animex.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.os.BundleCompat
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress

internal fun needsUriReadGrant(scheme: String?): Boolean = scheme.equals("content", ignoreCase = true)

@Composable
fun rememberExternalLauncher(onResult: (Intent?) -> Unit): ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult(it.data) }

object ExternalPlayer {
    private const val VLC_PACKAGE = "org.videolan.vlc"

    data class Result(
        val positionMs: Long,
        val durationMs: Long,
        val completed: Boolean,
        val crossedDone: Boolean,
    )

    data class App(
        val packageName: String,
        val label: String,
    )

    val KNOWN =
        listOf(
            App("is.xyz.mpv", "mpv"),
            App("app.marlboroadvance.mpvex", "mpvEx"),
            App("com.mxtech.videoplayer.ad", "MX Player"),
            App("com.mxtech.videoplayer.pro", "MX Player Pro"),
            App("com.mxtech.videoplayer.free", "MX Player Free"),
            App("org.videolan.vlc", "VLC"),
            App("live.mehiz.mpvkt", "mpvKt"),
            App("com.brouken.player", "Just Player"),
        )

    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        return KNOWN
            .filter { app ->
                runCatching { pm.getPackageInfo(app.packageName, 0) }.isSuccess
            }.map { app ->
                val label =
                    runCatching {
                        val info = pm.getApplicationInfo(app.packageName, 0)
                        pm.getApplicationLabel(info).toString()
                    }.getOrDefault(app.label)
                app.copy(label = label)
            }
    }

    fun record(
        epId: String,
        data: Intent?,
    ): Result? {
        val extras = data?.extras ?: return null

        fun number(vararg keys: String): Long? =
            keys.firstNotNullOfOrNull {
                BundleCompat.getSerializable(extras, it, Number::class.java)?.toLong()
            }
        val duration = number("duration", "extra_duration") ?: return null
        val position = number("position", "extra_position") ?: return null
        if (duration <= 0) return null
        val completed = extras.getString("end_by") == "playback_completion"
        val finalPosition = if (completed) duration else position.coerceIn(0L, duration)
        val crossed = Progress.save(epId, finalPosition, duration)
        Progress.flush()
        return Result(finalPosition, duration, completed, crossed)
    }

    fun open(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        epId: String,
        url: String,
        title: String? = null,
        onMessage: ((String) -> Unit)? = null,
    ): Boolean {
        val showMessage: (String) -> Unit = onMessage ?: { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        if (url.isBlank()) {
            showMessage("Link videonya kosong nih")
            return false
        }
        val pkg = PlayerPrefs.externalPackage.value
        val start = Progress.resumePosition(epId)
        val uri = Uri.parse(url)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                putExtra("title", title)
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra("return_result", true)
                if (start > 0) {
                    if (pkg == VLC_PACKAGE) putExtra("position", start) else putExtra("position", start.toInt())
                }
                if (needsUriReadGrant(uri.scheme)) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (pkg.isNotBlank()) setPackage(pkg)
            }
        return try {
            launcher.launch(intent)
            History.commit(epId)
            true
        } catch (_: Exception) {
            try {
                intent.setPackage(null)
                val chooser = Intent.createChooser(intent, "Buka pake")
                if (needsUriReadGrant(uri.scheme)) chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                launcher.launch(chooser)
                History.commit(epId)
                true
            } catch (_: Exception) {
                showMessage("Gak nemu player eksternal")
                false
            }
        }
    }
}
