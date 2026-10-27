@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmpalette.rememberPaletteState
import com.materialkolor.palette.themeColors
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.ContrastPreset
import com.uwu.animex.data.local.PaletteRole
import com.uwu.animex.data.local.PaletteStyle
import com.uwu.animex.data.local.get
import com.uwu.animex.data.local.seedColor
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.ShapedIconBadge
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.COLOR_ANIM_DEFAULT_MS
import com.uwu.animex.ui.theme.COLOR_ANIM_OPTIONS_MS
import com.uwu.animex.ui.theme.coerceToColorAnimMs
import com.uwu.animex.ui.theme.decodeImageBitmap
import com.uwu.animex.ui.theme.paletteColorScheme
import com.uwu.animex.ui.theme.parseHexColor
import com.uwu.animex.ui.theme.toHexString
import com.uwu.animex.ui.theme.tonalRamp
import com.uwu.animex.ui.tracker.SheetGroupGap
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ContrastDialog(onDismiss: () -> Unit) {
    val original = rememberSaveable { Appearance.settings.value.contrast }
    var selected by rememberSaveable { mutableFloatStateOf(original) }
    var confirmed by remember { mutableStateOf(false) }
    val cancel = {
        if (!confirmed) Appearance.setContrast(original)
        onDismiss()
    }

    AppDialog(
        icon = Icons.Outlined.Contrast,
        title = "Kontras",
        onDismiss = cancel,
        confirmButton = {
            DialogConfirmButton("Simpan") {
                confirmed = true
                onDismiss()
            }
        },
        dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = cancel) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContrastPreset.entries.forEach { preset ->
                    DialogOptionRow(
                        label = preset.label,
                        selected = kotlin.math.abs(selected - preset.value) < 0.01f,
                        onClick = {
                            selected = preset.value
                            Appearance.setContrast(preset.value)
                        },
                    )
                }
            }
        },
    )
}

/** Dialog durasi animasi perubahan warna (single choice, milidetik). Disimpan saat menekan Simpan. */
@Composable
internal fun ColorAnimationDialog(onDismiss: () -> Unit) {
    val original = rememberSaveable { Appearance.settings.value.colorAnimationMs.coerceToColorAnimMs() }
    var selected by rememberSaveable { mutableIntStateOf(original) }

    AppDialog(
        icon = Icons.Outlined.Timer,
        title = "Durasi animasi",
        onDismiss = onDismiss,
        confirmButton = {
            DialogConfirmButton("Simpan") {
                Appearance.setColorAnimationMs(selected)
                onDismiss()
            }
        },
        dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = onDismiss) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Durasi tersimpan di luar pilihan ikut tampil sebagai opsi tambahan, seperti di builder.
                (COLOR_ANIM_OPTIONS_MS + original).distinct().sorted().forEach { ms ->
                    DialogOptionRow(
                        label = if (ms == COLOR_ANIM_DEFAULT_MS) "$ms ms (bawaan)" else "$ms ms",
                        selected = ms == selected,
                        onClick = { selected = ms },
                    )
                }
            }
        },
    )
}

@Composable
private fun ColorSheetScaffold(
    startLabel: String,
    onStart: () -> Unit,
    endLabel: String,
    onEnd: () -> Unit,
    onDismiss: () -> Unit,
    startEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = hapticClick(block = onStart),
                    enabled = startEnabled,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(startLabel) }
                Button(
                    onClick = hapticClick(block = onEnd),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(endLabel) }
            }
            Spacer(Modifier.height(SheetGroupGap))
            content()
        }
    }
}

