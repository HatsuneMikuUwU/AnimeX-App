package com.uwu.animex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Tinggi area yang ditutupi floating bar (bar + margin + inset nav). Konten scroll menambahkannya di padding bawah. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Floating pill bottom bar (port dari FloatingBottomBar InstallerX-Revived).
 * Indikator bisa digeser (drag) atau di-tap, posisinya pakai spring.
 */
@Composable
fun <T> FloatingBottomBar(
    items: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (item: T, index: Int) -> Unit,
    labelContent: @Composable (item: T, index: Int) -> Unit,
) {
    val count = items.size
    if (count == 0) return

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dir = if (isLtr) 1 else -1
    val scope = rememberCoroutineScope()
    val onSelectedNow by rememberUpdatedState(onSelected)
    val selectedNow by rememberUpdatedState(selectedIndex)

    val position = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val indicatorSpring = remember { spring<Float>(dampingRatio = 0.7f, stiffness = 380f) }

    LaunchedEffect(selectedIndex) {
        if (!dragging) position.animateTo(selectedIndex.toFloat(), indicatorSpring)
    }

    val pressScale by animateFloatAsState(
        targetValue = if (dragging) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "floatingBarIndicatorScale",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val tabWidth = min(80.dp, (maxWidth - 48.dp) / count)
        val tabWidthPx = with(density) { tabWidth.toPx() }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
        ) {
            Box(Modifier.padding(4.dp)) {
                // Indikator
                Box(
                    Modifier
                        .offset { IntOffset((position.value * tabWidthPx * dir).roundToInt(), 0) }
                        .size(width = tabWidth, height = 56.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                )

                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .pointerInput(count, tabWidthPx, isLtr) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    val target = position.value.roundToInt().coerceIn(0, count - 1)
                                    scope.launch { position.animateTo(target.toFloat(), indicatorSpring) }
                                    if (target != selectedNow) onSelectedNow(target)
                                },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch { position.animateTo(selectedNow.toFloat(), indicatorSpring) }
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                if (tabWidthPx > 0f) {
                                    scope.launch {
                                        position.snapTo(
                                            (position.value + dragAmount / tabWidthPx * dir)
                                                .coerceIn(0f, (count - 1).toFloat()),
                                        )
                                    }
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val active = if (dragging) position.value.roundToInt().coerceIn(0, count - 1) else selectedIndex
                    items.forEachIndexed { index, item ->
                        val color by animateColorAsState(
                            targetValue = if (index == active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            label = "floatingBarTabColor",
                        )
                        Column(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .selectable(
                                    selected = index == selectedIndex,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { if (index != selectedIndex) onSelected(index) },
                                ),
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides color) {
                                iconContent(item, index)
                                labelContent(item, index)
                            }
                        }
                    }
                }
            }
        }
    }
}
