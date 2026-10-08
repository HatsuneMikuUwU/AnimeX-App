@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.settings

import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.BuildConfig
import com.uwu.animex.core.cache.AppCache
import com.uwu.animex.data.local.AccentPalette
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.ColorSpec
import com.uwu.animex.data.local.DataBackup
import com.uwu.animex.data.local.PaletteStyle
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.clearLoadCache
import com.uwu.animex.ui.theme.DynamicColorSupported
import com.uwu.animex.ui.theme.paletteColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TELEGRAM_URL = "https://t.me/uwuowoumuchannel"

private val ItemOuter = 24.dp
private val ItemInner = 4.dp
private val ItemGap = 4.dp
private val PageGap = 16.dp

private enum class SettingsPage(
    val title: String,
) {
    Main("Pengaturan"),
    Customization("Kustomisasi"),
    NightMode("Mode malam"),
    PaletteStyle("Gaya palet"),
    ColorSpec("Spek warna"),
    Storage("Penyimpanan"),
}

private fun itemShape(
    index: Int,
    count: Int,
): Shape =
    RoundedCornerShape(
        topStart = if (index == 0) ItemOuter else ItemInner,
        topEnd = if (index == 0) ItemOuter else ItemInner,
        bottomStart = if (index == count - 1) ItemOuter else ItemInner,
        bottomEnd = if (index == count - 1) ItemOuter else ItemInner,
    )

