@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.appViewModel
import com.uwu.animex.ui.search.GenreCard
import com.uwu.animex.ui.search.TypeCard
import com.uwu.animex.ui.search.YearCard

@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Kategori",
        onBack = onBack,
        emptyMessage = "Kategorinya kosong nih",
        kind = ExploreKind.GENRES,
    ) { item ->
        GenreCard(item, modifier = Modifier.fillMaxWidth()) {
            val filterId = item.id?.takeIf { it.isNotBlank() } ?: item.displayName
            onFilter("genre", filterId, item.displayName)
        }
    }
}

@Composable
fun StudioScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Studio",
        onBack = onBack,
        emptyMessage = "Studionya kosong nih",
        kind = ExploreKind.STUDIOS,
    ) { item ->
        TypeCard(item.displayName, modifier = Modifier.fillMaxWidth()) {
            onFilter("studio", item.displayName, item.displayName)
        }
    }
}

@Composable
fun TypeScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tipe",
        onBack = onBack,
        emptyMessage = "Tipenya kosong nih",
        kind = ExploreKind.TYPES,
    ) { item ->
        TypeCard(item.displayName, modifier = Modifier.fillMaxWidth()) {
            onFilter("type", item.displayName, item.displayName)
        }
    }
}

@Composable
fun YearScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tahun",
        onBack = onBack,
        emptyMessage = "Tahunnya kosong nih",
        kind = ExploreKind.YEARS,
    ) { item ->
        YearCard(item, modifier = Modifier.fillMaxWidth()) {
            onFilter("year", item.displayName, item.displayName)
        }
    }
}

@Composable
private fun ExploreListScaffold(
    title: String,
    onBack: () -> Unit,
    emptyMessage: String,
    kind: ExploreKind,
    itemContent: @Composable (ExploreItem) -> Unit,
) {
    val vm: ExploreListViewModel = appViewModel(key = "explore:$kind") { ExploreListViewModel(kind, it.animeRepository) }
    val ui by vm.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik")
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
            when (val s = ui.items) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Yah, gagal muat nih")
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
