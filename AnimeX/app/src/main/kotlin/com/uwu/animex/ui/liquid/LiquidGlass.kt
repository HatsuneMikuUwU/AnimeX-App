package com.uwu.animex.ui.liquid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal val IosIndicatorSpecular: Highlight =
    Highlight(
        width = 1.dp,
        alpha = 1f,
        style =
            BloomStroke(
                color = Color.White.copy(alpha = 0.12f),
                innerBlurRadius = 2.0.dp,
                primaryLight =
                    LightSource(
                        position = LightPosition(0.5f, -0.3f, -0.05f),
                        color = Color.White,
                        intensity = 1f,
                    ),
                secondaryLight =
                    LightSource(
                        position = LightPosition(0.5f, 0.8f, -0.5f),
                        color = Color.White,
                        intensity = 0.4f,
                    ),
                dualPeak = true,
            ),
    )

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

@Composable
internal fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f,
): Highlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary =
        remember(tilt, baseStyle.primaryLight, extraDegrees) {
            val basePrimary = baseStyle.primaryLight
            val gx = tilt.gravityX
            val gy = tilt.gravityY
            val gMagSq = gx * gx + gy * gy
            val (lx0, ly0) =
                if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
                    val invMag = 1f / sqrt(gMagSq)
                    (gx * invMag) to (gy * invMag)
                } else {
                    0f to -1f
                }
            val rad = extraDegrees * PI / 180.0
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            basePrimary.copy(
                position =
                    LightPosition(
                        x = LIGHT_REF_X + (c * lx0 - s * ly0),
                        y = LIGHT_REF_Y + (s * lx0 + c * ly0),
                        z = basePrimary.position.z,
                    ),
            )
        }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    containerColor: Color,
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = remember(containerColor) { containerColor.copy(alpha = 0.4f) }
    val highlight = remember { IosIndicatorSpecular.copy(alpha = 0.75f) }
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
            highlight = { highlight },
            onDrawSurface = { drawRect(tint) },
        )
}
