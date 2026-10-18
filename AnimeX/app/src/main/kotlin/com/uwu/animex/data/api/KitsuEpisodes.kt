package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Mengambil thumbnail (poster) tiap episode dari Kitsu, mengikuti fitur
 * "Show posters from Kitsu" di CloudStream. Anime dicari lewat ID MAL
 * (hasil [MalIdLookup]) lalu dipetakan ke Kitsu via `lookupMapping`.
 */
object KitsuEpisodes {
    /** Data satu episode dari Kitsu. Salah satu field boleh null. */
    data class Info(
        val thumbnail: String? = null,
        val synopsis: String? = null,
    )

    private data class Description(
        val en: String? = null,
    )

    private data class Original(
        val url: String? = null,
    )

    private data class Thumbnail(
        val original: Original? = null,
    )

    private data class Node(
        val number: Int? = null,
        val description: Description? = null,
        val thumbnail: Thumbnail? = null,
    )

    private data class Episodes(
        val nodes: List<Node?>? = null,
    )

    private data class Mapping(
        val episodes: Episodes? = null,
    )

    private data class DataNode(
        val lookupMapping: Mapping? = null,
    )

    private data class Response(
        @SerializedName("data") val data: DataNode? = null,
    )

    private const val ENDPOINT = "https://kitsu.io/api/graphql"
    private val JSON = "application/json".toMediaType()
    private val gson = Gson()

    // movieId -> (nomor episode -> info). Map kosong = sudah dicek, Kitsu tidak punya datanya.
    private val cache = ConcurrentHashMap<String, Map<Int, Info>>()

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    private fun query(malId: Int): String =
        "query { lookupMapping(externalId: $malId, externalSite: MYANIMELIST_ANIME) { " +
            "__typename ... on Anime { id episodes(first: 2000) { nodes { number " +
            "description thumbnail { original { url } } } } } } }"

    private suspend fun fetch(malId: Int): Map<Int, Info> =
        withContext(Dispatchers.IO) {
            val payload = JsonObject().apply { addProperty("query", query(malId)) }.toString()
            val req =
                Request
                    .Builder()
                    .url(ENDPOINT)
                    .header("Accept", "application/json")
                    .header("Origin", "https://kitsu.io")
                    .post(payload.toRequestBody(JSON))
                    .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("Kitsu HTTP ${r.code}")
                gson
                    .fromJson(r.body.string(), Response::class.java)
                    ?.data
                    ?.lookupMapping
                    ?.episodes
                    ?.nodes
                    .orEmpty()
                    .mapNotNull { n ->
                        val num = n?.number ?: return@mapNotNull null
                        val thumb = UrlSecurity.secureOrNull(n.thumbnail?.original?.url)
                        val synopsis = n.description?.en?.trim()?.takeIf { it.isNotBlank() }
                        if (thumb == null && synopsis == null) return@mapNotNull null
                        num to Info(thumb, synopsis)
                    }.toMap()
            }
        }

    /** Peta nomor episode -> thumbnail dan sinopsis Kitsu, atau kosong bila tidak tersedia / gagal. */
    suspend fun episodes(movieId: String?): Map<Int, Info> {
        movieId ?: return emptyMap()
        cache[movieId]?.let { return it }
        val malId = MalIdLookup.find(movieId) ?: return emptyMap().also { cache[movieId] = it }
        return try {
            fetch(malId).also { cache[movieId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap() // gagal jaringan: jangan di-cache supaya bisa dicoba lagi
        }
    }
}
