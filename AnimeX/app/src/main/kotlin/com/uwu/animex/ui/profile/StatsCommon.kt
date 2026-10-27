@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.rememberTonalPalette
import com.materialkolor.ktx.toneColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uwu.animex.data.tracker.ListRank
import com.uwu.animex.data.tracker.ListRankings
import com.uwu.animex.data.tracker.ListStat
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.hapticClick
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun StatsPlaceholder(
    loading: Boolean,
    onRetry: () -> Unit,
    message: String,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            AppLoadingIndicator()
        } else {
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = hapticClick(block = onRetry), shapes = ButtonDefaults.shapes()) {
                Text("Coba lagi")
            }
        }
    }
}

internal enum class StatMode(
    val label: String,
) {
    TITLES("Judul"),
    TIME("Waktu"),
    SCORE("Skor"),
}

internal fun fmt(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')

internal fun formatLabel(key: String?) =
    when (key) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        "TV_SPECIAL" -> "TV Special"
        "CM" -> "CM"
        "PV" -> "PV"
        "UNKNOWN" -> "Lainnya"
        else -> key ?: "?"
    }

/**
 * Warna aksen status (hijau, biru, kuning, merah) dari palet tonal yang diselaraskan ke warna
 * primary tema, jadi tetap terbaca sebagai status tapi menyatu dengan tema.
 */
@Composable
private fun statusAccents(): List<Color> {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val tone = if (dark) 80 else 40
    val green = rememberTonalPalette(seed = Color(0xFF2E7D32), harmonizeWith = cs.primary)
    val blue = rememberTonalPalette(seed = Color(0xFF1565C0), harmonizeWith = cs.primary)
    val amber = rememberTonalPalette(seed = Color(0xFFF9A825), harmonizeWith = cs.primary)
    val red = rememberTonalPalette(seed = Color(0xFFC62828), harmonizeWith = cs.primary)
    return listOf(green.toneColor(tone), blue.toneColor(tone), amber.toneColor(tone), red.toneColor(tone))
}

@Composable
internal fun statusColors(): Map<String, Color> {
    val cs = MaterialTheme.colorScheme
    val (green, blue, amber, red) = statusAccents()
    return mapOf(
        "CURRENT" to green,
        "COMPLETED" to blue,
        "PAUSED" to amber,
        "DROPPED" to red,
        "PLANNING" to cs.outline,
        "REPEATING" to cs.tertiary,
    )
}

@Composable
internal fun List<ListStat>.toSlices(
    label: (String?) -> String,
    colors: Map<String, Color>,
): List<StatSlice> {
    val cs = MaterialTheme.colorScheme
    val accents = statusAccents()
    val fallback =
        listOf(
            cs.primary,
            cs.tertiary,
            cs.secondary,
            accents[0],
            accents[1],
            accents[2],
            accents[3],
            cs.outline,
        )
    return mapIndexed { i, st ->
        StatSlice(label(st.key), st.count, colors[st.key] ?: fallback[i % fallback.size], cs.onPrimary)
    }
}

@Composable
private fun StatCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(cs.surfaceContainer)
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ModeChips(
    modes: List<StatMode>,
    selected: StatMode,
    onSelect: (StatMode) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { m ->
            FilterChip(selected = selected == m, onClick = hapticClick { onSelect(m) }, label = { Text(m.label) })
        }
    }
}

private fun barValue(
    v: Double,
    mode: StatMode,
): String =
    when (mode) {
        StatMode.TITLES -> v.roundToLong().toString()
        StatMode.TIME -> if (v >= 100) v.roundToLong().toString() else "%.1f".format(Locale.US, v).removeSuffix(".0")
        StatMode.SCORE -> "%.1f".format(Locale.US, v).removeSuffix(".0")
    }

private fun ListStat.valueFor(mode: StatMode): Double =
    when (mode) {
        StatMode.TITLES -> count.toDouble()
        StatMode.TIME -> minutes / 60.0
        StatMode.SCORE -> meanScore
    }

@Composable
internal fun BarCard(
    title: String,
    modes: List<StatMode>,
    items: List<ListStat>,
    label: (ListStat) -> String,
) {
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(modes.first()) }
    StatCard(title) {
        ModeChips(modes, mode) { mode = it }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("Belum ada data", color = cs.onSurfaceVariant)
        } else {
            val max = remember(items, mode) { items.maxOf { it.valueFor(mode) }.takeIf { it > 0 } ?: 1.0 }
            val chartHeight = 120.dp
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { st ->
                    val v = st.valueFor(mode)
                    Column(
                        Modifier.width(44.dp).height(chartHeight + 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            barValue(v, mode),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(28.dp)
                                .height((chartHeight * (v / max).toFloat()).coerceAtLeast(4.dp))
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 10.dp,
                                        topEnd = 10.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 4.dp,
                                    ),
                                ).background(cs.primary),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label(st), style = MaterialTheme.typography.labelSmall, color = cs.onSurface, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RankCard(
    title: String,
    rankings: ListRankings,
) {
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(StatMode.TITLES) }
    var expanded by remember { mutableStateOf(false) }
    val all =
        when (mode) {
            StatMode.TITLES -> rankings.byCount
            StatMode.TIME -> rankings.byTime
            StatMode.SCORE -> rankings.byScore
        }
    val shown = if (expanded) all else all.take(5)
    StatCard(title) {
        ModeChips(StatMode.entries, mode) { mode = it }
        Spacer(Modifier.height(8.dp))
        if (all.isEmpty()) {
            Text("Belum ada data", color = cs.onSurfaceVariant)
        } else {
            shown.forEachIndexed { index, r -> RankRow(index + 1, r) }
            if (all.size > 5) {
                TextButton(
                    onClick = hapticClick { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (expanded) "Tampilkan lebih sedikit" else "Tampilkan semua (${all.size})")
                }
            }
        }
    }
}

@Composable
private fun RankRow(
    position: Int,
    r: ListRank,
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$position",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (!r.image.isNullOrBlank()) {
            AsyncImage(
                model = r.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(r.name ?: "?", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${r.count} judul · ${r.minutes / 60} jam · skor ${fmt(r.meanScore)}",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
