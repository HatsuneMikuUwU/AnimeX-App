package com.uwu.animex.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun RingsBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
) {
    val colors =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        )
    val parallax = rememberParallaxState(enableParallax, LocalContext.current, sensitivity = 0.3f)
    val time = rememberAnimatedTime(speedMultiplier)

    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        val tiltX = parallax.tiltX.value
        val tiltY = parallax.tiltY.value
        val twoPi = 2f * PI.toFloat()

        RING_CONFIGS.forEachIndexed { index, config ->
            val halfX = (config.endX - config.startX) / 2f
            val halfY = (config.endY - config.startY) / 2f
            val cx = config.startX + halfX + halfX * sin(t * twoPi / config.periodX)
            val cy = config.startY + halfY + halfY * sin(t * twoPi / config.periodY)
            val strength = config.depth * 50f
            val center = Offset(size.width * cx + tiltX * strength, size.height * cy + tiltY * strength)
            val color = colors[index % 3]

            val inner = config.radii.first()
            val outer = config.radii.last()
            val count = config.radii.size
            val spacing = (outer - inner) / (count - 1)
            val ripple = (t / RIPPLE_PERIODS[index % RIPPLE_PERIODS.size]) % 1f
            for (ringIndex in 0 until count) {
                val position = ringIndex + ripple
                val travelled = position / count
                val fadeIn = (position / 0.6f).coerceAtMost(1f)
                val alpha = 0.15f * (1f - travelled) * fadeIn
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = (inner + position * spacing) * density,
                    center = center,
                    style = Stroke(width = (2.0f - 0.8f * travelled) * density),
                )
            }
        }
    }
}

private val RIPPLE_PERIODS = floatArrayOf(2600f, 3400f, 2200f, 3000f, 3700f, 2800f)

private class RingConfig(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val periodX: Int,
    val periodY: Int,
    val radii: List<Float>,
    val depth: Float,
)

private val RING_CONFIGS =
    listOf(
        RingConfig(0.2f, 0.2f, 0.3f, 0.25f, 9000, 8000, listOf(47f, 63f, 80f), 0.8f),
        RingConfig(0.85f, 0.15f, 0.8f, 0.2f, 10000, 7500, listOf(43f, 60f), 0.6f),
        RingConfig(0.5f, 0.5f, 0.55f, 0.55f, 8500, 9500, listOf(37f, 53f, 70f), 0.5f),
        RingConfig(0.15f, 0.75f, 0.2f, 0.8f, 7000, 8000, listOf(50f, 67f), 0.7f),
        RingConfig(0.8f, 0.85f, 0.85f, 0.8f, 8800, 7600, listOf(40f, 57f, 73f), 0.6f),
        RingConfig(0.75f, 0.4f, 0.8f, 0.45f, 9200, 8400, listOf(45f, 62f), 0.4f),
    )
