@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.Episode
import com.uwu.animex.data.Movie
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, onPlay: (episodeId: String, title: String) -> Unit) {
    val state by rememberLoad(id) {
        coroutineScope {
            val m = async { Api.detail(id) }
            val e = async { Api.episodes(id) }
            m.await() to e.await()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
            is UiState.Ready -> {
                val (movie, eps) = s.value
                val title = movie?.title.orEmpty()
                LazyColumn(Modifier.padding(pad)) {
                    item { Header(movie, eps) { ep -> ep.id?.let { onPlay(it, "$title - Ep ${ep.index.orEmpty()}") } } }
                    item {
                        Text(
                            "${eps.size} Episode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(eps) { ep ->
                        EpisodeRow(ep) { ep.id?.let { onPlay(it, "$title - Ep ${ep.index.orEmpty()}") } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(m: Movie?, eps: List<Episode>, onPlay: (Episode) -> Unit) {
    if (m == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val first = eps.minByOrNull { it.index?.toIntOrNull() ?: Int.MAX_VALUE }
    Column {
        Poster(m.image_cover ?: m.image_poster, Modifier.fillMaxWidth().aspectRatio(16f / 9f), 0.dp)
        Row(Modifier.padding(16.dp)) {
            Poster(m.image_poster, Modifier.size(100.dp, 150.dp), 12.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                if (!m.studio.isNullOrBlank()) Text(m.studio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${fmtNum(m.views)} views • ${fmtNum(m.favorites)} favorit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val genres = m.genre.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (genres.isNotEmpty()) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(genres) { g -> SuggestionChip(onClick = {}, label = { Text(g) }) }
            }
        }
        Button(
            onClick = { first?.let(onPlay) },
            enabled = first != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (first != null) "Putar Episode ${first.index.orEmpty()}" else "Belum ada episode")
        }
        if (!m.synopsis.isNullOrBlank()) {
            Text(
                m.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Poster(ep.image, Modifier.size(96.dp, 54.dp), 8.dp) },
        headlineContent = { Text("Episode ${ep.index.orEmpty()}") },
        supportingContent = { if (!ep.title.isNullOrBlank()) Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { ep.key_time?.let { Text(it, style = MaterialTheme.typography.labelSmall) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}
