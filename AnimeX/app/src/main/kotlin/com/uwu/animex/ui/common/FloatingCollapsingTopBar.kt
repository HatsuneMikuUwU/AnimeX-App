package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.glassStroke
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import kotlin.math.roundToInt

private const val MORPH_STEPS = 24
private val CapsuleHeight = 56.dp
private val CapsuleMargin = 12.dp
private val CapsuleTopGap = 6.dp
private val CapsuleBottomGap = 8.dp
private val CapsuleRadius = 28.dp

/**
 * Toolbar besar yang morph jadi kapsul floating saat di-collapse. Expanded: transparan tanpa blur.
 * Collapsed: kapsul ber-blur dengan margin di sekelilingnya, konten tetap tembus di belakangnya.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingCollapsingTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    backdrop: LayerBackdrop?,
    onBack: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expandedHeight: Dp = 160.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val state = scrollBehavior.state
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val expandedTotal = statusTop + expandedHeight
    val collapsedTotal = statusTop + CapsuleTopGap + CapsuleHeight + CapsuleBottomGap

    SideEffect {
        val limit = with(density) { -(expandedTotal - collapsedTotal).toPx() }
        if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
    }

    val step by remember(state) {
        derivedStateOf { (state.collapsedFraction.coerceIn(0f, 1f) * MORPH_STEPS).roundToInt() }
    }
    val p = step / MORPH_STEPS.toFloat()
    val radius = CapsuleRadius * p
    val shape = RoundedCornerShape(radius)
    val container = cs.surfaceContainerHigh
    val titleSize: TextUnit = (32f + (20f - 32f) * p).sp

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(
                Modifier
                    .graphicsLayer { alpha = state.collapsedFraction.coerceIn(0f, 1f) }
                    .blurEffect(backdrop, shape = shape, blendColor = container)
                    .background(backdrop.appBarColor(container), shape)
                    .glassStroke(cornerRadius = radius),
            )
            Box {
                FilledTonalIconButton(
                    onClick = hapticClick(block = onBack),
                    shapes = IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = container.copy(alpha = 1f - p),
                            contentColor = cs.onSurface,
                        ),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                }
            }
            Box {
                CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
                    ProvideTextStyle(
                        MaterialTheme.typography.headlineLarge.copy(
                            fontSize = titleSize,
                            lineHeight = TextUnit.Unspecified,
                        ),
                    ) { title() }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides cs.onSurface) { actions() }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val f = state.collapsedFraction.coerceIn(0f, 1f)

        fun mix(
            a: Int,
            b: Int,
        ): Int = (a + (b - a) * f).roundToInt()

        val top = statusTop.roundToPx()
        val expandedPx = expandedTotal.roundToPx()
        val collapsedPx = collapsedTotal.roundToPx()
        val height = mix(expandedPx, collapsedPx)
        val margin = mix(0, CapsuleMargin.roundToPx())
        val gap = CapsuleTopGap.roundToPx()
        val capsuleH = CapsuleHeight.roundToPx()
        val centerY = top + gap + capsuleH / 2

        val nav = measurables[1].measure(Constraints())
        val act = measurables[3].measure(Constraints(maxWidth = width / 2))

        val navX = mix(12.dp.roundToPx(), (12 + 8).dp.roundToPx())
        val actEnd = mix(12.dp.roundToPx(), (12 + 8).dp.roundToPx())
        val actX = width - actEnd - act.width
        val titleXCollapsed = (12 + 8).dp.roundToPx() + nav.width + 12.dp.roundToPx()
        val titleX = mix(16.dp.roundToPx(), titleXCollapsed)
        val titleMax = mix(width - 32.dp.roundToPx(), actX - 8.dp.roundToPx() - titleXCollapsed)
        val titlePlaceable = measurables[2].measure(Constraints(maxWidth = titleMax.coerceAtLeast(0)))
        val titleCenterY = mix(top + (expandedHeight - 44.dp).roundToPx(), centerY)

        val bgTop = mix(top, top + gap)
        val bgHeight = mix(expandedPx - top, capsuleH)
        val bg = measurables[0].measure(Constraints.fixed((width - 2 * margin).coerceAtLeast(0), bgHeight.coerceAtLeast(0)))

        layout(width, height) {
            bg.place(margin, bgTop)
            nav.place(navX, centerY - nav.height / 2)
            titlePlaceable.place(titleX, titleCenterY - titlePlaceable.height / 2)
            act.place(actX, centerY - act.height / 2)
        }
    }
}