@Composable
internal fun CustomColorSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    var color by remember { mutableStateOf(settings.seedColor) }
    var hexText by remember { mutableStateOf(settings.seedColor.toHexString()) }
    var candidates by remember { mutableStateOf<List<Color>>(emptyList()) }
    var extracting by remember { mutableStateOf(false) }

    fun setColor(
        c: Color,
        syncHex: Boolean = true,
    ) {
        color = c
        if (syncHex) hexText = c.toHexString()
        Appearance.setCustomSeed(c.toArgb())
    }

    val paletteState = rememberPaletteState()
    var pendingImage by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(pendingImage) {
        val image = pendingImage ?: return@LaunchedEffect
        paletteState.generate(image)
        val colors = paletteState.palette?.themeColors(fallback = color).orEmpty().ifEmpty { listOf(color) }
        candidates = colors
        setColor(colors.first())
        extracting = false
        pendingImage = null
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            extracting = true
            scope.launch {
                val image = decodeImageBitmap(context, uri)
                if (image == null) {
                    extracting = false
                    Toast.makeText(context, "Gagal membaca gambar", Toast.LENGTH_SHORT).show()
                } else {
                    pendingImage = image
                }
            }
        }

    var showCmfSheet by remember { mutableStateOf(false) }
    if (showCmfSheet) {
        ColorPickSheet(
            title = "Seed tersier (CMF)",
            initial = settings.cmfTertiarySeed.let { if (it != 0) Color(it) else color },
            onApply = { Appearance.setCmfTertiarySeed(it.toArgb()) },
            onDismiss = { showCmfSheet = false },
        )
    }

    ColorSheetScaffold(
        startLabel = "Pakai preset",
        onStart = {
            Appearance.setCustomSeed(0)
            Toast.makeText(context, "Kembali ke warna preset", Toast.LENGTH_SHORT).show()
            onDismiss()
        },
        endLabel = "Selesai",
        onEnd = onDismiss,
        onDismiss = onDismiss,
    ) {
        ColorEditorGroup(
            label = "Warna dasar",
            color = color,
            hexText = hexText,
            onHexChange = {
                hexText = it
                parseHexColor(it)?.let { parsed -> setColor(parsed, syncHex = false) }
            },
            onColor = { setColor(it) },
        )

        Spacer(Modifier.height(SheetGroupGap))
        val imageCount = if (candidates.size > 1) 2 else 1
        SheetItemSurface(
            shape = groupedItemShape(0, imageCount),
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = !extracting,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapedIconBadge(Icons.Outlined.Image)
                Text(
                    if (extracting) "Membaca gambar..." else "Ambil warna dari gambar",
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (candidates.size > 1) {
            SheetItemSurface(shape = groupedItemShape(1, 2)) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Warna dari gambar",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        candidates.forEach { c ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(onClick = hapticClick(block = { setColor(c) }), role = Role.Button)
                                    .padding(4.dp),
                            ) {
                                SeedPreview(c, selected = c == color)
                            }
                        }
                    }
                }
            }
        }

        if (settings.paletteStyle == PaletteStyle.Cmf) {
            Spacer(Modifier.height(SheetGroupGap))
            ColorRoleRow(
                icon = Icons.Outlined.ColorLens,
                title = "Seed tersier (CMF)",
                argb = settings.cmfTertiarySeed,
                emptyLabel = "Otomatis dari warna dasar",
                autoColor = MaterialTheme.colorScheme.tertiary,
                shape = groupedItemShape(0, 1),
                onClick = { showCmfSheet = true },
                onReset = { Appearance.setCmfTertiarySeed(0) },
            )
        }

        Spacer(Modifier.height(SheetGroupGap))
        SheetItemSurface(shape = groupedItemShape(0, 1)) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Palet tonal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TonalRamp(seed = color)
            }
        }
    }
}

@Composable
internal fun RoleOverrideSheet(onDismiss: () -> Unit) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<PaletteRole?>(null) }

    ColorSheetScaffold(
        startLabel = "Reset semua",
        onStart = { Appearance.clearRoleOverrides() },
        endLabel = "Selesai",
        onEnd = onDismiss,
        onDismiss = onDismiss,
        startEnabled = !settings.roleOverrides.isEmpty,
    ) {
        val roles = PaletteRole.entries
        roles.forEachIndexed { index, role ->
            ColorRoleRow(
                icon = role.icon,
                title = role.label,
                argb = settings.roleOverrides.get(role),
                emptyLabel = "Otomatis",
                autoColor = role.themeColor(MaterialTheme.colorScheme),
                shape = groupedItemShape(index, roles.size),
                onClick = { editing = role },
                onReset = { Appearance.setRoleOverride(role, 0) },
            )
        }
    }

    editing?.let { role ->
        ColorPickSheet(
            title = role.label,
            initial = settings.roleOverrides.get(role).let { if (it != 0) Color(it) else settings.seedColor },
            onApply = { Appearance.setRoleOverride(role, it.toArgb()) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ColorPickSheet(
    title: String,
    initial: Color,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(initial) }
    var hexText by remember { mutableStateOf(initial.toHexString()) }

    ColorSheetScaffold(
        startLabel = "Gak usah deh",
        onStart = onDismiss,
        endLabel = "Terapkan",
        onEnd = {
            onApply(color)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        ColorEditorGroup(
            label = title,
            color = color,
            hexText = hexText,
            onHexChange = {
                hexText = it
                parseHexColor(it)?.let { parsed -> color = parsed }
            },
            onColor = {
                color = it
                hexText = it.toHexString()
            },
        )
    }
}

@Composable
private fun ColorEditorGroup(
    label: String,
    color: Color,
    hexText: String,
    onHexChange: (String) -> Unit,
    onColor: (Color) -> Unit,
) {
    SheetItemSurface(shape = groupedItemShape(0, 3)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(Icons.Outlined.Colorize)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            SeedPreview(color)
        }
    }
    SheetItemSurface(shape = groupedItemShape(1, 3)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            HsvSliders(color = color, onColor = onColor)
        }
    }
    SheetItemSurface(shape = groupedItemShape(2, 3)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(Icons.Outlined.Tag)
            OutlinedTextField(
                value = hexText,
                onValueChange = onHexChange,
                label = { Text("Hex") },
                singleLine = true,
                isError = parseHexColor(hexText) == null,
                modifier = Modifier.weight(1f),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                    ),
            )
        }
    }
}

