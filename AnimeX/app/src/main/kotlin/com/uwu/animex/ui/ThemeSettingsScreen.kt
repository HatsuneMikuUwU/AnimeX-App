@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.uwu.animex.data.AppContrastLevel
import com.uwu.animex.data.AppPaletteStyle
import com.uwu.animex.data.AppThemeMode
import com.uwu.animex.data.PresetSeedColors
import com.uwu.animex.data.SeedColorOption
import com.uwu.animex.data.ThemePrefs
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Tampilan" (Appearance) screen — ported from InstallerX-Revived ThemeSettingsPage
 * with ColorSwatchPreview-style multi-segment palette circles, separate card for
 * accent colors, palette style always available (works with dynamic seed), and
 * ripples clipped to rounded shapes.
 */
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val canUseDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isDarkActive = when (ThemePrefs.themeMode) {
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val dynamicOn = ThemePrefs.useDynamicColor && canUseDynamicColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tampilan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { PreviewCard() }

            item {
                SettingsSection(title = "Mode tema") {
                    ThemeModeRow()
                }
            }

            item {
                SettingsSection(title = "Warna") {
                    SwitchRow(
                        title = "Warna dinamis",
                        subtitle = if (canUseDynamicColor)
                            "Ambil warna dari wallpaper (Material You)"
                        else
                            "Butuh Android 12 ke atas",
                        checked = ThemePrefs.useDynamicColor && canUseDynamicColor,
                        enabled = canUseDynamicColor,
                        onCheckedChange = ThemePrefs::updateUseDynamicColor,
                    )
                }
            }

            // Separate card for accent/seed colors (hidden while dynamic is on)
            item {
                AnimatedVisibility(
                    visible = !dynamicOn,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    SettingsSection(title = "Warna aksen") {
                        SeedColorGrid()
                    }
                }
            }

            // Palette style always available — applies even with dynamic seed
            item {
                SettingsSection(title = "Gaya palet") {
                    PaletteStyleGrid()
                }
            }

            item {
                SettingsSection(title = "Kontras") {
                    ContrastRow()
                }
            }

            item {
                SettingsSection(title = "Gelap") {
                    SwitchRow(
                        title = "Latar AMOLED",
                        subtitle = if (isDarkActive)
                            "Hitam pekat saat mode gelap aktif"
                        else
                            "Aktif saat mode gelap sedang digunakan",
                        checked = ThemePrefs.amoledBlack,
                        onCheckedChange = ThemePrefs::updateAmoledBlack,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard() {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Pratinjau", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    scheme.primary to "Primer",
                    scheme.secondaryContainer to "Sekunder",
                    scheme.tertiaryContainer to "Tersier",
                ).forEach { (color, label) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(color),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** Rounded ripple that follows [shape]. Clip first, then clickable with bounded ripple. */
@Composable
private fun Modifier.roundedClickable(
    shape: androidx.compose.ui.graphics.Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
private fun ThemeModeRow() {
    data class Opt(val mode: AppThemeMode, val label: String, val icon: ImageVector)

    val options = listOf(
        Opt(AppThemeMode.SYSTEM, "Sistem", Icons.Filled.PhoneAndroid),
        Opt(AppThemeMode.LIGHT, "Terang", Icons.Filled.LightMode),
        Opt(AppThemeMode.DARK, "Gelap", Icons.Filled.DarkMode),
    )
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            val selected = ThemePrefs.themeMode == opt.mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape,
                    )
                    .roundedClickable(shape) { ThemePrefs.updateThemeMode(opt.mode) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    opt.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    opt.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .roundedClickable(shape, enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(),
        )
    }
}

// ── Color swatch cache (same idea as InstallerX ColorPalatteCard) ────────────

private val colorSchemeCache = ConcurrentHashMap<String, ColorScheme>()

private fun mapPaletteStyle(style: AppPaletteStyle): PaletteStyle = when (style) {
    AppPaletteStyle.TonalSpot -> PaletteStyle.TonalSpot
    AppPaletteStyle.Neutral -> PaletteStyle.Neutral
    AppPaletteStyle.Vibrant -> PaletteStyle.Vibrant
    AppPaletteStyle.Expressive -> PaletteStyle.Expressive
    AppPaletteStyle.Rainbow -> PaletteStyle.Rainbow
    AppPaletteStyle.FruitSalad -> PaletteStyle.FruitSalad
    AppPaletteStyle.Monochrome -> PaletteStyle.Monochrome
    AppPaletteStyle.Fidelity -> PaletteStyle.Fidelity
    AppPaletteStyle.Content -> PaletteStyle.Content
}

@Composable
private fun SeedColorGrid() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemMinWidth = 88.dp
        val columns = (maxWidth / itemMinWidth).toInt().coerceAtLeast(1)
        val chunked = PresetSeedColors.chunked(columns)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            chunked.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    rowItems.forEach { option ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            ColorSwatchPreview(
                                option = option,
                                currentStyle = ThemePrefs.paletteStyle,
                                isSelected = ThemePrefs.seedColorKey == option.key,
                                onClick = { ThemePrefs.updateSeedColorKey(option.key) },
                            )
                        }
                    }
                    val remaining = columns - rowItems.size
                    if (remaining > 0) {
                        repeat(remaining) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Multi-segment palette circle — ported from InstallerX ColorSwatchPreview /
 * FullSwatchContent: primary / secondary / tertiary arcs + center primary dot.
 */
@Composable
private fun ColorSwatchPreview(
    option: SeedColorOption,
    currentStyle: AppPaletteStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val style = mapPaletteStyle(currentStyle)
    val cacheKey = remember(option.color, style) {
        "${option.color.toArgb()}_${style.name}_false"
    }

    val scheme by produceState<ColorScheme?>(
        initialValue = colorSchemeCache[cacheKey],
        key1 = cacheKey,
    ) {
        val cached = colorSchemeCache[cacheKey]
        if (cached != null) {
            value = cached
        } else {
            withContext(Dispatchers.Default) {
                val newScheme = dynamicColorScheme(
                    seedColor = option.color,
                    isDark = false,
                    style = style,
                    contrastLevel = 0.0,
                )
                colorSchemeCache[cacheKey] = newScheme
                value = newScheme
            }
        }
    }

    val itemShape = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .roundedClickable(itemShape, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        val current = scheme
        if (current != null) {
            FullSwatchContent(current, isSelected)
        } else {
            FallbackSwatchContent(option.color, isSelected)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FullSwatchContent(scheme: ColorScheme, isSelected: Boolean) {
    val primaryForSwatch = remember(scheme) { scheme.primaryContainer.copy(alpha = 0.9f) }
    val secondaryForSwatch = remember(scheme) { scheme.secondaryContainer.copy(alpha = 0.6f) }
    val tertiaryForSwatch = remember(scheme) { scheme.tertiaryContainer.copy(alpha = 0.9f) }
    val squircleBg = remember(scheme) { scheme.primary.copy(alpha = 0.3f) }

    Box(
        modifier = Modifier
            .size(64.dp)
            .background(color = squircleBg, shape = RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(color = primaryForSwatch, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = tertiaryForSwatch, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(color = secondaryForSwatch, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = scheme.inversePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackSwatchContent(baseColor: Color, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(color = baseColor.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(baseColor.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(baseColor),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteStyleGrid() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppPaletteStyle.entries.forEach { style ->
            val selected = ThemePrefs.paletteStyle == style
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape,
                    )
                    .roundedClickable(shape) { ThemePrefs.updatePaletteStyle(style) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    style.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContrastRow() {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppContrastLevel.entries.forEach { level ->
            val selected = ThemePrefs.contrastLevel == level
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape,
                    )
                    .roundedClickable(shape) { ThemePrefs.updateContrastLevel(level) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (level == AppContrastLevel.High) {
                    Icon(
                        Icons.Filled.Contrast,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    level.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
