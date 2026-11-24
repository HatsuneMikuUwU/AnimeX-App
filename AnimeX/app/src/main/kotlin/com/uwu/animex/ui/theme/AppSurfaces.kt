package com.uwu.animex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal val LocalAmoledSurfaces = staticCompositionLocalOf { false }

private const val DarkCardAlpha = 0.88f

@Composable
fun appBackgroundColor(): Color =
    if (LocalAmoledSurfaces.current) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

@Composable
fun appCardBaseColor(): Color =
    if (LocalAmoledSurfaces.current) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

@Composable
fun appCardAlpha(): Float = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkCardAlpha else 1f

@Composable
fun appCardColor(): Color = appCardBaseColor().copy(alpha = appCardAlpha())
