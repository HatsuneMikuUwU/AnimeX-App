@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val controller = LocalThemeController.current
    val settings = controller.settings
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Tampilan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Sesuaikan tampilan AnimeX", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = controller::reset) {
                Icon(Icons.Filled.RestartAlt, "Reset tampilan")
            }
        }

        androidx.compose.foundation.lazy.LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp, top = 10.dp),
        ) {
            item {
                SettingsCard(Icons.Filled.SettingsBrightness, "Mode tampilan", "Pilih kapan tema terang atau gelap digunakan") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeModeChip("Sistem", ThemeMode.SYSTEM, Icons.Filled.SettingsBrightness, settings.mode) {
                            controller.update { it.copy(mode = ThemeMode.SYSTEM) }
                        }
                        ThemeModeChip("Terang", ThemeMode.LIGHT, Icons.Filled.WbSunny, settings.mode) {
                            controller.update { it.copy(mode = ThemeMode.LIGHT) }
                        }
                        ThemeModeChip("Gelap", ThemeMode.DARK, Icons.Filled.DarkMode, settings.mode) {
                            controller.update { it.copy(mode = ThemeMode.DARK) }
                        }
                    }
                }
            }

            item {
                SettingsCard(Icons.Filled.Palette, "Warna sistem", "Gunakan warna wallpaper Android atau warna AnimeX") {
                    SettingSwitchRow(
                        title = "Dynamic Color",
                        subtitle = if (dynamicAvailable) "Mengikuti warna wallpaper perangkat" else "Membutuhkan Android 12 atau lebih baru",
                        checked = settings.dynamicColor && dynamicAvailable,
                        enabled = dynamicAvailable,
                        onCheckedChange = { checked -> controller.update { it.copy(dynamicColor = checked) } },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SettingSwitchRow(
                        title = "True Black",
                        subtitle = "Gunakan #000000 untuk permukaan utama pada mode gelap",
                        checked = settings.trueBlack,
                        enabled = settings.mode != ThemeMode.LIGHT,
                        onCheckedChange = { checked -> controller.update { it.copy(trueBlack = checked) } },
                    )
                }
            }

            item {
                SettingsCard(Icons.Filled.FormatPaint, "Accent & custom theme", "19 warna Material + custom color untuk komponen Material 3") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            AccentSwatch(
                                preset = preset,
                                selected = !settings.useCustomAccent && settings.preset == preset,
                                onClick = { controller.update { it.copy(preset = preset, useCustomAccent = false, dynamicColor = false) } },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = { controller.update { it.copy(useCustomAccent = true, dynamicColor = false) } },
                        label = { Text(if (settings.useCustomAccent) "Custom accent aktif" else "Pakai custom accent") },
                        leadingIcon = { Icon(Icons.Filled.Palette, null) },
                    )

                    if (settings.useCustomAccent) {
                        Spacer(Modifier.height(12.dp))
                        Text("Hue (0–360°)", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = settings.customHue,
                            onValueChange = { value -> controller.update { it.copy(customHue = value) } },
                            valueRange = 0f..360f,
                        )
                        Text("Saturation (0–100%)", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = settings.customSaturation,
                            onValueChange = { value -> controller.update { it.copy(customSaturation = value) } },
                            valueRange = 0f..1f,
                        )
                        Text("Brightness (25–100%)", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = settings.customBrightness,
                            onValueChange = { value -> controller.update { it.copy(customBrightness = value) } },
                            valueRange = 0.25f..1f,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(42.dp).clip(MaterialTheme.shapes.medium),
                            color = Color.hsv(settings.customHue, settings.customSaturation, settings.customBrightness),
                        ) {}
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Material UI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pengaturan ini diterapkan ke seluruh komponen Material 3 dan tersimpan otomatis di perangkat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    mode: ThemeMode,
    icon: ImageVector,
    selectedMode: ThemeMode,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selectedMode == mode,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        trailingIcon = if (selectedMode == mode) ({ Icon(Icons.Filled.Check, null) }) else null,
    )
}

@Composable
private fun AccentSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape),
            color = preset.color,
        ) {}
        Text(preset.label, Modifier.padding(start = 7.dp), maxLines = 1)
        if (selected) Icon(Icons.Filled.Check, null, Modifier.padding(start = 4.dp).size(16.dp))
    }
}
