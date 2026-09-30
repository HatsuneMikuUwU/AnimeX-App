package com.uwu.animex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating pill-style bottom navigation bar, inspired by InstallerX-Revived.
 * Sits above the system nav bar with rounded capsule, elevation, and a sliding indicator.
 */
@Composable
fun FloatingBottomBar(
    selectedIndex: Int,
    items: List<FloatingNavItem>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    indicatorColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    barHeight: Dp = 64.dp,
    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = containerColor,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
                val tabCount = items.size.coerceAtLeast(1)
                val tabWidth = maxWidth / tabCount

                val indicatorOffset = remember { Animatable(selectedIndex.toFloat()) }
                LaunchedEffect(selectedIndex) {
                    indicatorOffset.animateTo(
                        targetValue = selectedIndex.toFloat(),
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    )
                }

                // Sliding indicator behind selected tab
                Box(
                    Modifier
                        .offset(x = tabWidth * indicatorOffset.value)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(indicatorColor),
                )

                Row(
                    Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        FloatingBottomBarItem(
                            item = item,
                            selected = selectedIndex == index,
                            contentColor = contentColor,
                            activeContentColor = activeContentColor,
                            onClick = { onSelect(index) },
                        )
                    }
                }
            }
        }
    }
}

data class FloatingNavItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun RowScope.FloatingBottomBarItem(
    item: FloatingNavItem,
    selected: Boolean,
    contentColor: Color,
    activeContentColor: Color,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) activeContentColor else contentColor,
        animationSpec = tween(220),
        label = "tabTint",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
