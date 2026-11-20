package com.uwu.animex.ui.player

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

internal data class AmbientColors(
    val top: Color,
    val left: Color,
    val center: Color,
    val right: Color,
) {
    fun blendToward(
        target: AmbientColors,
        fraction: Float,
    ): AmbientColors {
        val f = fraction.coerceIn(0f, 1f)
        return AmbientColors(
            top = lerp(top, target.top, f),
            left = lerp(left, target.left, f),
            center = lerp(center, target.center, f),
            right = lerp(right, target.right, f),
        )
    }

    companion object {
        val Neutral =
            AmbientColors(
                top = Color.Transparent,
                left = Color.Transparent,
                center = Color.Transparent,
                right = Color.Transparent,
            )
    }
}

internal fun sampleAmbientColors(textureView: TextureView): AmbientColors? {
    if (!textureView.isAvailable || textureView.width <= 0 || textureView.height <= 0) return null
    val bmp: Bitmap =
        runCatching { textureView.getBitmap(16, 9) }.getOrNull() ?: return null
    return try {
        val top = averageRegion(bmp, 2, 13, 0, 2)
        val left = averageRegion(bmp, 0, 4, 4, 8)
        val center = averageRegion(bmp, 5, 10, 4, 8)
        val right = averageRegion(bmp, 11, 15, 4, 8)
        AmbientColors(top = top, left = left, center = center, right = right)
    } finally {
        bmp.recycle()
    }
}

private fun averageRegion(
    bmp: Bitmap,
    x0: Int,
    x1: Int,
    y0: Int,
    y1: Int,
): Color {
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0
    val maxX = x1.coerceAtMost(bmp.width - 1)
    val maxY = y1.coerceAtMost(bmp.height - 1)
    for (y in y0..maxY) {
        for (x in x0..maxX) {
            val px = bmp.getPixel(x, y)
            rSum += android.graphics.Color.red(px)
            gSum += android.graphics.Color.green(px)
            bSum += android.graphics.Color.blue(px)
            count++
        }
    }
    if (count == 0) return Color.Transparent
    val r = (rSum / count).toInt()
    val g = (gSum / count).toInt()
    val b = (bSum / count).toInt()
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    val brightnessFactor = ((hsv[2] - 0.03f) / 0.14f).coerceIn(0f, 1f)
    if (brightnessFactor <= 0f) return Color.Transparent
    if (hsv[1] > 0.08f) {
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0f, 0.92f)
    }
    hsv[2] = hsv[2].coerceIn(0.32f, 0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = brightnessFactor)
}

@Composable
internal fun CinematicAmbientBackdrop(
    colors: AmbientColors,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val animSpec = tween<Color>(durationMillis = if (reduceMotion) 0 else 1_400, easing = LinearEasing)
    val left by animateColorAsState(colors.left, animationSpec = animSpec, label = "ambientLeft")
    val center by animateColorAsState(colors.center, animationSpec = animSpec, label = "ambientCenter")
    val right by animateColorAsState(colors.right, animationSpec = animSpec, label = "ambientRight")

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(240.dp),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        if (left.alpha > 0.005f) {
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                left.copy(alpha = left.alpha * 0.34f),
                                left.copy(alpha = left.alpha * 0.12f),
                                Color.Transparent,
                            ),
                        center = Offset(w * 0.18f, 0f),
                        radius = w * 0.75f,
                    ),
            )
        }
        if (center.alpha > 0.005f) {
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                center.copy(alpha = center.alpha * 0.38f),
                                center.copy(alpha = center.alpha * 0.14f),
                                Color.Transparent,
                            ),
                        center = Offset(w * 0.50f, 0f),
                        radius = w * 0.82f,
                    ),
            )
        }
        if (right.alpha > 0.005f) {
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                right.copy(alpha = right.alpha * 0.34f),
                                right.copy(alpha = right.alpha * 0.12f),
                                Color.Transparent,
                            ),
                        center = Offset(w * 0.82f, 0f),
                        radius = w * 0.75f,
                    ),
            )
        }
    }
}

@Composable
internal fun CinematicStatusBarGlow(
    color: Color,
    alpha: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val animSpec = tween<Color>(durationMillis = if (reduceMotion) 0 else 1_400, easing = LinearEasing)
    val animated by animateColorAsState(color, animationSpec = animSpec, label = "ambientTop")
    val blended =
        if (animated.alpha <= 0.005f) {
            Color.Black
        } else {
            lerp(Color.Black, animated.copy(alpha = 1f), 0.28f * animated.alpha)
        }
    Box(modifier.background(blended.copy(alpha = alpha)))
}
