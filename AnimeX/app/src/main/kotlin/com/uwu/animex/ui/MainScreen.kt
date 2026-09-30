@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val barBackdrop = rememberBarBackdrop()

    Scaffold(
        bottomBar = {
            val navItems = NAV.mapIndexed { i, item ->
                if (i == BOOKMARK_TAB && malLoggedIn) NavItem("MAL", Icons.Filled.AccountCircle) else item
            }
            AppFloatingBottomBar(
                items = navItems,
                selectedIndex = tab,
                backdrop = barBackdrop,
                onSelected = { i ->
                    tab = i
                    if (query.isNotBlank()) clearSearch()
                },
                iconContent = { item, _ -> Icon(item.icon, contentDescription = item.label) },
                labelContent = { item, _ ->
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        },
    ) { pad ->
        CompositionLocalProvider(LocalBottomBarInset provides pad.calculateBottomPadding()) {
        Column(
            Modifier
                .padding(top = pad.calculateTopPadding())
                .fillMaxSize()
                .barBackdropSource(barBackdrop),
        ) {
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

            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                            bottomPad = 16.dp + LocalBottomBarInset.current,
                        )
                    }
                }
            }
        }
        }
    }
}
