@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.ui.common.BadgeStyle
import com.uwu.animex.ui.common.ShapedIconBadge
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.SingleItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.toggleHaptic
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal val STATUS_ORDER: List<Pair<WatchStatus?, androidx.compose.ui.graphics.vector.ImageVector>> =
    listOf(
        null to Icons.Outlined.RemoveCircleOutline,
        WatchStatus.WATCHING to Icons.Outlined.PlayCircleOutline,
        WatchStatus.PLAN_TO_WATCH to Icons.Outlined.Schedule,
        WatchStatus.COMPLETED to Icons.Outlined.CheckCircleOutline,
        WatchStatus.ON_HOLD to Icons.Outlined.PauseCircleOutline,
        WatchStatus.DROPPED to Icons.Outlined.DeleteOutline,
    )

internal val SCORE_LABELS =
    listOf(
        "—",
        "Appalling",
        "Horrible",
        "Very Bad",
        "Bad",
        "Average",
        "Fine",
        "Good",
        "Very Good",
        "Great",
        "Masterpiece",
    )

internal val PRIORITY_LABELS = listOf("Low", "Medium", "High")

private fun dateFmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

internal fun millisOf(date: String?): Long? = date?.let { runCatching { dateFmt().parse(it)?.time }.getOrNull() }

internal fun dateOf(millis: Long): String = dateFmt().format(Date(millis))

internal fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

internal fun displayDate(date: String): String {
    val ms = millisOf(date) ?: return date
    val fmt =
        DateFormat
            .getDateInstance(
                DateFormat.MEDIUM,
                Locale.getDefault(),
            ).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return fmt.format(Date(ms))
}

@Composable
internal fun Notice(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun ProgressRow(
    icon: ImageVector,
    value: Int,
    total: Int?,
    label: String,
    max: Int?,
    onValueChange: (Int) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SingleItemShape,
) {
    SheetItemSurface(shape = shape, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ShapedIconBadge(icon, contentDescription = label)
                Spacer(Modifier.width(16.dp))
                BasicTextField(
                    value = value.toString(),
                    onValueChange = { onValueChange(it.filter(Char::isDigit).toIntOrNull() ?: 0) },
                    modifier = Modifier.width(IntrinsicSize.Min),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                if (total != null) {
                    Text(
                        "/$total",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        modifier = Modifier.padding(start = if (total == null) 8.dp else 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalIconButton(
                onClick = hapticClick {
                    onMinus()
                },
                enabled = value > 0,
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(Icons.Outlined.Remove, contentDescription = "Kurangin") }
            FilledTonalIconButton(
                onClick = hapticClick {
                    onPlus()
                },
                enabled = max == null || value < max,
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(Icons.Outlined.Add, contentDescription = "Tambahin") }
        }
    }
}

@Composable
internal fun ValueRow(
    icon: ImageVector,
    label: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SingleItemShape,
) {
    SheetItemSurface(shape = shape, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ShapedIconBadge(icon, contentDescription = label)
                Text(
                    label,
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = hapticClick {
                    onMinus()
                },
                enabled = minusEnabled,
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(Icons.Outlined.Remove, contentDescription = "Kurangin") }
            FilledTonalIconButton(
                onClick = hapticClick {
                    onPlus()
                },
                enabled = plusEnabled,
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(Icons.Outlined.Add, contentDescription = "Tambahin") }
        }
    }
}

@Composable
internal fun DateField(
    icon: ImageVector,
    label: String,
    date: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    shape: Shape = SingleItemShape,
) {
    SheetItemSurface(shape = shape, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ShapedIconBadge(icon, contentDescription = label)
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(label, color = MaterialTheme.colorScheme.onSurface)
                    if (date != null) {
                        Text(
                            displayDate(date),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (date != null) {
                IconButton(onClick = hapticClick(block = onClear), shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Close, contentDescription = "Hapus tanggal")
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shape: Shape = SingleItemShape,
) {
    SheetItemSurface(
        shape = shape,
        onClick = { onChange(!checked) },
        hapticType = toggleHaptic(checked),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ShapedIconBadge(icon)
                Text(
                    title,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
internal fun TextRow(
    icon: ImageVector,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    shape: Shape = SingleItemShape,
) {
    SheetItemSurface(shape = shape) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(icon, contentDescription = placeholder)
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(placeholder) },
                singleLine = false,
                modifier = Modifier.weight(1f),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }
    }
}

@Composable
internal fun DeleteRow(
    enabled: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tint = cs.error.copy(alpha = if (enabled) 1f else 0.38f)
    SheetItemSurface(shape = shape, onClick = onClick, enabled = enabled) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(Icons.Outlined.DeleteOutline, style = BadgeStyle.Error)
            Text(
                "Hapus",
                Modifier.padding(horizontal = 16.dp),
                color = tint,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

internal val SheetGroupGap = 12.dp
