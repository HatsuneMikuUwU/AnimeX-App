package com.uwu.animex.ui.cuplix

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.BatteryOptimizationDialog
import com.uwu.animex.ui.detail.bestDirect
import com.uwu.animex.ui.detail.feedDirect
import kotlinx.coroutines.launch

internal const val OPEN_ENDED_CLIP_MS = 60_000L

internal fun cuplixDownloadId(clip: Cuplix): String = "cuplix:${cuplixKey(clip)}"

internal fun Cuplix.clipStartTimeMs(): Long = time_start?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

internal fun Cuplix.clipEndTimeMs(startMs: Long = clipStartTimeMs()): Long =
    time_end?.trim()?.toLongOrNull()?.takeIf { it > startMs } ?: (startMs + OPEN_ENDED_CLIP_MS)

internal suspend fun resolveClipServer(
    clip: Cuplix,
    force: Boolean = false,
    forPlayback: Boolean = false,
): Server? {
    val clipId = clip.id.orEmpty()
    val episodeId = clip.id_episode.orEmpty()
    val direct =
        clipId
            .takeIf { it.isNotBlank() }
            ?.let { runSuspendCatching { Api.cuplixServers(it).clipServer() }.getOrNull() }
            ?.takeIf { !it.link.isNullOrBlank() }
    return direct
        ?: runSuspendCatching {
            Api.servers(episodeId, force = force).let { if (forPlayback) it.feedDirect() else it.bestDirect() }
        }.getOrNull()
}

@Composable
internal fun rememberClipDownload(onMessage: (String) -> Unit): (Cuplix) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Cuplix?>(null) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun proceed(clip: Cuplix) {
        if (!asked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            val server = resolveClipServer(clip)
            val link = server?.link
            if (link.isNullOrBlank()) {
                onMessage("Gak ada server yang bisa dipakai buat unduh")
                return@launch
            }
            val start = clip.clipStartTimeMs()
            val end = clip.clipEndTimeMs(start)
            Downloads.enqueue(
                ctx,
                cuplixDownloadId(clip),
                link,
                Downloads.Meta(
                    movieId = clip.id_movie,
                    movieTitle = clip.anime?.takeIf { it.isNotBlank() } ?: clip.caption,
                    epIndex = clip.episode,
                    epTitle = clip.caption,
                    image = clip.poster?.takeIf { it.isNotBlank() } ?: clip.url_thumbnail,
                    quality = server.quality,
                    clipStartMs = start,
                    clipEndMs = end,
                ),
            )
            onMessage("Lagi ngunduh Cuplix")
            if (BatteryOptimization.shouldPrompt(ctx)) showBatteryDialog = true
        }
    }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val clip = pending
            pending = null
            if (uri == null) return@rememberLauncherForActivityResult
            Downloads.setFolder(ctx, uri)
            if (clip != null) proceed(clip)
        }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onDismiss = { showBatteryDialog = false },
            title = "Biar unduhan gak putus",
            message =
                "Hemat baterai bisa nahan unduhan AnimeX pas layar mati atau app di latar belakang. " +
                    "Matiin pembatasan baterai buat AnimeX supaya unduhan lanjut terus.",
        )
    }

    return { clip ->
        val folderOk =
            Downloads.folderUri.value?.let { u ->
                runCatching {
                    DocumentFile.fromTreeUri(ctx, Uri.parse(u))?.canWrite() == true
                }.getOrDefault(false)
            } == true
        if (folderOk) {
            proceed(clip)
        } else {
            pending = clip
            folderPicker.launch(Downloads.folderUri.value?.let(Uri::parse))
        }
    }
}
