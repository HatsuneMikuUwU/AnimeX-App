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
                    rewatching = synced.rewatching,
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
