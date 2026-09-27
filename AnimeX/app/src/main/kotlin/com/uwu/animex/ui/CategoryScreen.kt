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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreData
import com.uwu.animex.data.ExploreItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

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
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        when (val s = load.state) {
            UiState.Loading -> Box(Modifier.fillMaxSize().padding(pad)) { CenterLoading() }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(pad)) { CenterText("Gagal memuat") }
            is UiState.Ready -> {
                val list = itemsSelector(s.value)
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(pad)) { CenterText(emptyMessage) }
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = pad.calculateTopPadding() + 8.dp,
                            bottom = 16.dp,
                        ),
                    ) {
                        items(list) { item ->
                            Box(Modifier.padding(vertical = 6.dp)) {
                                itemContent(item)
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}
