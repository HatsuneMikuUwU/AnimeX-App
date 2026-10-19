/*
 * Diadaptasi dari Morphe Manager (https://github.com/MorpheApp/morphe-manager)
 * Copyright 2026 Morphe. Dilisensikan di bawah GNU GPL v3 (dengan ketentuan tambahan Section 7).
 * Dimodifikasi untuk AnimeX: tanpa efek "patching completed", dengan pengaturan sendiri.
 */

package com.uwu.animex.ui.background

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/*
 * Mesin animasi latar: waktu berbasis frame yang dibatasi ~60 Hz, berhenti saat aplikasi
 * tidak aktif, dan parallax dari accelerometer yang dihaluskan dengan spring.
 */

// Latar bergerak pelan, jadi step 60 Hz tidak bisa dibedakan dari 120 Hz,
// dan tiap step yang dilewati berarti satu repaint layar penuh yang dihemat GPU
internal const val BACKGROUND_STEP_INTERVAL_MS = 16f

// Kemiringan terkecil yang layak menggerakkan target parallax (di bawahnya cuma noise sensor)
private const val TILT_TARGET_THRESHOLD = 0.02f

// Jam dibungkus tiap satu jam animasi supaya presisi Float tetap terjaga.
// Angkanya kelipatan semua periode siklus yang dipakai latar, jadi loop-nya tetap mulus
private const val ANIMATED_TIME_WRAP_MS = 3_600_000f

/**
 * Menjalankan [frameLoop] hanya selama host dalam keadaan RESUMED. Begitu layar dikunci atau
 * app pindah ke belakang, loop dibatalkan sehingga frame clock tidak terus aktif untuk siapa-siapa.
 */
@Composable
fun AnimationFrameEffect(frameLoop: suspend CoroutineScope.() -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            frameLoop()
        }
    }
}

/**
 * Memanggil [onStep] sekali tiap [BACKGROUND_STEP_INTERVAL_MS] dengan jumlah milidetik animasi
 * sejak step sebelumnya (waktu nyata dikali kecepatan yang naik/turun halus menuju [speedMultiplier]),
 * beserta kecepatan halus itu sendiri.
 */
@Composable
fun BackgroundStepEffect(
    speedMultiplier: Float,
    boost: Float = 1f,
    rampPerSecond: Float = 2.5f,
    onStep: (scaledMs: Float, speed: Float) -> Unit,
) {
    val targetSpeed = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeed.floatValue = speedMultiplier }

    AnimationFrameEffect {
        // boost: seberapa jauh latar ini ikut melaju dibanding speedMultiplier (1 = persis mengikuti)
        fun boostedTarget() = 1f + (targetSpeed.floatValue - 1f) * boost

        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = boostedTarget()
        var elapsedMs = 0f
        var pendingMs = 0f
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs
                currentSpeed += (boostedTarget() - currentSpeed) * (delta / 1000f) * rampPerSecond

                elapsedMs += delta
                pendingMs += delta * currentSpeed
                if (elapsedMs >= BACKGROUND_STEP_INTERVAL_MS) {
                    onStep(pendingMs, currentSpeed)
                    elapsedMs -= BACKGROUND_STEP_INTERVAL_MS
                    pendingMs = 0f
                }
            }
        }
    }
}

/** Jam animasi dalam milidetik; berubah mulus saat [speedMultiplier] diganti. */
@Composable
fun rememberAnimatedTime(speedMultiplier: Float): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    BackgroundStepEffect(speedMultiplier) { scaledMs, _ ->
        time.floatValue = (time.floatValue + scaledMs) % ANIMATED_TIME_WRAP_MS
    }
    return time
}

class ParallaxState(
    val tiltX: State<Float>,
    val tiltY: State<Float>,
)

/**
 * Parallax dari accelerometer. Nilai kemiringan dihaluskan dengan satu pasang spring yang
 * mengejar target terbaru, jadi gerakannya lembut dan tidak membuat ratusan coroutine per detik.
 */
@Composable
fun rememberParallaxState(
    enableParallax: Boolean,
    context: Context,
    sensitivity: Float = 0.3f,
): ParallaxState {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }
    val tiltTarget = remember { MutableStateFlow(Offset.Zero) }

    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            tiltTarget.value = Offset.Zero
            smoothTiltX.snapTo(0f)
            smoothTiltY.snapTo(0f)
            return@LaunchedEffect
        }
        tiltTarget.collectLatest { target ->
            coroutineScope {
                launch {
                    smoothTiltX.animateTo(
                        target.x,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    )
                }
                launch {
                    smoothTiltY.animateTo(
                        target.y,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    )
                }
            }
        }
    }

    DisposableEffect(enableParallax) {
        if (!enableParallax) return@DisposableEffect onDispose { }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?: return@DisposableEffect onDispose { }

        var baselineX = 0f
        var baselineY = 0f
        var calibrated = false

        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!calibrated) {
                        baselineX = event.values[0]
                        baselineY = event.values[1]
                        calibrated = true
                    }
                    val target =
                        Offset(
                            (event.values[0] - baselineX) * sensitivity,
                            -(event.values[1] - baselineY) * sensitivity,
                        )
                    if ((target - tiltTarget.value).getDistance() > TILT_TARGET_THRESHOLD) {
                        tiltTarget.value = target
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    return ParallaxState(smoothTiltX.asState(), smoothTiltY.asState())
}
