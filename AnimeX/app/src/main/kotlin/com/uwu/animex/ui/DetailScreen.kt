@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
                title = { Text("Detail") },
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
                LazyColumn(Modifier.padding(pad)) {
                    item { Header(movie) }
                    items(eps) { ep -> EpisodeRow(ep) { ep.id?.let { onPlay(it, "${movie?.title.orEmpty()} - Ep ${ep.index.orEmpty()}") } } }
                }
            }
        }
    }
}

@Composable
private fun Header(m: Movie?) {
    if (m == null) return
    Column {
        AsyncImage(
            model = Api.absUrl(m.image_cover ?: m.image_poster),
            contentDescription = m.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Column(Modifier.padding(16.dp)) {
            Text(m.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            val meta = listOfNotNull(m.type, m.year, m.status, m.studio).filter { it.isNotBlank() }.joinToString(" • ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (!m.genre.isNullOrBlank()) Text(m.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (!m.synopsis.isNullOrBlank()) Text(m.synopsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            Text("Episode", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text("Episode ${ep.index.orEmpty()}") },
        supportingContent = { if (!ep.title.isNullOrBlank()) Text(ep.title) },
        trailingContent = { ep.key_time?.let { Text(it, style = MaterialTheme.typography.labelSmall) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}
