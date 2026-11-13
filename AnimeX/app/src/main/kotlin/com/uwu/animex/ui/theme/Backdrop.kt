package com.uwu.animex.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.BarEffect
import com.uwu.animex.ui.liquid.liquidGlass
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

@Composable
fun rememberBlurBackdrop(
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.background,
): LayerBackdrop? {
    val blurSetting by Appearance.settings.collectAsStateWithLifecycle()
    if (
        !enabled ||
        !blurSetting.blur ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        !isRenderEffectSupported()
    ) {
        return null
    }
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

@Composable
fun Modifier.blurEffect(
    backdrop: LayerBackdrop?,
    enabled: Boolean = true,
    blurRadius: Float = 25f,
    shape: Shape = RectangleShape,
    blendColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    blendAlpha: Float = 0.8f,
): Modifier {
    if (!enabled || backdrop == null) return this
    val blend = blendColor.copy(alpha = blendAlpha)
    return this.then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors =
                BlurColors(
                    blendColors = listOf(BlendColorEntry(color = blend)),
                ),
        ),
    )
}

enum class GlassStyle { Flat, Blur, Liquid }

@Composable
fun resolveGlassStyle(
    backdrop: LayerBackdrop?,
    effect: BarEffect,
): GlassStyle =
    when {
        backdrop == null -> GlassStyle.Flat
        effect == BarEffect.LIQUID_GLASS && isRuntimeShaderSupported() -> GlassStyle.Liquid
        else -> GlassStyle.Blur
    }

@Composable
fun Modifier.barGlass(
    style: GlassStyle,
    backdrop: LayerBackdrop?,
    shape: Shape,
    container: Color,
    drawFlatBackground: Boolean = true,
    strokeCornerRadius: Dp = Dp.Infinity,
): Modifier =
    when (style) {
        GlassStyle.Liquid -> if (backdrop != null) liquidGlass(backdrop, shape, container, strokeCornerRadius) else this
        GlassStyle.Blur -> blurEffect(backdrop, shape = shape, blendColor = container).glassStroke(cornerRadius = strokeCornerRadius)
        GlassStyle.Flat -> (if (drawFlatBackground) background(container, shape) else this).glassStroke(cornerRadius = strokeCornerRadius)
    }

@Composable
fun glassContainerColor(
    base: Color = MaterialTheme.colorScheme.surfaceContainer,
    alpha: Float = 0.8f,
): Color {
    val blurSetting by Appearance.settings.collectAsStateWithLifecycle()
    val translucent = blurSetting.blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return if (translucent) base.copy(alpha = alpha) else base
}
