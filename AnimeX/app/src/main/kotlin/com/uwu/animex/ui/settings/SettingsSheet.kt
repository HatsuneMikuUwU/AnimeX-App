@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.BuildConfig
import com.uwu.animex.data.local.AccentPalette
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.ui.common.MaterialStarShape
import com.uwu.animex.ui.theme.DynamicColorSupported
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.theme.staticColorScheme

/* ---------------------------------------------------------------------------------------------
 * Sheet Pengaturan bergaya ImageToolbox (drawer dari kanan):
 *   - grup "Profil & Tentang"  (profil MAL, tentang, pembaruan)
 *   - grup "Kustomisasi"       (skema warna, warna dinamis, AMOLED)
 *   - grup "Mode malam"        (Gelap / Terang / Sistem, default tertutup)
 * ------------------------------------------------------------------------------------------- */

private val ItemOuter = 16.dp
private val ItemInner = 4.dp
private val GroupCorner = 28.dp

private fun itemShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) ItemOuter else ItemInner,
    topEnd = if (index == 0) ItemOuter else ItemInner,
    bottomStart = if (index == count - 1) ItemOuter else ItemInner,
    bottomEnd = if (index == count - 1) ItemOuter else ItemInner,
)

/** Sheet pengaturan dari sisi kanan di atas layar utama, seperti drawer pengaturan ImageToolbox. */
@Composable
fun SettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenMal: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(topStart = GroupCorner, bottomStart = GroupCorner),
                color = cs.surface,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainer)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text("Pengaturan", fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProfileAboutGroup(
                            onOpenMal = onOpenMal,
                            onOpenAbout = onOpenAbout,
                            onOpenUpdate = onOpenUpdate,
                        )
                        CustomizationGroup()
                        NightModeGroup()
                        Spacer(Modifier.height(8.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }
}

/* ------------------------------------------ Grup 1 ------------------------------------------ */

@Composable
private fun ProfileAboutGroup(
    onOpenMal: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val user by Mal.user.collectAsStateWithLifecycle()
    val pic = user?.picture

    SettingGroup(icon = Icons.Outlined.Forum, title = "Profil & Tentang") {
        // Profil (MAL) – kartu dengan avatar berbentuk bintang di kanan
        PrefItem(
            icon = Icons.Outlined.AccountCircle,
            title = if (loggedIn) user?.name ?: "Sabar bentar ya…" else "Login MyAnimeList",
            subtitle = if (loggedIn) "MyAnimeList · ketuk buat lihat profil" else "Sambungin progres nonton ke daftar MAL kamu",
            shape = itemShape(0, 3),
            container = cs.secondaryContainer,
            content = cs.onSecondaryContainer,
            onClick = onOpenMal,
            end = {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(64.dp)
                        .clip(MaterialStarShape)
                        .background(cs.surfaceVariant.copy(alpha = 0.5f)),
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
                            modifier = Modifier.size(36.dp),
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        // Tentang – warna campuran (mixed container) seperti "Kirim Log"
        PrefItem(
            icon = Icons.Outlined.Info,
            title = "Tentang AnimeX",
            subtitle = "Versi ${BuildConfig.VERSION_NAME}, kode sumber, catatan rilis, dan lapor kendala",
            shape = itemShape(1, 3),
            container = mixedContainer(cs.tertiaryContainer, cs.primaryContainer).copy(alpha = 0.9f),
            content = cs.onTertiaryContainer,
            onClick = onOpenAbout,
        )
        // Pembaruan – warna tersier seperti "Sumbangan"
        PrefItem(
            icon = Icons.Outlined.SystemUpdate,
            title = "Cek pembaruan",
            subtitle = "Kamu lagi di v${BuildConfig.VERSION_NAME}. Klik buat cek versi terbaru AnimeX",
            shape = itemShape(2, 3),
            container = cs.tertiaryContainer,
            content = cs.onTertiaryContainer,
            onClick = onOpenUpdate,
            iconContainerOverride = lerp(cs.tertiaryContainer, cs.tertiary, if (dark) 0.2f else 0.1f),
            iconContentOverride = cs.onTertiaryContainer,
        )
    }
}

/* ------------------------------------------ Grup 2 ------------------------------------------ */

@Composable
private fun CustomizationGroup() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val dynamicActive = settings.dynamicColor && DynamicColorSupported
    val dark = rememberAppDarkTheme(settings.mode)
    var showAccentSheet by rememberSaveable { mutableStateOf(false) }

    SettingGroup(icon = Icons.Outlined.DesignServices, title = "Kustomisasi") {
        // Skema warna
        PrefItem(
            icon = Icons.Outlined.Palette,
            title = "Skema warna",
            subtitle = "Tema aplikasi akan didasarkan pada warna yang dipilih",
            shape = itemShape(0, 3),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !dynamicActive,
            onDisabledClick = {
                Toast.makeText(ctx, "Matikan warna dinamis dulu buat ganti skema warna", Toast.LENGTH_SHORT).show()
            },
            onClick = { showAccentSheet = true },
            end = { ColorSchemePreview() },
        )
        // Warna dinamis
        SwitchItem(
            icon = Icons.Outlined.FormatColorFill,
            title = "Warna-warna yang dinamis",
            subtitle = if (DynamicColorSupported) {
                "Jika diaktifkan, warna aplikasi akan mengikuti warna wallpaper kamu (Material You)"
            } else {
                "Butuh Android 12 ke atas"
            },
            shape = itemShape(1, 3),
            checked = dynamicActive,
            enabled = DynamicColorSupported,
            onChange = Appearance::setDynamicColor,
        )
        // AMOLED
        SwitchItem(
            icon = Icons.Outlined.Contrast,
            title = "Mode AMOLED",
            subtitle = "Latar jadi hitam total di mode gelap, lebih hemat baterai di layar OLED",
            shape = itemShape(2, 3),
            checked = settings.amoled,
            enabled = true,
            onChange = Appearance::setAmoled,
        )
    }

    if (showAccentSheet) {
        AccentSheet(
            selected = settings.accent,
            dark = dark,
            onSelect = Appearance::setAccent,
            onDismiss = { showAccentSheet = false },
        )
    }
}

@Composable
private fun ColorSchemePreview() {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary
    val secondary = cs.secondary
    val tertiary = cs.tertiary
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(72.dp)
            .clip(MaterialStarShape)
            .background(cs.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(60.dp).clip(CircleShape)) {
            val s = Size(size.width, size.height)
            drawArc(primary, 180f, 180f, true, Offset.Zero, s)
            drawArc(secondary, 90f, 90f, true, Offset.Zero, s)
            drawArc(tertiary, 0f, 90f, true, Offset.Zero, s)
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(lerp(primary, if (primary.luminance() < 0.3f) Color.White else Color.Black, 0.5f)),
        )
        Icon(
            Icons.Outlined.Edit,
            contentDescription = "Ubah",
            modifier = Modifier.size(16.dp),
            tint = primary,
        )
    }
}

@Composable
private fun NightModeGroup() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    SettingGroup(
        icon = Icons.Outlined.BrightnessMedium,
        title = "Mode malam",
        initiallyExpanded = false,
    ) {
        val options = listOf(
            Triple("Gelap", Icons.Outlined.DarkMode, ThemeMode.DARK),
            Triple("Terang", Icons.Outlined.LightMode, ThemeMode.LIGHT),
            Triple("Sistem", Icons.Outlined.SettingsSuggest, ThemeMode.SYSTEM),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        end = {
            val thumb: (@Composable () -> Unit)? = if (checked) {
                @Composable { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                null
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = thumb,
            )
        },
    )
}

@Composable
private fun AccentSheet(
    selected: AccentPalette,
    dark: Boolean,
    onSelect: (AccentPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(
                "Skema warna",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            Text(
                "Pilih warna utama aplikasi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentPalette.entries.forEach { accent ->
                    AccentSwatch(accent, accent == selected, dark) { onSelect(accent) }
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(accent: AccentPalette, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val scheme = remember(accent, dark) { staticColorScheme(accent, dark) }
    val shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
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

/* ------------------------------------- Komponen bersama ------------------------------------- */

private fun mixedContainer(a: Color, b: Color): Color = lerp(a, b, 0.4f)

@Composable
private fun itemContainer(): Color = MaterialTheme.colorScheme.surfaceContainerLowest

@Composable
private fun SettingGroup(
    icon: ImageVector,
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "group-chevron")
    val groupColor = lerp(cs.surfaceContainer, cs.surfaceContainerLowest, 0.4f)
    val dark = cs.background.luminance() < 0.5f

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupCorner))
            .background(groupColor),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GroupCorner))
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = icon,
                containerOfItem = groupColor,
                dark = dark,
                containerOverride = null,
                contentOverride = null,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Ciutkan" else "Lebarkan",
                modifier = Modifier.rotate(rotation),
                tint = cs.onSurface,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    containerOfItem: Color,
    dark: Boolean,
    containerOverride: Color?,
    contentOverride: Color?,
) {
    val cs = MaterialTheme.colorScheme
    val container = containerOverride ?: if (dark) {
        lerp(cs.primary, cs.primaryContainer, 0.5f).copy(alpha = 0.2f)
    } else {
        lerp(cs.primaryContainer, cs.primary, 0.5f).copy(alpha = 0.35f)
    }
    val content = contentOverride ?: run {
        val isLight = containerOfItem.luminance() > 0.2f
        if (isLight) lerp(Color.Black, cs.onPrimaryContainer, 0.35f) else lerp(Color.White, cs.primary, 0.35f)
    }
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(percent = 32)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
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
    iconContainerOverride: Color? = null,
    iconContentOverride: Color? = null,
    end: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val alpha by animateFloatAsState(if (enabled) 1f else 0.5f, label = "item-alpha")
    val effectiveClick: (() -> Unit)? = when {
        enabled -> onClick
        else -> onDisabledClick
    }
    val body: @Composable () -> Unit = {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = icon,
                    containerOfItem = container,
                    dark = dark,
                    containerOverride = iconContainerOverride,
                    contentOverride = iconContentOverride,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 14.sp,
                            color = content.copy(alpha = 0.5f),
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
