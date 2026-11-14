package com.uwu.animex.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur

enum class FadeEdge { Top, Bottom }

private const val EDGE_BLUR_RADIUS = 24f

private const val EDGE_BLUR_CURVE = 0.6f

private const val EDGE_TINT_ALPHA = 0.45f

@Composable
fun EdgeFade(
    backdrop: LayerBackdrop?,
    edge: FadeEdge,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
) {
    if (backdrop != null && isRuntimeShaderSupported()) {
        val gradient =
            remember(edge) {
                val base = if (edge == FadeEdge.Top) ProgressiveBlur.Top else ProgressiveBlur.Bottom
                base.copy(curve = EDGE_BLUR_CURVE)
            }
        val colors =
            remember(background) {
                BlurColors(blendColors = listOf(BlendColorEntry(color = background.copy(alpha = EDGE_TINT_ALPHA))))
            }
        Box(
            modifier.progressiveTextureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = EDGE_BLUR_RADIUS,
                gradient = gradient,
                colors = colors,
            ),
        )
    } else {
        val brush = remember(background, edge) { if (edge == FadeEdge.Top) topScrim(background) else bottomScrim(background) }
        Box(modifier.background(brush))
    }
}

fun Modifier.overhang(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier =
    layout { measurable, constraints ->
        val t = top.roundToPx()
        val b = bottom.roundToPx()
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placeable = measurable.measure(Constraints.fixed(w, h + t + b))
        layout(w, h) { placeable.place(0, -t) }
    }
