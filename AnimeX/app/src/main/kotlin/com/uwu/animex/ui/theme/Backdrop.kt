package com.uwu.animex.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

/*
 * Efek blur (glassmorphism) untuk bottom bar & toolbar.
 * Diport dari InstallerX-Revived (ui/theme/Backdrop.kt) dan memakai library yang sama: miuix-blur.
 *
 * Cara pakai:
 *  1. `val backdrop = rememberBlurBackdrop()` di level Scaffold.
 *  2. Konten yang discroll diberi `Modifier.layerBackdrop(backdrop)` (jadi sumber blur).
 *  3. Bar diberi `Modifier.blurEffect(backdrop)` + containerColor = `backdrop.appBarColor(fallback)`.
 */

/**
 * LayerBackdrop dengan background solid supaya tidak ada artefak alpha-blending.
 * @return LayerBackdrop kalau perangkat mendukung RenderEffect (Android 13+), selain itu null
 * (bar kembali ke warna solid biasa).
 */
@Composable
fun rememberBlurBackdrop(
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.background,
): LayerBackdrop? {
    // miuix-blur butuh Android 13+ (API 33); di bawah itu bar tetap solid.
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isRenderEffectSupported()) return null
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

/** Transparan kalau blur aktif (supaya efek terlihat), kalau tidak pakai warna [fallback]. */
fun LayerBackdrop?.appBarColor(fallback: Color): Color = if (this != null) Color.Transparent else fallback

/**
 * Terapkan efek blur ala InstallerX pada bar.
 * @param backdrop sumber visual; null berarti efek dilewati.
 * @param blendColor warna yang dicampur di atas hasil blur (default: surfaceContainer, alpha 80%).
 */
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
            colors = BlurColors(
                blendColors = listOf(BlendColorEntry(color = blend)),
            ),
        ),
    )
}