@Composable
fun SettingsScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenMal: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 4 },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 4 },
    ) {
        // State di sini ikut hilang pas layar ditutup, jadi buka lagi selalu mulai dari halaman utama.
        var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
        BackHandler(enabled = visible) {
            if (page == SettingsPage.Main) onDismiss() else page = SettingsPage.Main
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != SettingsPage.Main
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 5 * dir }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(280)) { -it / 5 * dir })
                },
                label = "settings-page",
            ) { current ->
                SettingsPageScaffold(
                    title = current.title,
                    onBack = { if (current == SettingsPage.Main) onDismiss() else page = SettingsPage.Main },
                ) {
                    when (current) {
                        SettingsPage.Main ->
                            MainPage(
                                onNavigate = { page = it },
                                onOpenMal = onOpenMal,
                                onOpenAbout = onOpenAbout,
                            )
                        SettingsPage.Customization -> CustomizationGroup()
                        SettingsPage.NightMode -> NightModeGroup()
                        SettingsPage.PaletteStyle -> PaletteStyleGroup()
                        SettingsPage.ColorSpec -> ColorSpecGroup()
                        SettingsPage.Storage -> StorageGroup(onOpenDownloads = onOpenDownloads)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = cs.background,
        topBar = {
            LargeFlexibleTopAppBar(
                expandedHeight = 160.dp,
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = cs.surfaceContainerHigh,
                                contentColor = cs.onSurface,
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = cs.background,
                        scrolledContainerColor = cs.background,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(PageGap),
        ) {
            content()
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun MainPage(
    onNavigate: (SettingsPage) -> Unit,
    onOpenMal: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val spec2025Ok = settings.paletteStyle.supportsSpec2025
    val effectiveSpec = if (spec2025Ok) settings.colorSpec else ColorSpec.SPEC_2021
    val modeLabel =
        when (settings.mode) {
            ThemeMode.DARK -> "Gelap"
            ThemeMode.LIGHT -> "Terang"
            ThemeMode.SYSTEM -> "Ikuti sistem"
        }

    ProfileAboutGroup(onOpenMal = onOpenMal, onOpenAbout = onOpenAbout)

    ItemGroup {
        NavItem(
            icon = Icons.Outlined.DesignServices,
            title = "Kustomisasi",
            subtitle = "Skema warna, warna dinamis, AMOLED, blur",
            shape = itemShape(0, 4),
            onClick = { onNavigate(SettingsPage.Customization) },
        )
        NavItem(
            icon = Icons.Outlined.BrightnessMedium,
            title = "Mode malam",
            subtitle = modeLabel,
            shape = itemShape(1, 4),
            onClick = { onNavigate(SettingsPage.NightMode) },
        )
        NavItem(
            icon = Icons.Outlined.Style,
            title = "Gaya palet",
            subtitle = settings.paletteStyle.label,
            shape = itemShape(2, 4),
            onClick = { onNavigate(SettingsPage.PaletteStyle) },
        )
        NavItem(
            icon = Icons.Outlined.Tune,
            title = "Spek warna",
            subtitle = effectiveSpec.label,
            shape = itemShape(3, 4),
            onClick = { onNavigate(SettingsPage.ColorSpec) },
        )
    }

    ItemGroup {
        NavItem(
            icon = Icons.Outlined.Storage,
            title = "Penyimpanan",
            subtitle = "Unduhan, backup, restore, dan cache",
            shape = itemShape(0, 1),
            onClick = { onNavigate(SettingsPage.Storage) },
        )
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    PrefItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        shape = shape,
        container = itemContainer(),
        content = cs.onSurface,
        onClick = onClick,
        badge = cs.primary,
        badgeTint = cs.onPrimary,
    )
}

@Composable
private fun ItemGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
        content = content,
    )
}

@Composable
private fun ProfileAboutGroup(
    onOpenMal: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val user by Mal.user.collectAsStateWithLifecycle()
    val pic = user?.picture

    ItemGroup {
        PrefItem(
            icon = Icons.Outlined.AccountCircle,
            title = if (loggedIn) user?.name ?: "Sabar bentar ya…" else "Login",
            subtitle = if (loggedIn) "Ketuk buat lihat profil" else "Sambungin progres nonton ke daftar MAL kamu",
            shape = itemShape(0, 3),
            container = cs.secondaryContainer,
            content = cs.onSecondaryContainer,
            onClick = onOpenMal,
            end = {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(72.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loggedIn && !pic.isNullOrBlank()) {
                        AsyncImage(
                            model = pic,
                            contentDescription = "Foto profil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = cs.onPrimary,
                        )
                    }
                }
            },
        )
        PrefItem(
            icon = Icons.Outlined.Info,
            title = "Tentang AnimeX",
            subtitle = "Versi ${BuildConfig.VERSION_NAME}, kode sumber, catatan rilis, dan lapor kendala",
            shape = itemShape(1, 3),
            container = cs.primaryContainer,
            content = cs.onPrimaryContainer,
            onClick = onOpenAbout,
        )
        PrefItem(
            icon = Icons.AutoMirrored.Outlined.Send,
            title = "MikuDayo",
            subtitle = "Ketuk buat gabung channel Telegram",
            shape = itemShape(2, 3),
            container = cs.tertiaryContainer,
            content = cs.onTertiaryContainer,
            onClick = { uri.openUri(TELEGRAM_URL) },
        )
    }
}

@Composable
private fun CustomizationGroup() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val dynamicActive = settings.dynamicColor && DynamicColorSupported
    var showAccentSheet by rememberSaveable { mutableStateOf(false) }

    ItemGroup {
        PrefItem(
            icon = Icons.Outlined.Palette,
            title = "Skema warna",
            subtitle = "Tema aplikasi akan didasarkan pada warna yang dipilih",
            shape = itemShape(0, 5),
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
        SwitchItem(
            icon = Icons.Outlined.FormatColorFill,
            title = "Warna-warna yang dinamis",
            subtitle =
                if (DynamicColorSupported) {
                    "Jika diaktifkan, warna aplikasi akan mengikuti warna wallpaper kamu (Material You)"
                } else {
                    "Butuh Android 12 ke atas"
                },
            shape = itemShape(1, 5),
            checked = dynamicActive,
            enabled = DynamicColorSupported,
            onChange = Appearance::setDynamicColor,
        )
        SwitchItem(
            icon = Icons.Outlined.Contrast,
            title = "Mode AMOLED",
            subtitle = "Latar jadi hitam total di mode gelap, lebih hemat baterai di layar OLED",
            shape = itemShape(2, 5),
            checked = settings.amoled,
            enabled = true,
            onChange = Appearance::setAmoled,
        )
        SwitchItem(
            icon = Icons.Outlined.Image,
            title = "Tema dari poster",
            subtitle = "Warna aksen mengikuti dominant color poster saat buka detail anime",
            shape = itemShape(3, 5),
            checked = settings.coverTheme,
            enabled = true,
            onChange = Appearance::setCoverTheme,
        )
        SwitchItem(
            icon = Icons.Outlined.BlurOn,
            title = "Efek blur",
            subtitle =
                if (BlurSupported) {
                    "Bottom bar, search bar dan toolbar jadi buram transparan seperti kaca"
                } else {
                    "Butuh Android 13 ke atas"
                },
            shape = itemShape(4, 5),
            checked = settings.blur && BlurSupported,
            enabled = BlurSupported,
            onChange = Appearance::setBlur,
        )
    }

    if (showAccentSheet) {
        AccentSheet(
            selected = settings.accent,
            onSelect = Appearance::setAccent,
            onDismiss = { showAccentSheet = false },
        )
    }
}

@Composable
private fun ColorSchemePreview() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val swatch = rememberSwatchScheme(settings.accent)
    val scheme = swatch ?: MaterialTheme.colorScheme
    val primaryArc = remember(scheme) { scheme.primaryContainer.copy(alpha = 0.9f) }
    val secondaryArc = remember(scheme) { scheme.secondaryContainer.copy(alpha = 0.6f) }
    val tertiaryArc = remember(scheme) { scheme.tertiaryContainer.copy(alpha = 0.9f) }
    val backdrop = remember(scheme) { scheme.primary.copy(alpha = 0.3f) }
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(72.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(color = primaryArc, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = tertiaryArc, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(color = secondaryArc, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Ubah",
                    modifier = Modifier.size(16.dp),
                    tint = scheme.inversePrimary,
                )
            }
        }
    }
}

