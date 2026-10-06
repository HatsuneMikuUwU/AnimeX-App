package com.uwu.animex.core.image

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * Ekstrak hue dominan dari poster untuk theme-from-cover-art.
 * Skip pixel abu-abu / terlalu gelap / terlalu terang agar hasilnya “hidup”.
 */
object DominantColor {
    private const val SAMPLE = 48
    private const val HUE_BINS = 36 // 10° per bin
    private const val MIN_SAT = 0.18f
    private const val MIN_VAL = 0.12f
    private const val MAX_VAL = 0.95f

    /** Cache sederhana biar buka detail yang sama nggak hitung ulang. */
    private val cache = object : LinkedHashMap<String, Float>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 48
    }

    /**
     * @return hue 0..360, atau null kalau gagal / gambar terlalu netral.
     */
    suspend fun extractHue(context: Context, url: String): Float? {
        synchronized(cache) { cache[url] }?.let { return it }

        val result = runCatching {
            val req = ImageRequest.Builder(context)
                .data(url)
                .size(SAMPLE)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(req)
        }.getOrNull() as? SuccessResult ?: return null

        val bitmap = runCatching { result.image.toBitmap() }.getOrNull() ?: return null
        val hue = dominantHue(bitmap) ?: return null

        synchronized(cache) { cache[url] = hue }
        return hue
    }

    fun dominantHue(bitmap: Bitmap): Float? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val bins = FloatArray(HUE_BINS)
        val hsv = FloatArray(3)
        val stepX = maxOf(1, w / SAMPLE)
        val stepY = maxOf(1, h / SAMPLE)

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val a = (c ushr 24) and 0xFF
                if (a < 200) {
                    x += stepX
                    continue
                }
                android.graphics.Color.colorToHSV(c, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                if (sat >= MIN_SAT && value in MIN_VAL..MAX_VAL) {
                    val bin = ((hsv[0] / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
                    // Bobot: saturasi × value → warna cerah & jenuh lebih diunggulkan
                    bins[bin] += sat * value
                }
                x += stepX
            }
            y += stepY
        }

        var best = -1
        var bestW = 0f
        for (i in bins.indices) {
            if (bins[i] > bestW) {
                bestW = bins[i]
                best = i
            }
        }
        if (best < 0 || bestW < 0.5f) return null

        // Soften: rata-rata bin tetangga berbobot
        val prev = bins[(best - 1 + HUE_BINS) % HUE_BINS]
        val next = bins[(best + 1) % HUE_BINS]
        val total = prev + bestW + next
        val offset = if (total > 0f) (next - prev) / total else 0f
        return ((best + 0.5f + offset) * (360f / HUE_BINS) + 360f) % 360f
    }

    /** Preview swatch dari hue (buat debug / settings). */
    fun previewColor(hue: Float, dark: Boolean): Color =
        if (dark) Color.hsl(hue, 0.85f, 0.80f) else Color.hsl(hue, 0.45f, 0.40f)
}
