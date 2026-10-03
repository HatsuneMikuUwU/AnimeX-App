package com.uwu.animex.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FloatingTabItem(val label: String, val icon: ImageVector)

@Composable
fun FloatingTabBar(
    items: List<FloatingTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                FloatingTabButton(
                    item = item,
                    selected = i == selectedIndex,
                    onClick = { onSelect(i) },
                )
            }
        }
    }
}

@Composable
private fun FloatingTabButton(
    item: FloatingTabItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f),
        label = "tabContainer",
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        label = "tabContent",
    )
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .background(container)
            .padding(horizontal = if (selected) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            item.icon,
            contentDescription = if (selected) null else item.label,
            tint = content,
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(
                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
            ) + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(
                    item.label,
                    color = content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
