package com.uwu.animex.ui.common

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.uwu.animex.ui.liquid.DampedDragAnimation
import com.uwu.animex.ui.liquid.InnerShadow
import com.uwu.animex.ui.liquid.InteractiveHighlight
import com.uwu.animex.ui.liquid.IosIndicatorSpecular
import com.uwu.animex.ui.liquid.innerShadow
import com.uwu.animex.ui.liquid.lens
import com.uwu.animex.ui.liquid.rememberCombinedBackdrop
import com.uwu.animex.ui.liquid.rememberGravityRotatedHighlight
import com.uwu.animex.ui.liquid.vibrancy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.sign

private val LocalTabScale = staticCompositionLocalOf { { 1f } }

private val LocalTabContentColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun <T> LiquidFloatingTabBar(
    items: List<T>,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeContentColor: Color = indicatorColor,
    tabMinWidth: Dp = 76.dp,
    iconContent: @Composable (item: T, index: Int) -> Unit,
    labelContent: @Composable (item: T, index: Int) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = CircleShape
    val glassTint = remember(containerColor) { containerColor.copy(alpha = 0.4f) }

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size
    val maxIndex = (tabsCount - 1).coerceAtLeast(0)

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex().coerceIn(0, maxIndex)) }
    val selectedIndexUpdated by rememberUpdatedState(selectedIndex)
    val onSelectedUpdated by rememberUpdatedState(onSelected)
    val performHaptic = rememberHapticAction()

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx).toInt().fastCoerceIn(0, maxIndex)
    }

    val dampedDragAnimation =
        remember(animationScope, tabsCount, density, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentIndex.toFloat(),
                valueRange = 0f..maxIndex.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                canDrag = { position -> position.x in 0f..totalWidthPx },
                onDragStarted = { position -> updateValue(indexAt(position.x).toFloat()) },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, maxIndex)
                    if (currentIndex != targetIndex) {
                        performHaptic()
                        currentIndex = targetIndex
                        onSelectedUpdated(targetIndex)
                    }
                    updateValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDragCancelled = {
                    updateValue(currentIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    if (tabWidthPx > 0f && dragAmount.x != 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, maxIndex.toFloat()),
                        )
                        animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    }
                },
            )
        }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { selectedIndexUpdated() }.collectLatest { index ->
            val safe = index.coerceIn(0, maxIndex)
            if (currentIndex != safe) {
                currentIndex = safe
                dampedDragAnimation.animateToValue(safe.toFloat())
            }
        }
    }

    val activateTab =
        remember(dampedDragAnimation) {
            { index: Int ->
                if (currentIndex != index) {
                    performHaptic()
                    currentIndex = index
                    onSelectedUpdated(index)
                }
                dampedDragAnimation.animateToValue(index.toFloat())
            }
        }

    val tabsContent: @Composable RowScope.() -> Unit = {
        val scale = LocalTabScale.current
        val color = LocalTabContentColor.current
        items.forEachIndexed { index, item ->
            Column(
                modifier =
                    Modifier
                        .defaultMinSize(minWidth = tabMinWidth)
                        .semantics(mergeDescendants = true) {
                            selected = index == currentIndex
                            role = Role.Tab
                            onClick {
                                activateTab(index)
                                true
                            }
                        }.fillMaxHeight()
                        .weight(1f)
                        .graphicsLayer {
                            val itemScale = scale()
                            scaleX = itemScale
                            scaleY = itemScale
                        },
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

    val interactiveHighlight =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember(animationScope, tabWidthPx) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) {
                                (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                            } else {
                                size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                            },
                            size.height / 2f,
                        )
                    },
                )
            }
        } else {
            null
        }

    val baseHighlight = rememberGravityRotatedHighlight(IosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(IosIndicatorSpecular, extraDegrees = 90f)
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalTabContentColor provides contentColor) {
            Row(
                Modifier
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                        tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                    }.graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow =
                            Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                alpha = if (isDark) 0.2f else 0.1f,
                            ),
                    ).drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            blur(4.dp.toPx(), 4.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                            )
                        },
                        highlight = { baseHighlight.copy(alpha = 0.75f) },
                        layerBlock = {
                            val width = size.width.coerceAtLeast(1f)
                            val s = lerp(1f, 1f + 16.dp.toPx() / width, dampedDragAnimation.pressProgress)
                            scaleX = s
                            scaleY = s
                        },
                        onDrawSurface = { drawRect(glassTint) },
                    ).then(
                        if (interactiveHighlight != null) {
                            interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier)
                        } else {
                            Modifier
                        },
                    ).then(dampedDragAnimation.modifier)
                    .height(FloatingTabBarHeight)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent,
            )
        }

        CompositionLocalProvider(
            LocalTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
            LocalTabContentColor provides activeContentColor,
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            vibrancy()
                            blur(4.dp.toPx(), 4.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                            )
                        },
                        onDrawSurface = { drawRect(glassTint) },
                    ).then(interactiveHighlight?.modifier ?: Modifier)
                    .height(FloatingTabBarHeight - 8.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabsContent()
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }.drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { pillShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                refractionHeight = 10.dp.toPx() * progress,
                                refractionAmount = 14.dp.toPx() * progress,
                                depthEffect = true,
                                chromaticAberration = 0.5f,
                            )
                        },
                        highlight = { pillHighlight.copy(alpha = dampedDragAnimation.pressProgress) },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                color = if (!isDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                                alpha = 1f - progress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    ).innerShadow(shape = pillShape) {
                        InnerShadow(
                            radius = 8.dp * dampedDragAnimation.pressProgress,
                            color = Color.Black.copy(alpha = 0.15f),
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    }.height(FloatingTabBarHeight - 8.dp)
                    .width(tabWidthDp),
            )
        }
    }
}
