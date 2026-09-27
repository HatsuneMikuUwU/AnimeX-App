package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.History
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import java.util.Calendar

private val TITLES = mapOf(
    "update" to "Episode Baru", "hot" to "Sedang Hangat", "new" to "Judul Baru",
    "random" to "Jas Por Yu", "popular" to "Populer", "history" to "Lanjut Nonton",
    "waiting" to "Paling Dinanti", "today" to "Jadwal Hari Ini",
)

@Composable
fun ListScreen(key: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val title = TITLES[key] ?: "Daftar"
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        val topPad = pad.calculateTopPadding()
        if (key == "history") {
            if (History.items.isEmpty()) {
                Box(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
                    CenterText("Belum ada riwayat tontonan")
                }
            } else {
                ContinueWatchingGrid(
                    list = History.items,
                    onOpen = onOpen,
                    bottomPad = 16.dp,
                    topPad = topPad + 8.dp,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                ) { movie ->
                    movie.id?.let(History::remove)
                }
            }
        } else if (key == "today") {
            val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
            val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
            PullToRefresh(
                isRefreshing = load.isRefreshing,
                onRefresh = load.refresh,
                topAppBarScrollBehavior = scrollBehavior,
                contentPadding = PaddingValues(top = topPad),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = load.state) {
                    UiState.Loading -> CenterLoading()
                    is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                    is UiState.Ready -> {
                        val list = s.value.filter { it.day.equals(todayLabel, true) }
                        if (list.isEmpty()) CenterText("Tidak ada hasil")
                        else MovieGrid(list, onOpen, bottomPad = 16.dp, showTime = true)
                    }
                }
            }
        } else if (key == "waiting") {
            val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
            PullToRefresh(
                isRefreshing = load.isRefreshing,
                onRefresh = load.refresh,
                topAppBarScrollBehavior = scrollBehavior,
                contentPadding = PaddingValues(top = topPad),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = load.state) {
                    UiState.Loading -> CenterLoading()
                    is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                    is UiState.Ready ->
                        if (s.value.waiting.isEmpty()) CenterText("Tidak ada hasil")
                        else MovieGrid(s.value.waiting, onOpen, bottomPad = 16.dp)
                }
            }
        } else {
            val load = rememberLoad("list" to key) { force ->
                if (key == "update") Api.newEpisodes(force = force) else Api.homeMovies(key, force = force)
            }
            PullToRefresh(
                isRefreshing = load.isRefreshing,
                onRefresh = load.refresh,
                topAppBarScrollBehavior = scrollBehavior,
                contentPadding = PaddingValues(top = topPad),
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

@Composable
fun FilterListScreen(
    kind: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val barTitle = title.ifBlank { "Kategori" }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = barTitle,
                largeTitle = barTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        val load = rememberLoad("filter" to (kind to id)) { force ->
            Api.exploreMovies(kind, id, force = force)
        }
        PullToRefresh(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            topAppBarScrollBehavior = scrollBehavior,
            contentPadding = PaddingValues(top = pad.calculateTopPadding()),
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
