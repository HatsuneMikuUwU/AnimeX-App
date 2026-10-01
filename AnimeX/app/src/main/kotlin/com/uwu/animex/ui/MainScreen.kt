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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.Downloads
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
    onOpenDownloads: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val malLoggedIn by Mal.loggedIn.collectAsState()
    val downloads by Downloads.items.collectAsState()
    val activeDownloads = remember(downloads) {
        downloads.values.count {
            it.status == Downloads.Status.QUEUED || it.status == Downloads.Status.DOWNLOADING
        }
    }

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
                    if (query.isBlank() && searchBarState.targetValue != SearchBarValue.Expanded) {
                        IconButton(onClick = onOpenDownloads, shapes = IconButtonDefaults.shapes()) {
                            BadgedBox(
                                badge = {
                                    if (activeDownloads > 0) {
                                        Badge { Text(if (activeDownloads > 99) "99+" else "$activeDownloads") }
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Unduhan")
                            }
                        }
                        IconButton(onClick = onOpenProfile, shapes = IconButtonDefaults.shapes()) { MalAvatar() }
                    }
                }
            },
            placeholder = { Text("Cari Anime...") },
        )
    }

    Scaffold(
        bottomBar = {
            val navItems = NAV.mapIndexed { i, item ->
                if (i == BOOKMARK_TAB && malLoggedIn) NavItem("MAL", Icons.Filled.AccountCircle) else item
            }.map { FloatingNavItem(it.label, it.icon) }
            FloatingNavBarHost(
                items = navItems,
                selectedIndex = tab,
                onSelected = {
                    tab = it
                    if (query.isNotBlank()) clearSearch()
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
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
                    else -> BookmarkScreen(onOpen)
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
}
