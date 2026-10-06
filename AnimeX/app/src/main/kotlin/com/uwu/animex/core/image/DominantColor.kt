package com.uwu.animex.core.image

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.exp
import kotlin.math.pow

/**
 * Ekstrak hue dominan dari poster untuk theme-from-cover-art.
 *
 * Strategi (lebih akurat dari histogram kasar):
 * - Decode 128px (cukup detail, masih ringan)
 * - 72 bin hue (resolusi 5°)
 * - Bobot vibrancy × center-weight (subjek poster biasanya di tengah)
 * - Peak refine dengan interpolasi parabola antar-bin
 * - Skip pixel transparan / abu-abu / near-black / near-white
 */
object DominantColor {
    private const val SAMPLE = 128
    private const val HUE_BINS = 72 // 5° per bin
    private const val MIN_SAT = 0.12f
    private const val MIN_VAL = 0.10f
    private const val MAX_VAL = 0.97f
    /** Minimal bobot relatif terhadap total supaya tidak ambil noise. */
    private const val MIN_PEAK_RATIO = 0.04f

    private val cache = object : LinkedHashMap<String, Float>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 64
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
        val cx = (w - 1) * 0.5f
        val cy = (h - 1) * 0.5f
        // Radius normalisasi: diagonal setengah
        val invR = 1f / (sqrt(cx * cx + cy * cy).coerceAtLeast(1f))

        // Sample padat — step 1 di bitmap yang sudah di-downscale Coil
        var totalWeight = 0f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = bitmap.getPixel(x, y)
                val a = (c ushr 24) and 0xFF
                if (a < 220) continue

                android.graphics.Color.colorToHSV(c, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]
                if (sat < MIN_SAT || value < MIN_VAL || value > MAX_VAL) continue

                // Vibrancy: utamakan warna jenuh & tidak terlalu gelap
                // (mirip scoring Android Palette / Material dynamic color)
                val vibrancy = sat.pow(1.4f) * (0.35f + 0.65f * value)

                // Center weight: gaussian kasar — subjek poster biasanya di tengah,
                // tepi sering border/teks/logo
                val dx = (x - cx) * invR
                val dy = (y - cy) * invR
                val center = exp(-2.2f * (dx * dx + dy * dy)).toFloat()

                val weight = vibrancy * (0.35f + 0.65f * center)
                val bin = ((hue / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
                bins[bin] += weight
                totalWeight += weight
            }
        }

        if (totalWeight < 1e-3f) return null

        // Cari peak; soft-blend 1 tetangga kiri-kanan biar cluster lebar tidak terpecah
        var best = -1
        var bestScore = 0f
        for (i in bins.indices) {
            val prev = bins[(i - 1 + HUE_BINS) % HUE_BINS]
            val next = bins[(i + 1) % HUE_BINS]
            val score = bins[i] + 0.45f * (prev + next)
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        if (best < 0 || bins[best] / totalWeight < MIN_PEAK_RATIO) return null

        // Parabolic interpolation untuk sub-bin accuracy
        val y0 = bins[(best - 1 + HUE_BINS) % HUE_BINS]
        val y1 = bins[best]
        val y2 = bins[(best + 1) % HUE_BINS]
        val denom = 2f * (2f * y1 - y0 - y2)
        val delta = if (denom > 1e-6f || denom < -1e-6f) {
            ((y0 - y2) / denom).coerceIn(-0.5f, 0.5f)
        } else {
            0f
        }

        return ((best + 0.5f + delta) * (360f / HUE_BINS) + 360f) % 360f
    }

    /** Preview swatch dari hue (buat debug / settings). */
    fun previewColor(hue: Float, dark: Boolean): Color =
        if (dark) Color.hsl(hue, 0.85f, 0.80f) else Color.hsl(hue, 0.45f, 0.40f)

    private fun sqrt(v: Float): Float = kotlin.math.sqrt(v.toDouble()).toFloat()
}
