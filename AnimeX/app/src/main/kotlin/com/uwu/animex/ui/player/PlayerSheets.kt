@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.util.WindowBlurEffect

internal data class PlayerSheetOption(
    val icon: ImageVector,
    val label: String,
)

@Composable
internal fun PlayerOptionSheet(
    options: List<PlayerSheetOption>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
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
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            options.forEachIndexed { index, option ->
                SheetOptionItem(
                    index = index,
                    count = options.size,
                    icon = option.icon,
                    label = option.label,
                    selected = index == selected,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
internal fun SpeedSheet(
    speed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    val speeds = speedChoices()
    PlayerOptionSheet(
        options = speeds.map { PlayerSheetOption(Icons.Outlined.Speed, speedLabel(it)) },
        selected = speeds.indexOf(speed),
        onDismiss = onDismiss,
    ) { index -> onSelect(speeds[index]) }
}

@Composable
internal fun SleepTimerSheet(
    deadlineMs: Long,
    nowMs: Long,
    afterEpisode: Boolean,
    onDismiss: () -> Unit,
    onSelect: (SleepSelection) -> Unit,
) {
    PlayerOptionSheet(
        options =
            SLEEP_OPTIONS.mapIndexed { index, label ->
                val icon =
                    when (index) {
                        0 -> Icons.Outlined.TimerOff
                        SLEEP_OPTIONS.lastIndex -> Icons.Outlined.Bedtime
                        else -> Icons.Outlined.Timer
                    }
                PlayerSheetOption(icon, label)
            },
        selected = sleepSelectedIndex(deadlineMs, nowMs, afterEpisode),
        onDismiss = onDismiss,
    ) { index -> onSelect(sleepSelection(index)) }
}

@Composable
internal fun QualitySheet(
    servers: List<Server>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    PlayerOptionSheet(
        options =
            servers.map { server ->
                PlayerSheetOption(
                    if (server.isDirect) Icons.Outlined.HighQuality else Icons.Outlined.Public,
                    server.label(),
                )
            },
        selected = selected,
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}
