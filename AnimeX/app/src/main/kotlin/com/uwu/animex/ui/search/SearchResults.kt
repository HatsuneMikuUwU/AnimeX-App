@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.TraceMoe
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.SectionHeader
import com.uwu.animex.ui.common.fabClearance
import com.uwu.animex.ui.common.hapticClick

private const val PREVIEW_LIMIT = 3

@Composable
internal fun CategoryContent(
    data: ExploreData,
    listState: LazyListState,
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenYear: () -> Unit,
    onOpenType: () -> Unit,
) {
    val types = data.typeOrDefault
    val genres = data.genre.take(PREVIEW_LIMIT)
    val studios = data.studio
    val years = data.year.take(PREVIEW_LIMIT)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = 12.dp + LocalTopInset.current,
                bottom = fabClearance() + LocalBottomInset.current,
            ),
    ) {
        if (genres.isNotEmpty()) {
            item {
                SectionHeader("Kategori", onMore = onOpenCategory, topPadding = 4.dp, icon = Icons.Outlined.Category)
            }
            itemsIndexed(genres) { index, item ->
                GenreCard(
                    item = item,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = if (index == genres.lastIndex) 0.dp else 8.dp,
                            ),
                ) {
                    val filterId = item.id?.takeIf { it.isNotBlank() } ?: item.displayName
                    onFilter("genre", filterId, item.displayName)
                }
            }
        }

        item {
            SectionHeader(
                "Studio",
                onMore = onOpenStudio,
                topPadding = if (genres.isEmpty()) 4.dp else 18.dp,
                icon = Icons.Outlined.Movie,
            )
            if (studios.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    studios.forEach { item ->
                        TypeCard(item.displayName) {
                            onFilter("studio", item.displayName, item.displayName)
                        }
                    }
                }
            } else {
                Text(
                    "Studionya kosong nih",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 13.sp,
                )
            }
        }

        if (years.isNotEmpty()) {
            item { SectionHeader("Tahun", onMore = onOpenYear, icon = Icons.Outlined.CalendarMonth) }
            itemsIndexed(years) { index, item ->
                YearCard(
                    item = item,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = if (index == years.lastIndex) 0.dp else 8.dp),
                ) {
                    onFilter("year", item.displayName, item.displayName)
                }
            }
        }

        item {
            SectionHeader("Tipe", onMore = onOpenType, icon = Icons.Outlined.Tv)
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                types.forEach { item ->
                    TypeCard(item.displayName) {
                        onFilter("type", item.displayName, item.displayName)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImageSearchResults(
    results: List<TraceMoe.Result>,
    resolvingTitle: String?,
    onPick: (TraceMoe.Result) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp + LocalTopInset.current,
                    bottom = fabClearance() + LocalBottomInset.current,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val top = results.first()
            item(key = "top") {
                TopResultCard(top, busy = resolvingTitle == top.displayTitle, enabled = resolvingTitle == null) {
                    onPick(top)
                }
            }
            itemsIndexed(
                results.drop(1),
                key = { i, it -> "$i-${it.anilist?.id}-${it.episode}-${it.displayTitle}" },
                contentType = { _, _ -> "trace-result" },
            ) { _, hit ->
                ResultRowCard(
                    hit,
                    busy = resolvingTitle == hit.displayTitle,
                    enabled = resolvingTitle == null,
                ) {
                    onPick(hit)
                }
            }
        }
    }
}

@Composable
private fun TopResultCard(
    hit: TraceMoe.Result,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = enabled, onClick = hapticClick(block = onClick), role = Role.Button)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = hit.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularWavyProgressIndicator(
                    progress = { hit.similarityPercent / 100f },
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    "${hit.similarityPercent}% mirip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    hit.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hit.episodeLabel.isNotBlank()) {
                    Text(
                        "Episode ${hit.episodeLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            if (busy) {
                LoadingIndicator(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ResultRowCard(
    hit: TraceMoe.Result,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = hapticClick(block = onClick), role = Role.Button)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = hit.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(Modifier.weight(1f)) {
            Text(
                hit.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.episodeLabel.isNotBlank()) {
                Text(
                    "Ep ${hit.episodeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (busy) {
            LoadingIndicator(Modifier.size(22.dp))
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "${hit.similarityPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun isListScrollingUp(listState: LazyListState): Boolean {
    var previousIndex by remember(listState) { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousOffset by remember(listState) { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    return remember(listState) {
        derivedStateOf {
            val up =
                if (previousIndex != listState.firstVisibleItemIndex) {
                    previousIndex > listState.firstVisibleItemIndex
                } else {
                    previousOffset >= listState.firstVisibleItemScrollOffset
                }
            previousIndex = listState.firstVisibleItemIndex
            previousOffset = listState.firstVisibleItemScrollOffset
            up
        }
    }.value
}
