@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
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
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.Mal
import com.uwu.animex.data.SearchHistory
import kotlinx.coroutines.launch

private data class NavItem(val label: String, val icon: ImageVector)

private const val BOOKMARK_TAB = 3

private val NAV = listOf(
    NavItem("Home", Icons.Filled.Home),
    NavItem("Jadwal", Icons.Filled.DateRange),
    NavItem("Explore", Icons.Filled.Explore),
    NavItem("Bookmark", Icons.Filled.Bookmark),
    NavItem("Unduhan", Icons.Filled.Download),
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
    val malLoggedIn by Mal.loggedIn.collectAsState()

    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    val barOffset = remember { Animatable(0f) }
    val barConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f && barHeightPx > 0f) {
                    scope.launch {
                        barOffset.snapTo((barOffset.value + available.y).coerceIn(-barHeightPx, 0f))
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (barHeightPx > 0f) {
                    barOffset.animateTo(if (barOffset.value <= -barHeightPx / 2) -barHeightPx else 0f)
                }
                return Velocity.Zero
            }
        }
    }
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

    val hideProfile = searchBarState.targetValue == SearchBarValue.Expanded ||
        textFieldState.text.isNotEmpty() ||
        query.isNotBlank()

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
                    if (!hideProfile) {
                        IconButton(onClick = onOpenProfile, shapes = IconButtonDefaults.shapes()) { MalAvatar() }
                    }
                }
            },
            placeholder = { Text("Mau nonton apa hari ini?") },
        )
    }

    LaunchedEffect(tab, searchBarState.targetValue) { barOffset.animateTo(0f) }

    Scaffold(
        modifier = Modifier.nestedScroll(barConnection),
        bottomBar = {
            ShortNavigationBar {
                NAV.mapIndexed { i, item ->
                    if (i == BOOKMARK_TAB && malLoggedIn) NavItem("MAL", Icons.Filled.AccountCircle) else item
                }.forEachIndexed { i, item ->
                    ShortNavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            if (query.isNotBlank()) clearSearch()
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding()).fillMaxSize()) {
            CompositionLocalProvider(LocalTopInset provides with(density) { barHeightPx.toDp() }) {
            Box(Modifier.fillMaxSize()) {
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

            SearchBar(
                state = searchBarState,
                inputField = inputField,
                modifier = Modifier
                    .onSizeChanged { barHeightPx = it.height.toFloat() }
                    .graphicsLayer { translationY = barOffset.value }
                    .statusBarsPadding()
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
        }
    }
}
