package com.uwu.animex.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Orange = Color(0xFFF26B3A)
    val Red = Color(0xFFE53935)
    val Star = Color(0xFFF5B942)
    val Purple = Color(0xFF7B3FA0)
}

private val Light = lightColorScheme(
    primary = Color(0xFFB94A1F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF7DFD7),
    outline = Color(0xFF85736D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF5B1A00),
    primaryContainer = Color(0xFF822F0E),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondaryContainer = Color(0xFF5D4036),
    onSecondaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFEDE0DC),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFEDE0DC),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BB),
    surfaceContainer = Color(0xFF271D1A),
    surfaceContainerHigh = Color(0xFF32251F),
    outline = Color(0xFFA08D86),
)

@Composable
fun AnimeinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
