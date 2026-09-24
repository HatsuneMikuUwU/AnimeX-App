@file:OptIn(ExperimentalFoundationApi::class)

package com.uwu.animex.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.HomeData
import com.uwu.animex.data.Movie

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    val state by rememberLoad(Unit) { Api.home() }
    when (val s = state) {
        UiState.Loading -> CenterLoading()
        is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
        is UiState.Ready -> HomeContent(s.value, onOpen, onMore)
    }
}

@Composable
private fun HomeContent(h: HomeData, onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
        if (h.slider.isNotEmpty()) {
            item {
                val pager = rememberPagerState(pageCount = { h.slider.size })
                HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { i ->
                    Poster(h.slider[i].image, Modifier.fillMaxWidth().aspectRatio(1.8f), 20.dp)
                }
            }
        }
        section("Lanjut Nonton", h.history, null, onMore) { PortraitRow(h.history, onOpen) }
        section("Episode Baru", h.update, "update", onMore) { PortraitRow(h.update, onOpen) }
        section("Sedang Hangat", h.hot, "hot", onMore) { HotBlock(h.hot, onOpen) }
        section("Judul Baru", h.new, "new", onMore) { PortraitRow(h.new, onOpen) }
        section("Jadwal Hari ini", h.today, null, onMore) { PortraitRow(h.today, onOpen, showTime = true) }
        section("Jas Por Yu", h.random, "random", onMore) { HotBlock(h.random, onOpen) }
        section("Paling Dinanti", h.waiting, null, onMore) { PortraitRow(h.waiting, onOpen) }
        section("Populer", h.popular, "popular", onMore) { PortraitRow(h.popular, onOpen) }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<Movie>,
    more: String?,
    onMore: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    if (list.isEmpty()) return
    item { SectionHeader(title, more?.let { key -> { onMore(key) } }) }
    item { content() }
}