@Composable
private fun NightModeGroup() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    ItemGroup {
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
private fun PaletteStyleGroup() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    ItemGroup {
        val options = PaletteStyle.entries
        options.forEachIndexed { index, style ->
            RadioItem(
                title = style.label,
                selected = settings.paletteStyle == style,
                shape = itemShape(index, options.size),
                onClick = { Appearance.setPaletteStyle(style) },
            )
        }
    }
}

@Composable
private fun ColorSpecGroup() {
    val ctx = LocalContext.current
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val spec2025Ok = settings.paletteStyle.supportsSpec2025
    ItemGroup {
        val options = ColorSpec.entries
        options.forEachIndexed { index, spec ->
            val needs2025 = spec == ColorSpec.SPEC_2025
            val selected = if (spec2025Ok) settings.colorSpec == spec else spec == ColorSpec.SPEC_2021
            RadioItem(
                title = spec.label,
                subtitle = if (needs2025 && !spec2025Ok) "${settings.paletteStyle.label} belum mendukung spek 2025" else null,
                selected = selected,
                enabled = spec2025Ok,
                shape = itemShape(index, options.size),
                onDisabledClick = {
                    Toast.makeText(ctx, "Gaya palet ini cuma mendukung spek 2021", Toast.LENGTH_SHORT).show()
                },
                onClick = { Appearance.setColorSpec(spec) },
            )
        }
    }
}

