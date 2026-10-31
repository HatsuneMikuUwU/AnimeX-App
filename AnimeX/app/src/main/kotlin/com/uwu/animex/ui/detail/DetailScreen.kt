@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.detail

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.api.TitleNoise
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.statusOf
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedNavIcon
import com.uwu.animex.ui.common.AppBadgedBox
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.DetailPlaceholder
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.FloatingTabBarHeight
import com.uwu.animex.ui.common.FloatingTabBarMargin
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.navigation.NotificationRouter
import com.uwu.animex.ui.onboarding.needsNotificationSetup
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.tracker.TrackerEditSheet
import com.uwu.animex.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class DetailTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val index: Int,
)

@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.factory(id), key = id),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val movie = (state as? UiState.Ready)?.value?.movie
    val seasons = (state as? UiState.Ready)?.value?.seasons.orEmpty()
    val movieId = movie?.id ?: id
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val sourcePref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val source = SyncSourcePref.effective(sourcePref, malLoggedIn, aniLoggedIn)
    val loggedIn = source != SyncSource.LOCAL
    val bookmarks by Bookmarks.entries.collectAsStateWithLifecycle()
    val malLibItems by MalLibrary.items.collectAsStateWithLifecycle()
    val aniLibItems by AniListLibrary.items.collectAsStateWithLifecycle()
    val malLinkMap by Mal.links.collectAsStateWithLifecycle()
    val aniLinkMap by AniList.links.collectAsStateWithLifecycle()
    val aniFavorites by AniList.favorites.collectAsStateWithLifecycle()

    var favOverride by remember(id) { mutableStateOf<Boolean?>(null) }
    var favBusy by remember(id) { mutableStateOf(false) }
    LaunchedEffect(source, id) {
        if (source == SyncSource.ANILIST) runCatching { AniList.refreshFavorites() }
    }
    val malItems = if (source == SyncSource.ANILIST) aniLibItems else malLibItems
    val malLinks = if (source == SyncSource.ANILIST) aniLinkMap else malLinkMap
    var malPreloaded by remember(id, source) { mutableStateOf<SyncResult?>(null) }
    var preloadSettled by remember(id, source) { mutableStateOf(false) }
    val titleMatch =
        remember(loggedIn, malItems, movie?.title) {
            if (!loggedIn) return@remember null

            fun norm(t: String?) = t.orEmpty().lowercase().filter { it.isLetterOrDigit() }
            val raw = movie?.title ?: return@remember null
            val clean =
                raw
                    .replace(TitleNoise.parenBracket, " ")
                    .replace(TitleNoise.subIndo, " ")
            val keys = setOf(norm(raw), norm(clean)).filter { it.length >= 3 }
            if (keys.isEmpty()) return@remember null
            malItems.firstOrNull { item -> (listOf(item.name) + item.synonyms).any { norm(it) in keys } }
        }
    var showStatusSheet by remember(id) { mutableStateOf(false) }
    var preloadTick by remember(id) { mutableIntStateOf(0) }
    var statusRefreshing by remember(id, source) { mutableStateOf(false) }
    var lastStatus by remember(id, source) { mutableStateOf<WatchStatus?>(null) }
    var lastRewatching by remember(id, source) { mutableStateOf(false) }

    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    var episodeCount by remember(id) { mutableIntStateOf(0) }
    var unwatchedCount by remember(id) { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val sortPrefs = remember { ctx.getSharedPreferences("episode_sort", Context.MODE_PRIVATE) }
    var episodeSort by remember(id) {
        mutableStateOf(
            runCatching {
                EpisodeSort.valueOf(sortPrefs.getString(id, null) ?: "Newest")
            }.getOrDefault(EpisodeSort.Newest),
        )
    }

    fun setSort(s: EpisodeSort) {
        episodeSort = s
        sortPrefs.edit().putString(id, s.name).apply()
    }
    var showSortSheet by remember(id) { mutableStateOf(false) }
    val alerts by EpisodeAlerts.alerts.collectAsStateWithLifecycle()

    LaunchedEffect(episodeCount, alerts) {
        val alert = alerts[movieId] ?: alerts[id] ?: return@LaunchedEffect
        if (episodeCount > alert.lastEpisode) {
            EpisodeAlerts.setLastEpisode(ctx, alert.movieId, episodeCount)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val infoState = rememberLazyListState()
    val episodeState = rememberLazyListState()
    val seasonState = rememberLazyListState()
    val characterState = rememberLazyListState()
    val infoUp = isScrollingUp(infoState)
    val episodeUp = isScrollingUp(episodeState)
    val seasonUp = isScrollingUp(seasonState)
    val characterUp = isScrollingUp(characterState)

    val isWaitingStatus =
        remember(movie?.status) {
            val s = movie?.status?.lowercase().orEmpty()
            s.contains("waiting") ||
                s.contains("upcoming") ||
                s.contains("belum") ||
                s.contains("soon") ||
                s.contains("not yet") ||
                s == "tba" ||
                s.contains("akan datang")
        }
    val fabExpanded =
        when (tab) {
            0 -> infoUp
            1 -> episodeUp
            2 -> seasonUp
            else -> characterUp
        }

    LaunchedEffect(movie?.id, source, preloadTick) {
        val m = movie ?: return@LaunchedEffect
        if (source == SyncSource.LOCAL) {
            statusRefreshing = false
            return@LaunchedEffect
        }
        if (showStatusSheet) return@LaunchedEffect
        val withId = m.copy(id = movieId)
        try {
            if (source == SyncSource.ANILIST) {
                AniList.preload(withId)
                malPreloaded = AniList.preloaded(movieId)
            } else {
                Mal.preload(withId)
                malPreloaded = Mal.preloaded(movieId)
            }
            preloadSettled = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            preloadSettled = true
        } finally {
            statusRefreshing = false
        }
    }

    val malId = if (loggedIn) malLinks[movieId] ?: malPreloaded?.id?.toIntOrNull() else null
    val libItem =
        malId?.let { mid -> malItems.firstOrNull { it.syncId == mid.toString() } }
            ?: titleMatch
    val malKnown = libItem != null || malPreloaded != null
    val statusKnown = !loggedIn || malKnown || preloadSettled
    val computedStatus =
        when {
            !statusKnown -> null
            loggedIn && malKnown -> (libItem?.status ?: malPreloaded?.myStatus?.status)?.toWatchStatus()
            else -> bookmarks.statusOf(movieId)
        }
    val computedRewatching =
        statusKnown &&
            loggedIn &&
            malKnown &&
            (libItem?.rewatching ?: malPreloaded?.myStatus?.isRewatching) == true
    val syncedWatched: Int? =
        when {
            !loggedIn || !malKnown -> null
            (libItem?.status ?: malPreloaded?.myStatus?.status) == SyncWatchType.COMPLETED -> Int.MAX_VALUE
            else -> libItem?.episodesCompleted ?: malPreloaded?.myStatus?.watchedEpisodes
        }
    val badgeStatus: WatchStatus? =
        when {
            !statusKnown -> null
            loggedIn -> if (malKnown) computedStatus else null
            else -> bookmarks.statusOf(movieId)
        }
    val episodeBadge =
        if (badgeStatus == WatchStatus.WATCHING || badgeStatus == WatchStatus.PLAN_TO_WATCH) unwatchedCount else 0

    val landscape = isLandscape()
    val detailTabs =
        remember {
            listOf(
                DetailTab("Info", Icons.Outlined.Info, Icons.Filled.Info, 0),
                DetailTab("Episode", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary, 1),
                DetailTab("Season", Icons.Outlined.Layers, Icons.Filled.Layers, 2),
                DetailTab("Karakter", Icons.Outlined.People, Icons.Filled.People, 3),
            )
        }
    LaunchedEffect(detailTabs.size) {
        if (tab !in detailTabs.indices) tab = 0
    }

    Row(Modifier.fillMaxSize()) {
        if (landscape && state !is UiState.Error) {
            NavigationRail {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    detailTabs.forEach { (label, icon, selectedIcon, index) ->
                        NavigationRailItem(
                            selected = tab == index,
                            onClick = hapticClick { if (state is UiState.Ready) tab = index },
                            icon = {
                                TabBadge(if (index == 1) episodeBadge else 0) {
                                    AnimatedNavIcon(tab == index, icon, label, selectedIcon = selectedIcon)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        val backdrop = rememberBlurBackdrop()
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = screenContainerColor(),
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                FloatingTopBar(
                    backdrop = backdrop,
                    onBack = onBack,
                    actions = {
                        val ready = movie != null
                        val alertKey =
                            when {
                                alerts.containsKey(movieId) -> movieId
                                alerts.containsKey(id) -> id
                                else -> null
                            }
                        val alertOn = alertKey != null
                        DetailActionButton(
                            enabled = ready,
                            onClick = hapticClick { if (movie != null) shareAnime(ctx, movie) },
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "Bagikan",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DetailActionButton(
                            enabled = ready,
                            onClick = hapticClick {
                                if (movie == null) return@hapticClick
                                if (alertOn) {
                                    EpisodeAlerts.disable(alertKey)
                                    snackbar.show(snackScope, "Notif episode baru dimatiin")
                                } else {
                                    EpisodeAlerts.enable(movie.copy(id = movieId), episodeCount)
                                    if (needsNotificationSetup(ctx)) NotificationRouter.pendingOpenSetup.value = true
                                    snackbar.show(snackScope, "Nanti kamu dikasih tau kalau ada episode baru")
                                }
                            },
                        ) {
                            Icon(
                                if (alertOn) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                                contentDescription =
                                    if (alertOn) "Matiin notif episode baru" else "Notif episode baru",
                                tint =
                                    if (alertOn) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                        if (source == SyncSource.ANILIST) {
                            val aniFavId = aniLinkMap[movieId]
                            val fav =
                                favOverride ?: (aniFavId != null && aniFavorites.any { it.id == aniFavId })
                            DetailActionButton(
                                enabled = ready && !favBusy,
                                onClick = hapticClick {
                                    if (movie == null) return@hapticClick
                                    val withId = movie.copy(id = movieId)
                                    favBusy = true

                                    if (aniFavId != null) favOverride = !fav
                                    snackScope.launch {
                                        try {
                                            AniList.toggleFavorite(withId)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            snackbar.show(
                                                snackScope,
                                                "Gagal update favorite di AniList: ${e.toUserMessage()}",
                                            )
                                        } finally {
                                            favOverride = null
                                            favBusy = false
                                        }
                                    }
                                },
                            ) {
                                val motion = MaterialTheme.motionScheme
                                AnimatedContent(
                                    targetState = fav,
                                    transitionSpec = { AppMotion.crossfade(motion) },
                                    label = "detail-fav-icon",
                                ) { f ->
                                    Icon(
                                        if (f) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription =
                                            if (f) "Buang dari favorites" else "Tambahin ke favorites",
                                        tint =
                                            if (f) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = movie != null,
                    enter = AppMotion.popEnter(),
                    exit = AppMotion.popExit(),
                ) {
                    if (movie != null) {
                        SideEffect {
                            if (!statusRefreshing) {
                                lastStatus = computedStatus
                                lastRewatching = computedRewatching
                            }
                        }
                        val status = if (statusRefreshing) lastStatus else computedStatus
                        val rewatching = if (statusRefreshing) lastRewatching else computedRewatching
                        Column(
                            modifier =
                                Modifier.padding(
                                    bottom = if (!landscape) FloatingTabBarHeight + FloatingTabBarMargin else 0.dp,
                                ),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!isWaitingStatus && tab == 1) {
                                SmallFloatingActionButton(
                                    onClick = hapticClick { showSortSheet = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(end = 4.dp),
                                ) {
                                    Icon(episodeSort.icon, contentDescription = "Urutkan episode")
                                }
                            }
                            ExtendedFloatingActionButton(
                                onClick = hapticClick { showStatusSheet = true },
                                expanded = fabExpanded,
                                shape = RoundedCornerShape(16.dp),
                                icon = {
                                    val motion = MaterialTheme.motionScheme
                                    AnimatedContent(
                                        targetState = status to rewatching,
                                        transitionSpec = { AppMotion.crossfade(motion) },
                                        label = "fab-status-icon",
                                    ) { (s, rw) ->
                                        Icon(
                                            if (rw) Icons.Outlined.Repeat else s?.icon ?: Icons.Outlined.BookmarkBorder,
                                            contentDescription = null,
                                        )
                                    }
                                },
                                text = {
                                    val motion = MaterialTheme.motionScheme
                                    AnimatedContent(
                                        targetState = status to rewatching,
                                        transitionSpec = { AppMotion.crossfade(motion) },
                                        label = "fab-status-text",
                                    ) { (s, rw) ->
                                        Text(if (rw) "Nonton Ulang" else s?.label ?: "Atur Status Dong")
                                    }
                                },
                            )
                        }
                    }
                }
            },
        ) { pad ->

            val showBar = state !is UiState.Error && !landscape
            val contentPad =
                if (showBar) {
                    PaddingValues(top = pad.calculateTopPadding(), bottom = floatingTabBarSpace() + 8.dp)
                } else {
                    pad
                }
            Box(Modifier.fillMaxSize()) {
                BlurContentBox(contentPad, backdrop) {
                    ExpressivePullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshAll() },
                        errors = viewModel.refreshError,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        UiStateContent(
                            state = state,
                            onRetry = { viewModel.refresh() },
                            loading = { DetailPlaceholder() },
                        ) { payload ->
                            EpisodeListContent(
                                id = id,
                                movie = payload.movie,
                                seasons = seasons,
                                initialEpisodes = payload.episodes,
                                initialFirstEpisode = payload.firstEpisode,
                                viewModel = viewModel,
                                modifier = Modifier,
                                snackbar = snackbar,
                                tab = tab,
                                isWaitingStatus = isWaitingStatus,
                                infoState = infoState,
                                episodeState = episodeState,
                                seasonState = seasonState,
                                characterState = characterState,
                                episodeSort = episodeSort,
                                onEpisodeSortChange = { setSort(it) },
                                onEpisodeCount = { episodeCount = it },
                                onUnwatchedCount = { unwatchedCount = it },
                                syncedWatched = syncedWatched,
                                onOpen = onOpen,
                                onPlay = onPlay,
                            )
                        }
                    }
                }
                if (showBar) {
                    FloatingTabBarOverlay(
                        items = detailTabs,
                        selectedIndex = { tab },
                        onSelected = { if (state is UiState.Ready) tab = it },
                        backdrop = backdrop,
                        label = { it.label },
                        icon = { item, i ->
                            TabBadge(if (i == 1) episodeBadge else 0) {
                                AnimatedNavIcon(tab == i, item.icon, item.label, selectedIcon = item.selectedIcon)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        EpisodeSortSheet(
            current = episodeSort,
            onDismiss = { showSortSheet = false },
            onSelect = {
                setSort(it)
                showSortSheet = false
            },
        )
    }

    if (showStatusSheet && movie != null) {
        TrackerEditSheet(
            movie = movie.copy(id = movieId),
            onDismiss = {
                showStatusSheet = false
                statusRefreshing = loggedIn
                malPreloaded = null
                preloadTick++
            },
        )
    }
}

@Composable
private fun TabBadge(
    count: Int,
    content: @Composable () -> Unit,
) {
    AppBadgedBox(count = count) {
        content()
    }
}

@Composable
internal fun DetailActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
