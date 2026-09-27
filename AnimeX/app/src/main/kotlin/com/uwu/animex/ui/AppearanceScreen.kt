package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** Base appearance without Monet flag. */
private enum class BaseMode { System, Light, Dark }

private fun ColorSchemeMode.toBase(): BaseMode = when (this) {
    ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> BaseMode.Light
    ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> BaseMode.Dark
    else -> BaseMode.System
}

private fun ColorSchemeMode.isMonet(): Boolean = when (this) {
    ColorSchemeMode.MonetSystem, ColorSchemeMode.MonetLight, ColorSchemeMode.MonetDark -> true
    else -> false
}

private fun resolveMode(monet: Boolean, base: BaseMode): ColorSchemeMode = when {
    monet && base == BaseMode.Light -> ColorSchemeMode.MonetLight
    monet && base == BaseMode.Dark -> ColorSchemeMode.MonetDark
    monet -> ColorSchemeMode.MonetSystem
    base == BaseMode.Light -> ColorSchemeMode.Light
    base == BaseMode.Dark -> ColorSchemeMode.Dark
    else -> ColorSchemeMode.System
}

private fun BaseMode.label(): String = when (this) {
    BaseMode.System -> "Ikuti sistem"
    BaseMode.Light -> "Terang"
    BaseMode.Dark -> "Gelap"
}

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val settings = LocalThemeSettings.current
    var mode by settings.mode
    var keyColor by settings.keyColor
    var useCustom by settings.useCustomColor

    val monet = mode.isMonet()
    val base = mode.toBase()

    var showModeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Tampilan",
                largeTitle = "Tampilan",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SmallTitle(text = "Tema")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                // Monet = warna dari wallpaper sistem (bukan accent kustom)
                SwitchPreference(
                    checked = monet && !useCustom,
                    onCheckedChange = { on ->
                        if (on) {
                            settings.setUseCustomColor(false)
                            useCustom = false
                            val next = resolveMode(true, base)
                            settings.setMode(next)
                            mode = next
                        } else if (!useCustom) {
                            val next = resolveMode(false, base)
                            settings.setMode(next)
                            mode = next
                        }
                    },
                    title = "Tema dinamis",
                    summary = "Warna dari wallpaper sistem (Monet)",
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "Mode gelap",
                    summary = base.label(),
                    onClick = { showModeDialog = true },
                )
            }

            Spacer(Modifier.height(8.dp))
            SmallTitle(text = "Warna kustom")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                // Terpisah dari wallpaper Monet: seed color sendiri
                SwitchPreference(
                    checked = useCustom && monet,
                    onCheckedChange = { on ->
                        if (on) {
                            settings.setUseCustomColor(true)
                            useCustom = true
                            val next = resolveMode(true, base)
                            settings.setMode(next)
                            mode = next
                        } else {
                            settings.setUseCustomColor(false)
                            useCustom = false
                            // Kembali ke static (bukan wallpaper) kecuali user nyalakan tema dinamis lagi
                            val next = resolveMode(false, base)
                            settings.setMode(next)
                            mode = next
                        }
                    },
                    title = "Warna kustom",
                    summary = "Skema warna dari aksen yang dipilih",
                )
                if (useCustom && monet) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Pilih warna",
                        summary = "Ketuk untuk ganti aksen",
                        onClick = { showColorDialog = true },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccentPresets.forEach { color ->
                            val selected = keyColor == color
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (selected) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        settings.setKeyColor(color)
                                        keyColor = color
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModeDialog) {
        WindowDialog(
            show = true,
            title = "Mode gelap",
            summary = "Pilih mode tampilan",
            onDismissRequest = { showModeDialog = false },
        ) {
            Column {
                listOf(BaseMode.System, BaseMode.Light, BaseMode.Dark).forEach { option ->
                    val selected = base == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = resolveMode(monet || useCustom, option)
                                settings.setMode(next)
                                mode = next
                                showModeDialog = false
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label(),
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(text = "✓", color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "Batal", onClick = { showModeDialog = false })
                }
            }
        }
    }

    if (showColorDialog) {
        WindowDialog(
            show = true,
            title = "Warna kustom",
            summary = "Pilih aksen untuk skema kustom",
            onDismissRequest = { showColorDialog = false },
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AccentPresets.forEach { color ->
                        val selected = keyColor == color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) Modifier.border(3.dp, MiuixTheme.colorScheme.primary, CircleShape)
                                    else Modifier.border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                                )
                                .clickable {
                                    settings.setKeyColor(color)
                                    keyColor = color
                                    showColorDialog = false
                                },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "Tutup", onClick = { showColorDialog = false })
                }
            }
        }
    }
}
