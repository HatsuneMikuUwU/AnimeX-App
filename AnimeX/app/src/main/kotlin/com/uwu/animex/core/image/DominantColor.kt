package com.uwu.animex.core.image

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.pow

/**
 * Ekstrak hue dominan dari poster untuk theme-from-cover-art.
 *
 * Strategi (cepat + tetap akurat):
 * - Decode kecil (64px) — hue dominan tidak butuh detail
 * - Semua kerja berat jalan di Dispatchers.Default (bukan main thread)
 * - Baca pixel sekali lewat getPixels() (bukan getPixel() per pixel)
 * - RGB → hue/sat/val inline (tanpa alokasi / colorToHSV), vibrancy pakai LUT,
 *   center-weight separable (row × col) jadi tidak ada exp() per pixel
 * - 72 bin hue + peak refine parabola, skip pixel transparan / abu-abu / near-black / near-white
 * - Cache 2 lapis: memory LRU + disk (SharedPreferences) → buka ulang poster = instan,
 *   bahkan setelah app di-restart
 * - Request yang sama (url sama) di-dedupe, tidak decode dobel
 */
object DominantColor {
    private const val SAMPLE = 64
    private const val HUE_BINS = 72 // 5° per bin
    private const val MIN_SAT = 0.12f
    private const val MIN_VAL = 0.10f
    private const val MAX_VAL = 0.97f
    /** Minimal bobot relatif terhadap total supaya tidak ambil noise. */
    private const val MIN_PEAK_RATIO = 0.04f

    private const val PREFS = "dominant_hue_cache"
    private const val PREFS_KEY = "entries"
    private const val DISK_MAX = 300

    /** Sentinel: poster sudah pernah dianalisis tapi terlalu netral (jangan diulang). */
    private const val NEUTRAL = -1f

    /** sat^1.4 untuk sat 0..255 — hindari pow() per pixel. */
    private val SAT_LUT = FloatArray(256) { (it / 255f).pow(1.4f) }

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > DISK_MAX
    }
    private var diskLoaded = false
    private val inFlight = HashMap<String, CompletableDeferred<Float?>>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cek cache saja (sinkron, murah) — aman dipanggil dari composition. */
    fun peek(context: Context, url: String): Float? {
        ensureDiskLoaded(context)
        val v = synchronized(lock) { cache[url] } ?: return null
        return v.takeIf { it >= 0f }
    }

    /**
     * @return hue 0..360, atau null kalau gagal / gambar terlalu netral.
     */
    suspend fun extractHue(context: Context, url: String): Float? {
        ensureDiskLoaded(context)
        synchronized(lock) { cache[url] }?.let { return it.takeIf { v -> v >= 0f } }

        val app = context.applicationContext
        var owner = false
        val deferred = synchronized(lock) {
            inFlight[url] ?: CompletableDeferred<Float?>().also {
                inFlight[url] = it
                owner = true
            }
        }
        if (!owner) return deferred.await()

        var hue: Float? = null
        var ok = false
        try {
            val req = ImageRequest.Builder(app)
                .data(url)
                .size(SAMPLE)
                .allowHardware(false)
                // jangan menuhin memory cache dengan thumbnail kecil
                .memoryCachePolicy(CachePolicy.READ_ONLY)
                .build()
            val result = app.imageLoader.execute(req) as? SuccessResult
            if (result != null) {
                ok = true
                hue = withContext(Dispatchers.Default) {
                    val bmp = runCatching { result.image.toBitmap() }.getOrNull()
                    bmp?.let { dominantHue(it) }
                }
            }
        } finally {
            synchronized(lock) { inFlight.remove(url) }
            deferred.complete(hue)
        }

        // Simpan juga hasil "netral" supaya poster abu-abu tidak dianalisis berulang.
        if (ok) {
            synchronized(lock) { cache[url] = hue ?: NEUTRAL }
            persist(app)
        }
        return hue
    }

    /** Hitung hue dominan. Aman dipanggil dari thread background. */
    fun dominantHue(bitmap: Bitmap): Float? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)

        // Center weight separable: exp(-2.2(dx²+dy²)) = exp(-2.2dx²) · exp(-2.2dy²)
        val cx = (w - 1) * 0.5f
        val cy = (h - 1) * 0.5f
        val invR = 1f / kotlin.math.sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
        val colW = FloatArray(w) { x ->
            val dx = (x - cx) * invR
            exp(-2.2f * dx * dx)
        }
        val rowW = FloatArray(h) { y ->
            val dy = (y - cy) * invR
            exp(-2.2f * dy * dy)
        }

        val bins = FloatArray(HUE_BINS)
        val minSat255 = (MIN_SAT * 255f).toInt()
        val minVal255 = (MIN_VAL * 255f).toInt()
        val maxVal255 = (MAX_VAL * 255f).toInt()
        val binScale = HUE_BINS / 360f
        var totalWeight = 0f

        var i = 0
        for (y in 0 until h) {
            val rw = rowW[y]
            for (x in 0 until w) {
                val c = px[i++]
                if ((c ushr 24) < 220) continue

                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                if (mx < minVal255 || mx > maxVal255) continue
                val delta = mx - mn
                if (delta == 0) continue
                val sat255 = delta * 255 / mx
                if (sat255 < minSat255) continue

                var hue = when (mx) {
                    r -> 60f * (g - b) / delta
                    g -> 60f * (b - r) / delta + 120f
                    else -> 60f * (r - g) / delta + 240f
                }
                if (hue < 0f) hue += 360f

                // Vibrancy: utamakan warna jenuh & tidak terlalu gelap
                val value = mx / 255f
                val vibrancy = SAT_LUT[sat255] * (0.35f + 0.65f * value)
                val weight = vibrancy * (0.35f + 0.65f * rw * colW[x])

                val bin = (hue * binScale).toInt().coerceIn(0, HUE_BINS - 1)
                bins[bin] += weight
                totalWeight += weight
            }
        }

        if (totalWeight < 1e-3f) return null

        // Cari peak; soft-blend 1 tetangga kiri-kanan biar cluster lebar tidak terpecah
        var best = -1
        var bestScore = 0f
        for (k in bins.indices) {
            val prev = bins[(k - 1 + HUE_BINS) % HUE_BINS]
            val next = bins[(k + 1) % HUE_BINS]
            val score = bins[k] + 0.45f * (prev + next)
            if (score > bestScore) {
                bestScore = score
                best = k
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

    // ---- Disk cache (satu string "url\thue" per baris, urutan = LRU lama → baru) ----

    private fun ensureDiskLoaded(context: Context) {
        if (diskLoaded) return
        synchronized(lock) {
            if (diskLoaded) return
            runCatching {
                val raw = context.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREFS_KEY, null)
                raw?.lineSequence()?.forEach { line ->
                    val t = line.lastIndexOf('\t')
                    if (t <= 0) return@forEach
                    val hue = line.substring(t + 1).toFloatOrNull() ?: return@forEach
                    cache[line.substring(0, t)] = hue
                }
            }
            diskLoaded = true
        }
    }

    private fun persist(context: Context) {
        ioScope.launch {
            val snapshot = synchronized(lock) {
                buildString {
                    for ((k, v) in cache) append(k).append('\t').append(v).append('\n')
                }
            }
            runCatching {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(PREFS_KEY, snapshot).apply()
            }
        }
    }
}
