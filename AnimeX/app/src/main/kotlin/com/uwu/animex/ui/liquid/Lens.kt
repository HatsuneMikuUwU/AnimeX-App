package com.uwu.animex.ui.liquid

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import com.uwu.animex.R
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/**
 * Liquid glass refraction using NagramXF's own shader (`res/raw/liquid_glass_shader.agsl`, copied verbatim)
 * and the same uniform setup as NagramXF's `LiquidGlassEffect.update(...)`.
 *
 * @param shaderSource contents of `R.raw.liquid_glass_shader`, see [rememberLiquidGlassShader].
 * @param thickness glass edge thickness in px (NagramXF default: 11dp). Clamped to 1/5 of the shorter side.
 * @param intensity refraction strength (NagramXF default: 0.75).
 * @param index refractive index (NagramXF default: 1.5).
 * @param foregroundColor overlay drawn by the shader over the refracted image (NagramXF's `foregroundColor`).
 */
fun BackdropEffectScope.lens(
    shaderSource: String,
    thickness: Float,
    intensity: Float = DEFAULT_LIQUID_INTENSITY,
    index: Float = DEFAULT_LIQUID_INDEX,
    foregroundColor: Color = Color.Transparent,
) {
    if (!isRuntimeShaderSupported()) return

    val t = thickness.fastCoerceAtMost(size.minDimension / 5f)
    if (t < 1f || intensity <= 0f) return

    // Refracted samples can land well outside the shape, so make sure the capture has room for them.
    val neededPadding = t * intensity * PADDING_FACTOR
    if (padding < neededPadding) {
        padding = neededPadding
    }

    val radii = roundedRectCornerRadii() ?: return

    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    val pad = padding / sf
    val width = size.width / sf
    val height = size.height / sf
    val resolutionX = width + pad * 2f
    val resolutionY = height + pad * 2f

    // Corner radii clamping, as in LiquidGlassEffect.update()
    var rLT = radii[0] / sf
    var rRT = radii[1] / sf
    var rRB = radii[2] / sf
    var rLB = radii[3] / sf
    if (rLT + rLB > height) {
        val a = rLT / (rLT + rLB)
        rLT = height * a
        rLB = height * (1f - a)
    }
    if (rRT + rRB > height) {
        val a = rRT / (rRT + rRB)
        rRT = height * a
        rRB = height * (1f - a)
    }

    val fa = foregroundColor.alpha
    val fr = foregroundColor.red * fa
    val fg = foregroundColor.green * fa
    val fb = foregroundColor.blue * fa

    runtimeShaderEffect(
        key = "NagramLiquidGlass",
        shaderString = shaderSource,
        uniformShaderName = "img",
    ) {
        setFloatUniform("resolution", resolutionX, resolutionY)
        setFloatUniform("center", resolutionX / 2f, resolutionY / 2f)
        setFloatUniform("size", width / 2f, height / 2f)
        setFloatUniform("radius", rRB, rRT, rLB, rLT)
        setFloatUniform("thickness", t / sf)
        setFloatUniform("refract_intensity", intensity)
        setFloatUniform("refract_index", index)
        setFloatUniform("foreground_color_premultiplied", fr, fg, fb, fa)
    }
}

/** Loads NagramXF's `liquid_glass_shader.agsl` from raw resources (read once). */
@Composable
fun rememberLiquidGlassShader(): String {
    val context = LocalContext.current
    return remember(context) {
        context.resources
            .openRawResource(R.raw.liquid_glass_shader)
            .bufferedReader()
            .use { it.readText() }
    }
}

const val DEFAULT_LIQUID_INTENSITY = 0.75f
const val DEFAULT_LIQUID_INDEX = 1.5f
private const val PADDING_FACTOR = 6f

private fun BackdropEffectScope.roundedRectCornerRadii(): FloatArray? {
    val cornerShape = shape as? CornerBasedShape ?: return null
    val sizePx = size
    val maxRadius = sizePx.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (isLtr) cornerShape.topStart.toPx(sizePx, this) else cornerShape.topEnd.toPx(sizePx, this)
    val topRight = if (isLtr) cornerShape.topEnd.toPx(sizePx, this) else cornerShape.topStart.toPx(sizePx, this)
    val bottomRight = if (isLtr) {
        cornerShape.bottomEnd.toPx(
            sizePx,
            this,
        )
    } else {
        cornerShape.bottomStart.toPx(sizePx, this)
    }
    val bottomLeft = if (isLtr) cornerShape.bottomStart.toPx(sizePx, this) else cornerShape.bottomEnd.toPx(sizePx, this)
    return floatArrayOf(
        topLeft.fastCoerceAtMost(maxRadius),
        topRight.fastCoerceAtMost(maxRadius),
        bottomRight.fastCoerceAtMost(maxRadius),
        bottomLeft.fastCoerceAtMost(maxRadius),
    )
}
