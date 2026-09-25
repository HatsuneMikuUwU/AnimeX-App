package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.Movie
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private val numFmt: NumberFormat by lazy { NumberFormat.getIntegerInstance(Locale("id", "ID")) }

fun fmtNum(s: String?): String {
    val n = s?.toLongOrNull() ?: return s.orEmpty()
    return numFmt.format(n)
}

fun Movie.label(): String? =
    episode_index?.takeIf { it.isNotBlank() }?.let { "Episode $it" } ?: genre?.takeIf { it.isNotBlank() }

@Composable
fun Poster(url: String?, modifier: Modifier, radius: Dp = 16.dp) {
    AsyncImage(
        model = Api.absUrl(url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun SectionHeader(title: String, onMore: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (onMore != null) {
            FilledTonalIconButton(onClick = onMore) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Lihat semua")
            }
        }
    }
}

@Composable
private fun StatLine(badge: @Composable () -> Unit, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        badge()
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlayBadge() = Box(Modifier.size(14.dp).clip(CircleShape).background(AppColors.Red), Alignment.Center) {
    Icon(Icons.Filled.PlayArrow, null, Modifier.size(10.dp), tint = Color.White)
}

@Composable
private fun StarBadge() = Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = AppColors.Star)

@Composable
private fun ClockBadge() = Box(Modifier.size(14.dp).clip(CircleShape).background(AppColors.Purple), Alignment.Center) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitCard(
    m: Movie,
    modifier: Modifier,
    showTime: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick),
    ) {
        Poster(m.image_poster, Modifier.fillMaxWidth().height(150.dp))
        Text(
            m.label().orEmpty(), color = AppColors.Orange, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            m.title.orEmpty(), fontSize = 13.sp, maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(4.dp))
        StatLine({ PlayBadge() }, "${fmtNum(m.views)} views", AppColors.Red)
        Spacer(Modifier.height(2.dp))
        StatLine({ StarBadge() }, "${fmtNum(m.favorites)} favorites", AppColors.Star)
        if (showTime && !m.time.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            StatLine({ ClockBadge() }, m.time, AppColors.Purple)
        }
    }
}

@Composable
fun PortraitRow(list: List<Movie>, onOpen: (String) -> Unit, showTime: Boolean = false) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { m -> PortraitCard(m, Modifier.width(105.dp), showTime) { m.id?.let(onOpen) } }
    }
}

@Composable
fun ContinueWatchingRow(list: List<Movie>, onOpen: (String) -> Unit, onRemove: (Movie) -> Unit) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }

    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id ?: it.hashCode() }) { m ->
            PortraitCard(
                m,
                Modifier.width(105.dp),
                onLongClick = { pendingRemove = m },
            ) { m.id?.let(onOpen) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Hapus dari Lanjut Nonton?") },
            text = { Text("Progres tontonan \"${target.title.orEmpty()}\" akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(target)
                    pendingRemove = null
                }) { Text("Hapus", color = AppColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Batal") }
            },
        )
    }
}

@Composable
fun HotBlock(list: List<Movie>, onOpen: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { m ->
            Column(Modifier.width(268.dp).clickable { m.id?.let(onOpen) }) {
                Poster(m.image_cover ?: m.image_poster, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Poster(m.image_poster, Modifier.size(70.dp, 99.dp), 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.label().orEmpty(), color = AppColors.Orange, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.title.orEmpty(), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            StatLine({ PlayBadge() }, "${fmtNum(m.views)} views", AppColors.Red, Modifier.weight(1f))
                            StatLine({ StarBadge() }, "${fmtNum(m.favorites)} favorites", AppColors.Star, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomPreviewPager(list: List<Movie>, onOpen: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { list.size })
    LaunchedEffect(list) {
        if (list.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5000)
            if (pager.isScrollInProgress) continue
            val next = (pager.currentPage + 1) % list.size
            pager.animateScrollToPage(next)
        }
    }
    Column {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { i ->
            val m = list[i]
            Poster(
                m.image_cover ?: m.image_poster,
                Modifier.fillMaxWidth().aspectRatio(1.8f).clickable { m.id?.let(onOpen) },
                20.dp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            list.getOrNull(pager.currentPage)?.title.orEmpty(),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MovieGrid(list: List<Movie>, onOpen: (String) -> Unit, bottomPad: Dp, showTime: Boolean = false) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(list) { m -> PortraitCard(m, Modifier.fillMaxWidth(), showTime) { m.id?.let(onOpen) } }
    }
}

@Composable
fun ContinueWatchingGrid(list: List<Movie>, onOpen: (String) -> Unit, bottomPad: Dp, onRemove: (Movie) -> Unit) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(list, key = { it.id ?: it.hashCode() }) { m ->
            PortraitCard(
                m,
                Modifier.fillMaxWidth(),
                onLongClick = { pendingRemove = m },
            ) { m.id?.let(onOpen) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Hapus dari Lanjut Nonton?") },
            text = { Text("Progres tontonan \"${target.title.orEmpty()}\" akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(target)
                    pendingRemove = null
                }) { Text("Hapus", color = AppColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Batal") }
            },
        )
    }
}
