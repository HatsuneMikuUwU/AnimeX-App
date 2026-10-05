@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.AccentPalette
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.AppearanceSettings
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.ui.common.ExpressiveToggleChip
import com.uwu.animex.ui.common.RotatingCookieFrame
import com.uwu.animex.ui.theme.DynamicColorSupported
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.theme.staticColorScheme

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val dark = rememberAppDarkTheme(settings.mode)
    val dynamicActive = settings.dynamicColor && DynamicColorSupported

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                expandedHeight = 160.dp,
                title = { Text("Kustomisasi UI", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = cs.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                AppearanceHero()
                Spacer(Modifier.height(28.dp))

                SectionTitle("Tema")
                ModeCard(selected = settings.mode, onSelect = Appearance::setMode)
                Spacer(Modifier.height(28.dp))

                SectionTitle("Warna")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToggleRow(
                        index = 0,
                        count = 2,
                        icon = Icons.Filled.Wallpaper,
                        title = "Warna dinamis",
                        subtitle = if (DynamicColorSupported) {
                            "Warna ngikutin wallpaper kamu (Material You)"
                        } else {
                            "Butuh Android 12 ke atas"
                        },
                        checked = dynamicActive,
                        enabled = DynamicColorSupported,
                        onChange = Appearance::setDynamicColor,
                    )
                    AccentCard(
                        selected = settings.accent,
                        dark = dark,
                        enabled = !dynamicActive,
                        onSelect = Appearance::setAccent,
                    )
                }
                Spacer(Modifier.height(28.dp))

                SectionTitle("Layar")
                ToggleRow(
                    index = 0,
                    count = 1,
                    icon = Icons.Filled.Contrast,
                    title = "Hitam pekat (AMOLED)",
                    subtitle = "Latar jadi hitam total di mode gelap, lebih hemat baterai di layar OLED",
                    checked = settings.amoled,
                    enabled = true,
                    onChange = Appearance::setAmoled,
                )
                Spacer(Modifier.height(28.dp))

                FilledTonalButton(
                    onClick = Appearance::reset,
                    enabled = settings != AppearanceSettings(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Kembalikan ke bawaan", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AppearanceHero() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RotatingCookieFrame(frameSize = 136.dp, innerSize = 88.dp) {
            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = cs.onPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Bikin AnimeX sesuai seleramu",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Perubahan langsung kepakai di semua layar",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = cs.onSecondaryContainer)
    }
}

@Composable
private fun ModeCard(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.BrightnessMedium)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Mode tampilan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Ikut sistem, atau paksa terang/gelap",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ExpressiveToggleChip(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        label = mode.label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = { onChange(!checked) },
        enabled = enabled,
        shape = groupedShape(index, count),
        color = cs.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchIconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

private val SwitchIconSize = 16.dp

@Composable
private fun AccentCard(
    selected: AccentPalette,
    dark: Boolean,
    enabled: Boolean,
    onSelect: (AccentPalette) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = groupedShape(1, 2), color = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.ColorLens)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Warna aksen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (enabled) "Pilih warna utama aplikasi" else "Matikan warna dinamis buat milih aksen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentPalette.entries.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == selected,
                        dark = dark,
                        enabled = enabled,
                        onClick = { onSelect(accent) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    accent: AccentPalette,
    selected: Boolean,
    dark: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = remember(accent, dark) { staticColorScheme(accent, dark) }
    val shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(shape).background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary)
            } else {
                Box(Modifier.size(20.dp).clip(CircleShape).background(scheme.primaryContainer))
            }
        }
        Text(
            accent.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
