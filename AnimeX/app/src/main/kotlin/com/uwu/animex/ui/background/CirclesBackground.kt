/*
 * Diadaptasi dari Morphe Manager (https://github.com/MorpheApp/morphe-manager)
 * Copyright 2026 Morphe. Dilisensikan di bawah GNU GPL v3 (dengan ketentuan tambahan Section 7).
 * Dimodifikasi untuk AnimeX: tanpa efek "patching completed", dengan pengaturan sendiri.
 */

package com.uwu.animex.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lingkaran besar-kecil yang melayang pelan dengan parallax.
 * Posisi tiap lingkaran berosilasi dengan sin() pada periodenya sendiri.
 */
@Composable
fun CirclesBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val parallax = rememberParallaxState(enableParallax, LocalContext.current, sensitivity = 0.3f)
    val time = rememberAnimatedTime(speedMultiplier)

    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        val tiltX = parallax.tiltX.value
        val tiltY = parallax.tiltY.value
        val twoPi = 2f * PI.toFloat()

        // x, y, radius (dp), warna, alpha, kedalaman parallax
        val circles =
            listOf(
                Circle(0.20f + 0.05f * sin(t * twoPi / 8000f), 0.225f + 0.025f * sin(t * twoPi / 7000f), 133f, primary, 0.05f, 0.8f),
                Circle(0.85f + 0.03f * sin(t * twoPi / 9000f), 0.185f + 0.035f * sin(t * twoPi / 6500f), 93f, tertiary, 0.035f, 0.6f),
                Circle(0.715f + 0.035f * sin(t * twoPi / 7500f), 0.44f + 0.04f * sin(t * twoPi / 8500f), 67f, tertiary, 0.04f, 0.4f),
                Circle(0.815f + 0.035f * sin(t * twoPi / 9500f), 0.785f + 0.035f * sin(t * twoPi / 7200f), 107f, secondary, 0.035f, 0.7f),
                Circle(0.24f + 0.04f * sin(t * twoPi / 8200f), 0.765f + 0.035f * sin(t * twoPi / 6800f), 60f, primary, 0.04f, 0.5f),
                Circle(0.525f + 0.025f * sin(t * twoPi / 8800f), 0.895f + 0.025f * sin(t * twoPi / 7800f), 73f, secondary, 0.04f, 0.6f),
            )

        for (c in circles) {
            val strength = c.depth * 50f
            drawCircle(
                color = c.color.copy(alpha = c.alpha),
                radius = c.radiusDp * density,
                center = Offset(size.width * c.x + tiltX * strength, size.height * c.y + tiltY * strength),
            )
        }
    }
}

private class Circle(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val color: Color,
    val alpha: Float,
    val depth: Float,
)
