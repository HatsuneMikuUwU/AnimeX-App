@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.AppContrastLevel
import com.uwu.animex.data.AppPaletteStyle
import com.uwu.animex.data.AppThemeMode
import com.uwu.animex.data.PresetSeedColors
import com.uwu.animex.data.ThemePrefs

/**
 * "Tampilan" (Appearance) screen — the new settings screen requested by the
 * user, hosting the UI customisation ported from InstallerX-Revived's
 * ThemeSettingsPage.kt: theme mode, Material You dynamic color, seed/accent
 * color, palette style and contrast level, plus an AMOLED pure-black switch.
 * Rebuilt with AnimeX's own plain Material3 widgets instead of InstallerX's
 * Miuix/blur-backdrop widget library, which has no equivalent here.
 */
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val canUseDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isDarkActive = when (ThemePrefs.themeMode) {
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
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
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

                        AnimatedVisibility(
                            visible = !(ThemePrefs.useDynamicColor && canUseDynamicColor),
                            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                HorizontalDivider()
                                Text(
                                    "Warna aksen",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SeedColorGrid()
                            }
                        }
                    }
                }
            }

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

@Composable
private fun ThemeModeRow() {
    data class Opt(val mode: AppThemeMode, val label: String, val icon: ImageVector)

    val options = listOf(
        Opt(AppThemeMode.SYSTEM, "Sistem", Icons.Filled.PhoneAndroid),
        Opt(AppThemeMode.LIGHT, "Terang", Icons.Filled.LightMode),
        Opt(AppThemeMode.DARK, "Gelap", Icons.Filled.DarkMode),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            val selected = ThemePrefs.themeMode == opt.mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { ThemePrefs.updateThemeMode(opt.mode) }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
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

@Composable
private fun SeedColorGrid() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PresetSeedColors.forEach { option ->
            val selected = ThemePrefs.seedColorKey == option.key
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(option.color)
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { ThemePrefs.updateSeedColorKey(option.key) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = option.label,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp),
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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { ThemePrefs.updatePaletteStyle(style) }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppContrastLevel.entries.forEach { level ->
            val selected = ThemePrefs.contrastLevel == level
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { ThemePrefs.updateContrastLevel(level) }
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
