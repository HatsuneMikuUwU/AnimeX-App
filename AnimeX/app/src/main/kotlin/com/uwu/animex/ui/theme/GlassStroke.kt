package com.uwu.animex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

fun Modifier.glassStroke(
    topColor: Color,
    bottomColor: Color,
    cornerRadius: Dp = Dp.Infinity,
    strokeWidth: Dp = 1.dp,
): Modifier =
    drawWithContent {
        drawContent()

        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@drawWithContent

        val strokePx = strokeWidth.toPx().coerceAtLeast(0f)
        if (strokePx <= 0f) return@drawWithContent

        val maxRadius = min(width, height) / 2f
        val radius = if (cornerRadius == Dp.Infinity) maxRadius else min(cornerRadius.toPx(), maxRadius)

        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, width, height, CornerRadius(radius))) }
        clipPath(outline) {
            if (topColor.alpha > 0f) drawEdgeStroke(topColor, radius, strokePx, isTop = true)
            if (bottomColor.alpha > 0f) drawEdgeStroke(bottomColor, radius, strokePx, isTop = false)
        }
    }

private fun DrawScope.drawEdgeStroke(
    color: Color,
    radius: Float,
    strokePx: Float,
    isTop: Boolean,
) {
    val half = strokePx / 2f
    val clipTop = if (isTop) 0f else max(size.height - radius * 2f, 0f)
    val clipBottom = if (isTop) min(radius * 2f, size.height) else size.height
    val offsetY = if (isTop) half else -half

    clipRect(
        left = -half,
        top = clipTop,
        right = size.width + half,
        bottom = clipBottom,
    ) {
        drawRoundRect(
            color = color,
            topLeft = Offset(-half, offsetY),
            size = Size(size.width + strokePx, size.height),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = strokePx),
        )
    }
}

@Composable
fun Modifier.glassStroke(
    enabled: Boolean = true,
    cornerRadius: Dp = Dp.Infinity,
    strokeWidth: Dp = 1.dp,
): Modifier {
    if (!enabled) return this
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (isDark) Color.White.copy(alpha = 0x28 / 255f) else Color.White
    val bottom = if (isDark) Color.White.copy(alpha = 0x14 / 255f) else Color.White
    return glassStroke(top, bottom, cornerRadius, strokeWidth)
}
