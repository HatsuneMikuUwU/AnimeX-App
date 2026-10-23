@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.notifications.subscription.NotificationHistory
import com.uwu.animex.notifications.subscription.SubscriptionHelper
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.common.cardBackground
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.detail.isScrollingUp
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.util.WindowBlurEffect

private enum class CenterTab(
    val label: String,
    val icon: ImageVector,
) {
    History("Riwayat", Icons.Outlined.History),
    Watching("Dipantau", Icons.Outlined.Notifications),
}

@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val history by NotificationHistory.items.collectAsStateWithLifecycle()
    val alerts by EpisodeAlerts.alerts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CenterTab.History.name) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val current = CenterTab.valueOf(tab)
    val watching = remember(alerts) { alerts.values.sortedByDescending { it.createdAt } }
    val historyState = rememberLazyListState()
    val watchingState = rememberLazyListState()
    val fabExpanded = isScrollingUp(if (current == CenterTab.History) historyState else watchingState)
    val counts = mapOf(CenterTab.History to history.size, CenterTab.Watching to watching.size)

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
                trailingIcon = if (current == CenterTab.History &&
                    history.isNotEmpty()
                ) {
                    Icons.Outlined.DeleteSweep
                } else {
                    null
                },
                trailingDescription = "Hapus semua riwayat",
                onTrailingClick = { NotificationHistory.clear() },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = hapticClick { showSheet = true },
                expanded = fabExpanded,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(current.icon, contentDescription = "Pilih daftar") },
                text = { Text("${current.label} (${counts.getValue(current)})") },
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
                val contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp + LocalTopInset.current,
                        bottom = 88.dp + LocalBottomInset.current,
                    )
                when (current) {
                    CenterTab.History ->
                        if (history.isEmpty()) {
                            AnimatedEmptyState(
                                icon = CenterTab.History.icon,
                                title = "Belum ada kabar episode baru",
                                message = "Pasang lonceng di anime yang kamu ikutin ya.",
                            )
                        } else {
                            LazyColumn(
                                state = historyState,
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(history, key = { "${it.mediaId}-${it.time}" }) { item ->
                                    NotificationCard(
                                        imageUrl = item.image,
                                        label =
                                            DateUtils
                                                .getRelativeTimeSpanString(
                                                    item.time,
                                                    System.currentTimeMillis(),
                                                    DateUtils.MINUTE_IN_MILLIS,
                                                ).toString(),
                                        title = item.title,
                                        body = item.content,
                                        actionIcon = Icons.Outlined.Close,
                                        actionDescription = "Hapus dari riwayat",
                                        onClick = { onOpen(item.mediaId) },
                                        onAction = { NotificationHistory.remove(item) },
                                    )
                                }
                            }
                        }

                    CenterTab.Watching ->
                        if (watching.isEmpty()) {
                            AnimatedEmptyState(
                                icon = CenterTab.Watching.icon,
                                title = "Belum ada anime yang dipantau",
                                message = "Ketuk ikon lonceng di halaman detail anime.",
                            )
                        } else {
                            LazyColumn(
                                state = watchingState,
                                contentPadding = contentPadding,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(watching, key = { it.movieId }) { item ->
                                    NotificationCard(
                                        imageUrl = item.imagePoster,
                                        label = "Dipantau",
                                        title = item.title ?: "Anime",
                                        body =
                                            if (item.lastEpisode > 0) {
                                                "Episode terakhir yang diketahui: ${item.lastEpisode}"
                                            } else {
                                                "Belum ada data episode"
                                            },
                                        actionIcon = Icons.Outlined.NotificationsOff,
                                        actionDescription = "Berhenti pantau",
                                        onClick = { onOpen(item.movieId) },
                                        onAction = { SubscriptionHelper.deleteSubscription(item.movieId) },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }

    if (showSheet) {
        CenterTabSheet(
            current = current,
            counts = counts,
            onDismiss = { showSheet = false },
            onSelect = {
                tab = it.name
                showSheet = false
            },
        )
    }
}

@Composable
private fun CenterTabSheet(
    current: CenterTab,
    counts: Map<CenterTab, Int>,
    onDismiss: () -> Unit,
    onSelect: (CenterTab) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            val entries = CenterTab.entries
            entries.forEachIndexed { index, t ->
                SheetOptionItem(
                    index = index,
                    count = entries.size,
                    icon = t.icon,
                    label = "${t.label} (${counts.getValue(t)})",
                    selected = t == current,
                    onClick = { onSelect(t) },
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(
    imageUrl: String?,
    label: String,
    title: String,
    body: String,
    actionIcon: ImageVector,
    actionDescription: String,
    onClick: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .cardBackground()
            .hapticClick(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(imageUrl, Modifier.size(70.dp, 99.dp), 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = hapticClick(block = onAction)) {
            Icon(
                actionIcon,
                contentDescription = actionDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
