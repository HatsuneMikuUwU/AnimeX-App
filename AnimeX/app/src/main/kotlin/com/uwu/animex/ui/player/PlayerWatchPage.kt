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

internal data class WatchChip(
    val icon: ImageVector,
    val label: String,
    val highlighted: Boolean = false,
    val onClick: () -> Unit,
)

private val WATCH_SECTIONS = listOf("Episode", "Saran")

@Composable
internal fun PlayerWatchPage(
    animeTitle: String,
    movieId: String?,
    currentEpId: String,
    currentIndex: String?,
    uploadDate: String? = null,
    playerViewModel: PlayerViewModel,
    commentsViewModel: DiscussionViewModel,
    commentsOpen: Boolean,
    onOpenComments: () -> Unit,
    onCloseComments: () -> Unit,
    descriptionOpen: Boolean = false,
    onOpenDescription: () -> Unit = {},
    onCloseDescription: () -> Unit = {},
    chips: List<WatchChip>,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPickEpisode: (Episode) -> Unit,
    onOpenAnime: (String) -> Unit,
    onNeedLogin: () -> Unit,
    onMetadataResolved: (episodeTitle: String, animeTitle: String, poster: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var section by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val episodeList by playerViewModel.episodeList.collectAsStateWithLifecycle()
    val aniZip by playerViewModel.aniZip.collectAsStateWithLifecycle()
    val commentsState by commentsViewModel.state.collectAsStateWithLifecycle()
    val alerts by EpisodeAlerts.alerts.collectAsStateWithLifecycle()
    val synced = rememberSyncedWatched(movieId)
    val hideStats = rememberHideStats()
    val ctx = LocalContext.current
    val openStatus = LocalOpenStatusSheet.current
    var movie by remember(movieId) { mutableStateOf<Movie?>(null) }
    var similar by remember(movieId) { mutableStateOf<List<com.uwu.animex.data.api.SimilarMovie>>(emptyList()) }
    var similarLoading by remember(movieId) { mutableStateOf(false) }

    LaunchedEffect(movieId) {
        if (movieId == null) return@LaunchedEffect
        playerViewModel.openEpisodeList(movieId, currentIndex)
        playerViewModel.loadAniZip(movieId)
        movie = runSuspendCatching { Api.detail(movieId) }.getOrNull()
    }
    LaunchedEffect(movie?.id, movieId, synced.source) {
        val m = movie ?: return@LaunchedEffect
        if (movieId == null) return@LaunchedEffect
        val withId = m.copy(id = movieId)
        runCatching {
            when (synced.source) {
                SyncSource.ANILIST -> AniList.preload(withId)
                SyncSource.MAL -> Mal.preload(withId)
                else -> Unit
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }
    LaunchedEffect(movieId, currentIndex) {
        if (movieId == null) return@LaunchedEffect
        playerViewModel.pinEpisode(movieId, currentIndex)
    }
    LaunchedEffect(movieId) {
        if (movieId == null) return@LaunchedEffect
        similarLoading = true
        similar = runSuspendCatching {
            com.uwu.animex.data.api.SimilarRepo
                .loadById(movieId)
        }.getOrDefault(emptyList())
        similarLoading = false
    }

    val download = rememberEpisodeDownload(animeTitle, movieId, movie, snackbar)
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(
        nearEnd,
        section,
        episodeList.hasMore,
        episodeList.loadingMore,
        episodeList.loading,
        episodeList.moreError,
    ) {
        if (section == 0 &&
            nearEnd &&
            episodeList.hasMore &&
            !episodeList.loadingMore &&
            !episodeList.loading &&
            !episodeList.moreError
        ) {
            playerViewModel.loadMoreEpisodes()
        }
    }

    val fallbackImage =
        movie?.image_cover?.takeIf { it.isNotBlank() } ?: movie?.image_poster?.takeIf { it.isNotBlank() }

    val curEpisodeObj =
        remember(episodeList.episodes, episodeList.pinned, currentEpId, currentIndex) {
            episodeList.episodes.firstOrNull { it.id == currentEpId }
                ?: episodeList.pinned?.takeIf { it.id == currentEpId }
                ?: Episode(id = currentEpId, index = currentIndex)
        }

    val epNumber = currentIndex?.trim()?.takeIf { it.isNotBlank() }
    val epMeta = epNumber?.toIntOrNull()?.let { aniZip?.episodes?.get(it) }
    val epTitle = epMeta?.title?.trim()?.takeIf { it.isNotBlank() } ?: curEpisodeObj.title?.trim()?.takeIf { it.isNotBlank() }
    val resolvedAnimeTitle = movie?.title?.takeIf { it.isNotBlank() } ?: animeTitle
    val headerTitle =
        when {
            epTitle != null -> epTitle
            epNumber != null -> "$resolvedAnimeTitle — Episode $epNumber"
            else -> resolvedAnimeTitle
        }
    val miniEpisodeLabel =
        when {
            epNumber != null && epTitle != null -> "Ep $epNumber • $epTitle"
            epNumber != null -> "Episode $epNumber"
            else -> headerTitle
        }
    val posterUrl = movie?.image_poster?.takeIf { it.isNotBlank() } ?: movie?.image_cover?.takeIf { it.isNotBlank() }

    LaunchedEffect(miniEpisodeLabel, resolvedAnimeTitle, posterUrl) {
        onMetadataResolved(miniEpisodeLabel, resolvedAnimeTitle, posterUrl)
    }

    val currentDownload by remember(currentEpId) { Downloads.itemFlow(currentEpId) }
        .collectAsStateWithLifecycle(initialValue = Downloads.item(currentEpId))

    val rawViews =
        if (hideStats) {
            null
        } else {
            curEpisodeObj.views?.takeIf { it.isNotBlank() } ?: movie?.views?.takeIf { it.isNotBlank() }
        }
    val viewsDisplay = rawViews?.let { "${fmtNum(it)} ditonton" }
    val dateDisplay = AniZip.formatDate(epMeta?.airDate) ?: uploadDate?.takeIf { it.isNotBlank() }
    val genreTags =
        remember(movie?.genre) {
            movie
                ?.genre
                ?.split(',', '/', '•')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(3)
                .orEmpty()
        }

    val alertActive = movieId != null && alerts.containsKey(movieId)

    val animatedBgActive = LocalAnimatedBackgroundActive.current
    val appearance by Appearance.settings.collectAsStateWithLifecycle()

    Box(modifier.background(cs.background)) {
        if (animatedBgActive) {
            AnimatedBackground(
                type = rememberActiveBackground(),
                enableParallax = appearance.backgroundParallax,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPad),
        ) {
            item(key = "yt-header") {
                YouTubeWatchHeader(
                    title = headerTitle,
                    viewsText = viewsDisplay,
                    dateText = dateDisplay,
                    ratingText = (epMeta?.rating?.takeIf { it.isNotBlank() } ?: movie?.score?.takeIf { it.isNotBlank() })
                        ?.let { "★ ${it.replace('.', ',')}" },
                    runtimeText = epMeta?.runtime?.let { "$it mnt" },
                    hashtags = genreTags,
                    onClick = onOpenDescription,
                )
            }

            item(key = "yt-channel") {
                YouTubeChannelBar(
                    poster = posterUrl,
                    animeTitle = resolvedAnimeTitle,
                    subtitle =
                        listOfNotNull(
                            movie?.studio?.takeIf { it.isNotBlank() } ?: movie?.type?.takeIf { it.isNotBlank() },
                            movie
                                ?.favorites
                                ?.takeIf { it.isNotBlank() && !hideStats }
                                ?.let { "${fmtNum(it)} favorit" },
                        ).joinToString(" • "),
                    alertActive = alertActive,
                    alertEnabled = movie != null && movieId != null,
                    onToggleAlert = {
                        val m = movie ?: return@YouTubeChannelBar
                        val id = movieId ?: return@YouTubeChannelBar
                        if (alertActive) {
                            EpisodeAlerts.disable(id)
                            snackbar.show(snackScope, "Notifikasi episode baru dimatikan")
                        } else {
                            EpisodeAlerts.enable(m.copy(id = id), episodeList.episodes.size)
                            if (needsNotificationSetup(ctx)) NotificationRouter.pendingOpenSetup.value = true
                            snackbar.show(snackScope, "Notifikasi episode baru diaktifkan")
                        }
                    },
                    onOpenAnime = movieId?.let { id -> { onOpenAnime(id) } },
                )
            }

            item(key = "yt-actions") {
                YouTubeActionPillsRow(
                    onPrev = onPrev,
                    onNext = onNext,
                    status = synced.status,
                    statusEnabled = movie != null && movieId != null,
                    onStatusClick = { movie?.let { openStatus(it.copy(id = movieId)) } },
                    onShare = {
                        if (movieId != null) {
                            shareEpisode(
                                ctx,
                                resolvedAnimeTitle,
                                movieId,
                                currentEpId,
                                currentIndex,
                            )
                        } else {
                            shareAnime(ctx, Movie(id = movieId, title = animeTitle))
                        }
                    },
                    currentDownload = currentDownload,
                    onStartDownload = { download(curEpisodeObj) },
                    chips = chips,
                )
            }

            item(key = "yt-comments-teaser") {
                CommentsPreviewCard(
                    state = commentsState,
                    onClick = onOpenComments,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            item(key = "yt-filter-chips") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YouTubeFilterChipsRow(
                        labels = WATCH_SECTIONS,
                        selected = section,
                        onSelect = { section = it },
                        modifier = Modifier.weight(1f),
                    )
                    if (section == 0 && movieId != null) {
                        Spacer(Modifier.width(8.dp))
                        WatchSortButton(
                            newestFirst = episodeList.newestFirst,
                            onClick = { playerViewModel.setEpisodeOrder(movieId, !episodeList.newestFirst) },
                        )
                    }
                }
            }

            when (section) {
                0 -> {
                    watchEpisodeItems(
                        movieId = movieId,
                        animeTitle = resolvedAnimeTitle,
                        list = episodeList,
                        currentEpId = currentEpId,
                        fallbackImage = fallbackImage,
                        malWatched = synced.watched,
                        aniZip = aniZip,
                        onRetry = { movieId?.let { playerViewModel.openEpisodeList(it, currentIndex, force = true) } },
                        onLoadMore = playerViewModel::loadMoreEpisodes,
                        onDownload = download,
                        onPick = onPickEpisode,
                    )
                }
                else -> {
                    watchSimilarFeedItems(
                        similar = similar,
                        loading = similarLoading,
                        onOpenAnime = onOpenAnime,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad),
        )

        AnimatedVisibility(
            visible = descriptionOpen,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
            enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        ) {
            YouTubeDescriptionSheetPanel(
                headerTitle = headerTitle,
                animeTitle = resolvedAnimeTitle,
                epNumber = epNumber,
                epMeta = epMeta,
                movie = movie,
                viewsText = viewsDisplay,
                dateText = dateDisplay,
                hideStats = hideStats,
                onOpenAnime = movieId?.let { id -> { onOpenAnime(id) } },
                onClose = onCloseDescription,
            )
        }

        AnimatedVisibility(
            visible = commentsOpen,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
            enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        ) {
            var dragY by remember { mutableFloatStateOf(0f) }
            val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }
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
                                        if (close) onCloseComments()
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
                    EpisodeCommentsPanel(
                        viewModel = commentsViewModel,
                        onClose = onCloseComments,
                        onNeedLogin = onNeedLogin,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeWatchHeader(
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
private fun YouTubeChannelBar(
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
private fun YouTubeActionPillsRow(
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    status: WatchStatus?,
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
                status?.icon ?: Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Text(
                status?.label ?: "Atur status",
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
private fun YouTubeFilterChipsRow(
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

@Composable
private fun YouTubeDescriptionSheetPanel(
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

@Composable
private fun WatchSortButton(
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

private fun LazyListScope.watchSimilarFeedItems(
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
                .padding(top = 10.dp),
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

private fun LazyListScope.watchEpisodeItems(
    movieId: String?,
    animeTitle: String,
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
                    animeTitle = animeTitle,
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
    animeTitle: String,
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
    val displayTitle = rawTitle ?: "Episode ${ep.index.orEmpty()}"

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
            Text(
                animeTitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subInfo =
                listOfNotNull(
                    if (rawTitle != null) ep.index?.takeIf { it.isNotBlank() }?.let { "Ep $it" } else null,
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
private fun WatchMessage(
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
private fun rememberEpisodeDownload(
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

@Composable
private fun CommentsPreviewCard(
    state: UiState<List<UserApi.Discussion>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val session by UserSession.user.collectAsStateWithLifecycle()
    val items =
        when (state) {
            is UiState.Ready -> state.value
            else -> emptyList()
        }
    val shape = RoundedCornerShape(14.dp)
    val motion = MaterialTheme.motionScheme
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(motion.defaultSpatialSpec())
            .clip(shape)
            .background(cs.surfaceContainer)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Komentar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = "Buka komentar",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        val phase =
            when {
                state is UiState.Loading -> 0
                state is UiState.Error -> 1
                items.isEmpty() -> 2
                else -> 3
            }
        AnimatedContent(
            targetState = phase,
            transitionSpec = { AppMotion.crossfade(motion) },
            label = "comments-preview",
        ) { p ->
            when (p) {
                0 ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).defaultPlaceholder())
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Memuat komentar...",
                            modifier = Modifier.weight(1f).defaultPlaceholder(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                1 ->
                    Text(
                        "Gagal memuat komentar. Ketuk buat coba lagi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                2 ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(session?.username, size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(cs.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "Tambahkan komentar...",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                else -> {
                    val top = items.firstOrNull()
                    if (top != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(top.username, size = 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                buildAnnotatedString {
                                    top.username?.takeIf { it.isNotBlank() }?.let { u ->
                                        withStyle(
                                            SpanStyle(
                                                color = cs.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                        ) {
                                            append("@$u  ")
                                        }
                                    }
                                    append(top.body())
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCommentsPanel(
    viewModel: DiscussionViewModel,
    onClose: () -> Unit,
    onNeedLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val actionBusy by viewModel.actionBusy.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val loadMoreError by viewModel.loadMoreError.collectAsStateWithLifecycle()
    val session by UserSession.user.collectAsStateWithLifecycle()
    val loggedIn = session?.isAuthenticated == true
    var draft by rememberSaveable { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    val threads = remember { HashMap<String, ThreadState>() }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val items =
        when (val s = state) {
            is UiState.Ready -> s.value
            else -> emptyList()
        }

    fun threadOf(id: String): ThreadState = threads.getOrPut(id) { ThreadState() }

    fun loadThread(
        parent: UserApi.Discussion,
        thread: ThreadState,
    ) {
        thread.expanded = true
        thread.loading = true
        viewModel.loadReplies(parent) { loaded ->
            thread.replies = loaded
            thread.loading = false
        }
    }

    fun toggleThread(parent: UserApi.Discussion) {
        val id = parent.id?.takeIf(String::isNotBlank) ?: return
        val thread = threadOf(id)
        if (thread.expanded) thread.expanded = false else loadThread(parent, thread)
    }

    fun startReply(target: ReplyTarget) {
        replyTarget = target
        if (target.mention && draft.isBlank()) draft = "@${target.username} "
    }

    fun cancelReply() {
        val target = replyTarget ?: return
        if (draft.trim() == "@${target.username}") draft = ""
        replyTarget = null
    }

    BackHandler(enabled = replyTarget != null) { cancelReply() }

    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { snackbar.show(snackScope, it) }
    }
    LaunchedEffect(replyTarget) {
        if (replyTarget != null) runCatching { focusRequester.requestFocus() }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore, loadingMore, loadMoreError, items.size) {
        if (nearEnd && hasMore && !loadingMore && !loadMoreError && items.isNotEmpty()) {
            viewModel.loadMore()
        }
    }

    Column(modifier.background(cs.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Komentar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = hapticClick(block = onClose)) {
                Icon(Icons.Outlined.Close, contentDescription = "Tutup komentar")
            }
        }
        HorizontalDivider(color = cs.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            UiStateContent(
                state = state,
                onRetry = { viewModel.refresh() },
                loading = { DiscussionListPlaceholder() },
            ) { _ ->
                if (items.isEmpty()) {
                    AnimatedEmptyState(
                        icon = Icons.Outlined.Forum,
                        title = "Belum ada komentar",
                        message = "Jadilah yang pertama!",
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(items, key = { index, d -> d.id ?: "item-$index" }) { _, d ->
                            val thread = d.id?.takeIf(String::isNotBlank)?.let(::threadOf)
                            DiscussionRow(
                                discussion = d,
                                currentUserId = session?.id,
                                loggedIn = loggedIn,
                                isEpisode = true,
                                actionBusy = actionBusy || sending,
                                thread = thread,
                                onToggleReplies = { toggleThread(d) },
                                onStartReply = ::startReply,
                                onEdit = { item, text, onComplete ->
                                    viewModel.edit(item, text) {
                                        onComplete()
                                        snackbar.show(snackScope, "Komentar diperbarui")
                                    }
                                },
                                onDelete = { item, onComplete ->
                                    viewModel.delete(item) {
                                        onComplete()
                                        snackbar.show(snackScope, "Komentar dihapus")
                                    }
                                },
                                onReport = { item, onComplete ->
                                    viewModel.report(item) {
                                        onComplete()
                                        snackbar.show(snackScope, "Laporan dikirim")
                                    }
                                },
                                onReact = { item, type -> viewModel.react(item, type) },
                                modifier = animateItemExpressive(),
                            )
                        }
                        if (loadMoreError) {
                            item(key = "load-more-retry") {
                                TextButton(
                                    onClick = hapticClick { viewModel.retryLoadMore() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text("Gagal muat lagi. Coba lagi") }
                            }
                        } else if (loadingMore) {
                            items(
                                count = 2,
                                key = { "load-more-placeholder-$it" },
                            ) { index ->
                                DiscussionRowPlaceholder(fraction = listOf(0.7f, 0.5f)[index % 2])
                            }
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
        CommentInputBar(
            draft = draft,
            onDraftChange = { draft = it },
            loggedIn = loggedIn,
            sending = sending,
            username = session?.username,
            placeholder = if (replyTarget != null) "Tulis balasan..." else "Tulis komentar...",
            replyTo = replyTarget,
            onCancelReply = ::cancelReply,
            focusRequester = focusRequester,
            onNeedLogin = onNeedLogin,
            onSend = { text ->
                val target = replyTarget
                if (target == null) {
                    viewModel.send(text) {
                        draft = ""
                        runCatching { listState.requestScrollToItem(0) }
                    }
                } else {
                    viewModel.reply(target.parent, text) {
                        draft = ""
                        replyTarget = null
                        snackbar.show(snackScope, "Balasan terkirim")
                        target.parent.id?.takeIf(String::isNotBlank)?.let { id ->
                            loadThread(target.parent, threadOf(id))
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun CommentInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    loggedIn: Boolean,
    sending: Boolean,
    username: String?,
    placeholder: String,
    replyTo: ReplyTarget?,
    onCancelReply: () -> Unit,
    focusRequester: FocusRequester,
    onNeedLogin: () -> Unit,
    onSend: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val canSend = loggedIn && !sending && draft.isNotBlank()
    if (!loggedIn) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainer)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Mau ikut komentar?",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = hapticClick(block = onNeedLogin), shapes = ButtonDefaults.shapes()) {
                Text("Login")
            }
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        if (replyTo != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Membalas ${replyTo.username}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        replyTo.snippet.replace('\n', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = hapticClick(block = onCancelReply)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Batal membalas", tint = cs.onSurfaceVariant)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserAvatar(
                username,
                size = 36.dp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cs.surfaceContainerHighest)
                    .then(if (loggedIn) Modifier else Modifier.hapticClick(onClick = onNeedLogin))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (loggedIn) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = !sending,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                } else {
                    Text(
                        "Login dulu buat komentar",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick =
                    hapticClick {
                        if (!loggedIn) {
                            onNeedLogin()
                        } else if (canSend) {
                            onSend(draft.trim())
                        }
                    },
                modifier = Modifier.size(44.dp),
            ) {
                when {
                    sending -> AppLoadingIndicator(Modifier.size(24.dp))
                    !loggedIn -> Icon(
                        Icons.AutoMirrored.Outlined.Login,
                        contentDescription = "Login",
                        tint = cs.onSurface,
                    )
                    else ->
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Kirim",
                            tint = if (canSend) cs.primary else cs.onSurface.copy(alpha = 0.38f),
                        )
                }
            }
        }
    }
}
