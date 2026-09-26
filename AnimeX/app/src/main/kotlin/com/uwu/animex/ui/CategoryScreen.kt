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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreData
import com.uwu.animex.data.ExploreItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

/** Full category (genre) list. */
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Kategori",
        onBack = onBack,
        emptyMessage = "Tidak ada kategori",
        itemsSelector = { it.genre },
    ) { item ->
        GenreCard(item, modifier = Modifier.fillMaxWidth()) {
            onFilter("genre", item.id ?: item.displayName, item.displayName)
        }
    }
}

/** Full year list. */
@Composable
fun YearScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tahun",
        onBack = onBack,
        emptyMessage = "Tidak ada tahun",
        itemsSelector = { it.year },
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
    itemsSelector: (ExploreData) -> List<ExploreItem>,
    itemContent: @Composable (ExploreItem) -> Unit,
) {
    val load = rememberLoad("explore") { force -> Api.explore(force) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat")
                is UiState.Ready -> {
                    val list = itemsSelector(s.value)
                    if (list.isEmpty()) {
                        CenterText(emptyMessage)
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(list) { item ->
                                Box(Modifier.padding(vertical = 6.dp)) {
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
