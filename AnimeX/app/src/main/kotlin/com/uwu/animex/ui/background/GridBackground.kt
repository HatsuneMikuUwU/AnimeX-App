

package com.uwu.animex.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GridBackground(
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
    val parallax = rememberParallaxState(enableParallax, LocalContext.current, sensitivity = 0.15f)
    val time = rememberAnimatedTime(speedMultiplier)

    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        val tiltX = parallax.tiltX.value
        val tiltY = parallax.tiltY.value
        val twoPi = 2f * PI.toFloat()

        val spacing = GRID_SPACING_DP * density
        val cols = (size.width / spacing).roundToInt().coerceAtLeast(2) + 1
        val rows = (size.height / spacing).roundToInt().coerceAtLeast(2) + 1
        val cellW = size.width / (cols - 1).toFloat()
        val cellH = size.height / (rows - 1).toFloat()
        val maxDist = sqrt(size.width * size.width + size.height * size.height) * 0.5f

        val ax = size.width * (0.5f + 0.32f * sin(t * twoPi / 23000f))
        val ay = size.height * (0.5f + 0.30f * sin(t * twoPi / 31000f + 1.1f))
        val bx = size.width * (0.5f + 0.32f * sin(t * twoPi / 27000f + 2.4f))
        val by = size.height * (0.5f + 0.30f * cos(t * twoPi / 19000f + 0.4f))

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * cellW + tiltX * 12f
                val y = row * cellH + tiltY * 12f

                val dx = x - size.width * 0.5f
                val dy = y - size.height * 0.5f
                val dist = sqrt(dx * dx + dy * dy)

                val distA = sqrt((x - ax) * (x - ax) + (y - ay) * (y - ay))
                val distB = sqrt((x - bx) * (x - bx) + (y - by) * (y - by))
                val wave =
                    0.5f * (
                        sin(t * twoPi / 3800f - distA / density * 0.036f) +
                            sin(t * twoPi / 4300f - distB / density * 0.036f + 1.7f)
                    )

                val radius = ((1.7f + wave * 0.85f) * density).coerceAtLeast(0.27f * density)
                val edgeDim = (1f - dist / maxDist).coerceIn(0.55f, 1f)
                val alpha = ((0.30f + wave * 0.10f) * edgeDim).coerceIn(0f, 0.50f)

                drawCircle(colors[(col + row) % 3].copy(alpha = alpha), radius, Offset(x, y))
            }
        }
    }
}

private const val GRID_SPACING_DP = 40f
