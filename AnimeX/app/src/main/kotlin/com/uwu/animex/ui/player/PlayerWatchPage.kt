@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.BatteryOptimizationDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.DiscussionRowPlaceholder
import com.uwu.animex.ui.common.EpisodeRowPlaceholder
import com.uwu.animex.ui.common.LocalOpenStatusSheet
import com.uwu.animex.ui.common.OverlayChip
import com.uwu.animex.ui.common.Poster
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
import com.uwu.animex.ui.detail.EpisodeRow
import com.uwu.animex.ui.discussion.DiscussionRow
import com.uwu.animex.ui.discussion.ReplyTarget
import com.uwu.animex.ui.discussion.ThreadState
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
    chips: List<WatchChip>,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPickEpisode: (Episode) -> Unit,
    onOpenAnime: (String) -> Unit,
    onNeedLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var section by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    val episodeList by playerViewModel.episodeList.collectAsStateWithLifecycle()
    val aniZip by playerViewModel.aniZip.collectAsStateWithLifecycle()
    val commentsState by commentsViewModel.state.collectAsStateWithLifecycle()
    val synced = rememberSyncedWatched(movieId)
    val openStatus = LocalOpenStatusSheet.current
    var movie by remember(movieId) { mutableStateOf<Movie?>(null) }
    var similar by remember(movieId) { mutableStateOf<List<com.uwu.animex.data.api.SimilarMovie>>(emptyList()) }
    var similarLoading by remember(movieId) { mutableStateOf(false) }

    LaunchedEffect(movieId) {
        if (movieId == null) return@LaunchedEffect
        playerViewModel.openEpisodeList(movieId, currentIndex)
        playerViewModel.loadAniZip(movieId)
        movie = runCatching { Api.detail(movieId) }.getOrNull()
    }
    LaunchedEffect(movieId, currentIndex) {
        if (movieId == null) return@LaunchedEffect
        playerViewModel.pinEpisode(movieId, currentIndex)
    }
    LaunchedEffect(movieId) {
        if (movieId == null) return@LaunchedEffect
        similarLoading = true
        similar = runCatching {
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

    Box(modifier.background(screenContainerColor())) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPad),
        ) {
            item(key = "header") {
                val epNumber = currentIndex?.trim()?.takeIf { it.isNotBlank() }
                val epMeta = epNumber?.toIntOrNull()?.let { aniZip?.episodes?.get(it) }
                val epTitle = epMeta?.title?.trim()?.takeIf { it.isNotBlank() }
                val headerTitle =
                    when {
                        epNumber == null -> epTitle ?: animeTitle
                        epTitle != null -> epTitle
                        else -> "Episode $epNumber"
                    }
                val headerMeta =
                    listOfNotNull(
                        epMeta?.rating?.takeIf { it.isNotBlank() }?.let { "Nilai: ${it.replace('.', ',')}" },
                        epMeta?.runtime?.let { "${it}m" },
                        AniZip.formatDate(epMeta?.airDate) ?: uploadDate?.takeIf { it.isNotBlank() },
                    ).joinToString("  •  ").takeIf { it.isNotEmpty() }
                WatchHeader(headerTitle, headerMeta)
            }
            item(key = "anime") {
                WatchAnimeRow(
                    title = movie?.title?.takeIf { it.isNotBlank() } ?: animeTitle,
                    movie = movie,
                    status = synced.status,
                    enabled = movie != null && movieId != null,
                    onClick = { movie?.let { openStatus(it.copy(id = movieId)) } },
                )
            }
            if (onPrev != null || onNext != null) {
                item(key = "nav-buttons") {
                    WatchNavButtons(
                        onPrev = onPrev,
                        onNext = onNext,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (chips.isNotEmpty()) {
                item(key = "chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(chips) { chip -> WatchChipButton(chip) }
                    }
                }
            }
            item(key = "comments-preview") {
                CommentsPreviewCard(
                    state = commentsState,
                    onClick = onOpenComments,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "sections") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WatchSectionToggleGroup(
                        labels = WATCH_SECTIONS,
                        selected = section,
                        onSelect = { section = it },
                    )
                    Spacer(Modifier.weight(1f))
                    if (section == 0 && movieId != null) {
                        WatchSortButton(
                            newestFirst = episodeList.newestFirst,
                            onClick = { playerViewModel.setEpisodeOrder(movieId, !episodeList.newestFirst) },
                        )
                    }
                }
            }
            if (section == 0) {
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
            } else {
                watchSimilarItems(similar, similarLoading, onOpenAnime)
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad),
        )
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
                                .width(32.dp)
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

private val WATCH_SECTIONS = listOf("Episode", "Mirip")

@Composable
private fun WatchHeader(
    title: String,
    meta: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!meta.isNullOrBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WatchAnimeRow(
    title: String,
    movie: Movie?,
    status: WatchStatus?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val stat = movie?.favorites?.takeIf { it.isNotBlank() }?.let { "${fmtNum(it)} favorit" }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(cs.surfaceContainerHigh)) {
            Poster(movie?.image_poster ?: movie?.image_cover, Modifier.fillMaxSize(), radius = 26.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stat != null) {
                Text(
                    stat,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        val pickStatus = hapticClick(block = onClick)
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(cs.primary.copy(alpha = if (enabled) 1f else 0.5f))
                .clickable(enabled = enabled, onClick = pickStatus)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                status?.icon ?: Icons.Outlined.BookmarkBorder,
                contentDescription = status?.label ?: "Atur status",
                tint = cs.onPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = cs.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
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
        targetValue = if (newestFirst) cs.surfaceContainerHigh else cs.secondaryContainer,
        animationSpec = motion.fastEffectsSpec(),
        label = "sortContainer",
    )
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(container)
                .clickable(onClick = hapticClick(block = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Sort,
            contentDescription = if (newestFirst) "Urutan: terbaru dulu" else "Urutan: terlama dulu",
            tint = if (newestFirst) cs.onSurface else cs.onSecondaryContainer,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleY = flip },
        )
    }
}

@Composable
private fun WatchSectionToggleGroup(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val full = 20.dp
    val inner = 8.dp
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            val corner by animateDpAsState(
                targetValue = if (isSelected) full else inner,
                animationSpec = motion.fastSpatialSpec(),
                label = "sectionCorner",
            )
            val startCorner = if (i == 0) full else corner.coerceAtLeast(0.dp)
            val endCorner = if (i == labels.lastIndex) full else corner.coerceAtLeast(0.dp)
            val container by animateColorAsState(
                targetValue = if (isSelected) cs.primaryContainer else cs.surfaceContainerHigh,
                animationSpec = motion.fastEffectsSpec(),
                label = "sectionContainer",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) cs.onPrimaryContainer else cs.onSurface,
                animationSpec = motion.fastEffectsSpec(),
                label = "sectionContent",
            )
            Box(
                modifier =
                    Modifier
                        .height(40.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = startCorner,
                                bottomStart = startCorner,
                                topEnd = endCorner,
                                bottomEnd = endCorner,
                            ),
                        ).background(container)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = hapticClick(block = { onSelect(i) }),
                        ).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun LazyListScope.watchSimilarItems(
    similar: List<com.uwu.animex.data.api.SimilarMovie>,
    loading: Boolean,
    onOpenAnime: (String) -> Unit,
) {
    when {
        loading && similar.isEmpty() ->
            item(key = "recs-loading") {
                WaitingCardPlaceholder(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            }
        similar.isEmpty() ->
            item(key = "recs-empty") {
                Text(
                    "Belum ada rekomendasi buat anime ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        else ->
            items(
                similar,
                key = { it.movie.id ?: it.movie.title.orEmpty() },
                contentType = { "rec" },
            ) { sm ->
                InlineHotRecCard(
                    movie = sm.movie,
                    onOpen = onOpenAnime,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
    }
}

private fun LazyListScope.watchEpisodeItems(
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
            items(8, key = { "episode-placeholder-$it" }) { EpisodeRowPlaceholder() }
        else -> {
            val pinned =
                list.episodes.firstOrNull { it.id == currentEpId }
                    ?: list.pinned?.takeIf { it.id == currentEpId }
            val ordered =
                if (pinned == null) list.episodes else listOf(pinned) + list.episodes.filter { it.id != pinned.id }
            items(
                ordered,
                key = { it.id ?: "${it.index}-${it.title}" },
                contentType = { "episode" },
            ) { ep ->
                val epDownload by remember(ep.id) { Downloads.itemFlow(ep.id) }
                    .collectAsStateWithLifecycle(initialValue = Downloads.item(ep.id))
                EpisodeRow(
                    ep = ep,
                    fallbackImage = fallbackImage,
                    download = epDownload,
                    malWatched = malWatched,
                    onDownload = { onDownload(ep) },
                    meta = ep.index
                        ?.trim()
                        ?.toIntOrNull()
                        ?.let { aniZip?.episodes?.get(it) },
                    current = ep.id == currentEpId,
                    showProgress = false,
                ) { onPick(ep) }
            }
            if (list.moreError) {
                item(key = "episodes-more-error") { WatchMessage("Gagal muat episode lainnya", onLoadMore) }
            } else if (list.loadingMore) {
                items(3, key = { "episode-more-placeholder-$it" }, contentType = { "episodePlaceholder" }) {
                    EpisodeRowPlaceholder()
                }
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
            runCatching { Api.servers(epId) }
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
private fun InlineHotRecCard(
    movie: Movie,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val statusLongClick = rememberStatusLongClick(movie)
    val hideStats = rememberHideStats()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainer)
            .hapticClick(
                onLongClick = statusLongClick,
                onClick = { movie.id?.let(onOpen) },
            ).padding(8.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(16.dp))) {
            Poster(
                movie.coverOrPoster(),
                Modifier.fillMaxWidth().height(118.dp),
                radius = 16.dp,
            )
            val viewsText = movie.views?.takeIf { it.isNotBlank() && !hideStats }
            val starText = movie.score?.takeIf { it.isNotBlank() } ?: movie.favorites.takeUnless { hideStats }
            if (!viewsText.isNullOrBlank() || !starText.isNullOrBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            ),
                        ),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!viewsText.isNullOrBlank()) {
                        OverlayChip(Icons.Outlined.PlayCircleOutline, fmtNum(viewsText), cs.error)
                    }
                    if (!starText.isNullOrBlank()) {
                        OverlayChip(Icons.Outlined.Star, fmtNum(starText), cs.tertiary)
                    }
                }
            }
        }
        Text(
            movie.genre?.takeIf { it.isNotBlank() } ?: " ",
            color = cs.primary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        Text(
            movie.title.orEmpty(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
        )
        Text(
            movie.synopsis.orEmpty(),
            color = cs.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
        )
    }
}

@Composable
private fun WatchNavButtons(
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val prevSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val prevPressed by prevSource.collectIsPressedAsState()
    val nextPressed by nextSource.collectIsPressedAsState()
    val prevWidth by animateDpAsState(
        targetValue = if (prevPressed) 76.dp else 64.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "prevWidth",
    )
    val outer = 24.dp
    val prevInner by animateDpAsState(
        targetValue = if (prevPressed) outer else 8.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "prevInner",
    )
    val nextInner by animateDpAsState(
        targetValue = if (nextPressed) outer else 8.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "nextInner",
    )
    val both = onPrev != null && onNext != null
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (onPrev != null) {
            FilledTonalButton(
                onClick = hapticClick(block = onPrev),
                modifier = (if (both) Modifier.width(prevWidth) else Modifier.weight(1f)).height(48.dp),
                shape =
                    if (both) {
                        RoundedCornerShape(
                            topStart = outer,
                            bottomStart = outer,
                            topEnd = prevInner.coerceAtLeast(0.dp),
                            bottomEnd = prevInner.coerceAtLeast(0.dp),
                        )
                    } else {
                        RoundedCornerShape(outer)
                    },
                colors =
                    if (both) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.secondaryContainer,
                            contentColor = cs.onSecondaryContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
                    },
                contentPadding = if (both) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp),
                interactionSource = prevSource,
            ) {
                Icon(
                    Icons.Outlined.SkipPrevious,
                    contentDescription = if (both) "Episode sebelumnya" else null,
                    modifier = Modifier.size(20.dp),
                )
                if (!both) {
                    Spacer(Modifier.width(8.dp))
                    Text("Sebelumnya", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        if (onNext != null) {
            Button(
                onClick = hapticClick(block = onNext),
                modifier = Modifier.weight(1f).height(48.dp),
                shape =
                    if (both) {
                        RoundedCornerShape(
                            topStart = nextInner.coerceAtLeast(0.dp),
                            bottomStart = nextInner.coerceAtLeast(0.dp),
                            topEnd = outer,
                            bottomEnd = outer,
                        )
                    } else {
                        RoundedCornerShape(outer)
                    },
                contentPadding = PaddingValues(horizontal = 16.dp),
                interactionSource = nextSource,
            ) {
                Text("Selanjutnya", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun WatchChipButton(chip: WatchChip) {
    val cs = MaterialTheme.colorScheme
    val container = if (chip.highlighted) cs.primaryContainer else cs.surfaceContainerHigh
    val content = if (chip.highlighted) cs.onPrimaryContainer else cs.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(onClick = hapticClick(block = chip.onClick))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(chip.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(chip.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

@Composable
private fun CommentsPreviewCard(
    state: UiState<List<UserApi.Discussion>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val items =
        when (state) {
            is UiState.Ready -> state.value
            else -> emptyList()
        }
    val shape = RoundedCornerShape(16.dp)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Komentar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = "Buka komentar",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
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
                        Box(Modifier.size(28.dp).clip(CircleShape).defaultPlaceholder())
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Placeholder",
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
                    Text(
                        "Belum ada komentar. Jadilah yang pertama!",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                else -> {
                    val top = items.firstOrNull()
                    if (top != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(top.username, size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                top.body(),
                                style = MaterialTheme.typography.bodySmall,
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
