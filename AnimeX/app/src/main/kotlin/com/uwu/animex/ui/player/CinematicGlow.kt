package com.uwu.animex.ui.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import kotlin.coroutines.resume
import kotlin.math.exp
import kotlinx.coroutines.suspendCancellableCoroutine

/** Interval sampling frame video buat glow. Bitmap-nya cuma 32x18, jadi murah. */
internal const val GLOW_SAMPLE_INTERVAL_MS = 600L
private const val GLOW_W = 32
private const val GLOW_H = 18

/**
 * Ambil snapshot kecil dari frame video yang lagi tampil. Return null kalau surface belum siap,
 * konten diproteksi (DRM), atau API-nya gak mendukung.
 */
internal suspend fun PlayerView.grabGlowFrame(): ImageBitmap? {
    val view = videoSurfaceView ?: return null
    if (view.width <= 0 || view.height <= 0) return null
    return when (view) {
        is TextureView -> {
            if (!view.isAvailable) return null
            val bmp = Bitmap.createBitmap(GLOW_W, GLOW_H, Bitmap.Config.ARGB_8888)
            runCatching { view.getBitmap(bmp) }.getOrNull()?.asImageBitmap()
        }
        is SurfaceView -> {
            if (!view.holder.surface.isValid) return null
            val bmp = Bitmap.createBitmap(GLOW_W, GLOW_H, Bitmap.Config.ARGB_8888)
            val ok =
                suspendCancellableCoroutine<Boolean> { cont ->
                    runCatching {
                        PixelCopy.request(
                            view,
                            bmp,
                            { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) },
                            Handler(Looper.getMainLooper()),
                        )
                    }.onFailure { if (cont.isActive) cont.resume(false) }
                }
            if (ok) bmp.asImageBitmap() else null
        }
        else -> null
    }
}

private const val GLOW_SCALE_Y = 1.9f
private const val GLOW_TAU_SECONDS = 0.6f

/**
 * Warna glow yang ditransisikan halus (exponential smoothing per piksel) ke warna frame terbaru,
 * jadi gak ada kedip / cross-fade yang putus-putus tiap sampling. Fade ke bawah sudah "dipanggang"
 * ke alpha bitmap-nya.
 */
@Stable
internal class GlowState {
    private val bitmap = Bitmap.createBitmap(GLOW_W, GLOW_H, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bitmap.asImageBitmap()
    private val cur = FloatArray(GLOW_W * GLOW_H * 3)
    private val tgt = IntArray(GLOW_W * GLOW_H)
    private val out = IntArray(GLOW_W * GLOW_H)
    private val rowAlpha =
        IntArray(GLOW_H) { row ->
            val y = (row + 0.5f) / GLOW_H
            // Penuh sampai tepi bawah video (~55% layer), lalu pudar habis di ujung bawah.
            val t = ((y - 0.55f) / 0.45f).coerceIn(0f, 1f)
            ((1f - t) * 255f).toInt()
        }
    var hasData = false
        private set
    var tick by mutableIntStateOf(0)
        private set

    fun setTarget(frame: ImageBitmap) {
        frame.readPixels(tgt, 0, 0, GLOW_W, GLOW_H)
    }

    fun snap() {
        for (i in tgt.indices) {
            val c = tgt[i]
            cur[i * 3] = ((c shr 16) and 0xFF).toFloat()
            cur[i * 3 + 1] = ((c shr 8) and 0xFF).toFloat()
            cur[i * 3 + 2] = (c and 0xFF).toFloat()
        }
        hasData = true
    }

    /** @return true kalau sudah (hampir) sama dengan target. */
    fun step(k: Float): Boolean {
        var maxDiff = 0f
        for (i in tgt.indices) {
            val c = tgt[i]
            val r = ((c shr 16) and 0xFF).toFloat()
            val g = ((c shr 8) and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()
            val dr = r - cur[i * 3]
            val dg = g - cur[i * 3 + 1]
            val db = b - cur[i * 3 + 2]
            cur[i * 3] += dr * k
            cur[i * 3 + 1] += dg * k
            cur[i * 3 + 2] += db * k
            maxDiff = maxOf(maxDiff, kotlin.math.abs(dr), kotlin.math.abs(dg), kotlin.math.abs(db))
        }
        return maxDiff < 1f
    }

    fun render() {
        for (i in out.indices) {
            val a = rowAlpha[i / GLOW_W]
            out[i] =
                (a shl 24) or
                (cur[i * 3].toInt().coerceIn(0, 255) shl 16) or
                (cur[i * 3 + 1].toInt().coerceIn(0, 255) shl 8) or
                cur[i * 3 + 2].toInt().coerceIn(0, 255)
        }
        bitmap.setPixels(out, 0, GLOW_W, 0, 0, GLOW_W, GLOW_H)
        tick++
    }
}

@Composable
internal fun rememberGlowState(
    frame: ImageBitmap?,
    reduceMotion: Boolean,
): GlowState {
    val state = remember { GlowState() }
    LaunchedEffect(frame) {
        if (frame == null) return@LaunchedEffect
        state.setTarget(frame)
        if (!state.hasData || reduceMotion) {
            state.snap()
            state.render()
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val done = state.step(1f - exp(-dt / GLOW_TAU_SECONDS))
            state.render()
            if (done) break
        }
    }
    return state
}

/**
 * Cahaya ambient ala "Cinematic lighting" YouTube: warna video yang diblur & diperbesar ke bawah
 * dari tepi player, lalu pudar. Ukurannya ngikutin kotak video 16:9.
 */
@Composable
internal fun CinematicGlow(
    state: GlowState,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (!state.hasData) return
    Box(
        modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = GLOW_SCALE_Y
                transformOrigin = TransformOrigin(0.5f, 0f)
                this.alpha = alpha()
            },
    ) {
        val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(28.dp) else Modifier
        Canvas(Modifier.fillMaxSize().then(blurModifier)) {
            state.tick // baca supaya redraw tiap warna berubah
            drawImage(
                image = state.image,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
