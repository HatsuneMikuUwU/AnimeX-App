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

fun LayerBackdrop?.appBarColor(fallback: Color): Color = if (this != null) Color.Transparent else fallback

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

/**
 * Picks how a bar looks. No backdrop (blur setting off) means flat; otherwise the bar's chosen effect,
 * with liquid glass falling back to blur when AGSL shaders are unsupported.
 */
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

/** Container surface of a floating bar for the given [style]. */
@Composable
fun Modifier.barGlass(
    style: GlassStyle,
    backdrop: LayerBackdrop?,
    shape: Shape,
    container: Color,
    drawFlatBackground: Boolean = true,
): Modifier =
    when (style) {
        GlassStyle.Liquid -> if (backdrop != null) liquidGlass(backdrop, shape, container) else this
        GlassStyle.Blur -> blurEffect(backdrop, shape = shape, blendColor = container).glassStroke()
        GlassStyle.Flat -> (if (drawFlatBackground) background(container, shape) else this).glassStroke()
    }
