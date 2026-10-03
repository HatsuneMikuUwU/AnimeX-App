@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Downloads
import com.uwu.animex.data.History
import com.uwu.animex.data.Movie

@Composable
fun DownloadStatusButton(item: Downloads.Item?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { if (item == null) onStart() else menu = true }, shapes = IconButtonDefaults.shapes()) {
            if (item == null) {
                Icon(Icons.Filled.Download, contentDescription = "Unduh")
            } else {
                when (item.status) {
                    Downloads.Status.QUEUED ->
                        AppLoadingIndicator(Modifier.size(24.dp))
                    Downloads.Status.DOWNLOADING ->
                        if (item.percent >= 0f) {
                            SmallWavyProgress(
                                progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            AppLoadingIndicator(Modifier.size(24.dp))
                        }
                    Downloads.Status.PAUSED -> Icon(Icons.Filled.Pause, contentDescription = "Lagi di-pause")
                    Downloads.Status.COMPLETED -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Udah kelar diunduh",
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
                        text = { Text("Pause") },
                        onClick = {
                            menu = false
                            Downloads.pause(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Gak jadi") },
                        onClick = {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.PAUSED -> {
                    DropdownMenuItem(
                        text = { Text("Lanjut") },
                        onClick = {
                            menu = false
                            Downloads.resume(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.FAILED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi dong") },
                        onClick = {
                            menu = false
                            Downloads.retry(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.COMPLETED -> DropdownMenuItem(
                    text = { Text("Hapus file unduhannya") },
                    onClick = {
                        menu = false
                        Downloads.remove(ctx, id)
                    },
                )
            }
        }
    }
}

private data class DownloadGroup(val key: String, val items: List<Downloads.Item>)

private fun statusLine(ctx: Context, d: Downloads.Item): String {
    val size = Formatter.formatShortFileSize(ctx, d.bytes)
    val quality = d.meta.quality?.takeIf { it.isNotBlank() }
    val status = when (d.status) {
        Downloads.Status.QUEUED -> "Ngantri dulu…"
        Downloads.Status.DOWNLOADING ->
            if (d.percent >= 0f) "${d.percent.toInt()}% · $size" else size
        Downloads.Status.PAUSED -> "Di-pause · $size"
        Downloads.Status.COMPLETED -> "Kelar · $size"
        Downloads.Status.FAILED -> d.error?.let { "Gagal: $it" } ?: "Gagal"
    }
    return listOfNotNull(quality, status).joinToString(" · ")
}

@Composable
fun DownloadsScreen(
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val downloads by Downloads.items.collectAsState()
    val groups = remember(downloads) {
        downloads.values
            .groupBy { it.meta.movieId ?: it.id }
            .map { (key, items) ->
                DownloadGroup(key, items.sortedBy { it.meta.epIndex?.toFloatOrNull() ?: Float.MAX_VALUE })
            }
            .sortedWith(
                compareBy<DownloadGroup> { g -> g.items.all { it.status == Downloads.Status.COMPLETED } }
                    .thenByDescending { g -> g.items.maxOf { it.startTimeMs } },
            )
    }
    if (groups.isEmpty()) {
        CenterText("Belum ada yang kamu unduh nih")
        return
    }
    val open: (Downloads.Item) -> Unit = { d ->
        if (d.status == Downloads.Status.COMPLETED) {
            History.record(
                Movie(
                    id = d.meta.movieId,
                    title = d.meta.movieTitle,
                    image_poster = d.meta.image,
                    views = d.meta.views,
                    favorites = d.meta.favorites,
                ),
                d.meta.epIndex,
                d.id,
            )
            onPlay(
                d.id,
                "${d.meta.movieTitle.orEmpty()} - Ep ${d.meta.epIndex.orEmpty()}",
                d.meta.movieId,
                d.meta.epIndex,
            )
        } else {
            d.meta.movieId?.let(onOpen)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp + LocalTopInset.current, bottom = 16.dp + LocalBottomInset.current)) {
        items(groups, key = { it.key }) { g ->
            if (g.items.size == 1) {
                DownloadCard(g.items.first()) { open(g.items.first()) }
            } else {
                DownloadGroupCard(g, open)
            }
        }
    }
}

@Composable
private fun DownloadCard(d: Downloads.Item, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
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
                Text(
                    statusLine(ctx, d),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            DownloadStatusButton(d, onStart = {})
        }
    }
}

@Composable
private fun DownloadGroupCard(group: DownloadGroup, onItemClick: (Downloads.Item) -> Unit) {
    val ctx = LocalContext.current
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }
    val first = group.items.first()
    val done = group.items.count { it.status == Downloads.Status.COMPLETED }
    val active = group.items.count {
        it.status == Downloads.Status.QUEUED || it.status == Downloads.Status.DOWNLOADING
    }
    val total = Formatter.formatShortFileSize(ctx, group.items.sumOf { it.bytes })
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
    ) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Poster(first.meta.image, Modifier.size(60.dp, 86.dp), 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        first.meta.movieTitle.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${group.items.size} episode",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        listOfNotNull(
                            "$done kelar",
                            active.takeIf { it > 0 }?.let { "$it lagi jalan" },
                            total,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Tutup daftar episode" else "Buka daftar episode",
                    modifier = Modifier.padding(12.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    group.items.forEach { d ->
                        Card(
                            onClick = { onItemClick(d) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Row(
                                Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Episode ${d.meta.epIndex.orEmpty()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        statusLine(ctx, d),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                DownloadStatusButton(d, onStart = {})
                            }
                        }
                    }
                }
            }
        }
    }
}
