@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.History
import java.util.Calendar

private val TITLES = mapOf(
    "update" to "Episode Baru", "hot" to "Sedang Hangat", "new" to "Judul Baru",
    "random" to "Jas Por Yu", "popular" to "Populer", "history" to "Lanjut Nonton",
    "waiting" to "Paling Dinanti", "today" to "Jadwal Hari Ini",
)

@Composable
fun ListScreen(key: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TITLES[key] ?: "Daftar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (key == "history") {
                if (History.items.isEmpty()) {
                    CenterText("Belum ada riwayat tontonan")
                } else {
                    ContinueWatchingGrid(History.items, onOpen, bottomPad = 16.dp) { movie ->
                        movie.id?.let(History::remove)
                    }
                }
            } else if (key == "today") {
                val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
                val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
                PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
                    when (val s = load.state) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                        is UiState.Ready -> {
                            val list = s.value.filter { it.day.equals(todayLabel, true) }
                            if (list.isEmpty()) CenterText("Tidak ada jadwal")
                            else MovieGrid(list, onOpen, bottomPad = 16.dp, showTime = true)
                        }
                    }
                }
            } else {
                val load = rememberLoad("list" to key) { force ->
                    if (key == "update") Api.newEpisodes(force = force) else Api.homeMovies(key, force = force)
                }
                PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
                    when (val s = load.state) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                        is UiState.Ready -> MovieGrid(s.value, onOpen, bottomPad = 16.dp)
                    }
                }
            }
        }
    }
}

/** List hasil filter kategori (genre / type / studio / year). */
@Composable
fun FilterListScreen(
    kind: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "Kategori" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val load = rememberLoad("filter" to (kind to id)) { force ->
                Api.exploreMovies(kind, id, force = force)
            }
            PullToRefreshBox(
                isRefreshing = load.isRefreshing,
                onRefresh = load.refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = load.state) {
                    UiState.Loading -> CenterLoading()
                    is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                    is UiState.Ready ->
                        if (s.value.isEmpty()) CenterText("Tidak ada hasil")
                        else MovieGrid(s.value, onOpen, bottomPad = 16.dp)
                }
            }
        }
    }
}
