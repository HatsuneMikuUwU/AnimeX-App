package com.uwu.animex.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.network.NetworkModule
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object TraceMoe {
    private val gson = Gson()
    private val http = NetworkModule.client.newBuilder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    data class Title(
        val native: String? = null,
        val romaji: String? = null,
        val english: String? = null,
    ) {
        fun best(): String =
            listOfNotNull(english, romaji, native).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    data class AnilistInfo(
        val id: Long? = null,
        val idMal: Long? = null,
        val title: Title? = null,
        @SerializedName("isAdult") val isAdult: Boolean? = null,
    )

    data class Result(
        val anilist: AnilistInfo? = null,
        val filename: String? = null,
        val episode: Any? = null,
        val from: Double? = null,
        val to: Double? = null,
        val similarity: Double? = null,
        val video: String? = null,
        val image: String? = null,
    ) {
        val episodeLabel: String
            get() = when (val e = episode) {
                null -> ""
                is Number -> e.toInt().toString()
                else -> e.toString().trim()
            }

        val similarityPercent: Int
            get() = ((similarity ?: 0.0) * 100).toInt().coerceIn(0, 100)

        val displayTitle: String
            get() = anilist?.title?.best()?.takeIf { it.isNotBlank() }
                ?: filename?.substringBeforeLast(".")?.takeIf { it.isNotBlank() }
                ?: "Tidak dikenal"
    }

    data class Response(
        val frameCount: Long? = null,
        val error: String? = null,
        val result: List<Result>? = null,
    )

    fun compress(context: Context, uri: Uri, maxSide: Int = 1280, quality: Int = 85): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = run {
            val largest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var s = 1
            while (largest / s > maxSide * 2) s *= 2
            s
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("Gagal baca gambar")
        val scale = min(1f, maxSide.toFloat() / max(bmp.width, bmp.height).toFloat())
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (w == bmp.width && h == bmp.height) bmp
        else Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return out.toByteArray()
    }

    suspend fun search(imageJpeg: ByteArray): List<Result> = withContext(Dispatchers.IO) {
        val body = imageJpeg.toRequestBody("image/jpeg".toMediaType())
        val req = Request.Builder()
            .url("https://api.trace.moe/search?anilistInfo=1&cutBorders=1")
            .post(body)
            .header("User-Agent", "AnimeX/1.0 (trace.moe client)")
            .build()
        http.newCall(req).execute().use { resp ->
            val json = resp.body.string()
            if (!resp.isSuccessful) {
                error("trace.moe ${resp.code}: ${json.take(120)}")
            }
            val parsed = gson.fromJson(json, Response::class.java)
            if (!parsed.error.isNullOrBlank()) error(parsed.error)
            parsed.result.orEmpty()
                .filter { (it.similarity ?: 0.0) >= 0.5 }
                .sortedByDescending { it.similarity ?: 0.0 }
                .distinctBy { it.displayTitle.lowercase() }
                .take(8)
        }
    }
}
