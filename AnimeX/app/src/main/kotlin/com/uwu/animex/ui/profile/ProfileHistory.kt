@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwu.animex.data.history.HISTORY_TODAY
import com.uwu.animex.data.history.HISTORY_YESTERDAY
import com.uwu.animex.data.history.ProfileHistoryItem
import com.uwu.animex.data.history.ProfileHistoryState
import com.uwu.animex.ui.common.cardBackground
import com.uwu.animex.ui.common.defaultPlaceholder
import com.uwu.animex.ui.common.hapticClick

@Composable
internal fun ProfileHistorySection(
    state: ProfileHistoryState,
    onLoad: () -> Unit,
    onOpen: (String) -> Unit,
    failedMessage: String,
) {
    LaunchedEffect(Unit) { onLoad() }
    val cs = MaterialTheme.colorScheme
    when {
        state.items.isEmpty() && (state.loading || !state.loaded && state.error == null) ->
            HistoryPlaceholder()
        state.items.isEmpty() && state.error != null ->
            StatsPlaceholder(loading = false, onRetry = onLoad, message = failedMessage)
        state.items.isEmpty() ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = cs.onSurfaceVariant)
                Text("Belum ada riwayat", color = cs.onSurfaceVariant)
            }
        else -> {
            val groups = remember(state.items) { state.items.groupBy { it.group }.entries.toList() }
            val expanded = remember { mutableStateMapOf<String, Boolean>() }
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (label, items) ->
                    val open = expanded[label] ?: (label == HISTORY_TODAY || label == HISTORY_YESTERDAY)
                    HistoryGroup(
                        label = label,
                        items = items,
                        open = open,
                        onToggle = { expanded[label] = !open },
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryPlaceholder() {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(2) { group ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).cardBackground()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Placeholder", Modifier.weight(1f).defaultPlaceholder(), fontSize = 16.sp, maxLines = 1)
                    Spacer(Modifier.size(8.dp))
                    Text("0 item", Modifier.defaultPlaceholder(), style = MaterialTheme.typography.labelMedium)
                }
                repeat(if (group == 0) 3 else 2) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Placeholder title", Modifier.fillMaxWidth(0.8f).defaultPlaceholder(), fontSize = 15.sp, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Text("Today, 0:00 AM", Modifier.defaultPlaceholder(), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.size(12.dp))
                        Text("ep. 0", Modifier.defaultPlaceholder(), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.size(6.dp))
            }
        }
    }
}

@Composable
private fun HistoryGroup(
    label: String,
    items: List<ProfileHistoryItem>,
    open: Boolean,
    onToggle: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "history-group-chevron")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).cardBackground()) {
        Row(
            Modifier
                .fillMaxWidth()
                .hapticClick(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${items.size} item", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (open) "Tutup" else "Buka",
                modifier = Modifier.size(22.dp).rotate(rotation),
                tint = cs.onSurfaceVariant,
            )
        }
        AnimatedVisibility(open) {
            Column {
                items.forEach { HistoryTile(it, onOpen) }
                Spacer(Modifier.size(6.dp))
            }
        }
    }
}

@Composable
private fun HistoryTile(
    item: ProfileHistoryItem,
    onOpen: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .hapticClick { item.url?.let(onOpen) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.time.isNotBlank()) {
                Text(
                    item.time,
                    modifier = Modifier.padding(top = 2.dp),
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            item.action,
            modifier = Modifier.widthIn(max = 120.dp),
            color = cs.primary,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
