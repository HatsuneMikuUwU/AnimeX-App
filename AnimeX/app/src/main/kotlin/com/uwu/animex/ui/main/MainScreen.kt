@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.SearchHistory
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.bookmark.BookmarkScreen
import com.uwu.animex.ui.common.AnimatedNavIcon
import com.uwu.animex.ui.common.rememberAppMotion
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.PaginatedMovieGrid
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberHapticAction
import com.uwu.animex.ui.home.HomeScreen
import com.uwu.animex.ui.profile.AccountAvatar
import com.uwu.animex.ui.schedule.ScheduleScreen
import com.uwu.animex.ui.search.ExploreScreen
import com.uwu.animex.ui.search.SearchHistoryList
import com.uwu.animex.ui.theme.GlassStyle
import com.uwu.animex.ui.theme.UiScaled
import com.uwu.animex.ui.theme.barGlass
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.theme.resolveGlassStyle
import com.uwu.animex.ui.theme.topScrim
import com.uwu.animex.ui.update.UpdateBanner
import com.uwu.animex.ui.viewmodel.BookmarkViewModel
import com.uwu.animex.ui.viewmodel.ExploreViewModel
import com.uwu.animex.ui.viewmodel.ExploreViewModels
import com.uwu.animex.ui.viewmodel.HomeViewModel
import com.uwu.animex.ui.viewmodel.ScheduleViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.blur.layerBackdrop

private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private const val BOOKMARK_TAB = 3

private val MAIN_FADE_EXTRA = 16.dp

