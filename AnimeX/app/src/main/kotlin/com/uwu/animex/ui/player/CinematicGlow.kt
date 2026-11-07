package com.uwu.animex.ui.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import kotlin.coroutines.resume
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

/**
 * Cahaya ambient ala "Cinematic lighting" YouTube: frame video yang diblur & diperbesar, nempel
 * di bawah player lalu pudar ke background. Taruh di belakang konten, ukurannya ngikutin kotak video 16:9.
 */
@Composable
internal fun CinematicGlow(
    frame: ImageBitmap?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                // Layer diperlebar ke bawah (1.9x tinggi) dari tepi atas; bagian di bawah video yang kelihatan.
                scaleX = 1.12f
                scaleY = 1.9f
                transformOrigin = TransformOrigin(0.5f, 0f)
                alpha = 0.9f
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawWithContent {
                drawContent()
                // Penuh sampai tepi bawah video (~55% layer), lalu pudar habis di ujung bawah.
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            0f to Color.Black,
                            0.55f to Color.Black,
                            1f to Color.Transparent,
                        ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Crossfade(
            targetState = frame,
            animationSpec = if (reduceMotion) snap() else tween(durationMillis = 700),
            label = "cinematicGlow",
            modifier = Modifier.fillMaxSize(),
        ) { f ->
            if (f != null) {
                val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(28.dp) else Modifier
                Canvas(Modifier.fillMaxSize().then(blurModifier)) {
                    drawImage(
                        image = f,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
    }
}
