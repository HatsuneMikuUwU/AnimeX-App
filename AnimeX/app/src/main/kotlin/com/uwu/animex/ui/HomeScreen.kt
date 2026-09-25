@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreItem
import com.uwu.animex.data.History
import com.uwu.animex.data.HomeData
import com.uwu.animex.data.Movie

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onOpenCharacter: () -> Unit = {},
    onOpenCharacterDetail: (ExploreItem) -> Unit = {},
) {
    val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
    PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                is UiState.Ready -> HomeContent(
                    s.value, onOpen, onMore, onOpenCharacter, onOpenCharacterDetail,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    h: HomeData,
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenCharacterDetail: (ExploreItem) -> Unit,
) {
    val manraAtTop = h.manraPos == "top" && h.manra.isNotEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
        val previewSource = h.random.ifEmpty { h.hot }.ifEmpty { h.new }
        if (previewSource.isNotEmpty()) {
            item {
                val previewList = remember(previewSource) { previewSource.shuffled() }
                RandomPreviewPager(previewList, onOpen)
            }
        }
        if (manraAtTop) {
            manraSection(h.manra, onOpenCharacter, onOpenCharacterDetail)
        }
        val history = History.items.ifEmpty { h.history }
        val historyIsLocal = History.items.isNotEmpty()
        section("Lanjut Nonton", history, if (historyIsLocal) "history" else null, onMore) {
            if (historyIsLocal) {
                ContinueWatchingRow(history, onOpen) { movie -> movie.id?.let(History::remove) }
            } else {
                PortraitRow(history, onOpen)
            }
        }
        section("Episode Baru", h.update, "update", onMore) { PortraitRow(h.update, onOpen) }
        section("Sedang Hangat", h.hot, "hot", onMore) { HotBlock(h.hot, onOpen) }
        section("Judul Baru", h.new, "new", onMore) { PortraitRow(h.new, onOpen) }
        section("Jadwal Hari ini", h.today, null, onMore) { PortraitRow(h.today, onOpen, showTime = true) }
        section("Jas Por Yu", h.random, "random", onMore) { HotBlock(h.random, onOpen) }
        section("Paling Dinanti", h.waiting, null, onMore) { PortraitRow(h.waiting, onOpen) }
        section("Populer", h.popular, "popular", onMore) { PortraitRow(h.popular, onOpen) }
        if (!manraAtTop && h.manra.isNotEmpty()) {
            manraSection(h.manra, onOpenCharacter, onOpenCharacterDetail)
        }
    }
}

private fun LazyListScope.manraSection(
    list: List<ExploreItem>,
    onMore: () -> Unit,
    onOpen: (ExploreItem) -> Unit,
) {
    item { SectionHeader("Baca Manra", onMore = onMore) }
    item { ManraRow(list, onOpen) }
}

@Composable
private fun ManraRow(list: List<ExploreItem>, onOpen: (ExploreItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.id ?: it.displayName }) { item ->
            ManraCard(item, onClick = { onOpen(item) })
        }
    }
}

@Composable
private fun ManraCard(item: ExploreItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = Api.absUrl(item.imageUrl),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                startY = 80f,
                            ),
                        ),
                )
            }
            Text(
                "Manra",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCCE91E63))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
