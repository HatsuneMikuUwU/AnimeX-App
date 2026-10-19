package com.uwu.animex.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.ui.background.LocalAnimatedBackgroundActive
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
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

/**
 * True kalau toolbar harus memakai gaya "normal" (blur + warna latar). Saat latar animasi aktif,
 * toolbar yang masih expanded sepenuhnya transparan tanpa blur; begitu collapse, gayanya sama
 * persis dengan saat latar animasi mati.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun toolbarSolid(scrollBehavior: TopAppBarScrollBehavior): Boolean {
    if (!LocalAnimatedBackgroundActive.current) return true
    val collapsed by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.collapsedFraction > TOOLBAR_COLLAPSE_THRESHOLD }
    }
    return collapsed
}

private const val TOOLBAR_COLLAPSE_THRESHOLD = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Modifier.toolbarBlur(
    backdrop: LayerBackdrop?,
    scrollBehavior: TopAppBarScrollBehavior,
): Modifier {
    if (!toolbarSolid(scrollBehavior)) return this
    return blurEffect(
        backdrop,
        blendColor = MaterialTheme.colorScheme.background,
        blendAlpha = 0.8f,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun toolbarContainerColor(
    backdrop: LayerBackdrop?,
    scrollBehavior: TopAppBarScrollBehavior,
): Color {
    val target =
        if (toolbarSolid(scrollBehavior)) {
            backdrop.appBarColor(MaterialTheme.colorScheme.background)
        } else {
            Color.Transparent
        }
    val color by animateColorAsState(target, tween(150), label = "toolbarContainer")
    return color
}
