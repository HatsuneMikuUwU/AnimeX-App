package com.uwu.animex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** InstallerX-style segmented list radii. */
val ThemeCornerRadius = 16.dp
val ThemeConnectionRadius = 5.dp
val ThemeSegmentGap = 2.dp

val LocalSegmentedItemShape = compositionLocalOf<Shape> {
    RoundedCornerShape(ThemeCornerRadius)
}

private fun segmentShape(index: Int, count: Int): Shape {
    if (count <= 1) return RoundedCornerShape(ThemeCornerRadius)
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = ThemeCornerRadius,
            topEnd = ThemeCornerRadius,
            bottomStart = ThemeConnectionRadius,
            bottomEnd = ThemeConnectionRadius,
        )
        count - 1 -> RoundedCornerShape(
            topStart = ThemeConnectionRadius,
            topEnd = ThemeConnectionRadius,
            bottomStart = ThemeCornerRadius,
            bottomEnd = ThemeCornerRadius,
        )
        else -> RoundedCornerShape(ThemeConnectionRadius)
    }
}

/**
 * Simplified SegmentedColumn — visual match to InstallerX: title in primary,
 * stacked surfaceBright items with connected corner radii and small gaps.
 */
@Composable
fun SegmentedColumn(
    title: String = "",
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: SegmentedColumnScope.() -> Unit,
) {
    val scope = SegmentedColumnScope().apply(content)
    val items = scope.items.filter { it.visible }
    if (items.isEmpty()) return

    Column(modifier = modifier.padding(contentPadding)) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp),
            )
        }
        items.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.size(ThemeSegmentGap))
            val shape = segmentShape(index, items.size)
            CompositionLocalProvider(LocalSegmentedItemShape provides shape) {
                item.content(shape)
            }
        }
    }
}

class SegmentedColumnScope {
    internal val items = mutableListOf<SegmentedItem>()

    fun item(
        visible: Boolean = true,
        content: @Composable (Shape) -> Unit,
    ) {
        items.add(SegmentedItem(visible, content))
    }
}

internal data class SegmentedItem(
    val visible: Boolean,
    val content: @Composable (Shape) -> Unit,
)

@Composable
fun BaseItemContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = LocalSegmentedItemShape.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = shape,
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * InstallerX-style BaseWidget: ListItem on surfaceBright with segmented shape,
 * optional icon, title, description, trailing slot, rounded ripple.
 */
@Composable
fun BaseWidget(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = false,
    description: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable BoxScope.(MutableInteractionSource) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.38f
    val shape = LocalSegmentedItemShape.current

    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }
    val baseContentColor = if (selected) {
        MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primaryContainer)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (selected) baseContentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val descColor = baseContentColor.copy(alpha = 0.7f)

    val colors = ListItemDefaults.colors(
        containerColor = backgroundColor,
        contentColor = baseContentColor,
        leadingContentColor = iconColor,
        trailingContentColor = iconColor,
        supportingContentColor = descColor,
        selectedContainerColor = backgroundColor,
        selectedContentColor = baseContentColor,
        selectedLeadingContentColor = iconColor,
        selectedTrailingContentColor = iconColor,
        selectedSupportingContentColor = descColor,
        disabledContainerColor = backgroundColor,
        disabledContentColor = baseContentColor,
        disabledLeadingContentColor = iconColor,
        disabledTrailingContentColor = iconColor,
        disabledSupportingContentColor = descColor,
    )

    val shapes = ListItemDefaults.shapes(
        shape = shape,
        pressedShape = RoundedCornerShape(ThemeCornerRadius),
        selectedShape = shape,
    )

    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(alpha))
        },
        supportingContent = description?.let {
            {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(alpha))
            }
        },
        leadingContent = when {
            icon != null -> {
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).alpha(alpha),
                    )
                }
            }
            iconPlaceholder -> {
                { Spacer(Modifier.size(24.dp)) }
            }
            else -> null
        },
        trailingContent = {
            Box(Modifier.alpha(alpha)) {
                trailingContent(interaction)
            }
        },
        colors = colors,
        shapes = shapes,
        selected = selected,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (description == null) 56.dp else 72.dp)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        enabled = enabled,
                        onClick = onClick,
                    )
                } else Modifier,
            ),
    )
}

@Composable
fun SwitchWidget(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = false,
    description: String? = null,
    enabled: Boolean = true,
) {
    BaseWidget(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        },
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        enabled = enabled,
        onClick = { if (enabled) onCheckedChange(!checked) },
    ) { interactionSource ->
        Switch(
            modifier = Modifier.clearAndSetSemantics {},
            enabled = enabled,
            checked = checked,
            interactionSource = interactionSource,
            colors = SwitchDefaults.colors(
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
            onCheckedChange = null,
        )
    }
}

@Composable
fun RadioButtonWidget(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = false,
    enabled: Boolean = true,
) {
    BaseWidget(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
        },
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
    ) { interactionSource ->
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics {},
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
            interactionSource = interactionSource,
        )
    }
}
