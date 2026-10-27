package com.uwu.animex.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.materialkolor.ktx.from
import com.materialkolor.ktx.toHex
import com.materialkolor.ktx.toneColor
import com.materialkolor.palettes.TonalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAMPLE_EDGE = 128

/**
 * Decode gambar dari [uri] ke [ImageBitmap] berukuran kecil, siap dikirim ke
 * `PaletteState.generate` (modul `material-kolor-palette`).
 */
suspend fun decodeImageBitmap(
    context: Context,
    uri: Uri,
): ImageBitmap? =
    withContext(Dispatchers.IO) {
        decodeSampled(context, uri)?.asImageBitmap()
    }

private fun decodeSampled(
    context: Context,
    uri: Uri,
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SAMPLE_EDGE) sample *= 2
    val opts =
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    return runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
}

/** `#RRGGBB` huruf besar. Alpha diabaikan karena seed selalu opak. */
fun Color.toHexString(): String = toHex()

/** Parse `#RGB`, `#RRGGBB`, atau `RRGGBB`. Mengembalikan null kalau tidak valid. */
fun parseHexColor(input: String): Color? {
    val hex = input.trim().removePrefix("#")
    val full =
        when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6 -> hex
            else -> return null
        }
    val rgb = full.toIntOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or rgb)
}

/** Nada 0 sampai 100 dari satu palet tonal, dibangun dari warna [seed]. */
fun tonalRamp(
    seed: Color,
    tones: List<Int> = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100),
): List<Pair<Int, Color>> {
    val palette = TonalPalette.from(seed)
    return tones.map { it to palette.toneColor(it) }
}
