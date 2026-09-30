@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.Mal
import com.uwu.animex.data.SearchHistory
import kotlinx.coroutines.launch

private data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private const val BOOKMARK_TAB = 3

private val NAV = listOf(
    NavItem("Home", Icons.Outlined.Home, Icons.Filled.Home),
    NavItem("Jadwal", Icons.Outlined.DateRange, Icons.Filled.DateRange),
    NavItem("Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
    NavItem("Bookmark", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    NavItem("Unduhan", Icons.Outlined.Download, Icons.Filled.Download),
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
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val malLoggedIn by Mal.loggedIn.collectAsState()

    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
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

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { submit(it) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { clearSearch() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Filled.Close, contentDescription = "Bersihin pencarian")
                        }
                    }
                    IconButton(onClick = onOpenProfile, shapes = IconButtonDefaults.shapes()) { MalAvatar() }
                }
            },
            placeholder = { Text("Mau nonton apa hari ini?") },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 8.dp),
                )
                ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
                    SearchHistoryList(
                        typed = textFieldState.text.toString(),
                        onPick = {
                            textFieldState.setTextAndPlaceCursorAtEnd(it)
                            submit(it)
                        },
                    )
                }

                CompositionLocalProvider(LocalBottomBarInset provides barHeight) {
                    Box(Modifier.weight(1f).fillMaxWidth().hazeSource(hazeState)) {
                        when (tab) {
                            0 -> HomeScreen(onOpen, onMore)
                            1 -> ScheduleScreen(onOpen)
                            2 -> ExploreScreen(
                                onFilter = onFilter,
                                onOpenCategory = onOpenCategory,
                                onOpenStudio = onOpenStudio,
                                onOpenYear = onOpenYear,
                                onOpenType = onOpenType,
                            )
                            3 -> BookmarkScreen(onOpen)
                            else -> DownloadsScreen(onOpen, onPlay)
                        }
                        if (query.isNotBlank()) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                PaginatedMovieGrid(
                                    loadKey = "search" to query,
                                    loader = { page, force -> Api.search(query, page = page, force = force) },
                                    onOpen = onOpen,
                                )
                            }
                        }
                    }
                }
            }

            FloatingNavBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                hazeState = hazeState,
            ) {
                NAV.mapIndexed { i, item ->
                    if (i == BOOKMARK_TAB && malLoggedIn) {
                        NavItem("MAL", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle)
                    } else item
                }.forEachIndexed { i, item ->
                    FloatingNavItem(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            if (query.isNotBlank()) clearSearch()
                        },
                        icon = if (tab == i) item.selectedIcon else item.icon,
                        label = item.label,
                    )
                }
            }
        }
    }
}