@Composable
private fun ColorRoleRow(
    icon: ImageVector,
    title: String,
    argb: Int,
    emptyLabel: String,
    autoColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onReset: () -> Unit,
) {
    SheetItemSurface(shape = shape, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(icon)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (argb == 0) {
                    Text(
                        emptyLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (argb != 0) {
                IconButton(onClick = hapticClick(block = onReset), shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Restore, contentDescription = "Reset")
                }
            }
            SeedPreview(if (argb != 0) Color(argb) else autoColor)
        }
    }
}

@Composable
internal fun rememberSeedScheme(seed: Color): ColorScheme? {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val scheme by produceState<ColorScheme?>(
        initialValue = null,
        seed,
        settings.paletteStyle,
        settings.colorSpec,
    ) {
        value =
            withContext(Dispatchers.Default) {
                paletteColorScheme(seed, false, settings.paletteStyle, settings.colorSpec)
            }
    }
    return scheme
}

@Composable
private fun SeedPreview(
    seed: Color,
    selected: Boolean = false,
) {
    val current = rememberSeedScheme(seed)
    if (current != null) SwatchContent(current, selected) else FallbackSwatchContent(seed, selected)
}

private fun PaletteRole.themeColor(cs: ColorScheme): Color =
    when (this) {
        PaletteRole.PRIMARY -> cs.primary
        PaletteRole.SECONDARY -> cs.secondary
        PaletteRole.TERTIARY -> cs.tertiary
        PaletteRole.NEUTRAL -> cs.surfaceContainerHighest
        PaletteRole.NEUTRAL_VARIANT -> cs.surfaceVariant
        PaletteRole.ERROR -> cs.error
    }

private val PaletteRole.icon: ImageVector
    get() =
        when (this) {
            PaletteRole.PRIMARY -> Icons.Outlined.Palette
            PaletteRole.SECONDARY -> Icons.Outlined.ColorLens
            PaletteRole.TERTIARY -> Icons.Outlined.Brush
            PaletteRole.NEUTRAL -> Icons.Outlined.Tonality
            PaletteRole.NEUTRAL_VARIANT -> Icons.Outlined.InvertColors
            PaletteRole.ERROR -> Icons.Outlined.ErrorOutline
        }

@Composable
private fun HsvSliders(
    color: Color,
    onColor: (Color) -> Unit,
) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    LaunchedEffect(color) {
        val fromState = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
        if (fromState.toArgb() != color.toArgb()) {
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }

    fun emit() = onColor(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Hue", style = MaterialTheme.typography.labelMedium)
        Slider(value = hue, onValueChange = { hue = it; emit() }, valueRange = 0f..360f)
        Text("Saturasi", style = MaterialTheme.typography.labelMedium)
        Slider(value = sat, onValueChange = { sat = it; emit() }, valueRange = 0f..1f)
        Text("Kecerahan", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = { value = it; emit() }, valueRange = 0f..1f)
    }
}

@Composable
private fun TonalRamp(seed: Color) {
    val ramp = remember(seed) { tonalRamp(seed) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        ramp.forEach { (_, c) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(c),
            )
        }
    }
}
