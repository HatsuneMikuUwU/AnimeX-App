@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipEpisode
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.Translator
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.background.AnimatedBackground
import com.uwu.animex.ui.background.LocalAnimatedBackgroundActive
import com.uwu.animex.ui.background.rememberActiveBackground
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppDropdownMenu
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.BatteryOptimizationDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.DiscussionRowPlaceholder
import com.uwu.animex.ui.common.EpisodeRowPlaceholder
import com.uwu.animex.ui.common.LocalOpenStatusSheet
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.SmallWavyProgress
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.UserAvatar
import com.uwu.animex.ui.common.WaitingCardPlaceholder
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.coverOrPoster
import com.uwu.animex.ui.common.defaultPlaceholder
import com.uwu.animex.ui.common.fmtNum
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.rememberHideStats
import com.uwu.animex.ui.common.rememberStatusLongClick
import com.uwu.animex.ui.common.rememberSyncedWatched
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.detail.shareAnime
import com.uwu.animex.ui.detail.shareEpisode
import com.uwu.animex.ui.discussion.DiscussionRow
import com.uwu.animex.ui.discussion.ReplyTarget
import com.uwu.animex.ui.discussion.ThreadState
import com.uwu.animex.ui.navigation.NotificationRouter
import com.uwu.animex.ui.onboarding.needsNotificationSetup
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import com.uwu.animex.ui.viewmodel.PlayerEpisodeList
import com.uwu.animex.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt



