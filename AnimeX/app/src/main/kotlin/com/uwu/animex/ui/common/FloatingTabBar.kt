package com.uwu.animex.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.glassStroke
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.LayerBackdrop

val FloatingTabBarHeight = 64.dp
val FloatingTabBarMargin = 12.dp

@Composable
fun floatingTabBarSpace(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingTabBarMargin + FloatingTabBarHeight

@Composable
fun FloatingTabBarFade(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val brush =
        remember(bg) {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.4f to bg.copy(alpha = 0.85f),
                1f to bg,
            )
        }
    Box(
        modifier
            .fillMaxWidth()
            .height(floatingTabBarSpace() + 24.dp)
            .background(brush),
    )
}

@Composable
fun <T> BoxScope.FloatingTabBarOverlay(
    items: List<T>,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: LayerBackdrop?,
    label: (T) -> String,
    icon: @Composable (item: T, index: Int) -> Unit,
) {
    FloatingTabBarFade(Modifier.align(Alignment.BottomCenter))
    FloatingTabBar(
        items = items,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        backdrop = backdrop,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FloatingTabBarMargin)
                .navigationBarsPadding(),
        iconContent = icon,
        labelContent = { item, _ ->
            Text(
                text = label(item),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        },
    )
}

@Composable
fun <T> FloatingTabBar(
    items: List<T>,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeContentColor: Color = indicatorColor,
    iconContent: @Composable (item: T, index: Int) -> Unit,
    labelContent: @Composable (item: T, index: Int) -> Unit,
) {
    val pillShape = CircleShape
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val tabsCount = items.size

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex().coerceIn(0, (tabsCount - 1).coerceAtLeast(0))) }
    val selectedIndexUpdated by rememberUpdatedState(selectedIndex)
    val onSelectedUpdated by rememberUpdatedState(onSelected)

    val valueAnimation = remember { Animatable(currentIndex.toFloat()) }
    val valueSpec = remember { spring<Float>(1f, 1000f, 0.001f) }
    val settleSpec = remember { spring<Float>(1f, 300f, 0.5f) }

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
    }

    fun settleOffset() {
        scope.launch { offsetAnimation.animateTo(0f, settleSpec) }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { selectedIndexUpdated() }.collectLatest { index ->
            val safe = index.coerceIn(0, (tabsCount - 1).coerceAtLeast(0))
            if (currentIndex != safe) {
                currentIndex = safe
                valueAnimation.animateTo(safe.toFloat(), valueSpec)
            }
        }
    }

    fun activateTab(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onSelectedUpdated(index)
        }
        scope.launch { valueAnimation.animateTo(index.toFloat(), valueSpec) }
    }

    val dragModifier =
        Modifier.pointerInput(tabsCount, isLtr) {
            inspectDragGestures(
                onDragStart = { down ->
                    scope.launch { valueAnimation.animateTo(indexAt(down.position.x).toFloat(), valueSpec) }
                },
                onDragEnd = {
                    val target = valueAnimation.targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                    if (currentIndex != target) {
                        currentIndex = target
                        onSelectedUpdated(target)
                    }
                    scope.launch { valueAnimation.animateTo(target.toFloat(), valueSpec) }
                    settleOffset()
                },
                onDragCancel = {
                    scope.launch { valueAnimation.animateTo(currentIndex.toFloat(), valueSpec) }
                    settleOffset()
                },
            ) { change, dragAmount ->
                val inside = change.position.x in 0f..totalWidthPx && change.previousPosition.x in 0f..totalWidthPx
                if (inside && tabWidthPx > 0f && dragAmount.x != 0f) {
                    val next =
                        (valueAnimation.targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat())
                    scope.launch { valueAnimation.animateTo(next, valueSpec) }
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            }
        }

    val tabsContent: @Composable RowScope.(Color) -> Unit = { color ->
        CompositionLocalProvider(LocalContentColor provides color) {
            items.forEachIndexed { index, item ->
                Column(
                    modifier =
                        Modifier
                            .defaultMinSize(minWidth = 76.dp)
                            .semantics(mergeDescendants = true) {
                                selected = index == currentIndex
                                role = Role.Tab
                                onClick {
                                    activateTab(index)
                                    true
                                }
                            }.fillMaxHeight()
                            .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    iconContent(item, index)
                    labelContent(item, index)
                }
            }
        }
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }.graphicsLayer { translationX = panelOffset }
                .blurEffect(backdrop, shape = pillShape, blendColor = containerColor)
                .background(backdrop.appBarColor(containerColor), pillShape)
                .glassStroke()
                .then(dragModifier)
                .height(FloatingTabBarHeight)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabsContent(contentColor)
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = valueAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }.clip(pillShape)
                        .background(indicatorColor.copy(alpha = 0.15f), pillShape)
                        .height(FloatingTabBarHeight - 8.dp)
                        .width(tabWidthDp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                        .height(FloatingTabBarHeight - 8.dp)
                        .graphicsLayer {
                            val progressOffset = valueAnimation.value * tabWidthPx
                            translationX = if (isLtr) -progressOffset else progressOffset
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabsContent(activeContentColor)
                }
            }
        }
    }
}

private suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)

        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent =
            drag(
                pointerId = initialDown.id,
                onDrag = { onDrag(it, it.positionChange()) },
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) return dragEvent
        }
    }
}
