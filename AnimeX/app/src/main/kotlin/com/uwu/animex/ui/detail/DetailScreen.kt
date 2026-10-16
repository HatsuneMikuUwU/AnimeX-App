@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.detail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.core.image.DominantColor
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.local.statusOf
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.ui.common.AnimatedNavIcon
import com.uwu.animex.ui.common.BatteryOptimizationDialog
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.DetailPlaceholder
import com.uwu.animex.ui.common.FloatingTabBarHeight
import com.uwu.animex.ui.common.FloatingTabBarMargin
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.theme.CoverArtTheme
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.theme.topScrim
import com.uwu.animex.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.uwu.animex.ui.common.hapticClick

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
    val movie = (state as? UiState.Ready)?.value?.movie
    val seasons = (state as? UiState.Ready)?.value?.seasons.orEmpty()
    val hasSeasons = seasons.isNotEmpty()
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
    val titleMatch =
        remember(loggedIn, malItems, movie?.title) {
            if (!loggedIn) return@remember null

            fun norm(t: String?) = t.orEmpty().lowercase().filter { it.isLetterOrDigit() }
            val raw = movie?.title ?: return@remember null
            val clean =
                raw
                    .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
                    .replace(Regex("(?i)subtitle indonesia|sub indo"), " ")
            val keys = setOf(norm(raw), norm(clean)).filter { it.length >= 3 }
            if (keys.isEmpty()) return@remember null
            malItems.firstOrNull { item -> (listOf(item.name) + item.synonyms).any { norm(it) in keys } }
        }
    var showStatusSheet by remember(id) { mutableStateOf(false) }
    var preloadTick by remember(id) { mutableIntStateOf(0) }

    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    var episodeCount by remember(id) { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    var coverHue by remember(id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(movie?.image_poster, appearance.coverTheme, id) {
        if (!appearance.coverTheme) {
            coverHue = null
            return@LaunchedEffect
        }
        val poster = movie?.image_poster?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val abs = Api.absUrl(poster) ?: return@LaunchedEffect
        DominantColor.peek(ctx, abs)?.let {
            coverHue = it
            return@LaunchedEffect
        }
        val hue = DominantColor.extractHue(ctx, abs)
        if (hue != null) coverHue = hue
    }
    val darkTheme = rememberAppDarkTheme(appearance.mode)
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
    var showBatteryDialog by remember { mutableStateOf(false) }
    val alerts by EpisodeAlerts.alerts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val infoState = rememberLazyListState()
    val episodeState = rememberLazyListState()
    val seasonState = rememberLazyListState()
    val characterState = rememberLazyListState()
    val trailerState = rememberLazyListState()
    val infoUp = isScrollingUp(infoState)
    val episodeUp = isScrollingUp(episodeState)
    val seasonUp = isScrollingUp(seasonState)
    val characterUp = isScrollingUp(characterState)
    val trailerUp = isScrollingUp(trailerState)

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
        when {
            tab == 0 -> infoUp
            isWaitingStatus && tab == 1 -> trailerUp
            !isWaitingStatus && tab == 1 -> episodeUp
            !isWaitingStatus && hasSeasons && tab == 2 -> seasonUp
            else -> characterUp
        }

    LaunchedEffect(movie?.id, source, preloadTick) {
        val m = movie ?: return@LaunchedEffect
        if (source == SyncSource.LOCAL || showStatusSheet) return@LaunchedEffect
        val withId = m.copy(id = movieId)
        try {
            if (source == SyncSource.ANILIST) {
                AniList.preload(withId)
                malPreloaded = AniList.preloaded(movieId)
            } else {
                Mal.preload(withId)
                malPreloaded = Mal.preloaded(movieId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val landscape = isLandscape()
    val detailTabs =
        remember(isWaitingStatus, hasSeasons) {
            if (isWaitingStatus) {
                listOf(
                    DetailTab("Info", Icons.Outlined.Info, Icons.Filled.Info, 0),
                    DetailTab("Trailer", Icons.Outlined.Movie, Icons.Filled.Movie, 1),
                    DetailTab("Karakter", Icons.Outlined.People, Icons.Filled.People, 2),
                )
            } else if (hasSeasons) {
                listOf(
                    DetailTab("Info", Icons.Outlined.Info, Icons.Filled.Info, 0),
                    DetailTab("Episode", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary, 1),
                    DetailTab("Season", Icons.Outlined.Layers, Icons.Filled.Layers, 2),
                    DetailTab("Karakter", Icons.Outlined.People, Icons.Filled.People, 3),
                )
            } else {
                listOf(
                    DetailTab("Info", Icons.Outlined.Info, Icons.Filled.Info, 0),
                    DetailTab("Episode", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary, 1),
                    DetailTab("Karakter", Icons.Outlined.People, Icons.Filled.People, 2),
                )
            }
        }
    LaunchedEffect(isWaitingStatus, hasSeasons, detailTabs.size) {
        if (tab !in detailTabs.indices) tab = 0
    }

    CoverArtTheme(
        hue = coverHue,
        enabled = appearance.coverTheme,
        darkTheme = darkTheme,
        amoled = appearance.amoled,
        paletteStyle = appearance.paletteStyle,
        colorSpec = appearance.colorSpec,
    ) {
        Row(Modifier.fillMaxSize()) {
            if (landscape && state is UiState.Ready) {
                NavigationRail {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        detailTabs.forEach { (label, icon, selectedIcon, index) ->
                            NavigationRailItem(
                                selected = tab == index,
                                onClick = hapticClick { tab = index },
                                icon = { AnimatedNavIcon(tab == index, icon, label, selectedIcon = selectedIcon) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            val backdrop = rememberBlurBackdrop()
            Scaffold(
                modifier = Modifier.weight(1f).nestedScroll(scrollBehavior.nestedScrollConnection),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    LargeFlexibleTopAppBar(
                        modifier = Modifier.blurEffect(backdrop, blendColor = MaterialTheme.colorScheme.background),
                        expandedHeight = 160.dp,
                        title = {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    (
                                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                            scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f)
                                    ) togetherWith (
                                        fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                                            scaleOut(tween(140, easing = FastOutSlowInEasing), targetScale = 0.92f)
                                    )
                                },
                                label = "detail-title",
                            ) { t ->
                                Text(
                                    when {
                                        t == 0 -> "Info"
                                        isWaitingStatus && t == 1 -> "Trailer"
                                        !isWaitingStatus && t == 1 ->
                                            if (episodeCount > 0) "$episodeCount Episode" else "Episode"
                                        !isWaitingStatus && hasSeasons && t == 2 -> "Season"
                                        else -> "Karakter"
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = hapticClick(block = onBack),
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                                shapes = IconButtonDefaults.shapes(),
                                colors =
                                    IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                            }
                        },
                        actions = {
                            if (movie != null) {
                                val alertOn = alerts.containsKey(movieId)
                                IconButton(
                                    shapes = IconButtonDefaults.shapes(),
                                    onClick = hapticClick {
                                        if (alertOn) {
                                            EpisodeAlerts.disable(movieId)
                                            snackbar.show(snackScope, "Notif episode baru dimatiin")
                                        } else {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(
                                                    ctx,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            EpisodeAlerts.enable(movie.copy(id = movieId), episodeCount)
                                            if (BatteryOptimization.shouldPrompt(ctx)) showBatteryDialog = true
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
                                    IconButton(
                                        enabled = !favBusy,
                                        onClick = hapticClick {
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
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(
                                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription =
                                                if (fav) "Buang dari favorites" else "Tambahin ke favorites",
                                            tint =
                                                if (fav) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                        )
                                    }
                                }
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                                scrolledContainerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                            ),
                        scrollBehavior = scrollBehavior,
                    )
                },
                floatingActionButton = {
                    if (movie != null) {
                        val malId = if (loggedIn) malLinks[movieId] ?: malPreloaded?.id?.toIntOrNull() else null
                        val libItem =
                            malId?.let { mid -> malItems.firstOrNull { it.syncId == mid.toString() } }
                                ?: titleMatch
                        val malKnown = libItem != null || malPreloaded != null
                        val status =
                            if (loggedIn && malKnown) {
                                (libItem?.status ?: malPreloaded?.myStatus?.status)?.toWatchStatus()
                            } else {
                                bookmarks.statusOf(movieId)
                            }
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
                                    Icon(status?.icon ?: Icons.Outlined.BookmarkBorder, contentDescription = null)
                                },
                                text = { Text(status?.label ?: "Atur Status Dong") },
                            )
                        }
                    }
                },
            ) { pad ->
                val showBar = state is UiState.Ready && !landscape
                val contentPad =
                    if (showBar) {
                        PaddingValues(top = pad.calculateTopPadding(), bottom = floatingTabBarSpace() + 8.dp)
                    } else {
                        pad
                    }
                Box(Modifier.fillMaxSize()) {
                    BlurContentBox(contentPad, backdrop) {
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
                                trailerState = trailerState,
                                episodeSort = episodeSort,
                                onEpisodeSortChange = { setSort(it) },
                                onEpisodeCount = { episodeCount = it },
                                onOpen = onOpen,
                                onPlay = onPlay,
                            )
                        }
                    }
                    if (!landscape) {
                        val bg = MaterialTheme.colorScheme.background
                        val barTop = pad.calculateTopPadding()
                        val fadeHeight = barTop + 32.dp
                        val fadeBrush = remember(bg) { topScrim(bg) }
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(fadeHeight)
                                .background(fadeBrush),
                        )
                    }
                    if (showBar) {
                        FloatingTabBarOverlay(
                            items = detailTabs,
                            selectedIndex = { tab },
                            onSelected = { tab = it },
                            backdrop = backdrop,
                            label = { it.label },
                            icon = { item, i ->
                                AnimatedNavIcon(tab == i, item.icon, item.label, selectedIcon = item.selectedIcon)
                            },
                        )
                    }
                }
            }
        }

        if (showBatteryDialog) {
            BatteryOptimizationDialog(onDismiss = { showBatteryDialog = false })
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
                    malPreloaded = null
                    preloadTick++
                },
            )
        }
    }
}