@Composable
private fun RadioItem(
    title: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onDisabledClick: () -> Unit = {},
    subtitle: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    PrefItem(
        icon = if (selected) Icons.Outlined.Check else Icons.Outlined.Palette,
        title = title,
        subtitle = subtitle,
        shape = shape,
        container = if (selected) cs.secondaryContainer.copy(alpha = 0.7f) else itemContainer(),
        content = if (selected) cs.onSecondaryContainer else cs.onSurface,
        enabled = enabled,
        onDisabledClick = onDisabledClick,
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

@Composable
private fun StorageGroup(onOpenDownloads: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    var working by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { sizeBytes = AppCache.sizeBytes(ctx) }

    val backupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            working = true
            scope.launch {
                val result = runCatching { DataBackup.export(ctx, uri) }
                working = false
                val msg =
                    result.fold(
                        onSuccess = { "Backup selesai, ${it.total} data disimpan" },
                        onFailure = { "Gagal bikin backup: ${it.message ?: "kesalahan tak dikenal"}" },
                    )
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) pendingRestore = uri }

    ItemGroup {
        PrefItem(
            icon = Icons.Outlined.Download,
            title = "Unduhan",
            subtitle = "Episode yang diunduh buat ditonton tanpa internet",
            shape = itemShape(0, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            onClick = onOpenDownloads,
        )
        PrefItem(
            icon = Icons.Outlined.Backup,
            title = "Backup data",
            subtitle = if (working) "Lagi memproses…" else "Simpan bookmark, riwayat, progres nonton, dan pengaturan ke satu berkas",
            shape = itemShape(1, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            enabled = !working,
            onClick = { backupLauncher.launch(DataBackup.suggestedFileName()) },
        )
        PrefItem(
            icon = Icons.Outlined.Restore,
            title = "Restore data",
            subtitle = "Pulihkan dari berkas backup, digabung dengan data yang ada sekarang",
            shape = itemShape(2, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            enabled = !working,
            onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        )
        PrefItem(
            icon = Icons.Outlined.DeleteSweep,
            title = "Hapus cache",
            subtitle =
                when {
                    clearing -> "Lagi menghapus…"
                    sizeBytes != null -> "Poster, dan data API · ${Formatter.formatShortFileSize(ctx, sizeBytes!!)}"
                    else -> "Poster, dan data API"
                },
            shape = itemShape(3, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            enabled = !clearing,
            onClick = { confirm = true },
        )
    }

    pendingRestore?.let { uri ->
        AppDialog(
            icon = Icons.Outlined.Restore,
            onDismiss = { pendingRestore = null },
            title = "Restore dari backup?",
            text = {
                Text(
                    "Bookmark, riwayat, progres nonton, pengingat episode, dan pengaturan tampilan dari backup " +
                        "bakal digabung ke data sekarang. Data dengan id yang sama ditimpa versi backup. " +
                        "Login MAL dan unduhan offline nggak ikut.",
                )
            },
            confirmButton = {
                DialogConfirmButton("Restore") {
                    pendingRestore = null
                    working = true
                    scope.launch {
                        val result = runCatching { DataBackup.restore(ctx, uri) }
                        working = false
                        val msg =
                            result.fold(
                                onSuccess = { "Restore selesai, ${it.total} data dipulihkan" },
                                onFailure = { "Gagal restore: ${it.message ?: "kesalahan tak dikenal"}" },
                            )
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            dismissButton = { DialogCancelButton { pendingRestore = null } },
        )
    }

    if (confirm) {
        AppDialog(
            icon = Icons.Outlined.DeleteSweep,
            onDismiss = { confirm = false },
            title = "Hapus semua cache?",
            text = {
                Text(
                    "Poster dan data API, yang kesimpen bakal dihapus, nanti diunduh lagi pas dibutuhin. " +
                        "Bookmark, riwayat nonton, dan login MAL tetap aman.",
                )
            },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    confirm = false
                    clearing = true
                    scope.launch {
                        val freed = runCatching { AppCache.clearAll(ctx) }.getOrNull()
                        clearLoadCache()
                        sizeBytes = AppCache.sizeBytes(ctx)
                        clearing = false
                        Toast
                            .makeText(
                                ctx,
                                if (freed != null) {
                                    "Cache dihapus, ${Formatter.formatShortFileSize(ctx, freed)} dibebaskan"
                                } else {
                                    "Gagal menghapus cache"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            },
            dismissButton = { DialogCancelButton { confirm = false } },
        )
    }
}

@Composable
private fun NightModeItem(
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
        container = if (selected) cs.secondaryContainer.copy(alpha = 0.7f) else itemContainer(),
        content = if (selected) cs.onSecondaryContainer else cs.onSurface,
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

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    PrefItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        shape = shape,
        container = itemContainer(),
        content = cs.onSurface,
        enabled = enabled,
        onClick = { onChange(!checked) },
        badge = cs.primary,
        badgeTint = cs.onPrimary,
        end = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

private const val PaletteColumns = 3

private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private val SwatchSchemeCache = ConcurrentHashMap<String, ColorScheme>()

@Composable
private fun AccentSheet(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            AccentPalette.entries.chunked(PaletteColumns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { accent ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            AccentSwatch(accent, accent == selected) { onSelect(accent) }
                        }
                    }
                    repeat(PaletteColumns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun rememberSwatchScheme(accent: AccentPalette): ColorScheme? {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val cacheKey = "${accent.name}_${settings.paletteStyle.name}_${settings.colorSpec.name}"
    val scheme by produceState<ColorScheme?>(initialValue = SwatchSchemeCache[cacheKey], key1 = cacheKey) {
        val cached = SwatchSchemeCache[cacheKey]
        if (cached != null) {
            value = cached
        } else {
            withContext(Dispatchers.Default) {
                val computed = paletteColorScheme(accent.seed, false, settings.paletteStyle, settings.colorSpec)
                SwatchSchemeCache[cacheKey] = computed
                value = computed
            }
        }
    }
    return scheme
}

@Composable
private fun AccentSwatch(
    accent: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = rememberSwatchScheme(accent)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        val current = scheme
        if (current != null) {
            SwatchContent(current, selected)
        } else {
            FallbackSwatchContent(accent.seed, selected)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            accent.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwatchContent(
    scheme: ColorScheme,
    selected: Boolean,
) {
    val primaryArc = remember(scheme) { scheme.primaryContainer.copy(alpha = 0.9f) }
    val secondaryArc = remember(scheme) { scheme.secondaryContainer.copy(alpha = 0.6f) }
    val tertiaryArc = remember(scheme) { scheme.tertiaryContainer.copy(alpha = 0.9f) }
    val backdrop = remember(scheme) { scheme.primary.copy(alpha = 0.3f) }
    Box(
        Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(color = primaryArc, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = tertiaryArc, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(color = secondaryArc, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = scheme.inversePrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FallbackSwatchContent(
    base: Color,
    selected: Boolean,
) {
    Box(
        Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(base.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(base.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(base),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun itemContainer(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun IconBadge(
    icon: ImageVector,
    container: Color,
    tint: Color,
) {
    Box(
        Modifier.size(40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
    }
}

@Composable
private fun PrefItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    badge: Color = content,
    badgeTint: Color = container,
    end: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.5f, label = "item-alpha")
    val effectiveClick: (() -> Unit)? =
        when {
            enabled -> onClick
            else -> onDisabledClick
        }
    val body: @Composable () -> Unit = {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = icon, container = badge, tint = badgeTint)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.8f),
                        )
                    }
                }
                end?.invoke()
            }
            bottom?.invoke(this)
        }
    }
    if (effectiveClick != null) {
        Surface(
            onClick = effectiveClick,
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().alpha(alpha),
        ) { body() }
    } else {
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().alpha(alpha),
        ) { body() }
    }
}
