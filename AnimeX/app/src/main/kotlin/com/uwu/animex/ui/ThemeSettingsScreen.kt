@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.twotone.Colorize
import androidx.compose.material.icons.twotone.InvertColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
 * Theme settings page — structure and widgets aligned with InstallerX-Revived
 * ThemeSettingsPage: LargeTopAppBar, SegmentedColumn groups, BaseWidget rows,
 * dialogs for mode/palette, ColorSwatchPreview grid, SwitchWidgets.
 */
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val canUseDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicOn = ThemePrefs.useDynamicColor && canUseDynamicColor
    val isDarkActive = when (ThemePrefs.themeMode) {
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showContrastDialog by remember { mutableStateOf(false) }

    if (showThemeModeDialog) {
        ThemeModeDialog(
            current = ThemePrefs.themeMode,
            onDismiss = { showThemeModeDialog = false },
            onSelect = {
                ThemePrefs.updateThemeMode(it)
                showThemeModeDialog = false
            },
        )
    }
    if (showPaletteDialog) {
        PaletteStyleDialog(
            current = ThemePrefs.paletteStyle,
            onDismiss = { showPaletteDialog = false },
            onSelect = {
                ThemePrefs.updatePaletteStyle(it)
                showPaletteDialog = false
            },
        )
    }
    if (showContrastDialog) {
        ContrastDialog(
            current = ThemePrefs.contrastLevel,
            onDismiss = { showContrastDialog = false },
            onSelect = {
                ThemePrefs.updateContrastLevel(it)
                showContrastDialog = false
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text("Pengaturan Tema") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets(left = 4.dp),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            // --- Mode & style ---
            item {
                SegmentedColumn(title = "Tampilan") {
                    item {
                        BaseWidget(
                            icon = Icons.Default.DarkMode,
                            title = "Mode tema",
                            description = when (ThemePrefs.themeMode) {
                                AppThemeMode.LIGHT -> "Terang"
                                AppThemeMode.DARK -> "Gelap"
                                AppThemeMode.SYSTEM -> "Ikuti sistem"
                            },
                            onClick = { showThemeModeDialog = true },
                        )
                    }
                    item {
                        BaseWidget(
                            icon = Icons.Filled.Palette,
                            title = "Gaya palet",
                            description = ThemePrefs.paletteStyle.label,
                            onClick = { showPaletteDialog = true },
                        )
                    }
                    item {
                        BaseWidget(
                            icon = Icons.Filled.Contrast,
                            title = "Kontras",
                            description = ThemePrefs.contrastLevel.label,
                            onClick = { showContrastDialog = true },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = Icons.TwoTone.InvertColors,
                            title = "Warna dinamis",
                            description = if (canUseDynamicColor)
                                "Ambil warna dari wallpaper (Material You)"
                            else
                                "Butuh Android 12 ke atas",
                            checked = ThemePrefs.useDynamicColor && canUseDynamicColor,
                            enabled = canUseDynamicColor,
                            onCheckedChange = ThemePrefs::updateUseDynamicColor,
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = Icons.TwoTone.Colorize,
                            title = "Latar AMOLED",
                            description = if (isDarkActive)
                                "Hitam pekat saat mode gelap aktif"
                            else
                                "Aktif saat mode gelap sedang digunakan",
                            checked = ThemePrefs.amoledBlack,
                            onCheckedChange = ThemePrefs::updateAmoledBlack,
                        )
                    }
                }
            }

            // --- Theme color (manual seed) ---
            item {
                AnimatedVisibility(
                    visible = !dynamicOn,
                    enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        expandVertically(tween(400, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                        shrinkVertically(tween(350, easing = FastOutSlowInEasing)),
                ) {
                    SegmentedColumn(title = "Warna tema") {
                        item {
                            BaseItemContainer {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 16.dp),
                                ) {
                                    val itemMinWidth = 88.dp
                                    val columns = (maxWidth / itemMinWidth).toInt().coerceAtLeast(1)
                                    val chunked = PresetSeedColors.chunked(columns)

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                                            onClick = {
                                                                ThemePrefs.updateSeedColorKey(option.key)
                                                            },
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
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Dialogs (InstallerX style) ───────────────────────────────────────────────

@Composable
private fun ThemeModeDialog(
    current: AppThemeMode,
    onDismiss: () -> Unit,
    onSelect: (AppThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mode tema") },
        text = {
            Column {
                listOf(
                    AppThemeMode.SYSTEM to "Ikuti sistem",
                    AppThemeMode.LIGHT to "Terang",
                    AppThemeMode.DARK to "Gelap",
                ).forEach { (mode, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}

@Composable
private fun PaletteStyleDialog(
    current: AppPaletteStyle,
    onDismiss: () -> Unit,
    onSelect: (AppPaletteStyle) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gaya palet") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AppPaletteStyle.entries.forEach { style ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(style) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = style == current, onClick = { onSelect(style) })
                        Spacer(Modifier.width(8.dp))
                        Text(style.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}

@Composable
private fun ContrastDialog(
    current: AppContrastLevel,
    onDismiss: () -> Unit,
    onSelect: (AppContrastLevel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kontras") },
        text = {
            Column {
                AppContrastLevel.entries.forEach { level ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(level) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = level == current, onClick = { onSelect(level) })
                        Spacer(Modifier.width(8.dp))
                        Text(level.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}

// ── Color swatch (InstallerX ColorPalatteCard) ───────────────────────────────

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
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(itemShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        val current = scheme
        if (current != null) {
            FullSwatchContent(current, isSelected)
        } else {
            FallbackSwatchContent(option.color, isSelected)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
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