private val NAV =
    listOf(
        NavItem("Home", Icons.Outlined.Home, Icons.Filled.Home),
        NavItem("Jadwal", Icons.Outlined.DateRange, Icons.Filled.DateRange),
        NavItem("Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
        NavItem("Bookmark", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    )

private const val PRELOAD_HOME_TIMEOUT_MS = 3_000L

private var coldStartRevalidated = false

@Composable
private fun PreloadTabData(syncSource: SyncSource) {
    val homeVm: HomeViewModel = viewModel()
    var preloadOthers by remember { mutableStateOf(false) }
    LaunchedEffect(homeVm) {
        withTimeoutOrNull(PRELOAD_HOME_TIMEOUT_MS) { homeVm.state.first { it !is UiState.Loading } }
        preloadOthers = true
    }
    if (preloadOthers) {
        val scheduleVm: ScheduleViewModel = viewModel()
        val exploreVm: ExploreViewModel = viewModel(factory = ExploreViewModels.exploreFactory())
        val bookmarkVm: BookmarkViewModel = viewModel()
        LaunchedEffect(syncSource) { bookmarkVm.refresh() }
        LaunchedEffect(Unit) {
            if (coldStartRevalidated) return@LaunchedEffect
            coldStartRevalidated = true
            homeVm.revalidate()
            scheduleVm.revalidate()
            exploreVm.revalidate()
            bookmarkVm.revalidate()
        }
    }
}

@Composable
fun MainScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onOpenCategory: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenYear: () -> Unit = {},
    onOpenType: () -> Unit = {},
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
    onOpenProfile: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var previousTab by rememberSaveable { mutableIntStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniListLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val syncPref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val syncSource = SyncSourcePref.effective(syncPref, malLoggedIn, aniListLoggedIn)

    PreloadTabData(syncSource)
    LaunchedEffect(malLoggedIn) {
        if (malLoggedIn) MalLibrary.refresh()
    }

    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val searchExpandHaptic = rememberHapticAction()
    LaunchedEffect(searchBarState) {
        var wasExpanded = searchBarState.currentValue == SearchBarValue.Expanded
        snapshotFlow { searchBarState.currentValue }
            .collect { value ->
                val expanded = value == SearchBarValue.Expanded
                if (expanded && !wasExpanded) {
                    searchExpandHaptic()
                }
                wasExpanded = expanded
            }
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var barHeightPx by rememberSaveable { mutableStateOf(0f) }
    val estimatedBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { if (it.isBlank()) query = "" }
    }

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = q
        SearchHistory.record(q)
        scope.launch { searchBarState.animateToCollapsed() }
    }

    fun clearSearch() {
        textFieldState.clearText()
        query = ""
    }

    val hasSearchText = textFieldState.text.isNotEmpty() || query.isNotBlank()

    BackHandler(
        enabled = hasSearchText && searchBarState.currentValue != SearchBarValue.Expanded,
    ) { clearSearch() }

    val hideProfile =
        searchBarState.targetValue == SearchBarValue.Expanded ||
            textFieldState.text.isNotEmpty() ||
            query.isNotBlank()

    val inputField: @Composable () -> Unit = {
        val searchSubmitHaptic = rememberHapticAction()
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { q ->
                searchSubmitHaptic()
                submit(q)
            },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSearchText) {
                        IconButton(onClick = hapticClick { clearSearch() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Outlined.Close, contentDescription = "Bersihin pencarian")
                        }
                    }
                    if (!hideProfile) {
                        IconButton(
                            onClick = hapticClick(block = onOpenProfile),
                            shapes = IconButtonDefaults.shapes(),
                        ) { AccountAvatar() }
                    }
                }
            },
            placeholder = { Text("Mau nonton apa hari ini?") },
        )
    }

    val landscape = isLandscape()
    val navItems =
        NAV.mapIndexed { i, item ->
            if (i == BOOKMARK_TAB && syncSource != SyncSource.LOCAL) {
                NavItem(syncSource.label, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle)
            } else {
                item
            }
        }
    val topInset =
        when {
            landscape -> 0.dp
            barHeightPx > 0f -> with(density) { barHeightPx.toDp() }
            else -> estimatedBarHeight
        }
    val selectTab: (Int) -> Unit = { i ->
        if (i != tab) {
            previousTab = tab
            tab = i
        }
        if (query.isNotBlank()) clearSearch()
    }

    val backdrop = rememberBlurBackdrop()
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    Scaffold(containerColor = screenContainerColor()) { _ ->
        val bottomInset = if (landscape) 0.dp else floatingTabBarSpace() + 8.dp
        Row(Modifier.fillMaxSize()) {
            if (landscape) {
                NavigationRail(
                    header = {
                        FloatingActionButton(
                            onClick = { scope.launch { searchBarState.animateToExpanded() } },
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = "Cari")
                        }
                    },
                ) {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        navItems.forEachIndexed { i, item ->
                            NavigationRailItem(
                                selected = tab == i,
                                onClick = hapticClick { selectTab(i) },
                                icon = {
                                    AnimatedNavIcon(tab == i, item.icon, item.label, selectedIcon = item.selectedIcon)
                                },
                                label = { Text(item.label) },
                            )
                        }
                        NavigationRailItem(
                            selected = false,
                            onClick = hapticClick(block = onOpenProfile),
                            icon = { AccountAvatar() },
                            label = { Text("Profil") },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (landscape) {
                            Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
                                ),
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                CompositionLocalProvider(
                    LocalTopInset provides topInset,
                    LocalBottomInset provides bottomInset,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (!landscape && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                    ) {
                        val motion = rememberAppMotion()
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { motion.fadeTogether() },
                            label = "main-tab",
                            modifier = Modifier.fillMaxSize(),
                        ) { currentTab ->
                            tabStateHolder.SaveableStateProvider(key = currentTab) {
                                when (currentTab) {
                                    0 -> HomeScreen(onOpen, onMore, onPlay)
                                    1 -> ScheduleScreen(onOpen)
                                    2 ->
                                        ExploreScreen(
                                            onFilter = onFilter,
                                            onOpen = onOpen,
                                            onOpenCategory = onOpenCategory,
                                            onOpenStudio = onOpenStudio,
                                            onOpenYear = onOpenYear,
                                            onOpenType = onOpenType,
                                        )
                                    else -> BookmarkScreen(onOpen)
                                }
                            }
                        }
                        if (query.isNotBlank()) {
                            val overlayAlpha = remember { Animatable(0f) }
                            val overlaySpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                            LaunchedEffect(Unit) { overlayAlpha.animateTo(1f, overlaySpec) }
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = overlayAlpha.value }
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                if (landscape) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                onClick = { scope.launch { searchBarState.animateToExpanded() } },
                                            ).padding(start = 16.dp, top = 8.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.Search, contentDescription = null)
                                        Text(
                                            query,
                                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        IconButton(
                                            onClick = hapticClick { clearSearch() },
                                            shapes = IconButtonDefaults.shapes(),
                                        ) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Bersihin pencarian")
                                        }
                                    }
                                }
                                PaginatedMovieGrid(
                                    loadKey = "search" to query,
                                    loader = { page, force -> Api.search(query, page = page, force = force) },
                                    onOpen = onOpen,
                                )
                            }
                        }
                    }
                }

                if (!landscape && barHeightPx > 0f) {
                    val bg = MaterialTheme.colorScheme.background
                    val fadeHeight = maxOf(floatingTabBarSpace() + MAIN_FADE_EXTRA, topInset + 16.dp)
                    val fadeBrush = remember(bg) { topScrim(bg) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(fadeHeight)
                            .background(fadeBrush),
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = topInset + 16.dp),
                ) {
                    UpdateBanner(onOpenDetails = onOpenUpdate)
                }

                if (!landscape) {
                    val searchGlassStyle = resolveGlassStyle(backdrop, appearance.barEffect)
                    SearchBar(
                        state = searchBarState,
                        inputField = inputField,
                        modifier =
                            Modifier
                                .onSizeChanged { barHeightPx = it.height.toFloat() }
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .barGlass(
                                    style = searchGlassStyle,
                                    backdrop = backdrop,
                                    shape = SearchBarDefaults.inputFieldShape,
                                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    drawFlatBackground = false,
                                ),
                        colors =
                            SearchBarDefaults.colors(
                                containerColor =
                                    if (searchGlassStyle == GlassStyle.Flat) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        Color.Transparent
                                    },
                            ),
                    )
                }

                if (!landscape) {
                    FloatingTabBarOverlay(
                        items = navItems,
                        selectedIndex = { tab.coerceIn(0, navItems.lastIndex) },
                        onSelected = selectTab,
                        backdrop = backdrop,
                        fadeExtra = MAIN_FADE_EXTRA,
                        label = { it.label },
                        icon = { item, i ->
                            AnimatedNavIcon(tab == i, item.icon, item.label, selectedIcon = item.selectedIcon)
                        },
                    )
                }
            }
        }
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = { UiScaled { inputField() } },
        ) {
            UiScaled {
                SearchHistoryList(
                    typed = textFieldState.text.toString(),
                    onPick = {
                        textFieldState.setTextAndPlaceCursorAtEnd(it)
                        submit(it)
                    },
                    onFill = { textFieldState.setTextAndPlaceCursorAtEnd(it) },
                    onOpenSuggestion = { movie ->
                        val id = movie.id
                        if (id.isNullOrBlank()) {
                            val t = movie.title.orEmpty()
                            textFieldState.setTextAndPlaceCursorAtEnd(t)
                            submit(t)
                        } else {
                            movie.title?.let { SearchHistory.record(it) }
                            clearSearch()
                            scope.launch { searchBarState.animateToCollapsed() }
                            onOpen(id)
                        }
                    },
                )
            }
        }
    }
}
