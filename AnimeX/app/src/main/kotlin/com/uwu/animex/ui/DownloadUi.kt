@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Downloads

@Composable
fun DownloadStatusButton(item: Downloads.Item?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { if (item == null) onStart() else menu = true }) {
            if (item == null) {
                Icon(Icons.Filled.Download, contentDescription = "Unduh")
            } else {
                when (item.status) {
                    Downloads.Status.QUEUED ->
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Downloads.Status.DOWNLOADING ->
                        if (item.percent >= 0f) {
                            CircularProgressIndicator(
                                progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    Downloads.Status.PAUSED -> Icon(Icons.Filled.Pause, contentDescription = "Dijeda")
                    Downloads.Status.COMPLETED -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Terunduh",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Downloads.Status.FAILED -> Icon(
                        Icons.Filled.Error,
                        contentDescription = "Gagal",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (item == null) return@DropdownMenu
            val id = item.id
            when (item.status) {
                Downloads.Status.QUEUED, Downloads.Status.DOWNLOADING -> {
                    DropdownMenuItem(
                        text = { Text("Jeda") },
                        onClick = { menu = false; Downloads.pause(ctx, id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Batalkan") },
                        onClick = { menu = false; Downloads.remove(ctx, id) },
                    )
                }
                Downloads.Status.PAUSED -> {
                    DropdownMenuItem(
                        text = { Text("Lanjutkan") },
                        onClick = { menu = false; Downloads.resume(ctx, id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = { menu = false; Downloads.remove(ctx, id) },
                    )
                }
                Downloads.Status.FAILED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi") },
                        onClick = { menu = false; Downloads.retry(ctx, id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = { menu = false; Downloads.remove(ctx, id) },
                    )
                }
                Downloads.Status.COMPLETED -> DropdownMenuItem(
                    text = { Text("Hapus file unduhan") },
                    onClick = { menu = false; Downloads.remove(ctx, id) },
                )
            }
        }
    }
}

@Composable
fun DownloadsScreen(onOpen: (String) -> Unit, onPlay: (episodeId: String, title: String) -> Unit) {
    val ctx = LocalContext.current
    val list = Downloads.items.values.sortedWith(
        compareBy<Downloads.Item> { it.status == Downloads.Status.COMPLETED }
            .thenByDescending { it.startTimeMs },
    )
    if (list.isEmpty()) {
        CenterText("Belum ada unduhan")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
        items(list, key = { it.id }) { d ->
            val title = "${d.meta.movieTitle.orEmpty()} - Ep ${d.meta.epIndex.orEmpty()}"
            Card(
                onClick = {
                    if (d.status == Downloads.Status.COMPLETED) {
                        com.uwu.animex.data.History.record(
                            com.uwu.animex.data.Movie(
                                id = d.meta.movieId,
                                title = d.meta.movieTitle,
                                image_poster = d.meta.image,
                            ),
                            d.meta.epIndex,
                            d.id,
                        )
                        onPlay(d.id, title)
                    } else {
                        d.meta.movieId?.let(onOpen)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(d.meta.image, Modifier.size(60.dp, 86.dp), 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.meta.movieTitle.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Episode ${d.meta.epIndex.orEmpty()}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        val size = Formatter.formatShortFileSize(ctx, d.bytes)
                        val quality = d.meta.quality?.takeIf { it.isNotBlank() }
                        val status = when (d.status) {
                            Downloads.Status.QUEUED -> "Menunggu…"
                            Downloads.Status.DOWNLOADING ->
                                if (d.percent >= 0f) "${d.percent.toInt()}% · $size" else size
                            Downloads.Status.PAUSED -> "Dijeda · $size"
                            Downloads.Status.COMPLETED -> "Selesai · $size"
                            Downloads.Status.FAILED -> d.error?.let { "Gagal: $it" } ?: "Gagal"
                        }
                        Text(
                            listOfNotNull(quality, status).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    when (d.status) {
                        Downloads.Status.QUEUED, Downloads.Status.DOWNLOADING ->
                            IconButton(onClick = { Downloads.pause(ctx, d.id) }) {
                                Icon(Icons.Filled.Pause, contentDescription = "Jeda")
                            }
                        Downloads.Status.PAUSED ->
                            IconButton(onClick = { Downloads.resume(ctx, d.id) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Lanjutkan")
                            }
                        Downloads.Status.FAILED ->
                            IconButton(onClick = { Downloads.retry(ctx, d.id) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Coba lagi")
                            }
                        Downloads.Status.COMPLETED -> Unit
                    }
                    IconButton(onClick = { Downloads.remove(ctx, d.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Hapus")
                    }
                }
            }
        }
    }
}
