package com.uwu.animex.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.BarEffect
import com.uwu.animex.ui.liquid.liquidGlass
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.material.isRenderEffectSupported
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

@Composable
fun rememberBlurBackdrop(
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.background,
): LayerBackdrop? {
    // Backdrop tetap direkam walau setting blur mati: dipakai progressive blur
    // pengganti fade (lihat progressiveBlurBand). Glass bar tetap ikut setting blur
    // lewat resolveGlassStyle().
    if (
        !enabled ||
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
): GlassStyle {
    val blurOn by Appearance.settings.collectAsStateWithLifecycle()
    return when {
        backdrop == null || !blurOn.blur -> GlassStyle.Flat
        effect == BarEffect.LIQUID_GLASS && isRuntimeShaderSupported() -> GlassStyle.Liquid
        else -> GlassStyle.Blur
    }
}

/** True kalau fade di belakang floating bar harus diganti progressive blur (blur off + device support). */
@Composable
fun useProgressiveBlurFade(backdrop: LayerBackdrop?): Boolean {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    return backdrop != null && !settings.blur
}

/**
 * Tinggi band blur atas, rumus sama dengan InstallerMiuixProgressiveAppBarSurface di InstallerX:
 * (inset status bar + tinggi bar + 20dp) * 1.2.
 */
@Composable
fun topBlurBandHeight(barHeight: Dp = 64.dp): Dp {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return (top + barHeight + 20.dp) * 1.2f
}

/**
 * Band progressive blur ala InstallerX mode OS4 (InstallerMiuixProgressiveAppBarSurface):
 * band diukur sendiri setinggi [bandHeight] dan ditempel ke tepi layar, tidak mengikuti
 * tinggi Box induknya, lalu dikaburkan dengan blur progresif dan tint 0.3.
 *
 * Memakai progressiveTextureBlur seperti InstallerX (curve 0.5, radius GlassTopAppBarDefaults):
 * ProgressiveBlur.Top untuk band atas, ProgressiveBlur.Bottom untuk band bawah.
 *
 * Pasang di Box induk yang ukurannya fixed (mis. matchParentSize).
 */
@Composable
fun Modifier.progressiveBlurBand(
    backdrop: LayerBackdrop,
    fromTop: Boolean,
    bandHeight: Dp,
    tint: Color = MaterialTheme.colorScheme.background,
): Modifier {
    val colors = BlurColors(blendColors = listOf(BlendColorEntry(color = tint.copy(alpha = 0.3f))))
    val radius = GlassTopAppBarDefaults.ProgressiveBlurRadius.value
    return this
        .layout { measurable, constraints ->
            val h = bandHeight.roundToPx()
            val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeRelative(0, if (fromTop) 0 else constraints.maxHeight - h)
            }
        }.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            gradient = (if (fromTop) ProgressiveBlur.Top else ProgressiveBlur.Bottom).copy(curve = 0.5f),
            blurRadius = radius,
            colors = colors,
        )
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
