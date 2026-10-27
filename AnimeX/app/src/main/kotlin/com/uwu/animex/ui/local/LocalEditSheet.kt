@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberHapticAction
import com.uwu.animex.ui.tracker.STATUS_ORDER
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.launch

@Composable
fun LocalEditSheet(
    movie: Movie,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(Bookmarks.status(movie.id)) }

    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
    ) {
        WindowBlurEffect()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = hapticClick(block = onDismiss),
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Gak usah deh") }
                Button(
                    onClick =
                        hapticClick {
                            Bookmarks.setStatus(movie, status)
                            onDismiss()
                        },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("Tambahin")
                }
            }

            val doHaptic = rememberHapticAction(HapticFeedbackType.ToggleOn)
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                STATUS_ORDER.forEach { (option, icon) ->
                    val label = option?.label ?: "Kosong"
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                positioning = TooltipAnchorPosition.Above,
                            ),
                        tooltip = { PlainTooltip { Text(label) } },
                        focusable = false,
                        state = tooltipState,
                    ) {
                        FilledIconToggleButton(
                            checked = status == option,
                            onCheckedChange = {
                                doHaptic()
                                scope.launch { tooltipState.show() }
                                status = option
                            },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors =
                                IconButtonDefaults.filledIconToggleButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) { Icon(icon, contentDescription = label) }
                    }
                }
            }
        }
    }
}
