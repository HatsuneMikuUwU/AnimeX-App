package com.uwu.animex.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Gaya item bottom sheet: tiap item punya latar sendiri, sudut luar bulat di item paling atas dan
// paling bawah, sudut dalam kecil di antaranya. Ikon ditaruh di dalam badge berbentuk.

private val ItemOuter = 19.dp
private val ItemInner = 4.dp

/** Shape item di dalam grup: atas (index 0), tengah, atau bawah (index terakhir). */
fun groupedItemShape(
    index: Int,
    count: Int,
): Shape =
    RoundedCornerShape(
        topStart = if (index == 0) ItemOuter else ItemInner,
        topEnd = if (index == 0) ItemOuter else ItemInner,
        bottomStart = if (index == count - 1) ItemOuter else ItemInner,
        bottomEnd = if (index == count - 1) ItemOuter else ItemInner,
    )

/** Shape untuk item yang berdiri sendiri (semua sudut bulat). */
val SingleItemShape: Shape = RoundedCornerShape(ItemOuter)

enum class BadgeStyle { Default, Selected, Tertiary, Error }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapedIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    style: BadgeStyle = BadgeStyle.Default,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (container, tint) =
        when (style) {
            BadgeStyle.Default -> cs.secondaryContainer to cs.onSecondaryContainer
            BadgeStyle.Selected -> cs.primary to cs.onPrimary
            BadgeStyle.Tertiary -> cs.tertiary to cs.onTertiary
            BadgeStyle.Error -> cs.errorContainer to cs.onErrorContainer
        }
    Box(
        modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize), tint = tint)
    }
}

/** Latar satu item di bottom sheet. Jarak antar item 4dp, margin samping 16dp. */
@Composable
fun SheetItemSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    val outer = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = container,
            modifier = outer,
            content = content,
        )
    } else {
        Surface(shape = shape, color = container, modifier = outer, content = content)
    }
}

/** Satu baris pilihan (ikon berbadge, label, dan isi kanan) untuk sheet berisi daftar pilihan. */
@Composable
fun SheetOptionItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (selected) cs.primaryContainer else cs.surfaceContainerHigh
    val content =
        when {
            selected -> cs.onPrimaryContainer
            highlight -> cs.tertiary
            else -> cs.onSurface
        }
    SheetItemSurface(
        shape = groupedItemShape(index, count),
        modifier = modifier,
        onClick = onClick,
        container = container,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(
                icon = icon,
                style =
                    when {
                        selected -> BadgeStyle.Selected
                        highlight -> BadgeStyle.Tertiary
                        else -> BadgeStyle.Default
                    },
            )
            Text(
                label,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = content,
            )
            if (trailing != null) {
                trailing()
            } else if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = content)
            }
        }
    }
}
