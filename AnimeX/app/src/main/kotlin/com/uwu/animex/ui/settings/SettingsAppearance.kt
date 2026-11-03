@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.BarEffect
import com.uwu.animex.data.local.PaletteStyle
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.data.local.contrastLabel
import com.uwu.animex.data.local.seedColor
import com.uwu.animex.ui.background.BackgroundType
import com.uwu.animex.ui.background.RandomInterval
import com.uwu.animex.ui.background.SeasonalEvent
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.rememberStableHapticAction
import com.uwu.animex.ui.theme.DynamicColorSupported
import com.uwu.animex.ui.theme.FONT_SCALE_MAX
import com.uwu.animex.ui.theme.FONT_SCALE_MIN
import com.uwu.animex.ui.theme.FONT_SCALE_STEP
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_MAX
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_MIN
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_STEP
import com.uwu.animex.ui.theme.UI_SCALE_MAX
import com.uwu.animex.ui.theme.UI_SCALE_MIN
import com.uwu.animex.ui.theme.UI_SCALE_STEP
import com.uwu.animex.ui.theme.coerceToFontScale
import com.uwu.animex.ui.theme.coerceToHomeCardScale
import com.uwu.animex.ui.theme.coerceToUiScale
import com.uwu.animex.ui.theme.effectiveSpec
import com.uwu.animex.ui.theme.toHexString
import com.uwu.animex.ui.theme.toUiScalePercent

@Composable
internal fun UiScaleGroup() {
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    var showDensityDialog by rememberSaveable { mutableStateOf(false) }
    var showFontDialog by rememberSaveable { mutableStateOf(false) }

    SettingGroup(
        icon = Icons.Outlined.ZoomIn,
        title = "Skala UI",
        initiallyExpanded = false,
    ) {
        SwitchItem(
            icon = Icons.Outlined.AspectRatio,
            title = "Skala otomatis",
            subtitle = "Sesuaikan ukuran tampilan dengan lebar layar perangkat",
            shape = itemShape(0, 3),
            checked = settings.autoScaleUi,
            enabled = true,
            onChange = Appearance::setAutoScaleUi,
        )
        PrefItem(
            icon = Icons.Outlined.ZoomIn,
            title = "Skala tampilan",
            subtitle =
                if (settings.autoScaleUi) {
                    "Dimatikan saat skala otomatis aktif"
                } else {
                    "${settings.uiScale.toUiScalePercent()}% dari ukuran bawaan sistem"
                },
            shape = itemShape(1, 3),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !settings.autoScaleUi,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showDensityDialog = true },
        )
        PrefItem(
            icon = Icons.Outlined.FormatSize,
            title = "Ukuran font",
            subtitle =
                if (settings.autoScaleUi) {
                    "Dimatikan saat skala otomatis aktif"
                } else {
                    "${settings.fontScale.toUiScalePercent()}% dari ukuran bawaan"
                },
            shape = itemShape(2, 3),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !settings.autoScaleUi,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showFontDialog = true },
        )
    }

    if (showDensityDialog) {
        LiveScaleDialog(
            icon = Icons.Outlined.ZoomIn,
            title = "Skala tampilan",
            description =
                "Menskala seluruh tampilan aplikasi (elemen dan teks), di atas zoom layar sistem. " +
                    "Diterapkan setelah menekan Terapkan.",
            currentScale = settings.uiScale,
            min = UI_SCALE_MIN,
            max = UI_SCALE_MAX,
            step = UI_SCALE_STEP,
            coerce = { it.coerceToUiScale() },
            onApply = Appearance::setUiScale,
            live = false,
            onDismiss = { showDensityDialog = false },
        )
    }

    if (showFontDialog) {
        LiveScaleDialog(
            icon = Icons.Outlined.FormatSize,
            title = "Ukuran font",
            description =
                "Mengatur ukuran teks saja, di atas skala tampilan dan ukuran font sistem. " +
                    "Diterapkan setelah menekan Terapkan.",
            currentScale = settings.fontScale,
            min = FONT_SCALE_MIN,
            max = FONT_SCALE_MAX,
            step = FONT_SCALE_STEP,
            coerce = { it.coerceToFontScale() },
            onApply = Appearance::setFontScale,
            live = false,
            onDismiss = { showFontDialog = false },
        )
    }
}

