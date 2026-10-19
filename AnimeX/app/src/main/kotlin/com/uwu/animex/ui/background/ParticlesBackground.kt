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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Partikel kecil yang hanyut dengan pantulan lembut di tepi layar.
 * Partikel yang berdekatan dihubungkan garis tipis (efek konstelasi).
 */
@Composable
fun ParticlesBackground(
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
    val parallax = rememberParallaxState(enableParallax, LocalContext.current, sensitivity = 0.2f)

    val particles =
        remember {
            ArrayList<Particle>(PARTICLE_COUNT).apply {
                repeat(PARTICLE_COUNT) {
                    val radius =
                        when (Random.nextFloat()) {
                            in 0f..0.6f -> 2.5f + Random.nextFloat() * 3f
                            in 0.6f..0.9f -> 4f + Random.nextFloat() * 4f
                            else -> 7f + Random.nextFloat() * 5f
                        }
                    add(
                        Particle(
                            x = Random.nextFloat(),
                            y = Random.nextFloat(),
                            vx = (Random.nextFloat() - 0.5f) * 0.00018f,
                            vy = (Random.nextFloat() - 0.5f) * 0.00018f,
                            radiusDp = radius,
                            colorIndex = it % 3,
                        ),
                    )
                }
            }
        }

    // Fisika memindahkan partikel di tempat lalu menaikkan step sekali per pass,
    // jadi kanvas digambar ulang sekali, bukan sekali per partikel
    val step = remember { mutableIntStateOf(0) }

    BackgroundStepEffect(speedMultiplier) { scaledMs, _ ->
        val scale = scaledMs / 16.67f
        for (p in particles) {
            var nx = p.x + p.vx * scale
            var ny = p.y + p.vy * scale
            var nvx = p.vx
            var nvy = p.vy

            if (nx < 0.02f) { nvx = abs(nvx); nx = 0.02f }
            if (nx > 0.98f) { nvx = -abs(nvx); nx = 0.98f }
            if (ny < 0.02f) { nvy = abs(nvy); ny = 0.02f }
            if (ny > 0.98f) { nvy = -abs(nvy); ny = 0.98f }

            nvx += (Random.nextFloat() - 0.5f) * 0.000004f
            nvy += (Random.nextFloat() - 0.5f) * 0.000004f

            val speed = sqrt(nvx * nvx + nvy * nvy)
            if (speed > MAX_SPEED) {
                nvx = nvx / speed * MAX_SPEED
                nvy = nvy / speed * MAX_SPEED
            }
            p.x = nx
            p.y = ny
            p.vx = nvx
            p.vy = nvy
        }
        step.intValue++
    }

    Canvas(modifier.fillMaxSize()) {
        step.intValue // dibaca supaya tiap step menginvalidasi gambar
        val tiltX = parallax.tiltX.value
        val tiltY = parallax.tiltY.value
        val connectDist = size.width * 0.22f
        val connectDistSq = connectDist * connectDist

        val positions =
            particles.map {
                Offset(it.x * size.width + tiltX * 25f, it.y * size.height + tiltY * 25f)
            }

        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val dx = positions[i].x - positions[j].x
                val dy = positions[i].y - positions[j].y
                val distSq = dx * dx + dy * dy
                if (distSq < connectDistSq) {
                    val proximity = 1f - sqrt(distSq) / connectDist
                    drawLine(
                        color = colors[(particles[i].colorIndex + particles[j].colorIndex) % 3]
                            .copy(alpha = (proximity * proximity * 0.10f).coerceIn(0f, 0.18f)),
                        start = positions[i],
                        end = positions[j],
                        strokeWidth = 1.2f,
                    )
                }
            }
        }

        particles.forEachIndexed { index, p ->
            val color = colors[p.colorIndex]
            val radius = p.radiusDp * density
            drawCircle(color.copy(alpha = 0.55f * 0.2f), radius * 2f, positions[index])
            drawCircle(color.copy(alpha = 0.55f), radius, positions[index])
        }
    }
}

private const val PARTICLE_COUNT = 65
private const val MAX_SPEED = 0.00025f

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radiusDp: Float,
    val colorIndex: Int,
)
