@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreItem

/** Full category (genre) list — AnimeIn GenreActivity → 3/2/explore/genre */
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Kategori",
        onBack = onBack,
        emptyMessage = "Tidak ada kategori",
        loadKey = "explore-genres",
        loader = { force -> Api.exploreGenres(force) },
    ) { item ->
        GenreCard(item, modifier = Modifier.fillMaxWidth()) {
            onFilter("genre", item.id ?: item.displayName, item.displayName)
        }
    }
}

/** Full studio list — from 3/2/explore/data (no dedicated endpoint in AnimeIn). */
@Composable
fun StudioScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Studio",
        onBack = onBack,
        emptyMessage = "Tidak ada studio",
        loadKey = "explore-studios",
        loader = { force -> Api.exploreStudios(force) },
    ) { item ->
        TypeCard(item.displayName, modifier = Modifier.fillMaxWidth()) {
            onFilter("studio", item.id ?: item.displayName, item.displayName)
        }
    }
}

/** Full type list — from explore data key "tipe". */
@Composable
fun TypeScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tipe",
        onBack = onBack,
        emptyMessage = "Tidak ada tipe",
        loadKey = "explore-types",
        loader = { force -> Api.explore(force, preview = false).typeOrDefault },
    ) { item ->
        TypeCard(item.displayName, modifier = Modifier.fillMaxWidth()) {
            onFilter("type", item.id ?: item.displayName, item.displayName)
        }
    }
}

/** Full year list — AnimeIn → 3/2/explore/year */
@Composable
fun YearScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tahun",
        onBack = onBack,
        emptyMessage = "Tidak ada tahun",
        loadKey = "explore-years",
        loader = { force -> Api.exploreYears(force) },
    ) { item ->
        YearCard(item, modifier = Modifier.fillMaxWidth()) {
            onFilter("year", item.id ?: item.displayName, item.displayName)
        }
    }
}

@Composable
private fun ExploreListScaffold(
    title: String,
    onBack: () -> Unit,
    emptyMessage: String,
    loadKey: String,
    loader: suspend (Boolean) -> List<ExploreItem>,
    itemContent: @Composable (ExploreItem) -> Unit,
) {
    val load = rememberLoad(loadKey) { force -> loader(force) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat")
                is UiState.Ready -> {
                    val list = s.value
                    if (list.isEmpty()) {
                        CenterText(emptyMessage)
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(list) { item ->
                                Box(Modifier.padding(vertical = 4.dp)) {
                                    itemContent(item)
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}
