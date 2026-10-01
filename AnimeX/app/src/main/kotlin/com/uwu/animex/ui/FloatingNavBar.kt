package com.uwu.animex.ui

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class FloatingNavItem(val label: String, val icon: ImageVector)

/**
 * Height the floating nav bar (plus system nav bar) occupies at the bottom of the screen.
 * Scaffold content is laid out behind the bar, so scrollables add this to their bottom
 * contentPadding to let the last item scroll clear of it. 0.dp when no bar is shown.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Height the floating search bar (plus status bar) occupies at the top of the screen.
 * Same idea as [LocalBottomBarInset]: content draws behind the bar and scrollables add this
 * to their top contentPadding. 0.dp when no floating bar is shown.
 */
val LocalTopBarInset = compositionLocalOf { 0.dp }

/** Scaffold padding without the bottom part, so content can draw behind the floating bar. */
@Composable
fun PaddingValues.withoutBottom(): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues.Absolute(
        left = calculateLeftPadding(dir),
        top = calculateTopPadding(),
        right = calculateRightPadding(dir),
    )
}

private val TabWidth = 76.dp
private val TabHeight = 56.dp
private val BarPadding = 4.dp

/** Slot for a Scaffold's bottomBar: centers the floating pill and keeps it clear of the system nav bar. */
@Composable
fun FloatingNavBarHost(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 4.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        FloatingNavBar(items = items, selectedIndex = selectedIndex, onSelected = onSelected)
    }
}

/**
 * Pill-shaped bottom navigation that floats above the content.
 * Tap a tab or drag the indicator horizontally to switch.
 */
@Composable
fun FloatingNavBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = (items.size - 1).coerceAtLeast(0)
    val tabWidthPx = with(LocalDensity.current) { TabWidth.toPx() }
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val settle = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(selectedIndex) {
        if (!dragging) position.animateTo(selectedIndex.toFloat(), settle)
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Box(
            Modifier
                .padding(BarPadding)
                .pointerInput(items.size, tabWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val target = position.value.roundToInt().coerceIn(0, last)
                            if (target != currentSelected) currentOnSelected(target)
                            scope.launch { position.animateTo(target.toFloat(), settle) }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { position.animateTo(currentSelected.toFloat(), settle) }
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch {
                                position.snapTo((position.value + dx / tabWidthPx).coerceIn(0f, last.toFloat()))
                            }
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .offset { IntOffset((position.value * tabWidthPx).roundToInt(), 0) }
                    .size(TabWidth, TabHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
            Row {
                items.forEachIndexed { i, item ->
                    FloatingNavTab(item = item, selected = i == selectedIndex, onClick = { onSelected(i) })
                }
            }
        }
    }
}

@Composable
private fun FloatingNavTab(item: FloatingNavItem, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "floatingNavTab",
    )
    Column(
        modifier = Modifier
            .size(TabWidth, TabHeight)
            .clip(CircleShape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = null, tint = color)
        Text(
            text = item.label,
            color = color,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
