@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipEpisode
import com.uwu.animex.data.api.Translator
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.model.Episode
import com.uwu.animex.ui.common.AppDropdownMenu
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.SmallWavyProgress
import com.uwu.animex.ui.common.WavyLinearProgress
import com.uwu.animex.ui.common.hapticClick

@Composable
internal fun EpisodeRow(
    ep: Episode,
    fallbackImage: String?,
    download: Downloads.Item?,
    malWatched: Int?,
    modifier: Modifier = Modifier,
    meta: AniZipEpisode? = null,
    onDownload: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    current: Boolean = false,
    onClick: () -> Unit,
) {
    val watch by remember(ep.id) { Progress.watchFlow(ep.id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(ep.id))
    val progress = Progress.fractionOf(watch)
    val doneInMal =
        malWatched != null &&
            (
                ep.index
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let { it <= malWatched } ?: false
            )
    val done = Progress.isDoneWatch(watch) || doneInMal
    val originalTitle = meta?.title ?: ep.title
    val overview = meta?.overview
    val epKey = ep.id ?: "${ep.index}-${ep.title}"

    var showTranslated by rememberSaveable(epKey) { mutableStateOf(false) }
    var translatedOverview by remember(overview) { mutableStateOf(overview?.let { Translator.cached(it.trim()) }) }
    var translatedTitle by remember(
        originalTitle,
    ) { mutableStateOf(originalTitle?.let { Translator.cached(it.trim()) }) }
    var translating by remember(overview, originalTitle) { mutableStateOf(false) }
    var translateFailed by remember(overview, originalTitle) { mutableStateOf(false) }

    LaunchedEffect(showTranslated, overview, originalTitle) {
        if (!showTranslated || overview == null) return@LaunchedEffect
        if (translatedOverview != null &&
            (originalTitle.isNullOrBlank() || translatedTitle != null)
        ) {
            return@LaunchedEffect
        }
        translating = true
        translateFailed = false

        val needTitle = !originalTitle.isNullOrBlank() && translatedTitle == null
        val needOverview = translatedOverview == null
        val results =
            Translator.toIndonesian(
                listOfNotNull(
                    originalTitle?.takeIf { needTitle },
                    overview.takeIf { needOverview },
                ),
            )
        var next = 0
        if (needTitle) translatedTitle = results[next++]
        val ov = if (needOverview) results[next] else translatedOverview
        if (ov == null) {
            translateFailed = true
            showTranslated = false
        } else {
            translatedOverview = ov
        }
        translating = false
    }

    val shownTitle = if (showTranslated) translatedTitle ?: originalTitle else originalTitle
    val title = if (shownTitle.isNullOrBlank()) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. $shownTitle"
    val extraInfo =
        listOfNotNull(
            meta?.rating?.let { "Nilai: ${it.replace('.', ',')}" },
            meta?.runtime?.let { "${it}m" },
            AniZip.formatDate(meta?.airDate),
        )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .hapticClick(onLongClick = onLongClick, onClick = onClick),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(126.dp, 72.dp), Alignment.Center) {
                    Poster(
                        meta?.image ?: ep.image?.takeIf { it.isNotBlank() } ?: fallbackImage,
                        Modifier.matchParentSize(),
                        10.dp,
                        keepPrevious = true,
                    )
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                        if (done) {
                            Icon(Icons.Outlined.Check, contentDescription = "Udah kamu tonton", tint = Color.White)
                        } else {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White)
                        }
                    }
                    if (!done && progress > 0f) {
                        WavyLinearProgress(
                            progress = { progress },
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0x66FFFFFF),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).heightIn(min = 72.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (extraInfo.isNotEmpty()) {
                        Text(
                            extraInfo.joinToString("  •  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        ep.key_time?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (overview == null && onDownload != null) {
                    Box(Modifier.height(72.dp), contentAlignment = Alignment.Center) {
                        DownloadButton(download, onStart = onDownload)
                    }
                }
            }
            if (overview != null) {
                OverviewWithTranslate(
                    text = if (showTranslated) translatedOverview ?: overview else overview,
                    showTranslated = showTranslated,
                    loading = translating,
                    failed = translateFailed,
                    onToggle = { showTranslated = !showTranslated },
                    trailing =
                        onDownload?.let { start ->
                            @Composable { DownloadButton(download, onStart = start) }
                        },
                )
            }
        }
    }
}

@Composable
internal fun DownloadButton(
    item: Downloads.Item?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = hapticClick {
                if (item ==
                    null
                ) {
                    onStart()
                } else {
                    menu = true
                }
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            if (item == null) {
                Icon(Icons.Outlined.Download, contentDescription = "Unduh")
            } else {
                when (item.status) {
                    Downloads.Status.QUEUED ->
                        AppLoadingIndicator(Modifier.size(24.dp))
                    Downloads.Status.DOWNLOADING ->
                        if (item.percent >= 0f) {
                            SmallWavyProgress(
                                progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            AppLoadingIndicator(Modifier.size(24.dp))
                        }
                    Downloads.Status.PAUSED -> Icon(Icons.Outlined.Pause, contentDescription = "Lagi di-pause")
                    Downloads.Status.COMPLETED ->
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Udah kelar diunduh",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    Downloads.Status.FAILED ->
                        Icon(
                            Icons.Outlined.Error,
                            contentDescription = "Gagal",
                            tint = MaterialTheme.colorScheme.error,
                        )
                }
            }
        }
        AppDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (item == null) return@AppDropdownMenu
            val id = item.id
            when (item.status) {
                Downloads.Status.QUEUED, Downloads.Status.DOWNLOADING -> {
                    DropdownMenuItem(
                        text = { Text("Pause") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.pause(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Gak jadi") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.PAUSED -> {
                    DropdownMenuItem(
                        text = { Text("Lanjut") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.resume(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.FAILED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi dong") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.retry(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.COMPLETED ->
                    DropdownMenuItem(
                        text = { Text("Hapus file unduhannya") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
            }
        }
    }
}

@Composable
private fun OverviewWithTranslate(
    text: String,
    showTranslated: Boolean,
    loading: Boolean,
    failed: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick =
                hapticClick {
                    if (loading) return@hapticClick
                    onToggle()
                },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        ) {
            if (loading) {
                AppLoadingIndicator(Modifier.size(18.dp))
            } else {
                Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    loading -> "Lagi nerjemahin..."
                    failed -> "Gagal, coba lagi"
                    showTranslated -> "Tampilkan teks asli"
                    else -> "Terjemahkan"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        trailing?.invoke()
    }
}
