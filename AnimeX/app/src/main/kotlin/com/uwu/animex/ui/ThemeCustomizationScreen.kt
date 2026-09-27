@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.uwu.animex.data.ThemeMode
import com.uwu.animex.data.ThemePrefs

private val SeedPresets = listOf(
    Color(0xFFB94A1F), // Oranye AnimeX (bawaan)
    Color(0xFFE53935), // Merah
    Color(0xFFD81B60), // Pink
    Color(0xFF8E24AA), // Ungu
    Color(0xFF5E35B1), // Ungu tua
    Color(0xFF3949AB), // Indigo
    Color(0xFF1E88E5), // Biru
    Color(0xFF00897B), // Teal
    Color(0xFF43A047), // Hijau
    Color(0xFFF9A825), // Kuning keemasan
    Color(0xFF6D4C41), // Coklat
    Color(0xFF546E7A), // Biru abu-abu
)

private fun PaletteStyle.label(): String = when (this) {
    PaletteStyle.TonalSpot -> "Tenang"
    PaletteStyle.Neutral -> "Netral"
    PaletteStyle.Vibrant -> "Cerah"
    PaletteStyle.Expressive -> "Ekspresif"
    PaletteStyle.Rainbow -> "Pelangi"
    PaletteStyle.FruitSalad -> "Segar"
    PaletteStyle.Monochrome -> "Monokrom"
    PaletteStyle.Fidelity -> "Fidelity"
    PaletteStyle.Content -> "Konten"
}

private fun PaletteStyle.description(): String = when (this) {
    PaletteStyle.TonalSpot -> "Warna netral & tenang, cocok dipakai sehari-hari"
    PaletteStyle.Neutral -> "Sedikit lebih berwarna dibanding monokrom"
    PaletteStyle.Vibrant -> "Warna mencolok dengan saturasi maksimal"
    PaletteStyle.Expressive -> "Kombinasi warna yang playful & berani"
    PaletteStyle.Rainbow -> "Variasi warna penuh gaya"
    PaletteStyle.FruitSalad -> "Kombinasi warna segar & ceria"
    PaletteStyle.Monochrome -> "Hitam, putih, dan abu-abu murni"
    PaletteStyle.Fidelity -> "Mengikuti warna sumber secara presisi"
    PaletteStyle.Content -> "Menyesuaikan warna dari konten sumber"
}

private fun contrastLabel(v: Float): String = when {
    v <= -0.34f -> "Rendah"
    v >= 0.67f -> "Tinggi"
    v >= 0.34f -> "Sedang"
    else -> "Standar"
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color = Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))

private fun Color.toHex(): String {
    val argb = this.toArgbInt()
    return "#%06X".format(argb and 0xFFFFFF)
}

private fun Color.toArgbInt(): Int = AndroidColor.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

