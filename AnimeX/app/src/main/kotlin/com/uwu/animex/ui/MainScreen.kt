@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
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
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalLibrary
import com.uwu.animex.data.SearchHistory
import com.uwu.animex.sync.ListSorting
import kotlinx.coroutines.launch

private data class NavItem(val label: String, val icon: ImageVector)

private const val BOOKMARK_TAB = 3

private enum class SortKey(val label: String, val first: ListSorting, val second: ListSorting) {
    UPDATED("Tanggal Update", ListSorting.UpdatedNew, ListSorting.UpdatedOld),
    TITLE("Judul", ListSorting.AlphabeticalA, ListSorting.AlphabeticalZ),
    SCORE("Skor", ListSorting.RatingHigh, ListSorting.RatingLow),
    RELEASE("Tanggal Rilis", ListSorting.ReleaseDateNew, ListSorting.ReleaseDateOld),
}

private val ListSorting.isAscending: Boolean
    get() = this == ListSorting.UpdatedOld || this == ListSorting.AlphabeticalA ||
        this == ListSorting.RatingLow || this == ListSorting.ReleaseDateOld

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
    var sortAvailable by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val sorting by MalLibrary.sorting.collectAsState()
    val supportedSorting by MalLibrary.supportedSorting.collectAsState()

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

    Scaffold(
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
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = tab == BOOKMARK_TAB && sortAvailable && query.isBlank(),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Box(Modifier.padding(start = 8.dp)) {
                        Surface(
                            onClick = { sortMenu = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Urutkan")
                            }
                        }
                        DropdownMenuPopup(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            val keys = SortKey.entries.filter { it.first in supportedSorting }
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                keys.forEachIndexed { index, key ->
                                    val active = sorting == key.first || sorting == key.second
                                    SelectableDropdownMenuItem(
                                        selected = active,
                                        onClick = {
                                            val next = when {
                                                !active -> key.first
                                                sorting == key.first && key.second in supportedSorting -> key.second
                                                else -> key.first
                                            }
                                            MalLibrary.setSorting(next)
                                            sortMenu = false
                                        },
                                        text = { Text(key.label) },
                                        shapes = MenuDefaults.itemShape(index, keys.size),
                                        modifier = Modifier.padding(end = 8.dp),
                                        leadingIcon = {
                                            if (active) {
                                                Icon(
                                                    if (sorting.isAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
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
                    3 -> BookmarkScreen(onOpen, onSortAvailable = { sortAvailable = it })
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
}
