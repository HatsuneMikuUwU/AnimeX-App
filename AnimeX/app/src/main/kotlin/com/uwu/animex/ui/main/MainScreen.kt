@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.SearchHistory
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.ui.bookmark.BookmarkScreen
import com.uwu.animex.ui.common.AnimatedNavIcon
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.PaginatedMovieGrid
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.home.HomeScreen
import com.uwu.animex.ui.profile.MalAvatar
import com.uwu.animex.ui.schedule.ScheduleScreen
import com.uwu.animex.ui.search.ExploreScreen
import com.uwu.animex.ui.search.SearchHistoryList
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.glassStroke
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.theme.topScrim
import com.uwu.animex.ui.update.UpdateBanner
import kotlinx.coroutines.launch
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
    val tabStateHolder = rememberSaveableStateHolder()
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()

    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var barHeightPx by remember { mutableFloatStateOf(0f) }
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

    BackHandler(enabled = query.isNotBlank()) { clearSearch() }

    val hideProfile =
        searchBarState.targetValue == SearchBarValue.Expanded ||
            textFieldState.text.isNotEmpty() ||
            query.isNotBlank()

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { submit(it) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { clearSearch() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Outlined.Close, contentDescription = "Bersihin pencarian")
                        }
                    }
                    if (!hideProfile) {
                        IconButton(onClick = onOpenProfile, shapes = IconButtonDefaults.shapes()) { MalAvatar() }
                    }
                }
            },
            placeholder = { Text("Mau nonton apa hari ini?") },
        )
    }

    val landscape = isLandscape()
    val navItems =
        NAV.mapIndexed { i, item ->
            if (i == BOOKMARK_TAB && malLoggedIn) NavItem("MAL", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle) else item
        }
    val topInset = if (landscape) 0.dp else with(density) { barHeightPx.toDp() }
    val selectTab: (Int) -> Unit = { i ->
        tab = i
        if (query.isNotBlank()) clearSearch()
    }

    val backdrop = rememberBlurBackdrop()
    Scaffold { _ ->
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
                                onClick = { selectTab(i) },
                                icon = { AnimatedNavIcon(tab == i, item.icon, item.label, selectedIcon = item.selectedIcon) },
                                label = { Text(item.label) },
                            )
                        }
                        NavigationRailItem(
                            selected = false,
                            onClick = onOpenProfile,
                            icon = { MalAvatar() },
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
                        tabStateHolder.SaveableStateProvider(key = tab) {
                            when (tab) {
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
                        if (query.isNotBlank()) {
                            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                if (landscape) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { scope.launch { searchBarState.animateToExpanded() } }
                                            .padding(start = 16.dp, top = 8.dp, end = 4.dp),
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
                                        IconButton(onClick = { clearSearch() }, shapes = IconButtonDefaults.shapes()) {
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
                                .blurEffect(
                                    backdrop,
                                    shape = SearchBarDefaults.inputFieldShape,
                                    blendColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .glassStroke(),
                        colors =
                            SearchBarDefaults.colors(
                                containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.surfaceContainerHigh),
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
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            SearchHistoryList(
                typed = textFieldState.text.toString(),
                onPick = {
                    textFieldState.setTextAndPlaceCursorAtEnd(it)
                    submit(it)
                },
            )
        }
    }
}