@Composable
fun ThemeCustomizationScreen(onBack: () -> Unit) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    val dynamicOn = ThemePrefs.useDynamicColor && ThemePrefs.isDynamicSupported()
    val seedSectionAlpha by animateFloatAsState(
        targetValue = if (dynamicOn) 0.4f else 1f,
        animationSpec = tween(300),
        label = "seedSectionAlpha",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kustomisasi Tampilan", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset ke bawaan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ThemePreviewCard() }

            item {
                SettingSection(
                    title = "Mode Tampilan",
                    subtitle = "Pilih mode terang, gelap, atau ikuti sistem",
                    icon = Icons.Filled.BrightnessAuto,
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val options = listOf(
                            Triple(ThemeMode.SYSTEM, "Sistem", Icons.Filled.BrightnessAuto),
                            Triple(ThemeMode.LIGHT, "Terang", Icons.Filled.LightMode),
                            Triple(ThemeMode.DARK, "Gelap", Icons.Filled.DarkMode),
                        )
                        options.forEachIndexed { i, (mode, label, icon) ->
                            SegmentedButton(
                                selected = ThemePrefs.themeMode == mode,
                                onClick = { ThemePrefs.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            ) { Text(label) }
                        }
                    }
                }
            }

            item {
                SettingSection(
                    title = "Warna Dinamis (Material You)",
                    subtitle = if (ThemePrefs.isDynamicSupported()) {
                        "Ambil palet warna otomatis dari wallpaper perangkat"
                    } else {
                        "Membutuhkan Android 12 ke atas — pakai warna kustom di bawah"
                    },
                    icon = Icons.Filled.Wallpaper,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Wallpaper, active = dynamicOn)
                            Spacer(Modifier.width(12.dp))
                            Text("Aktifkan warna dinamis", style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(
                            checked = dynamicOn,
                            enabled = ThemePrefs.isDynamicSupported(),
                            onCheckedChange = { ThemePrefs.setDynamicColor(it) },
                        )
                    }
                }
            }

            item {
                SettingSection(
                    title = "Warna Kustom (Seed Color)",
                    subtitle = "Pilih salah satu warna, atau buat warnamu sendiri",
                    icon = Icons.Filled.Palette,
                    modifier = Modifier.alpha(seedSectionAlpha),
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .shadow(3.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable(enabled = !dynamicOn) { showColorPicker = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Warna kustom", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(SeedPresets) { preset ->
                            SeedSwatch(
                                color = preset,
                                selected = !dynamicOn && preset.toArgbInt() == ThemePrefs.seedColor,
                                enabled = !dynamicOn,
                                onClick = { ThemePrefs.setSeedColor(preset.toArgbInt()) },
                            )
                        }
                    }
                }
            }

            item {
                SettingSection(
                    title = "Gaya Palet",
                    subtitle = ThemePrefs.paletteStyle.description(),
                    icon = Icons.Filled.Gradient,
                    modifier = Modifier.alpha(seedSectionAlpha),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaletteStyle.entries.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { style ->
                                    val selected = ThemePrefs.paletteStyle == style
                                    FilterChip(
                                        selected = selected,
                                        enabled = !dynamicOn,
                                        onClick = { ThemePrefs.setPaletteStyle(style) },
                                        label = { Text(style.label()) },
                                        leadingIcon = if (selected) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = !dynamicOn,
                                            selected = selected,
                                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                                            selectedBorderWidth = 1.5.dp,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            item {
                SettingSection(
                    title = "Kontras",
                    subtitle = "Atur seberapa kuat perbedaan warna teks & latar (${contrastLabel(ThemePrefs.contrastLevel)})",
                    icon = Icons.Filled.Tonality,
                    modifier = Modifier.alpha(seedSectionAlpha),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Tonality, active = true)
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = ThemePrefs.contrastLevel,
                            onValueChange = { ThemePrefs.setContrastLevel(it) },
                            enabled = !dynamicOn,
                            valueRange = -1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Rendah", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Standar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tinggi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SettingSection(
                    title = "Mode AMOLED",
                    subtitle = "Latar & permukaan jadi hitam pekat saat mode gelap aktif — hemat baterai di layar AMOLED",
                    icon = Icons.Filled.DarkMode,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.DarkMode, active = ThemePrefs.amoledMode)
                            Spacer(Modifier.width(12.dp))
                            Text("Hitam pekat (True Black)", style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(checked = ThemePrefs.amoledMode, onCheckedChange = { ThemePrefs.setAmoledMode(it) })
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
        }
    }

    if (showColorPicker) {
        SeedColorPickerDialog(
            initial = Color(ThemePrefs.seedColor),
            onDismiss = { showColorPicker = false },
            onConfirm = {
                ThemePrefs.setSeedColor(it.toArgbInt())
                showColorPicker = false
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Tampilan?") },
            text = { Text("Semua pengaturan tema akan dikembalikan ke bawaan.") },
            confirmButton = {
                TextButton(onClick = {
                    ThemePrefs.resetToDefault()
                    showResetDialog = false
                }) { Text("Reset", color = AppColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Batal") }
            },
        )
    }
}

/** Lingkaran ikon kecil yang berpendar lembut saat statusnya aktif. */
@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean) {
    val bg by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(250),
        label = "iconBadgeBg",
    )
    val tint by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "iconBadgeTint",
    )
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Swatch warna bulat dengan animasi scale & glow saat terpilih. */
@Composable
private fun SeedSwatch(color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "seedSwatchScale",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = tween(200),
        label = "seedSwatchRing",
    )
    Box(
        Modifier
            .size(46.dp)
            .scale(scale)
            .shadow(if (selected) 6.dp else 1.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .border(width = ringWidth, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Terpilih",
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (icon != null) 30.dp else 0.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ThemePreviewCard() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val heroBrush = remember(primary, secondary, tertiary) {
        Brush.linearGradient(listOf(primary, secondary, tertiary))
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column {
            // Hero band — menampilkan palet warna aktif secara langsung sebagai gradasi.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(heroBrush),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Tema Aktif",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            "AnimeX",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            Column(Modifier.padding(20.dp)) {
                Text(
                    "Perubahan di bawah langsung diterapkan ke seluruh aplikasi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PreviewDot(MaterialTheme.colorScheme.primary, "Primer")
                    PreviewDot(MaterialTheme.colorScheme.secondary, "Sekunder")
                    PreviewDot(MaterialTheme.colorScheme.tertiary, "Tersier")
                    PreviewDot(MaterialTheme.colorScheme.error, "Error")
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {}) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tombol")
                    }
                    OutlinedButton(onClick = {}) { Text("Outline") }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    var chipSelected by remember { mutableStateOf(true) }
                    FilterChip(selected = chipSelected, onClick = { chipSelected = !chipSelected }, label = { Text("Chip") })
                    var switchOn by remember { mutableStateOf(true) }
                    Switch(checked = switchOn, onCheckedChange = { switchOn = it })
                }
            }
        }
    }
}

@Composable
private fun PreviewDot(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(40.dp)
                .shadow(4.dp, CircleShape, spotColor = color)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SeedColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val initialHsv = remember {
        val out = FloatArray(3)
        AndroidColor.colorToHSV(initial.toArgbInt(), out)
        out
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember { mutableStateOf(hsvToColor(initialHsv[0], initialHsv[1], initialHsv[2]).toHex()) }

    val currentColor = hsvToColor(hue, sat, value)

    fun updateFromSlider() {
        hexText = hsvToColor(hue, sat, value).toHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warna Kustom") },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = currentColor)
                        .clip(RoundedCornerShape(16.dp))
                        .background(currentColor),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { txt ->
                        hexText = txt
                        val cleaned = txt.removePrefix("#")
                        if (cleaned.length == 6) {
                            runCatching {
                                val argb = AndroidColor.parseColor("#$cleaned")
                                val out = FloatArray(3)
                                AndroidColor.colorToHSV(argb, out)
                                hue = out[0]; sat = out[1]; value = out[2]
                            }
                        }
                    },
                    label = { Text("Kode Hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Warna (Hue)", style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it; updateFromSlider() }, valueRange = 0f..360f)
                Text("Saturasi", style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it; updateFromSlider() }, valueRange = 0f..1f)
                Text("Kecerahan", style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it; updateFromSlider() }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
