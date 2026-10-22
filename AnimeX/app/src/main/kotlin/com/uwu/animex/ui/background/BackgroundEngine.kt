

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

internal const val BACKGROUND_STEP_INTERVAL_MS = 16f

private const val TILT_TARGET_THRESHOLD = 0.02f

private const val ANIMATED_TIME_WRAP_MS = 3_600_000f

@Composable
fun AnimationFrameEffect(frameLoop: suspend CoroutineScope.() -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            frameLoop()
        }
    }
}

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

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) {}
            }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    return ParallaxState(smoothTiltX.asState(), smoothTiltY.asState())
}
