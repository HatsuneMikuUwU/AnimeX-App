@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.AccentColor
import com.uwu.animex.data.ContrastLevel
import com.uwu.animex.data.SettingsPrefs
import com.uwu.animex.data.ShapeStyle
import com.uwu.animex.data.ThemeMode

@Composable
fun UiCustomisationScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Kustomisasi UI", fontWeight = FontWeight.SemiBold)
                },
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
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SectionHeader(Icons.Filled.LightMode, "Mode tema")
            }
            item {
                ThemeModeSegment()
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader(Icons.Filled.Palette, "Warna aksen")
            }
            item {
                AccentColorPicker()
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader(Icons.Filled.Style, "Bentuk")
            }
            item {
                ShapeStyleSegment()
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader(Icons.Filled.Contrast, "Kontras")
            }
            item {
                ContrastSegment()
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader(Icons.Filled.DarkMode, "Opsi tampilan")
            }
            item {
                SettingsGroup {
                    ToggleListItem(
                        title = "AMOLED hitam",
                        subtitle = "Latar pure black di mode gelap",
                        checked = SettingsPrefs.amoledBlack,
                        onCheckedChange = SettingsPrefs::setAmoledBlack,
                        enabled = SettingsPrefs.themeMode != ThemeMode.LIGHT,
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    ToggleListItem(
                        title = "Kurangi animasi",
                        subtitle = "Motion scheme standar Material",
                        checked = SettingsPrefs.reduceMotion,
                        onCheckedChange = SettingsPrefs::setReduceMotion,
                        icon = Icons.Filled.MotionPhotosOff,
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(Icons.Filled.RoundedCorner, "Pratinjau Material")
            }
            item {
                MaterialPreview()
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ThemeModeSegment() {
    val modes = listOf(
        ThemeMode.SYSTEM to "Sistem",
        ThemeMode.LIGHT to "Terang",
        ThemeMode.DARK to "Gelap",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = SettingsPrefs.themeMode == mode,
                onClick = { SettingsPrefs.setThemeMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                icon = {
                    Icon(
                        imageVector = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun AccentColorPicker() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                val selected = SettingsPrefs.accentColor == accent
                val seed = accent.palette().seed
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .semantics {
                            this.selected = selected
                            role = Role.RadioButton
                        }
                        .clickable { SettingsPrefs.setAccentColor(accent) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(seed)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = accent.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeStyleSegment() {
    val styles = listOf(
        ShapeStyle.ROUNDED to "Rounded",
        ShapeStyle.SOFT to "Soft",
        ShapeStyle.SHARP to "Sharp",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        styles.forEachIndexed { index, (style, label) ->
            SegmentedButton(
                selected = SettingsPrefs.shapeStyle == style,
                onClick = { SettingsPrefs.setShapeStyle(style) },
                shape = SegmentedButtonDefaults.itemShape(index, styles.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ContrastSegment() {
    val levels = listOf(
        ContrastLevel.STANDARD to "Standar",
        ContrastLevel.MEDIUM to "Sedang",
        ContrastLevel.HIGH to "Tinggi",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        levels.forEachIndexed { index, (level, label) ->
            SegmentedButton(
                selected = SettingsPrefs.contrastLevel == level,
                onClick = { SettingsPrefs.setContrastLevel(level) },
                shape = SegmentedButtonDefaults.itemShape(index, levels.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column { content() }
    }
}

@Composable
private fun ToggleListItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    ListItem(
        supportingContent = {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        },
        leadingContent = icon?.let {
            {
                Icon(
                    it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
}

@Composable
private fun MaterialPreview() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Material You Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Primary · ${SettingsPrefs.accentColor.label()} · ${SettingsPrefs.shapeStyle.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Primary" to MaterialTheme.colorScheme.primary,
                    "Container" to MaterialTheme.colorScheme.primaryContainer,
                    "Surface" to MaterialTheme.colorScheme.surfaceContainerHigh,
                    "Outline" to MaterialTheme.colorScheme.outline,
                ).forEach { (label, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(color)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    MaterialTheme.shapes.small,
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {}) {
                    Text("Tonal button")
                }
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("Chip") },
                    leadingIcon = {
                        Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                    },
                )
            }

            Text(
                "Teks body memakai onSurface. Varian sekunder memakai onSurfaceVariant untuk hierarki yang jelas sesuai Material Design 3.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Supporting text · contrast ${SettingsPrefs.contrastLevel.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