@Composable
internal fun YouTubeWatchHeader(
    title: String,
    viewsText: String?,
    dateText: String?,
    ratingText: String?,
    runtimeText: String?,
    hashtags: List<String>,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val metaLine =
        remember(viewsText, dateText, ratingText, runtimeText, hashtags, cs.primary, cs.onSurface, cs.onSurfaceVariant) {
            buildAnnotatedString {
                val stats = listOfNotNull(viewsText, dateText, ratingText, runtimeText)
                withStyle(SpanStyle(color = cs.onSurfaceVariant)) {
                    append(stats.joinToString("  •  "))
                }
                if (hashtags.isNotEmpty()) {
                    if (stats.isNotEmpty()) append("  ")
                    withStyle(SpanStyle(color = cs.primary, fontWeight = FontWeight.Medium)) {
                        append(hashtags.joinToString(" ") { "#${it.replace(" ", "")}" })
                    }
                }
                append("  ")
                withStyle(SpanStyle(color = cs.onSurface, fontWeight = FontWeight.SemiBold)) {
                    append("...selengkapnya")
                }
            }
        }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 14.dp)
            .padding(top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            metaLine,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun YouTubeChannelBar(
    poster: String?,
    animeTitle: String,
    subtitle: String,
    alertActive: Boolean,
    alertEnabled: Boolean,
    onToggleAlert: () -> Unit,
    onOpenAnime: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .then(
                    if (onOpenAnime != null) {
                        Modifier.clickable(onClick = hapticClick(block = onOpenAnime))
                    } else {
                        Modifier
                    },
                ).padding(vertical = 2.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh),
            ) {
                Poster(poster, Modifier.fillMaxSize(), radius = 0.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    animeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        if (alertEnabled) {
            Row(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (alertActive) cs.primaryContainer else cs.surfaceContainer)
                    .clickable(onClick = hapticClick(block = onToggleAlert))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (alertActive) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    contentDescription = if (alertActive) "Matikan notifikasi" else "Aktifkan notifikasi",
                    tint = if (alertActive) cs.onPrimaryContainer else cs.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (alertActive) "Unsubscribe" else "Subscribe",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (alertActive) cs.onPrimaryContainer else cs.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun YouTubeActionPillsRow(
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    status: WatchStatus?,
    rewatching: Boolean,
    statusEnabled: Boolean,
    onStatusClick: () -> Unit,
    onShare: () -> Unit,
    currentDownload: Downloads.Item?,
    onStartDownload: () -> Unit,
    chips: List<WatchChip>,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    var dlMenu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onPrev != null || onNext != null) {
            Row(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .then(if (onPrev != null) Modifier.clickable(onClick = hapticClick(block = onPrev)) else Modifier)
                        .padding(start = 12.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.SkipPrevious,
                        contentDescription = "Episode sebelumnya",
                        tint = cs.onSurface.copy(alpha = if (onPrev != null) 1f else 0.38f),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Sblm",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = if (onPrev != null) 1f else 0.38f),
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(cs.outlineVariant),
                )
                Row(
                    Modifier
                        .fillMaxHeight()
                        .then(if (onNext != null) Modifier.clickable(onClick = hapticClick(block = onNext)) else Modifier)
                        .padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Lanjut",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = if (onNext != null) 1f else 0.38f),
                    )
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = "Episode selanjutnya",
                        tint = cs.onSurface.copy(alpha = if (onNext != null) 1f else 0.38f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Row(
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainer.copy(alpha = if (statusEnabled) 1f else 0.5f))
                .then(
                    if (statusEnabled) {
                        Modifier.clickable(onClick = hapticClick(block = onStatusClick))
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (rewatching) Icons.Outlined.Repeat else status?.icon ?: Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (rewatching) "Nonton Ulang" else status?.label ?: "Atur status",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
                maxLines = 1,
            )
        }

        YouTubePillActionChip(
            icon = Icons.Outlined.Share,
            label = "Bagikan",
            onClick = onShare,
        )

        Box {
            val dlLabel =
                when (currentDownload?.status) {
                    null -> "Unduh"
                    Downloads.Status.QUEUED -> "Antrean..."
                    Downloads.Status.DOWNLOADING ->
                        if (currentDownload.percent >= 0f) {
                            "${currentDownload.percent.toInt()}%"
                        } else {
                            "Mengunduh"
                        }
                    Downloads.Status.PAUSED -> "Dijeda"
                    Downloads.Status.COMPLETED -> "Tersimpan"
                    Downloads.Status.FAILED -> "Gagal unduh"
                }
            val dlHighlighted = currentDownload?.status == Downloads.Status.COMPLETED
            Row(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (dlHighlighted) cs.secondaryContainer else cs.surfaceContainer)
                    .clickable(
                        onClick = hapticClick {
                            if (currentDownload == null) onStartDownload() else dlMenu = true
                        },
                    ).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (currentDownload?.status) {
                    null -> Icon(Icons.Outlined.Download, null, tint = cs.onSurface, modifier = Modifier.size(18.dp))
                    Downloads.Status.QUEUED -> AppLoadingIndicator(Modifier.size(16.dp))
                    Downloads.Status.DOWNLOADING ->
                        if (currentDownload.percent >= 0f) {
                            SmallWavyProgress(
                                progress = { (currentDownload.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            AppLoadingIndicator(Modifier.size(16.dp))
                        }
                    Downloads.Status.PAUSED ->
                        Icon(Icons.Outlined.Pause, null, tint = cs.onSurface, modifier = Modifier.size(18.dp))
                    Downloads.Status.COMPLETED ->
                        Icon(
                            Icons.Outlined.CheckCircle,
                            null,
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    Downloads.Status.FAILED ->
                        Icon(Icons.Outlined.Error, null, tint = cs.error, modifier = Modifier.size(18.dp))
                }
                Text(
                    dlLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (dlHighlighted) cs.onSecondaryContainer else cs.onSurface,
                    maxLines = 1,
                )
            }
            AppDropdownMenu(expanded = dlMenu, onDismissRequest = { dlMenu = false }) {
                val item = currentDownload ?: return@AppDropdownMenu
                val id = item.id
                when (item.status) {
                    Downloads.Status.QUEUED, Downloads.Status.DOWNLOADING -> {
                        DropdownMenuItem(
                            text = { Text("Pause") },
                            onClick = hapticClick {
                                dlMenu = false
                                Downloads.pause(ctx, id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Batalkan") },
                            onClick = hapticClick {
                                dlMenu = false
                                Downloads.remove(ctx, id)
                            },
                        )
                    }
                    Downloads.Status.PAUSED -> {
                        DropdownMenuItem(
                            text = { Text("Lanjutkan") },
                            onClick = hapticClick {
                                dlMenu = false
                                Downloads.resume(ctx, id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus") },
                            onClick = hapticClick {
                                dlMenu = false
                                Downloads.remove(ctx, id)
                            },
                        )
                    }
                    Downloads.Status.FAILED -> {
                        DropdownMenuItem(
                            text = { Text("Coba lagi") },
                            onClick = hapticClick {
                                dlMenu = false
                                Downloads.retry(ctx, id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus") },
                            onClick = hapticClick {
                                dlMenu = false
                                Downloads.remove(ctx, id)
                            },
                        )
                    }
                    Downloads.Status.COMPLETED ->
                        DropdownMenuItem(
                            text = { Text("Hapus unduhan") },
                            onClick = hapticClick {
                                dlMenu = false
                                Downloads.remove(ctx, id)
                            },
                        )
                }
            }
        }

        chips.forEach { chip ->
            YouTubePillActionChip(
                icon = chip.icon,
                label = chip.label,
                highlighted = chip.highlighted,
                onClick = chip.onClick,
            )
        }
    }
}

@Composable
private fun YouTubePillActionChip(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (highlighted) cs.secondaryContainer else cs.surfaceContainer
    val fg = if (highlighted) cs.onSecondaryContainer else cs.onSurface
    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
internal fun YouTubeFilterChipsRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    Row(
        modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) cs.primary else cs.surfaceContainer,
                label = "ytFilterBg",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) cs.onPrimary else cs.onSurface,
                label = "ytFilterFg",
            )
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = hapticClick { onSelect(index) },
                    ).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}
