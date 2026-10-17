@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.jikan.JikanActivity
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.sync.providers.AniActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Model UI bersama buat history MAL (Jikan) dan AniList. */
internal data class ActivityEntry(
    val title: String,
    val trailing: String,
    val url: String?,
    val epochSeconds: Long?,
)

private fun episodeText(count: String?): String {
    val n = count?.trim().orEmpty()
    return if (n.isEmpty()) "Update" else "Nonton ep. $n"
}

internal fun JikanActivity.toEntry() =
    ActivityEntry(
        title = title,
        trailing = episodeText(increment.takeIf { it > 0 }?.toString()),
        url = url,
        epochSeconds = epochSeconds,
    )

internal fun AniActivity.toEntry(): ActivityEntry {
    val trailing =
        when (status.lowercase()) {
            "watched episode" -> episodeText(progress)
            "rewatched episode" -> "Ulang ${episodeText(progress).lowercase()}"
            "completed" -> "Selesai"
            "plans to watch" -> "Plan"
            "paused watching" -> "Pause"
            "dropped" -> "Drop"
            else -> status.replaceFirstChar { it.uppercase() }.ifBlank { "Update" }
        }
    return ActivityEntry(title = title, trailing = trailing, url = url, epochSeconds = createdAt)
}

internal sealed interface ActivityState {
    data object Loading : ActivityState

    data class Error(
        val message: String,
    ) : ActivityState

    data class Ready(
        val items: List<ActivityEntry>,
    ) : ActivityState
}

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun startOfDay(ms: Long): Long =
    Calendar
        .getInstance()
        .apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

private const val LABEL_TODAY = "Hari ini"
private const val LABEL_YESTERDAY = "Kemarin"

/** Label grup ala DailyAL: Hari ini, Kemarin, nama hari, Minggu lalu, dst. */
private fun groupLabel(epochSeconds: Long?): String {
    if (epochSeconds == null) return "Sebelumnya"
    val ms = epochSeconds * 1000L
    val days = ((startOfDay(System.currentTimeMillis()) - startOfDay(ms)) / DAY_MS).toInt()
    return when {
        days <= 0 -> LABEL_TODAY
        days == 1 -> LABEL_YESTERDAY
        days < 6 -> SimpleDateFormat("EEEE", Locale("id", "ID")).format(Date(ms)).replaceFirstChar { it.uppercase() }
        days < 14 -> "Minggu lalu"
        days < 21 -> "2 minggu lalu"
        days < 28 -> "3 minggu lalu"
        days < 35 -> "Bulan lalu"
        days < 60 -> "2 bulan lalu"
        else -> "Lebih lama"
    }
}

@Composable
internal fun ActivitySection(
    state: ActivityState,
    subtitle: String,
    onRetry: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "History",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(12.dp))
        when (state) {
            ActivityState.Loading ->
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator()
                }

            is ActivityState.Error ->
                CenteredMessage(
                    text = "Gagal muat history: ${state.message}",
                    action = {
                        FilledTonalButton(onClick = hapticClick(block = onRetry)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Coba lagi")
                        }
                    },
                )

            is ActivityState.Ready -> {
                val shown = state.items
                if (shown.isEmpty()) {
                    CenteredMessage(text = "History nggak ketemu. Pastiin profil kamu nggak diprivat.")
                } else {
                    val groups = shown.groupBy { groupLabel(it.epochSeconds) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { (label, items) -> HistoryGroup(label, items) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryGroup(
    label: String,
    items: List<ActivityEntry>,
) {
    val cs = MaterialTheme.colorScheme
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val open = expanded[label] ?: (label == LABEL_TODAY || label == LABEL_YESTERDAY)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainer),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded[label] = !open }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "${items.size} item",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(bottom = 8.dp)) { items.forEach { HistoryRow(it) } }
        }
    }
}

private val timeFormat get() = SimpleDateFormat("d MMM, HH:mm", Locale("id", "ID"))

@Composable
private fun HistoryRow(item: ActivityEntry) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val time = item.epochSeconds?.let { timeFormat.format(Date(it * 1000L)) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.url != null) { item.url?.let(uri::openUri) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (time != null) {
                Text(time, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(item.trailing, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = cs.onSecondaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(text, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
