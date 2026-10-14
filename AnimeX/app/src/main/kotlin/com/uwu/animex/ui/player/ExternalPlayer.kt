package com.uwu.animex.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.uwu.animex.data.local.PlayerPrefs

/** Daftar package player eksternal yang umum (port dari Animiru, disederhanakan). */
object ExternalPlayer {
    data class App(
        val packageName: String,
        val label: String,
    )

    val KNOWN =
        listOf(
            App("is.xyz.mpv", "mpv"),
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

    fun open(
        context: Context,
        url: String,
        title: String? = null,
    ): Boolean {
        if (url.isBlank()) {
            Toast.makeText(context, "Link videonya kosong nih", Toast.LENGTH_SHORT).show()
            return false
        }
        val pkg = PlayerPrefs.externalPackage.value
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("title", title)
                putExtra(Intent.EXTRA_TITLE, title)
                if (pkg.isNotBlank()) setPackage(pkg)
            }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            // fallback tanpa package spesifik
            try {
                intent.setPackage(null)
                context.startActivity(Intent.createChooser(intent, "Buka pake"))
                true
            } catch (_: Exception) {
                Toast.makeText(context, "Gak nemu player eksternal", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
