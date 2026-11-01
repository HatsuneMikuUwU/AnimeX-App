@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.anilist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.common.ShapedIconBadge
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.SingleItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.rememberHapticAction
import com.uwu.animex.ui.common.toggleHaptic
import com.uwu.animex.ui.tracker.ProgressRow
import com.uwu.animex.ui.tracker.SCORE_LABELS
import kotlinx.coroutines.launch

@Composable
internal fun ScoreSection(
    format: String,
    score: Double,
    shape: Shape,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit,
) {
    when (format) {
        "POINT_100" ->
            ProgressRow(
                icon = Icons.Outlined.Star,
                value = score.toInt(),
                total = 100,
                label = "",
                max = 100,
                modifier = modifier,
                shape = shape,
                onValueChange = { onChange(it.coerceIn(0, 100).toDouble()) },
                onMinus = { onChange((score - 1).coerceAtLeast(0.0)) },
                onPlus = { onChange((score + 1).coerceAtMost(100.0)) },
            )
        "POINT_10_DECIMAL" ->
            DecimalScoreRow(
                icon = Icons.Outlined.Star,
                value = score,
                label = "",
                modifier = modifier,
                shape = shape,
                onChange = onChange,
            )
        "POINT_5" ->
            SheetItemSurface(shape = shape, modifier = modifier) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val current = score.toInt()
                    for (star in 1..5) {
                        IconButton(
                            onClick = hapticClick { onChange(if (current == star) 0.0 else star.toDouble()) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                if (star <= current) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$star",
                                tint =
                                    if (star <= current) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
            }
        "POINT_3" ->
            SheetItemSurface(shape = shape, modifier = modifier) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SMILEYS.forEach { (value, icon, label) ->
                        ToggleIcon(icon = icon, label = label, checked = score.toInt() == value) {
                            onChange(if (score.toInt() == value) 0.0 else value.toDouble())
                        }
                    }
                }
            }
        else ->
            ProgressRow(
                icon = Icons.Outlined.Star,
                value = score.toInt(),
                total = 10,
                label = if (score.toInt() == 0) "" else SCORE_LABELS[score.toInt().coerceIn(0, 10)],
                max = 10,
                modifier = modifier,
                shape = shape,
                onValueChange = { onChange(it.coerceIn(0, 10).toDouble()) },
                onMinus = { onChange((score - 1).coerceAtLeast(0.0)) },
                onPlus = { onChange((score + 1).coerceAtMost(10.0)) },
            )
    }
}

@Composable
internal fun DecimalScoreRow(
    icon: ImageVector,
    value: Double,
    label: String,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SingleItemShape,
) {
    var text by remember { mutableStateOf(scoreText(value)) }
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = scoreText(value) }
    SheetItemSurface(shape = shape, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ShapedIconBadge(icon, contentDescription = label)
                Spacer(Modifier.width(16.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        val clean = raw.filter { it.isDigit() || it == '.' }
                        text = clean
                        val parsed = clean.toDoubleOrNull()
                        if (parsed != null) {
                            onChange(parsed.coerceIn(0.0, 10.0))
                        } else if (clean.isEmpty()) {
                            onChange(0.0)
                        }
                    },
                    modifier = Modifier.widthIn(min = 16.dp).width(IntrinsicSize.Min),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                Text(
                    "/10",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalIconButton(
                onClick = hapticClick {
                    onChange((value - 0.5).coerceAtLeast(0.0))
                },
                enabled = value > 0,
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(Icons.Outlined.Remove, contentDescription = "Kurangin") }
            FilledTonalIconButton(
                onClick = hapticClick {
                    onChange((value + 0.5).coerceAtMost(10.0))
                },
                enabled = value < 10,
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(Icons.Outlined.Add, contentDescription = "Tambahin") }
        }
    }
}

@Composable
internal fun ToggleIcon(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val doHaptic = rememberHapticAction(toggleHaptic(checked))
    val scope = rememberCoroutineScope()
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
            checked = checked,
            onCheckedChange = {
                doHaptic()
                scope.launch { tooltipState.show() }
                onClick()
            },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors =
                IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) { Icon(icon, contentDescription = label) }
    }
}

@Composable
internal fun ActionRow(
    icon: ImageVector,
    title: String,
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SheetItemSurface(shape = shape, onClick = onClick, enabled = enabled) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(icon)
            Text(
                title,
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
