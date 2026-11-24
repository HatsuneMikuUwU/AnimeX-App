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
internal fun WatchSortButton(
    newestFirst: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val flip by animateFloatAsState(
        targetValue = if (newestFirst) 1f else -1f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "sortFlip",
    )
    val container by animateColorAsState(
        targetValue = if (newestFirst) cs.surfaceContainer else cs.secondaryContainer,
        animationSpec = motion.fastEffectsSpec(),
        label = "sortContainer",
    )
    Row(
        modifier =
            Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .clickable(onClick = hapticClick(block = onClick))
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Sort,
            contentDescription = if (newestFirst) "Urutan: terbaru dulu" else "Urutan: terlama dulu",
            tint = if (newestFirst) cs.onSurface else cs.onSecondaryContainer,
            modifier = Modifier.size(18.dp).graphicsLayer { scaleY = flip },
        )
        Text(
            if (newestFirst) "Terbaru" else "Terlama",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (newestFirst) cs.onSurface else cs.onSecondaryContainer,
        )
    }
}

internal fun LazyListScope.watchSimilarFeedItems(
    similar: List<com.uwu.animex.data.api.SimilarMovie>,
    loading: Boolean,
    onOpenAnime: (String) -> Unit,
) {
    when {
        loading && similar.isEmpty() ->
            items(3, key = { "recs-loading-$it" }) {
                WaitingCardPlaceholder(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
            }
        similar.isEmpty() ->
            item(key = "recs-empty") {
                Text(
                    "Belum ada saran serupa untuk anime ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        else ->
            items(
                similar,
                key = { "yt-rec-${it.movie.id ?: it.movie.title.orEmpty()}" },
                contentType = { "ytRec" },
            ) { sm ->
                YouTubeRecommendationCard(
                    movie = sm.movie,
                    onOpen = onOpenAnime,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
    }
}

@Composable
private fun YouTubeRecommendationCard(
    movie: Movie,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val statusLongClick = rememberStatusLongClick(movie)
    val openStatus = LocalOpenStatusSheet.current
    val hideStats = rememberHideStats()

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceContainer)
            .hapticClick(
                onLongClick = statusLongClick,
                onClick = { movie.id?.let(onOpen) },
            ).padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(cs.surfaceContainerHigh),
        ) {
            Poster(
                movie.coverOrPoster(),
                Modifier.fillMaxSize(),
                radius = 12.dp,
            )
            val badgeParts =
                listOfNotNull(
                    movie.type?.takeIf { it.isNotBlank() },
                    movie.episode_index?.takeIf { it.isNotBlank() }?.let { "Ep $it" },
                    movie.score?.takeIf { it.isNotBlank() }?.let { "★ $it" },
                )
            if (badgeParts.isNotEmpty()) {
                Text(
                    badgeParts.joinToString(" • "),
                    color = cs.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.surfaceContainerHighest.copy(alpha = 0.9f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh),
            ) {
                Poster(movie.image_poster ?: movie.image_cover, Modifier.fillMaxSize(), radius = 0.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    movie.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaParts =
                    listOfNotNull(
                        movie.studio?.takeIf { it.isNotBlank() }
                            ?: movie.genre?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
                        movie.views?.takeIf { it.isNotBlank() && !hideStats }?.let { "${fmtNum(it)} ditonton" },
                        movie.year?.takeIf { it.isNotBlank() },
                    )
                if (metaParts.isNotEmpty()) {
                    Text(
                        metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            IconButton(
                onClick = hapticClick { openStatus(movie) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Opsi anime",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

internal fun LazyListScope.watchEpisodeItems(
    movieId: String?,
    list: PlayerEpisodeList,
    currentEpId: String,
    fallbackImage: String?,
    malWatched: Int?,
    aniZip: com.uwu.animex.data.api.AniZipInfo?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDownload: (Episode) -> Unit,
    onPick: (Episode) -> Unit,
) {
    if (movieId == null) {
        item(key = "episodes-unavailable") { WatchMessage("Daftar episode gak tersedia", null) }
        return
    }
    val error = list.error
    when {
        error != null && list.episodes.isEmpty() ->
            item(key = "episodes-error") { WatchMessage(error, onRetry) }
        list.loading && list.episodes.isEmpty() ->
            items(8, key = { "episode-placeholder-$it" }) { EpisodeRowPlaceholder(horizontalPadding = 12.dp) }
        else -> {
            val pinned =
                list.episodes.firstOrNull { it.id == currentEpId }
                    ?: list.pinned?.takeIf { it.id == currentEpId }
            val rest = if (pinned == null) list.episodes else list.episodes.filter { it.id != pinned.id }

            @Composable
            fun WatchYouTubeEpisodeRow(ep: Episode) {
                val epDownload by remember(ep.id) { Downloads.itemFlow(ep.id) }
                    .collectAsStateWithLifecycle(initialValue = Downloads.item(ep.id))
                YouTubeEpisodeListItem(
                    ep = ep,
                    fallbackImage = fallbackImage,
                    download = epDownload,
                    malWatched = malWatched,
                    meta = ep.index?.trim()?.toIntOrNull()?.let { aniZip?.episodes?.get(it) },
                    current = ep.id == currentEpId,
                    onDownload = { onDownload(ep) },
                    onClick = { onPick(ep) },
                )
            }

            if (pinned != null) {
                item(key = "episode-current", contentType = "episode") { WatchYouTubeEpisodeRow(pinned) }
            }
            items(
                rest,
                key = { it.id ?: "${it.index}-${it.title}" },
                contentType = { "episode" },
            ) { ep -> WatchYouTubeEpisodeRow(ep) }
            if (list.moreError) {
                item(key = "episodes-more-error") { WatchMessage("Gagal muat episode lainnya", onLoadMore) }
            } else if (list.loadingMore) {
                items(3, key = { "episode-more-placeholder-$it" }, contentType = { "episodePlaceholder" }) {
                    EpisodeRowPlaceholder(horizontalPadding = 12.dp)
                }
            }
        }
    }
}

@Composable
private fun YouTubeEpisodeListItem(
    ep: Episode,
    fallbackImage: String?,
    download: Downloads.Item?,
    malWatched: Int?,
    meta: AniZipEpisode?,
    current: Boolean,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val hideStats = rememberHideStats()
    val watch by remember(ep.id) { Progress.watchFlow(ep.id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(ep.id))
    val progress = Progress.fractionOf(watch)
    val doneInMal =
        malWatched != null &&
            (ep.index?.trim()?.toIntOrNull()?.let { it <= malWatched } ?: false)
    val done = Progress.isDoneWatch(watch) || doneInMal
    val rawTitle = meta?.title?.takeIf { it.isNotBlank() } ?: ep.title?.takeIf { it.isNotBlank() }
    val displayTitle =
        if (rawTitle == null) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. $rawTitle"

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) cs.primaryContainer else cs.surfaceContainer)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(160.dp, 90.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(cs.surfaceContainerHighest),
        ) {
            Poster(
                meta?.image ?: ep.image?.takeIf { it.isNotBlank() } ?: fallbackImage,
                Modifier.fillMaxSize(),
                radius = 10.dp,
                keepPrevious = true,
            )

            if (current) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(cs.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(cs.primary)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Diputar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimary,
                        )
                    }
                }
            } else {
                val badgeText =
                    meta?.runtime?.let { "${it}:00" }
                        ?: ep.index?.takeIf { it.isNotBlank() }?.let { "EP $it" }
                if (badgeText != null) {
                    Text(
                        badgeText,
                        color = cs.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(cs.surfaceContainerHighest.copy(alpha = 0.9f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                if (done) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cs.primaryContainer)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(Icons.Outlined.Check, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(11.dp))
                        Text(
                            "Ditonton",
                            color = cs.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if ((done || progress > 0f) && !current) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(cs.surfaceVariant.copy(alpha = 0.7f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(if (done) 1f else progress.coerceIn(0f, 1f))
                            .background(cs.primary),
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 90.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Text(
                displayTitle,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
                fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                color = if (current) cs.primary else cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subInfo =
                listOfNotNull(
                    ep.views?.takeIf { it.isNotBlank() && !hideStats }?.let { "${fmtNum(it)} ditonton" },
                    meta?.rating?.takeIf { it.isNotBlank() }?.let { "★ ${it.replace('.', ',')}" },
                    AniZip.formatDate(meta?.airDate) ?: ep.key_time?.takeIf { it.isNotBlank() },
                )
            if (subInfo.isNotEmpty()) {
                Text(
                    subInfo.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun WatchMessage(
    message: String,
    onRetry: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            TextButton(onClick = hapticClick(block = onRetry), shapes = ButtonDefaults.shapes()) {
                Text("Coba lagi")
            }
        }
    }
}

@Composable
internal fun rememberEpisodeDownload(
    animeTitle: String,
    movieId: String?,
    movie: Movie?,
    snackbar: SnackbarHostState,
): (Episode) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingEp by remember { mutableStateOf<Episode?>(null) }
    var pick by remember { mutableStateOf<Pair<Episode, List<Server>>?>(null) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun startDownload(
        ep: Episode,
        server: Server,
    ) {
        val epId = ep.id ?: return
        val link = server.link ?: return
        Downloads.enqueue(
            ctx,
            epId,
            link,
            Downloads.Meta(
                movieId = movie?.id ?: movieId.orEmpty(),
                movieTitle = movie?.title ?: animeTitle,
                epIndex = ep.index,
                epTitle = ep.title,
                image = movie?.image_poster,
                quality = server.quality,
                views = movie?.views,
                favorites = movie?.favorites,
            ),
        )
        snackbar.show(scope, "Lagi ngunduh Episode ${ep.index.orEmpty()}")
        if (BatteryOptimization.shouldPrompt(ctx)) showBatteryDialog = true
    }

    fun loadServers(ep: Episode) {
        val epId = ep.id ?: return
        scope.launch {
            runSuspendCatching { Api.servers(epId) }
                .onSuccess { all ->
                    val direct =
                        all
                            .filter { it.isDirect && !it.link.isNullOrBlank() }
                            .sortedByDescending { it.qualityValue }
                    when {
                        direct.isEmpty() ->
                            snackbar.show(scope, "Gak ada server yang bisa dipakai buat unduh")
                        direct.size == 1 -> startDownload(ep, direct.first())
                        else -> pick = ep to direct
                    }
                }.onFailure {
                    snackbar.show(scope, "Gagal muat server: ${it.toUserMessage()}")
                }
        }
    }

    fun proceedDownload(ep: Episode) {
        if (!asked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        loadServers(ep)
    }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val ep = pendingEp
            pendingEp = null
            if (uri == null) return@rememberLauncherForActivityResult
            Downloads.setFolder(ctx, uri)
            if (ep != null) proceedDownload(ep)
        }

    val download: (Episode) -> Unit = { ep ->
        val folderOk =
            Downloads.folderUri.value?.let { u ->
                runCatching {
                    DocumentFile.fromTreeUri(ctx, Uri.parse(u))?.canWrite() == true
                }.getOrDefault(false)
            } == true
        if (folderOk) {
            proceedDownload(ep)
        } else {
            pendingEp = ep
            folderPicker.launch(Downloads.folderUri.value?.let(Uri::parse))
        }
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

    pick?.let { (ep, servers) ->
        AppDialog(
            icon = Icons.Outlined.Download,
            onDismiss = { pick = null },
            title = "Mau kualitas yang mana?",
            text = {
                Column {
                    servers.forEach { sv ->
                        DialogOptionRow(
                            label =
                                sv.quality?.takeIf { it.isNotBlank() } ?: sv.name?.takeIf { it.isNotBlank() }
                                    ?: "Default",
                            selected = false,
                        ) {
                            pick = null
                            startDownload(ep, sv)
                        }
                    }
                }
            },
            confirmButton = { DialogCancelButton("Gak usah deh") { pick = null } },
        )
    }

    return download
}
