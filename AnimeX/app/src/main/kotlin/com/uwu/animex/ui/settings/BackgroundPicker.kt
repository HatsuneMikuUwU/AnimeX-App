package com.uwu.animex.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Grid3x3
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.ui.background.AnimatedBackground
import com.uwu.animex.ui.background.BackgroundType
import com.uwu.animex.ui.background.RandomInterval
import com.uwu.animex.ui.background.SeasonalEvent
import com.uwu.animex.ui.background.rememberActiveBackground
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.UiScaled

private const val TILE_COLUMNS = 3

@Composable
internal fun BackgroundPickerDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val shown = rememberActiveBackground()
    val event = remember { SeasonalEvent.on() }
    val seasonalOverride =
        event != null && settings.seasonalThemes && settings.backgroundType != BackgroundType.NONE
    val types = remember { BackgroundType.entries.filter { it !in BackgroundType.SEASONAL } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(cs.background)) {
            AnimatedBackground(type = shown, enableParallax = settings.backgroundParallax)
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            UiScaled {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .fillMaxHeight(0.62f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = cs.surfaceContainer,
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Latar animasi",
                            style = MaterialTheme.typography.titleLarge,
                            color = cs.onSurface,
                        )
                        Text(
                            "Pilihanmu langsung diterapkan dan kelihatan di belakang",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                        if (seasonalOverride && event != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cs.primaryContainer)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Celebration, contentDescription = null, tint = cs.onPrimaryContainer)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Lagi musim ${event.label}, latar khusus tampil sampai ${event.endLabel}. " +
                                        "Matikan Tema musiman kalau mau latar pilihanmu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onPrimaryContainer,
                                )
                            }
                        }
                        TileGrid(items = types) { type, tileModifier ->
                            BackgroundTile(
                                selected = settings.backgroundType == type,
                                icon = backgroundIcon(type),
                                label = type.label,
                                onClick = { Appearance.setBackgroundType(type) },
                                modifier = tileModifier,
                            )
                        }
                        if (settings.backgroundType == BackgroundType.RANDOM) {
                            Text(
                                "Ganti latar acak",
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface,
                            )
                            TileGrid(items = RandomInterval.entries) { interval, tileModifier ->
                                BackgroundTile(
                                    selected = settings.randomInterval == interval,
                                    icon = intervalIcon(interval),
                                    label = interval.label,
                                    onClick = { Appearance.setRandomInterval(interval) },
                                    modifier = tileModifier,
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            DialogConfirmButton("Selesai", onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> TileGrid(
    items: List<T>,
    tile: @Composable (T, Modifier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(TILE_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile(it, Modifier.weight(1f)) }
                repeat(TILE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BackgroundTile(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        label = "background-tile-container",
    )
    val content = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun backgroundIcon(type: BackgroundType): ImageVector =
    when (type) {
        BackgroundType.NONE -> Icons.Outlined.VisibilityOff
        BackgroundType.CIRCLES -> Icons.Outlined.Circle
        BackgroundType.RINGS -> Icons.Outlined.RadioButtonUnchecked
        BackgroundType.MESH -> Icons.Outlined.Grid3x3
        BackgroundType.SPACE -> Icons.Outlined.AutoAwesome
        BackgroundType.SHAPES -> Icons.Outlined.Pentagon
        BackgroundType.SNOW -> Icons.Outlined.AcUnit
        BackgroundType.GRID -> Icons.Outlined.Apps
        BackgroundType.PARTICLES -> Icons.Outlined.BubbleChart
        BackgroundType.LAVA -> Icons.Outlined.WaterDrop
        BackgroundType.HALLOWEEN -> Icons.Outlined.NightsStay
        BackgroundType.FIREWORKS -> Icons.Outlined.Flare
        BackgroundType.RANDOM -> Icons.Outlined.Shuffle
    }

private fun intervalIcon(interval: RandomInterval): ImageVector =
    when (interval) {
        RandomInterval.ON_LAUNCH -> Icons.Outlined.PlayCircleOutline
        RandomInterval.DAILY -> Icons.Outlined.Today
        RandomInterval.EVERY_3_DAYS -> Icons.Outlined.DateRange
    }
