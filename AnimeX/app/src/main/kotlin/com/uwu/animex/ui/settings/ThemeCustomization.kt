@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.CONTRAST_MAX
import com.uwu.animex.data.local.CONTRAST_MIN
import com.uwu.animex.data.local.ContrastPreset
import com.uwu.animex.data.local.PaletteRole
import com.uwu.animex.data.local.contrastLabel
import com.uwu.animex.data.local.coerceToContrast
import com.uwu.animex.data.local.get
import com.uwu.animex.data.local.seedColor
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.theme.decodeImageBitmap
import com.uwu.animex.ui.theme.parseHexColor
import com.uwu.animex.ui.theme.toHexString
import com.uwu.animex.ui.theme.tonalRamp
import com.kmpalette.rememberPaletteState
import com.materialkolor.ktx.hasEnoughContrast
import com.materialkolor.ktx.isCool
import com.materialkolor.ktx.isWarm
import com.materialkolor.palette.themeColors
import com.uwu.animex.data.local.PaletteStyle
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ */
/* Kontras                                                              */
/* ------------------------------------------------------------------ */

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
                Text(
                    selected.contrastLabel(),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Slider(
                    value = selected,
                    onValueChange = {
                        val snapped = it.coerceToContrast()
                        if (snapped != selected) {
                            selected = snapped
                            Appearance.setContrast(snapped)
                        }
                    },
                    valueRange = CONTRAST_MIN..CONTRAST_MAX,
                    steps = 19,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Rendah",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tinggi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                Text(
                    "Perubahan langsung terlihat. Kontras tinggi membuat teks dan ikon lebih tegas, " +
                        "kontras rendah membuat warna lebih lembut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/* ------------------------------------------------------------------ */
/* Warna kustom (seed)                                                  */
/* ------------------------------------------------------------------ */

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

    var showCmfDialog by remember { mutableStateOf(false) }
    if (showCmfDialog) {
        ColorPickDialog(
            title = "Seed tersier (CMF)",
            initial = settings.cmfTertiarySeed.let { if (it != 0) Color(it) else color },
            onApply = { Appearance.setCmfTertiarySeed(it.toArgb()) },
            onDismiss = { showCmfDialog = false },
        )
    }

    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Warna kustom", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pilih warna apa saja sebagai dasar tema. Perubahan langsung diterapkan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color),
            )

            HsvSliders(color = color, onColor = { setColor(it) })

            OutlinedTextField(
                value = hexText,
                onValueChange = {
                    hexText = it
                    parseHexColor(it)?.let { parsed -> setColor(parsed, syncHex = false) }
                },
                label = { Text("Hex") },
                singleLine = true,
                isError = parseHexColor(hexText) == null,
                modifier = Modifier.fillMaxWidth(),
            )

            FilledTonalButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !extracting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (extracting) "Membaca gambar..." else "Ambil warna dari gambar")
            }

            if (candidates.size > 1) {
                Text("Warna dari gambar", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    candidates.forEach { c ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (c == color) 3.dp else 1.dp,
                                    color = if (c == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ).clickable { setColor(c) },
                        )
                    }
                }
            }

            ColorHint(color)

            if (settings.paletteStyle == PaletteStyle.Cmf) {
                CmfSeedRow(
                    argb = settings.cmfTertiarySeed,
                    onEdit = { showCmfDialog = true },
                    onClear = { Appearance.setCmfTertiarySeed(0) },
                )
            }

            Text("Palet tonal", style = MaterialTheme.typography.labelLarge)
            TonalRamp(seed = color)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        Appearance.setCustomSeed(0)
                        Toast.makeText(context, "Kembali ke warna preset", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                ) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Pakai preset")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Selesai") }
            }
        }
    }
}

@Composable
private fun ColorHint(color: Color) {
    val white = Color.White.hasEnoughContrast(color)
    val black = Color.Black.hasEnoughContrast(color)
    val aa =
        when {
            white && black -> "Teks putih dan hitam lolos WCAG AA"
            white -> "Teks putih lolos WCAG AA"
            black -> "Teks hitam lolos WCAG AA"
            else -> "Teks putih maupun hitam belum lolos WCAG AA"
        }
    val temp = if (color.isWarm()) "hangat" else if (color.isCool()) "dingin" else "netral"
    Text(
        "$aa. Warna $temp.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CmfSeedRow(
    argb: Int,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onEdit)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (argb != 0) Color(argb) else MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text("Seed tersier (CMF)", style = MaterialTheme.typography.titleSmall)
            Text(
                if (argb != 0) Color(argb).toHexString() else "Otomatis dari warna dasar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (argb != 0) TextButton(onClick = onClear) { Text("Reset") }
    }
}

@Composable
private fun HsvSliders(
    color: Color,
    onColor: (Color) -> Unit,
) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    // Pertahankan hue/saturation saat nilai jatuh ke 0 atau 1 supaya slider tidak melompat.
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

/* ------------------------------------------------------------------ */
/* Override warna per peran                                             */
/* ------------------------------------------------------------------ */

@Composable
internal fun RoleOverrideSheet(onDismiss: () -> Unit) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<PaletteRole?>(null) }

    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Warna per peran", style = MaterialTheme.typography.titleLarge)
            Text(
                "Kunci satu palet ke warna tertentu. Palet lain tetap mengikuti warna dasar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PaletteRole.entries.forEach { role ->
                val argb = settings.roleOverrides.get(role)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { editing = role }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (argb != 0) Color(argb) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(role.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (argb != 0) Color(argb).toHexString() else "Otomatis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (argb != 0) {
                        TextButton(onClick = { Appearance.setRoleOverride(role, 0) }) { Text("Reset") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { Appearance.clearRoleOverrides() },
                    enabled = !settings.roleOverrides.isEmpty,
                ) { Text("Reset semua") }
                Button(onClick = onDismiss) { Text("Selesai") }
            }
        }
    }

    editing?.let { role ->
        RoleColorDialog(role = role, onDismiss = { editing = null })
    }
}

@Composable
private fun RoleColorDialog(
    role: PaletteRole,
    onDismiss: () -> Unit,
) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    ColorPickDialog(
        title = role.label,
        initial = settings.roleOverrides.get(role).let { if (it != 0) Color(it) else settings.seedColor },
        onApply = { Appearance.setRoleOverride(role, it.toArgb()) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ColorPickDialog(
    title: String,
    initial: Color,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(initial) }
    var hexText by remember { mutableStateOf(initial.toHexString()) }

    AppDialog(
        icon = Icons.Outlined.Contrast,
        title = title,
        onDismiss = onDismiss,
        confirmButton = {
            DialogConfirmButton("Terapkan") {
                onApply(color)
                onDismiss()
            }
        },
        dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = onDismiss) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color),
                )
                HsvSliders(color = color, onColor = {
                    color = it
                    hexText = it.toHexString()
                })
                OutlinedTextField(
                    value = hexText,
                    onValueChange = {
                        hexText = it
                        parseHexColor(it)?.let { parsed -> color = parsed }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    isError = parseHexColor(hexText) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorHint(color)
            }
        },
    )
}
