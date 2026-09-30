package com.uwu.animex.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.glass.FloatingBottomBar
import com.uwu.animex.ui.glass.FloatingBottomBarDefaults
import com.uwu.animex.ui.glass.FloatingBottomBarMode
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** Tinggi area yang ditutupi floating bar (bar + margin + inset nav). Konten scroll menambahkannya di padding bawah. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Backdrop untuk efek liquid glass. Hanya aktif di Android 13+ (AGSL runtime shader);
 * di bawah itu null dan bar memakai background solid.
 */
@Composable
fun rememberBarBackdrop(): LayerBackdrop? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isRuntimeShaderSupported()) return null
    val bg = MaterialTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(bg)
        drawContent()
    }
}

/** Pasang di konten yang ada di belakang bar. */
fun Modifier.barBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

@Composable
fun <T> AppFloatingBottomBar(
    items: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    iconContent: @Composable (item: T, index: Int) -> Unit,
    labelContent: @Composable (item: T, index: Int) -> Unit,
) {
    // backdrop hanya non-null di Android 13+; di bawah itu jangan sentuh kode miuix sama sekali.
    if (backdrop == null) {
        SimpleFloatingBottomBar(
            items = items,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            iconContent = iconContent,
            labelContent = labelContent,
        )
        return
    }

    Box(Modifier.fillMaxWidth()) {
        FloatingBottomBar(
            items = items,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            selectedIndex = { selectedIndex },
            onSelected = onSelected,
            backdrop = backdrop,
            mode = FloatingBottomBarMode.LiquidGlass,
            colors = FloatingBottomBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                indicatorColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            iconContent = iconContent,
            labelContent = labelContent,
        )
    }
}