@Composable
internal fun CustomizationGroup() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val dynamicActive = settings.dynamicColor && DynamicColorSupported
    val runningSpec = effectiveSpec(settings.paletteStyle, settings.colorSpec)
    val offeredSpecs = settings.paletteStyle.offeredSpecs
    var showAccentSheet by rememberSaveable { mutableStateOf(false) }
    var showStyleDialog by rememberSaveable { mutableStateOf(false) }
    var showSpecDialog by rememberSaveable { mutableStateOf(false) }
    var showCardScaleDialog by rememberSaveable { mutableStateOf(false) }
    var showBarEffectDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomColorSheet by rememberSaveable { mutableStateOf(false) }
    var showContrastDialog by rememberSaveable { mutableStateOf(false) }
    var showRoleSheet by rememberSaveable { mutableStateOf(false) }
    var showColorAnimDialog by rememberSaveable { mutableStateOf(false) }
    val animExtra = if (settings.animateColorChange) 1 else 0
    val itemCount = 16 + animExtra
    val barEffectEnabled = settings.blur && BlurSupported

    SettingGroup(
        icon = Icons.Outlined.DesignServices,
        title = "Kustomisasi",
        initiallyExpanded = false,
    ) {
        PrefItem(
            icon = Icons.Outlined.Palette,
            title = "Skema warna",
            subtitle = "Tema aplikasi akan didasarkan pada warna yang dipilih",
            shape = itemShape(0, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !dynamicActive,
            onDisabledClick = {
                Toast.makeText(ctx, "Matikan warna dinamis dulu buat ganti skema warna", Toast.LENGTH_SHORT).show()
            },
            onClick = { showAccentSheet = true },
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            end = { ColorSchemePreview() },
        )
        PrefItem(
            icon = Icons.Outlined.Colorize,
            title = "Warna kustom",
            subtitle =
                if (settings.customSeed != 0) {
                    "Aktif, ${settings.seedColor.toHexString()}"
                } else {
                    "Pilih warna sendiri atau ambil dari gambar"
                },
            shape = itemShape(1, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !dynamicActive,
            onDisabledClick = {
                Toast.makeText(ctx, "Matikan warna dinamis dulu buat pakai warna kustom", Toast.LENGTH_SHORT).show()
            },
            onClick = { showCustomColorSheet = true },
            badge = cs.primary,
            badgeTint = cs.onPrimary,
        )
        PrefItem(
            icon = Icons.Outlined.Style,
            title = "Gaya palet",
            subtitle = settings.paletteStyle.label,
            shape = itemShape(2, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showStyleDialog = true },
        )
        PrefItem(
            icon = Icons.Outlined.Tune,
            title = "Spek warna",
            subtitle =
                if (settings.paletteStyle.forcesSpec2026) {
                    "Expressive (2026), otomatis untuk gaya CMF"
                } else if (runningSpec != settings.colorSpec) {
                    "${settings.colorSpec.label}, berjalan sebagai ${runningSpec.label} di gaya ${settings.paletteStyle.label}"
                } else {
                    settings.colorSpec.label
                },
            shape = itemShape(3, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !settings.paletteStyle.forcesSpec2026,
            onDisabledClick = {
                Toast.makeText(ctx, "CMF selalu memakai spek 2026", Toast.LENGTH_SHORT).show()
            },
            onClick = { showSpecDialog = true },
            badge = cs.primary,
            badgeTint = cs.onPrimary,
        )
        PrefItem(
            icon = Icons.Outlined.Contrast,
            title = "Kontras",
            subtitle = settings.contrast.contrastLabel(),
            shape = itemShape(4, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showContrastDialog = true },
        )
        PrefItem(
            icon = Icons.Outlined.Palette,
            title = "Warna per peran",
            subtitle =
                if (settings.roleOverrides.isEmpty) {
                    "Kunci palet primary, secondary, tertiary, dan lainnya ke warna tertentu"
                } else {
                    "${settings.roleOverrides.count} peran dikunci"
                },
            shape = itemShape(5, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showRoleSheet = true },
        )
        SwitchItem(
            icon = Icons.Outlined.Animation,
            title = "Animasi perubahan warna",
            subtitle = "Warna aplikasi berubah halus saat skema, mode, atau gaya palet diganti",
            shape = itemShape(6, itemCount),
            checked = settings.animateColorChange,
            enabled = true,
            onChange = Appearance::setAnimateColorChange,
        )
        if (settings.animateColorChange) {
            PrefItem(
                icon = Icons.Outlined.Timer,
                title = "Durasi animasi",
                subtitle = "${settings.colorAnimationMs} ms",
                shape = itemShape(7, itemCount),
                container = itemContainer(),
                content = cs.onSurface,
                badge = cs.primary,
                badgeTint = cs.onPrimary,
                onClick = { showColorAnimDialog = true },
            )
        }
        SwitchItem(
            icon = Icons.Outlined.FormatColorFill,
            title = "Warna-warna yang dinamis",
            subtitle =
                if (DynamicColorSupported) {
                    "Jika diaktifkan, warna aplikasi akan mengikuti warna wallpaper kamu (Material You)"
                } else {
                    "Butuh Android 12 ke atas"
                },
            shape = itemShape(7 + animExtra, itemCount),
            checked = dynamicActive,
            enabled = DynamicColorSupported,
            onChange = Appearance::setDynamicColor,
        )
        SwitchItem(
            icon = Icons.Outlined.Contrast,
            title = "Mode AMOLED",
            subtitle = "Latar jadi hitam total di mode gelap, lebih hemat baterai di layar OLED",
            shape = itemShape(8 + animExtra, itemCount),
            checked = settings.amoled,
            enabled = true,
            onChange = Appearance::setAmoled,
        )
        SwitchItem(
            icon = Icons.Outlined.BlurOn,
            title = "Efek blur",
            subtitle =
                if (BlurSupported) {
                    "Aktifkan efek blur untuk aplikasi"
                } else {
                    "Butuh Android 13 ke atas"
                },
            shape = itemShape(9 + animExtra, itemCount),
            checked = settings.blur && BlurSupported,
            enabled = BlurSupported,
            onChange = Appearance::setBlur,
        )
        PrefItem(
            icon = Icons.Outlined.Tab,
            title = "Efek bar",
            subtitle =
                if (barEffectEnabled) {
                    "${settings.barEffect.label} untuk tab bar, toolbar, dan search bar"
                } else {
                    "Nyalakan efek blur dulu"
                },
            shape = itemShape(10 + animExtra, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = barEffectEnabled,
            onDisabledClick = {
                Toast.makeText(ctx, "Nyalakan efek blur dulu buat ngatur ini", Toast.LENGTH_SHORT).show()
            },
            onClick = { showBarEffectDialog = true },
            badge = cs.primary,
            badgeTint = cs.onPrimary,
        )
        PrefItem(
            icon = Icons.Outlined.ViewCarousel,
            title = "Ukuran poster",
            subtitle = "${settings.homeCardScale.toUiScalePercent()}% dari ukuran bawaan",
            shape = itemShape(11 + animExtra, itemCount),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showCardScaleDialog = true },
        )
        SwitchItem(
            icon = Icons.Outlined.Tag,
            title = "Angka disingkat",
            subtitle = "Jumlah dilihat dan favorit tampil singkat, misalnya 15.571 jadi 15,5k",
            shape = itemShape(12 + animExtra, itemCount),
            checked = settings.compactNumbers,
            enabled = true,
            onChange = Appearance::setCompactNumbers,
        )
        SwitchItem(
            icon = Icons.Outlined.VisibilityOff,
            title = "Sembunyikan dilihat",
            subtitle = "Sembunyikan jumlah dilihat di kartu dan halaman detail",
            shape = itemShape(13 + animExtra, itemCount),
            checked = settings.hideViews,
            enabled = true,
            onChange = Appearance::setHideViews,
        )
        SwitchItem(
            icon = Icons.Outlined.StarBorder,
            title = "Sembunyikan favorit",
            subtitle = "Sembunyikan jumlah favorit di kartu dan halaman detail",
            shape = itemShape(14 + animExtra, itemCount),
            checked = settings.hideFavorites,
            enabled = true,
            onChange = Appearance::setHideFavorites,
        )
        SwitchItem(
            icon = Icons.Outlined.TouchApp,
            title = "Getaran",
            subtitle = "Getar halus saat menekan tombol, kartu, switch, slider, menu, dll",
            shape = itemShape(15 + animExtra, itemCount),
            checked = settings.hapticsEnabled,
            enabled = true,
            onChange = Appearance::setHapticsEnabled,
        )
    }

    if (showCardScaleDialog) {
        LiveScaleDialog(
            icon = Icons.Outlined.ViewCarousel,
            title = "Ukuran poster",
            description =
                "Mengatur lebar kartu di daftar beranda (Lanjut Nonton, Episode Baru, Sedang Hangat, dan lainnya). " +
                    "Perubahan langsung terlihat saat slider digeser. Poster di dalam kartu ikut menyesuaikan.",
            currentScale = settings.homeCardScale,
            min = HOME_CARD_SCALE_MIN,
            max = HOME_CARD_SCALE_MAX,
            step = HOME_CARD_SCALE_STEP,
            coerce = { it.coerceToHomeCardScale() },
            onApply = Appearance::setHomeCardScale,
            onDismiss = { showCardScaleDialog = false },
        )
    }

    if (showCustomColorSheet) {
        CustomColorSheet(onDismiss = { showCustomColorSheet = false })
    }

    if (showContrastDialog) {
        ContrastDialog(onDismiss = { showContrastDialog = false })
    }

    if (showColorAnimDialog && settings.animateColorChange) {
        ColorAnimationDialog(onDismiss = { showColorAnimDialog = false })
    }

    if (showRoleSheet) {
        RoleOverrideSheet(onDismiss = { showRoleSheet = false })
    }

    if (showAccentSheet) {
        AccentSheet(
            selected = settings.accent,
            onSelect = {
                Appearance.setCustomSeed(0)
                Appearance.setAccent(it)
            },
            onDismiss = { showAccentSheet = false },
        )
    }

    if (showStyleDialog) {
        OptionDialog(
            icon = Icons.Outlined.Style,
            title = "Gaya palet",
            options = PaletteStyle.entries,
            selected = settings.paletteStyle,
            label = { it.label },
            onSelect = Appearance::setPaletteStyle,
            onDismiss = { showStyleDialog = false },
        )
    }

    if (showBarEffectDialog) {
        OptionDialog(
            icon = Icons.Outlined.Tab,
            title = "Efek bar",
            options = BarEffect.entries,
            selected = settings.barEffect,
            label = { it.label },
            onSelect = Appearance::setBarEffect,
            onDismiss = { showBarEffectDialog = false },
        )
    }

    if (showSpecDialog) {
        OptionDialog(
            icon = Icons.Outlined.Tune,
            title = "Spek warna",
            options = offeredSpecs,
            selected = if (settings.colorSpec in offeredSpecs) settings.colorSpec else runningSpec,
            label = { it.label },
            onSelect = Appearance::setColorSpec,
            onDismiss = { showSpecDialog = false },
        )
    }
}

@Composable
private fun LiveScaleDialog(
    icon: ImageVector,
    title: String,
    description: String,
    currentScale: Float,
    min: Float,
    max: Float,
    step: Float,
    coerce: (Float) -> Float,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
    live: Boolean = true,
    onSaved: () -> Unit = {},
) {
    val original = rememberSaveable { coerce(currentScale) }
    var selected by rememberSaveable { mutableFloatStateOf(original) }
    var confirmed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (confirmed) onDismiss() }
    val cancel = {
        if (live && !confirmed) onApply(original)
        onDismiss()
    }
    val span = max - min
    val sliderTick = rememberStableHapticAction(HapticFeedbackType.SegmentTick)
    val sliderState =
        rememberSliderState(
            value = (selected - min) / span,
            steps = Math.round(span / step) - 1,
        )

    AppDialog(
        icon = icon,
        title = title,
        onDismiss = cancel,
        confirmButton = {
            DialogConfirmButton(if (live) "Simpan" else "Terapkan") {
                confirmed = true
                if (!live && selected != original) onApply(selected)
                onDismiss()
                if (selected != original) onSaved()
            }
        },
        dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = cancel) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${selected.toUiScalePercent()}%",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Slider(
                    state = sliderState,
                    onValueChange = {
                        val snapped = coerce(min + it * span)
                        if (snapped != selected) sliderTick()
                        if (live && snapped != selected) onApply(snapped)
                        selected = snapped
                        sliderState.value = (snapped - min) / span
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${min.toUiScalePercent()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${max.toUiScalePercent()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
internal fun BackgroundGroup() {
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val event = remember { SeasonalEvent.on() }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showIntervalDialog by rememberSaveable { mutableStateOf(false) }
    val none = settings.backgroundType == BackgroundType.NONE
    val random = settings.backgroundType == BackgroundType.RANDOM
    val count = if (random) 4 else 3
    val types = remember { BackgroundType.entries.filter { it !in BackgroundType.SEASONAL } }

    SettingGroup(
        icon = Icons.Outlined.Animation,
        title = "Latar animasi",
        initiallyExpanded = false,
    ) {
        PrefItem(
            icon = Icons.Outlined.Animation,
            title = "Pilih latar",
            subtitle = "${settings.backgroundType.label}: ${settings.backgroundType.description}",
            shape = itemShape(0, count),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showPicker = true },
        )
        if (random) {
            PrefItem(
                icon = Icons.Outlined.Shuffle,
                title = "Ganti latar acak",
                subtitle = settings.randomInterval.label,
                shape = itemShape(1, count),
                container = itemContainer(),
                content = cs.onSurface,
                badge = cs.primary,
                badgeTint = cs.onPrimary,
                onClick = { showIntervalDialog = true },
            )
        }
        SwitchItem(
            icon = Icons.Outlined.Layers,
            title = "Parallax latar",
            subtitle =
                if (none) {
                    "Pilih latar animasi dulu"
                } else {
                    "Latar bergeser halus mengikuti kemiringan HP"
                },
            shape = itemShape(count - 2, count),
            checked = settings.backgroundParallax,
            enabled = !none,
            onChange = Appearance::setBackgroundParallax,
        )
        SwitchItem(
            icon = Icons.Outlined.Celebration,
            title = "Tema musiman",
            subtitle =
                when {
                    none -> "Pilih latar animasi dulu"
                    event != null -> "Lagi musim ${event.label}, latar khusus tampil sampai ${event.endLabel}"
                    else -> "Latar khusus saat Halloween, Natal, dan Tahun Baru"
                },
            shape = itemShape(count - 1, count),
            checked = settings.seasonalThemes,
            enabled = !none,
            onChange = Appearance::setSeasonalThemes,
        )
    }

    if (showPicker) {
        OptionDialog(
            icon = Icons.Outlined.Animation,
            title = "Latar animasi",
            options = types,
            selected = settings.backgroundType,
            label = { it.label },
            onSelect = Appearance::setBackgroundType,
            onDismiss = { showPicker = false },
        )
    }
    if (showIntervalDialog) {
        OptionDialog(
            icon = Icons.Outlined.Shuffle,
            title = "Ganti latar acak",
            options = RandomInterval.entries,
            selected = settings.randomInterval,
            label = { it.label },
            onSelect = Appearance::setRandomInterval,
            onDismiss = { showIntervalDialog = false },
        )
    }
}

@Composable
internal fun NightModeGroup() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    SettingGroup(
        icon = Icons.Outlined.BrightnessMedium,
        title = "Mode malam",
        initiallyExpanded = false,
    ) {
        val options =
            listOf(
                Triple("Gelap", Icons.Outlined.DarkMode, ThemeMode.DARK),
                Triple("Terang", Icons.Outlined.LightMode, ThemeMode.LIGHT),
                Triple("Sistem", Icons.Outlined.SettingsSuggest, ThemeMode.SYSTEM),
            )
        options.forEachIndexed { index, (label, icon, mode) ->
            NightModeItem(
                title = label,
                icon = icon,
                selected = settings.mode == mode,
                shape = itemShape(index, options.size),
                onClick = { Appearance.setMode(mode) },
            )
        }
    }
}

@Composable
internal fun NightModeItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    PrefItem(
        icon = icon,
        title = title,
        subtitle = null,
        shape = shape,
        container = if (selected) cs.primaryContainer else itemContainer(),
        content = if (selected) cs.onPrimaryContainer else cs.onSurface,
        onClick = onClick,
        badge = if (selected) cs.onSecondaryContainer else cs.primary,
        badgeTint = if (selected) cs.secondaryContainer else cs.onPrimary,
        end = {
            Icon(
                if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    )
}
