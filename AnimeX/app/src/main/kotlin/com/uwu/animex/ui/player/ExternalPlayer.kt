package com.uwu.animex.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress

@Composable
fun rememberExternalLauncher(onResult: (Intent?) -> Unit): ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult(it.data) }

object ExternalPlayer {
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
            App("com.brouken.player", "Just Player"),
            App("com.simplemobiletools.clock", "Simple Music Player"),
            App("com.google.android.youtube", "YouTube"),
            App("com.google.android.apps.youtube.unplugged", "YouTube"),
        )

    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        return KNOWN.filter { app ->
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

    @Suppress("DEPRECATION")
    fun record(
        epId: String,
        data: Intent?,
    ): Result? {
        val extras = data?.extras ?: return null
        fun number(vararg keys: String): Long? = keys.firstNotNullOfOrNull { (extras.get(it) as? Number)?.toLong() }
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
    ): Boolean {
        if (url.isBlank()) {
            Toast.makeText(context, "Link videonya kosong nih", Toast.LENGTH_SHORT).show()
            return false
        }
        val pkg = PlayerPrefs.externalPackage.value
        val start = Progress.resumePosition(epId)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                putExtra("title", title)
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra("return_result", true)
                if (start > 0) putExtra("position", start.toInt())
                if (pkg.isNotBlank()) setPackage(pkg)
            }
        return try {
            launcher.launch(intent)
            History.commit(epId)
            true
        } catch (_: Exception) {
            try {
                intent.setPackage(null)
                launcher.launch(Intent.createChooser(intent, "Buka pake"))
                History.commit(epId)
                true
            } catch (_: Exception) {
                Toast.makeText(context, "Gak nemu player eksternal", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
