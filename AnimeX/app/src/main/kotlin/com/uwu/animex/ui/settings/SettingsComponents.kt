@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.toggleHaptic

private val ItemOuter = 19.dp

private val ItemInner = 4.dp

private val ItemGap = 4.dp

internal val GroupCorner = 28.dp

internal fun itemShape(
    index: Int,
    count: Int,
): Shape =
    RoundedCornerShape(
        topStart = if (index == 0) ItemOuter else ItemInner,
        topEnd = if (index == 0) ItemOuter else ItemInner,
        bottomStart = if (index == count - 1) ItemOuter else ItemInner,
        bottomEnd = if (index == count - 1) ItemOuter else ItemInner,
    )

@Composable
internal fun <T> OptionDialog(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialIndex = rememberSaveable { options.indexOf(selected) }
    val initial = options.getOrNull(initialIndex) ?: selected
    var confirmed by remember { mutableStateOf(false) }
    val cancel = {
        if (!confirmed) onSelect(initial)
        onDismiss()
    }
    AppDialog(
        icon = icon,
        title = title,
        onDismiss = cancel,
        confirmButton = {
            DialogConfirmButton("OK") {
                confirmed = true
                onDismiss()
            }
        },
        dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = cancel) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { option ->
                    DialogOptionRow(
                        label = label(option),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
    )
}

@Composable
internal fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    PrefItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        shape = shape,
        container = itemContainer(),
        content = cs.onSurface,
        enabled = enabled,
        onClick = { onChange(!checked) },
        hapticType = toggleHaptic(checked),
        badge = cs.primary,
        badgeTint = cs.onPrimary,
        end = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

@Composable
internal fun itemContainer(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
internal fun SettingGroup(
    icon: ImageVector,
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "group-chevron")
    val toggle = hapticClick { expanded = !expanded }
    val groupColor = cs.surfaceContainer

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupCorner))
            .background(groupColor),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GroupCorner))
                .clickable(onClick = toggle)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, container = cs.primary, tint = cs.onPrimary)
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
            )
            IconButton(onClick = toggle, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Ciutkan" else "Lebarkan",
                    modifier = Modifier.rotate(rotation),
                    tint = cs.onSurface,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = AppMotion.expandEnter(),
            exit = AppMotion.expandExit(),
        ) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ItemGap),
                content = content,
            )
        }
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    container: Color,
    tint: Color,
) {
    Box(
        Modifier.size(40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
    }
}

@Composable
internal fun PrefItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    hapticType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    badge: Color = content,
    badgeTint: Color = container,
    end: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.5f, label = "item-alpha")
    val effectiveClick: (() -> Unit)? =
        when {
            enabled -> onClick
            else -> onDisabledClick
        }
    val body: @Composable () -> Unit = {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = icon, container = badge, tint = badgeTint)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.8f),
                        )
                    }
                }
                end?.invoke()
            }
            bottom?.invoke(this)
        }
    }
    if (effectiveClick != null) {
        val onHapticClick = hapticClick(type = hapticType, block = effectiveClick)
        Surface(
            onClick = onHapticClick,
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().alpha(alpha),
        ) { body() }
    } else {
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().alpha(alpha),
        ) { body() }
    }
}
