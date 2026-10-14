package com.uwu.animex.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
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
): Modifier {
    if (!enabled || backdrop == null) return this
    val blend = blendColor.copy(alpha = 0.8f)
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

/** Rekam poster (bukan seluruh konten) sebagai sumber blur chip, supaya nggak rekursif. */
fun Modifier.chipSource(backdrop: LayerBackdrop?): Modifier = if (backdrop != null) layerBackdrop(backdrop) else this

/** Chip blur dengan warna asli chip sebagai blend (alpha 0.8). Fallback ke warna solid kalau blur nonaktif. */
@Composable
fun Modifier.chipBlurBackground(
    fallback: Color,
    backdrop: LayerBackdrop?,
    shape: Shape = CircleShape,
): Modifier =
    this
        .clip(shape)
        .blurEffect(backdrop, shape = shape, blendColor = fallback)
        .background(backdrop.appBarColor(fallback), shape)
