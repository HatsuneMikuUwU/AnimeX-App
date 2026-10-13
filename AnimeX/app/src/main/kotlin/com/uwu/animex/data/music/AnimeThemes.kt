package com.uwu.animex.data.music

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.core.network.toApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val anime: String,
    val tag: String,
    val cover: String?,
    val audioUrl: String,
)

object AnimeThemes {
    private const val ENDPOINT = "https://api.animethemes.moe/animetheme"
    private const val INCLUDE = "anime.images,song.artists,animethemeentries.videos.audio"
    private const val FETCH_SIZE = 40
    private const val KEEP = 16
    private val COVER_FACETS = listOf("Large Cover", "Small Cover")

    suspend fun discover(): List<Song> {
        var failure: Exception? = null
        for (filtered in listOf(true, false)) {
            try {
                val list = request(filtered)
                if (list.isNotEmpty()) return list
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (failure == null) failure = e
            }
        }
        failure?.let { throw it }
        return emptyList()
    }

    private suspend fun request(filtered: Boolean): List<Song> {
        val body =
            withContext(Dispatchers.IO) {
                val url =
                    ENDPOINT
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("include", INCLUDE)
                        .addQueryParameter("sort", "random")
                        .addQueryParameter("page[size]", "$FETCH_SIZE")
                        .apply { if (filtered) addQueryParameter("filter[has]", "song") }
                        .build()
                val call =
                    NetworkModule.apiClient.newCall(
                        Request.Builder().url(url).header("Accept", "application/json").build(),
                    )
                val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { r ->
                        val text = r.body.string()
                        if (!r.isSuccessful) throw ApiException.Http(r.code)
                        text
                    }
                } catch (e: IOException) {
                    throw e.toApiException()
                } finally {
                    handle.dispose()
                }
            }
        return withContext(Dispatchers.Default) {
            try {
                JsonParser
                    .parseString(body)
                    .asJsonObject
                    .arr("animethemes")
                    .mapNotNull(::toSong)
                    .distinctBy { it.id }
                    .take(KEEP)
            } catch (e: JsonParseException) {
                throw ApiException.Parse(e)
            } catch (e: IllegalStateException) {
                throw ApiException.Parse(e)
            }
        }
    }

    private fun toSong(theme: JsonObject): Song? {
        val id = theme.str("id") ?: return null
        val song = theme.obj("song") ?: return null
        val title = song.str("title") ?: return null
        val artist =
            song
                .arr("artists")
                .mapNotNull { it.str("name") }
                .joinToString(", ")
                .ifBlank { "Unknown artist" }
        val anime = theme.obj("anime")
        val audio =
            theme
                .arr("animethemeentries")
                .asSequence()
                .flatMap { it.arr("videos").asSequence() }
                .mapNotNull { it.obj("audio")?.str("link") }
                .mapNotNull { UrlSecurity.secureOrNull(it) }
                .firstOrNull() ?: return null
        val images = anime?.arr("images").orEmpty()
        val cover =
            COVER_FACETS
                .firstNotNullOfOrNull { facet -> images.firstOrNull { it.str("facet") == facet }?.str("link") }
                ?: images.firstNotNullOfOrNull { it.str("link") }
        return Song(
            id = id,
            title = title,
            artist = artist,
            anime = anime?.str("name").orEmpty(),
            tag = theme.str("slug") ?: theme.str("type").orEmpty(),
            cover = UrlSecurity.secureOrNull(cover),
            audioUrl = audio,
        )
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): List<JsonObject> =
        get(key)
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { e -> e.takeIf { it.isJsonObject }?.asJsonObject }
            .orEmpty()
}
