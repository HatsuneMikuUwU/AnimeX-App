package com.uwu.animex.ui.liquid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.theme.glassStroke
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.material.EdgeLightSource
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.sensor.rememberDeviceTilt
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal val IosIndicatorSpecular: EdgeStroke =
    EdgeStroke(
        width = 1.dp,
        bevel = 2.0.dp,
        color = Color.White.copy(alpha = 0.12f),
        primary =
            EdgeLightSource(
                x = 0.5f,
                y = -0.3f,
                z = -0.05f,
                color = Color.White,
                intensity = 1f,
            ),
        secondary =
            EdgeLightSource(
                x = 0.5f,
                y = 0.8f,
                z = -0.5f,
                color = Color.White,
                intensity = 0.4f,
            ),
        // Cahaya hasil rotasi gravitasi menyapu horizontal, ikuti arah XY tiap cahaya.
        peak = EdgePeak.Axial,
    )

// Samakan dengan LIGHT_REF di edge-stroke shader.
private const val LIGHT_REF_X = 0.5f

private const val LIGHT_REF_Y = 0.7f

private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

// Kuantisasi 3 derajat: perubahan lebih halus tidak terlihat.
private const val GRAVITY_ANGLE_STEP_RAD = (3.0 * PI / 180.0).toFloat()

@Composable
private fun rememberQuantizedGravityAngle(): State<Float> {
    val tiltState = rememberDeviceTilt()
    return remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            val gx = tilt.gravityX
            val gy = tilt.gravityY
            val gMagSq = gx * gx + gy * gy
            if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
                (atan2(gy, gx) / GRAVITY_ANGLE_STEP_RAD).roundToInt() * GRAVITY_ANGLE_STEP_RAD
            } else {
                (-PI / 2).toFloat()
            }
        }
    }
}

@Composable
internal fun rememberGravityRotatedStroke(
    base: EdgeStroke,
    extraDegrees: Float = 0f,
): State<EdgeStroke> {
    val gravityAngle = rememberQuantizedGravityAngle()
    return remember(gravityAngle, base, extraDegrees) {
        derivedStateOf {
            val rad = gravityAngle.value + (extraDegrees * PI / 180.0).toFloat()
            val opposite = rad + PI.toFloat()
            base.copy(
                primary = base.primary.copy(x = LIGHT_REF_X + cos(rad), y = LIGHT_REF_Y + sin(rad)),
                secondary = base.secondary.copy(x = LIGHT_REF_X + cos(opposite), y = LIGHT_REF_Y + sin(opposite)),
            )
        }
    }
}

@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    containerColor: Color,
    strokeCornerRadius: Dp = Dp.Infinity,
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = remember(containerColor) { containerColor.copy(alpha = 0.4f) }
    return this
        .dropShadow(
            shape = shape,
            shadow =
                Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = if (isDark) 0.2f else 0.1f,
                ),
        ).drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4.dp.toPx(), 4.dp.toPx())
                lens(
                    refractionHeight = 20.dp.toPx(),
                    refractionAmount = 20.dp.toPx(),
                )
            },
            onDrawSurface = { drawRect(tint) },
        ).glassStroke(cornerRadius = strokeCornerRadius)
}
