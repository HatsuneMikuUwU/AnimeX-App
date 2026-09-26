package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private data class ModeOption(val mode: ColorSchemeMode, val title: String, val summary: String)

private val MODE_OPTIONS = listOf(
    ModeOption(ColorSchemeMode.System, "Ikuti sistem", "Sesuaikan dengan tema perangkat"),
    ModeOption(ColorSchemeMode.Light, "Terang", "Selalu gunakan tema terang"),
    ModeOption(ColorSchemeMode.Dark, "Gelap", "Selalu gunakan tema gelap"),
    ModeOption(ColorSchemeMode.MonetSystem, "Monet (sistem)", "Warna dinamis dari accent + sistem"),
    ModeOption(ColorSchemeMode.MonetLight, "Monet terang", "Warna dinamis dari accent (terang)"),
    ModeOption(ColorSchemeMode.MonetDark, "Monet gelap", "Warna dinamis dari accent (gelap)"),
)

private data class PaletteOption(val style: ThemePaletteStyle, val title: String)

private val PALETTE_OPTIONS = listOf(
    PaletteOption(ThemePaletteStyle.TonalSpot, "Tonal Spot"),
    PaletteOption(ThemePaletteStyle.Neutral, "Neutral"),
    PaletteOption(ThemePaletteStyle.Vibrant, "Vibrant"),
    PaletteOption(ThemePaletteStyle.Expressive, "Expressive"),
    PaletteOption(ThemePaletteStyle.Rainbow, "Rainbow"),
    PaletteOption(ThemePaletteStyle.FruitSalad, "Fruit Salad"),
    PaletteOption(ThemePaletteStyle.Monochrome, "Monochrome"),
    PaletteOption(ThemePaletteStyle.Fidelity, "Fidelity"),
    PaletteOption(ThemePaletteStyle.Content, "Content"),
)

@Composable
fun SettingsScreen() {
    val settings = LocalThemeSettings.current
    var mode by settings.mode
    var keyColor by settings.keyColor
    var paletteStyle by settings.paletteStyle

    val isMonet = mode == ColorSchemeMode.MonetSystem ||
        mode == ColorSchemeMode.MonetLight ||
        mode == ColorSchemeMode.MonetDark

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Setelan",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )

        // —— Tema ——
        SmallTitle(text = "Tampilan")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            MODE_OPTIONS.forEachIndexed { index, opt ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRadioRow(
                    title = opt.title,
                    summary = opt.summary,
                    selected = mode == opt.mode,
                    onClick = {
                        settings.setMode(opt.mode)
                        mode = opt.mode
                    },
                )
            }
        }

        // —— Warna accent (Monet) ——
        if (isMonet) {
            Spacer(Modifier.height(8.dp))
            SmallTitle(text = "Warna aksen")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Pilih warna seed untuk skema Monet",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentPresets.forEach { color ->
                        val selected = keyColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) Modifier.border(3.dp, MiuixTheme.colorScheme.primary, CircleShape)
                                    else Modifier.border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                                )
                                .clickable {
                                    settings.setKeyColor(color)
                                    keyColor = color
                                },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            SmallTitle(text = "Gaya palet")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                PALETTE_OPTIONS.forEachIndexed { index, opt ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRadioRow(
                        title = opt.title,
                        summary = null,
                        selected = paletteStyle == opt.style,
                        onClick = {
                            settings.setPaletteStyle(opt.style)
                            paletteStyle = opt.style
                        },
                    )
                }
            }
        }

        // —— Tentang ——
        Spacer(Modifier.height(8.dp))
        SmallTitle(text = "Tentang")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("AnimeX", style = MiuixTheme.textStyles.headline1)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Klien ANIMEIN v5 · UI Miuix (HyperOS)",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Versi 1.1.0-miuix",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun SettingsRadioRow(
    title: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // Simple selected indicator (dot)
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
    }
}
