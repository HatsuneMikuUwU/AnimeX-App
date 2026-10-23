@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.notifications.subscription.NotificationHistory
import com.uwu.animex.notifications.subscription.SubscriptionHelper
import com.uwu.animex.notifications.subscription.SubscriptionStore
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.rememberBlurBackdrop

private enum class CenterTab(
    val label: String,
) {
    History("Riwayat"),
    Watching("Dipantau"),
}

@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val history by NotificationHistory.items.collectAsStateWithLifecycle()
    val alerts by EpisodeAlerts.alerts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CenterTab.History.name) }
    val current = CenterTab.valueOf(tab)
    val watching = remember(alerts) { alerts.values.sortedByDescending { it.createdAt } }

    // Begitu layar ini kebuka, semua kabar dianggap udah dilihat.
    LaunchedEffect(history.size) { NotificationHistory.markAllRead() }

    val backdrop = rememberBlurBackdrop()
    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        "Pusat notifikasi",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = if (current == CenterTab.History && history.isNotEmpty()) Icons.Outlined.DeleteSweep else null,
                trailingDescription = "Hapus semua riwayat",
                onTrailingClick = { NotificationHistory.clear() },
            )
        },
        containerColor = screenContainerColor(),
    ) { pad ->
        BlurContentBox(pad, backdrop, contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
            ) {
                Spacer(Modifier.height(8.dp + LocalTopInset.current))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CenterTab.entries.forEach { t ->
                        val count = if (t == CenterTab.History) history.size else watching.size
                        FilterChip(
                            selected = t == current,
                            onClick = { tab = t.name },
                            label = { Text("${t.label} ($count)") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val bottom = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                when (current) {
                    CenterTab.History ->
                        if (history.isEmpty()) {
                            EmptyState("Belum ada kabar episode baru. Pasang lonceng di anime yang kamu ikutin ya.")
                        } else {
                            LazyColumn(
                                contentPadding = bottom,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.navigationBarsPadding(),
                            ) {
                                items(history, key = { "${it.mediaId}-${it.time}" }) { item ->
                                    HistoryRow(item, onClick = { onOpen(item.mediaId) })
                                }
                            }
                        }

                    CenterTab.Watching ->
                        if (watching.isEmpty()) {
                            EmptyState("Belum ada anime yang dipantau. Ketuk ikon lonceng di halaman detail anime.")
                        } else {
                            LazyColumn(
                                contentPadding = bottom,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.navigationBarsPadding(),
                            ) {
                                items(watching, key = { it.movieId }) { item ->
                                    WatchingRow(
                                        item = item,
                                        onClick = { onOpen(item.movieId) },
                                        onRemove = { SubscriptionHelper.deleteSubscription(item.movieId) },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRow(
    item: SubscriptionStore,
    onClick: () -> Unit,
) {
    val click = hapticClick(block = onClick)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Poster(item.image, Modifier.size(width = 56.dp, height = 80.dp), radius = 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils
                        .getRelativeTimeSpanString(item.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                        .toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = hapticClick { NotificationHistory.remove(item) }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Hapus dari riwayat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WatchingRow(
    item: EpisodeAlertEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val click = hapticClick(block = onClick)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Poster(item.imagePoster, Modifier.size(width = 56.dp, height = 80.dp), radius = 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.title ?: "Anime",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (item.lastEpisode > 0) "Episode terakhir yang diketahui: ${item.lastEpisode}" else "Belum ada data episode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = hapticClick(block = onRemove)) {
                Icon(
                    Icons.Outlined.NotificationsOff,
                    contentDescription = "Berhenti pantau",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
