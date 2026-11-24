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
internal fun YouTubeDescriptionSheetPanel(
    headerTitle: String,
    animeTitle: String,
    epNumber: String?,
    epMeta: AniZipEpisode?,
    movie: Movie?,
    viewsText: String?,
    dateText: String?,
    hideStats: Boolean,
    onOpenAnime: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var dragY by remember { mutableFloatStateOf(0f) }
    val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }

    val overview = epMeta?.overview?.trim()?.takeIf { it.isNotBlank() }
    var showTranslated by rememberSaveable(headerTitle) { mutableStateOf(false) }
    var translatedOverview by remember(overview) { mutableStateOf(overview?.let { Translator.cached(it) }) }
    var translating by remember(overview) { mutableStateOf(false) }

    LaunchedEffect(showTranslated, overview) {
        if (!showTranslated || overview == null || translatedOverview != null) return@LaunchedEffect
        translating = true
        val res = Translator.toIndonesian(listOf(overview))
        translatedOverview = res.firstOrNull()
        if (translatedOverview == null) showTranslated = false
        translating = false
    }

    BackHandler { onClose() }

    Surface(
        color = cs.background,
        shape = RectangleShape,
        shadowElevation = 8.dp,
        modifier =
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val close = dragY > dismissPx
                                dragY = 0f
                                if (close) onClose()
                            },
                            onDragCancel = { dragY = 0f },
                        ) { _, delta -> dragY += delta }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(cs.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Deskripsi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = hapticClick(block = onClose)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Tutup deskripsi")
                }
            }
            HorizontalDivider(color = cs.outlineVariant)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    headerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                )

                val scoreVal =
                    (epMeta?.rating?.takeIf { it.isNotBlank() } ?: movie?.score?.takeIf { it.isNotBlank() })
                        ?.replace('.', ',') ?: "—"
                val countVal =
                    if (hideStats) {
                        "—"
                    } else {
                        viewsText?.removeSuffix(" ditonton")
                            ?: movie?.favorites?.takeIf { it.isNotBlank() }?.let { fmtNum(it) }
                            ?: "—"
                    }
                val countLabel = if (viewsText != null) "Ditonton" else "Favorit"
                val releaseVal = dateText ?: movie?.year?.takeIf { it.isNotBlank() } ?: "—"

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.surfaceContainer)
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    YouTubeStatColumn(value = scoreVal, label = "Penilaian")
                    YouTubeStatColumn(value = countVal, label = countLabel)
                    YouTubeStatColumn(value = releaseVal, label = if (epNumber != null) "Rilis Ep $epNumber" else "Rilis")
                }

                if (overview != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Ringkasan Episode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (showTranslated) translatedOverview ?: overview else overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = hapticClick { if (!translating) showTranslated = !showTranslated },
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) {
                                if (translating) {
                                    AppLoadingIndicator(Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Outlined.Translate, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (showTranslated) "Teks asli" else "Terjemahkan",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                val synopsis = movie?.synopsis?.trim()?.takeIf { it.isNotBlank() }
                if (synopsis != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Sinopsis $animeTitle",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            synopsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                val metaBadges =
                    listOfNotNull(
                        movie?.type?.takeIf { it.isNotBlank() }?.let { "Tipe: $it" },
                        movie?.status?.takeIf { it.isNotBlank() }?.let { "Status: $it" },
                        movie?.studio?.takeIf { it.isNotBlank() }?.let { "Studio: $it" },
                        movie?.season?.takeIf { it.isNotBlank() }?.let { "Musim: $it" },
                        movie?.duration?.takeIf { it.isNotBlank() }?.let { "Durasi: $it" },
                    )
                if (metaBadges.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        metaBadges.forEach { badge ->
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cs.surfaceContainer)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                if (onOpenAnime != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceContainer)
                            .clickable(
                                onClick = hapticClick {
                                    onClose()
                                    onOpenAnime()
                                },
                            ).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Poster(
                            movie?.image_poster ?: movie?.image_cover,
                            Modifier.size(44.dp).clip(CircleShape),
                            radius = 22.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(animeTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Buka halaman detail anime",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun YouTubeStatColumn(
    value: String,
    label: String,
) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = cs.onSurface,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
