package com.uwu.animex.data.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CuplixClipExport {
    private const val POLL_MS = 300L
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun export(
        ctx: Context,
        url: String,
        startMs: Long,
        endMs: Long,
        output: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
        output.delete()
        val holder = ProgressHolder()
        val handler = Handler(Looper.getMainLooper())
        coroutineScope {
            var running: Transformer? = null
            val poller =
                launch {
                    while (true) {
                        delay(POLL_MS)
                        val current = running ?: continue
                        if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.toFloat())
                        }
                    }
                }
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val dataSource = OkHttpDataSource.Factory(NetworkModule.streamClient).setUserAgent(BROWSER_UA)
                    val clipping =
                        MediaItem.ClippingConfiguration
                            .Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build()
                    val item =
                        MediaItem
                            .Builder()
                            .setUri(url)
                            .setClippingConfiguration(clipping)
                            .build()
                    val transformer =
                        Transformer
                            .Builder(ctx)
                            .setAssetLoaderFactory(
                                DefaultAssetLoaderFactory(
                                    ctx,
                                    DefaultDecoderFactory.Builder(ctx).build(),
                                    Clock.DEFAULT,
                                    DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource),
                                    DataSourceBitmapLoader.Builder(ctx).build(),
                                ),
                            ).addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                    ) {
                                        if (cont.isActive) cont.resume(Unit)
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException,
                                    ) {
                                        if (cont.isActive) cont.resumeWithException(exportException)
                                    }
                                },
                            ).build()
                    running = transformer
                    cont.invokeOnCancellation { handler.post { transformer.cancel() } }
                    transformer.start(EditedMediaItem.Builder(item).build(), output.absolutePath)
                }
            } finally {
                poller.cancel()
            }
        }
    }
}
