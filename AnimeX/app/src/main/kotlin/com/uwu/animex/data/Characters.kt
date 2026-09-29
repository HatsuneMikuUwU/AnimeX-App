package com.uwu.animex.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class CharacterRole { MAIN, SUPPORTING, BACKGROUND }

data class Person(val name: String, val image: String?)

data class AnimeCharacter(
    val id: Int? = null,
    val character: Person,
    val role: CharacterRole?,
    val voiceActor: Person?,
)

object CharacterRepo {
    private const val BASE = "https://api.jikan.moe/v4"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val cache = object : LinkedHashMap<String, List<AnimeCharacter>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<AnimeCharacter>>?) =
            size > 40
    }

    suspend fun load(movie: Movie): List<AnimeCharacter> {
        val key = movie.id ?: return emptyList()
        synchronized(cache) { cache[key] }?.let { return it }

        val result = runCatching { fetch(movie) }.getOrNull() ?: return emptyList()
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun fetch(movie: Movie): List<AnimeCharacter> {
        Mal.malIdFor(movie.id)?.let { malId ->
            val list = parse(get("$BASE/anime/$malId/characters"))
            if (list.isNotEmpty()) return list
        }

        for (q in titleQueries(movie.title.orEmpty())) {
            val malId = searchMalId(q) ?: continue
            val list = parse(get("$BASE/anime/$malId/characters"))
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    private suspend fun searchMalId(query: String): Int? {
        val url = "$BASE/anime".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "1")
            .build()
            .toString()
        return get(url)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.firstOrNull()?.asObjOrNull()
            ?.get("mal_id")?.takeIf { !it.isJsonNull }?.asInt
    }

    private fun titleQueries(title: String): List<String> {
        val noParen = title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        val clean = noParen.replace(Regex("(?i)subtitle indonesia|sub indo"), " ")
        return listOf(title, clean)
            .map { it.replace(Regex("\\s+"), " ").trim().take(64) }
            .filter { it.length >= 3 }
            .distinct()
    }

    private suspend fun get(url: String): JsonElement? = withContext(Dispatchers.IO) {
        repeat(3) { attempt ->
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val (code, body) = http.newCall(req).execute().use { it.code to it.body.string() }
            when {
                code == 404 -> return@withContext null
                code == 429 || code >= 500 -> delay(1200L * (attempt + 1))
                code in 200..299 -> return@withContext JsonParser.parseString(body).asJsonObject
                    .get("data")?.takeIf { !it.isJsonNull }
                else -> error("Jikan HTTP $code")
            }
        }
        error("Jikan tidak merespons")
    }

    private fun parse(data: JsonElement?): List<AnimeCharacter> {
        val edges = data?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return edges.mapNotNull { e ->
            val edge = e.asObjOrNull() ?: return@mapNotNull null
            val node = edge.get("character")?.asObjOrNull() ?: return@mapNotNull null
            val name = node.str("name")?.flipName() ?: return@mapNotNull null
            AnimeCharacter(
                id = node.get("mal_id")?.takeIf { !it.isJsonNull }?.asInt,
                character = Person(name, node.image()),
                role = when (edge.str("role")) {
                    "Main" -> CharacterRole.MAIN
                    "Supporting" -> CharacterRole.SUPPORTING
                    else -> null
                },
                voiceActor = edge.get("voice_actors")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.asObjOrNull() }
                    ?.firstOrNull { it.str("language") == "Japanese" }
                    ?.get("person")?.asObjOrNull()
                    ?.let { p ->
                        val vaName = p.str("name")?.flipName() ?: return@let null
                        Person(vaName, p.image())
                    },
            )
        }
            .sortedBy { if (it.role == CharacterRole.MAIN) 0 else 1 }
            .take(50)
    }

    private fun JsonElement.asObjOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.str(k: String): String? =
        get(k)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun String.flipName(): String {
        val i = indexOf(", ")
        return if (i < 0) this else substring(i + 2) + " " + substring(0, i)
    }

    private fun JsonObject.image(): String? {
        val images = get("images")?.asObjOrNull() ?: return null
        val url = images.get("jpg")?.asObjOrNull()?.str("image_url")
            ?: images.get("webp")?.asObjOrNull()?.str("image_url")
        return url?.takeUnless { it.contains("questionmark") }
    }
}
