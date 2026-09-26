package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.ThemeMode
import com.uwu.animex.data.ThemePrefs
import com.uwu.animex.data.ThemeStyle

@Composable
fun SettingsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = BottomNavClearance),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SettingsSection("Tema") {
            SegmentedRow(
                options = listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.LIGHT to "Terang", ThemeMode.DARK to "Gelap"),
                selected = ThemePrefs.mode,
                onSelect = ThemePrefs::setMode,
            )
        }

        SettingsSection("Gaya Warna") {
            val styleOptions = buildList {
                add(ThemeStyle.DEFAULT to "Bawaan")
                if (supportsDynamicColor) add(ThemeStyle.DYNAMIC to "Dinamis")
                add(ThemeStyle.MONOCHROME to "Monokrom")
            }
            SegmentedRow(options = styleOptions, selected = ThemePrefs.style, onSelect = ThemePrefs::setStyle)
        }

        if (ThemePrefs.mode != ThemeMode.LIGHT) {
            SettingsSection("Hitam Pekat") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Latar benar-benar hitam saat mode gelap", modifier = Modifier.weight(1f))
                    Switch(checked = ThemePrefs.pureBlack, onCheckedChange = ThemePrefs::setPureBlack)
                }
            }
        }

        SettingsSection("Warna Aksen") {
            if (ThemePrefs.style == ThemeStyle.DYNAMIC) {
                Text(
                    "Nonaktif karena warna diambil otomatis dari wallpaper (gaya Dinamis)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                AccentColorPicker()
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun <T> SegmentedRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f).height(44.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun AccentColorPicker() {
    val selected = ThemePrefs.accentHex?.toColorOrNull()
    var showCustomDialog by remember { mutableStateOf(false) }

    LazyRow(contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ColorSwatch(color = null, isSelected = selected == null) { ThemePrefs.setAccent(null) }
        }
        items(AccentPresets) { color ->
            ColorSwatch(color = color, isSelected = selected == color) { ThemePrefs.setAccent(color.toHexString()) }
        }
        item {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showCustomDialog = true },
                Alignment.Center,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Warna kustom", modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showCustomDialog) {
        var hexInput by remember { mutableStateOf(ThemePrefs.accentHex ?: "#B94A1F") }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Warna Aksen Kustom") },
            text = {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("Kode hex, mis. #B94A1F") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (hexInput.toColorOrNull() != null) {
                        ThemePrefs.setAccent(hexInput)
                        showCustomDialog = false
                    }
                }) { Text("Terapkan") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun ColorSwatch(color: Color?, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.primary)
            .border(if (isSelected) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = "Terpilih", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
