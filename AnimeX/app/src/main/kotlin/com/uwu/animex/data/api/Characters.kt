package com.uwu.animex.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class CharacterRole { MAIN, SUPPORTING, BACKGROUND }

data class Person(val name: String, val image: String?)

data class AnimeCharacter(
    val id: Int? = null,
    val character: Person,
    val role: CharacterRole?,
    val voiceActor: Person?,
)

object CharacterRepo {
    private const val ENDPOINT = "https://graphql.anilist.co"
    private val JSON = "application/json".toMediaType()

    private val http = NetworkModule.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val FIELDS = """
        id
        characters(sort: ROLE, page: 1, perPage: 50) {
          edges {
            role
            voiceActors(language: JAPANESE) {
              name { userPreferred full native }
              image { large medium }
            }
            node {
              id
              name { userPreferred full native }
              image { large medium }
            }
          }
        }
    """

    private const val BY_MAL =
        "query(${'$'}id: Int) { Media(idMal: ${'$'}id, type: ANIME) { $FIELDS } }"
    private const val BY_SEARCH =
        "query(${'$'}s: String) { Media(search: ${'$'}s, type: ANIME) { $FIELDS } }"

    private val cache = object : LinkedHashMap<String, List<AnimeCharacter>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<AnimeCharacter>>?) =
            size > 40
    }

    suspend fun load(movie: Movie): List<AnimeCharacter> {
        val key = movie.id ?: return emptyList()
        synchronized(cache) { cache[key] }?.let { return it }

        val result = runSuspendCatching { fetch(movie) }.getOrNull() ?: return emptyList()
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun fetch(movie: Movie): List<AnimeCharacter> {
        Mal.malIdFor(movie.id)?.let { malId ->
            val media = queryMedia(BY_MAL, "id" to malId)
            if (media != null) return parse(media)
        }

        for (q in titleQueries(movie.title.orEmpty())) {
            val media = queryMedia(BY_SEARCH, "s" to q)
            if (media != null) {
                val list = parse(media)
                if (list.isNotEmpty()) return list
            }
        }
        return emptyList()
    }

    private fun titleQueries(title: String): List<String> {
        val noParen = title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        val clean = noParen.replace(Regex("(?i)subtitle indonesia|sub indo"), " ")
        return listOf(title, clean)
            .map { it.replace(Regex("\\s+"), " ").trim().take(64) }
            .filter { it.length >= 3 }
            .distinct()
    }

    private suspend fun queryMedia(q: String, variable: Pair<String, Any>): JsonObject? =
        queryRaw(q, variable)?.get("Media")?.asObjOrNull()

    private suspend fun queryRaw(q: String, variable: Pair<String, Any>): JsonObject? =
        withContext(Dispatchers.IO) {
            val vars = JsonObject().apply {
                when (val v = variable.second) {
                    is Int -> addProperty(variable.first, v)
                    else -> addProperty(variable.first, v.toString())
                }
            }
            val payload = JsonObject().apply {
                addProperty("query", q)
                add("variables", vars)
            }.toString()
            val req = Request.Builder()
                .url(ENDPOINT)
                .header("Accept", "application/json")
                .post(payload.toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { r ->
                val body = r.body.string()
                if (r.code == 404) return@use null
                if (!r.isSuccessful) error("AniList HTTP ${r.code}")
                JsonParser.parseString(body).asJsonObject
                    .get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            }
        }

    private fun parse(media: JsonObject): List<AnimeCharacter> {
        val edges = media.get("characters")?.asObjOrNull()?.get("edges")
            ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return edges.mapNotNull { e ->
            val edge = e.asObjOrNull() ?: return@mapNotNull null
            val node = edge.get("node")?.asObjOrNull() ?: return@mapNotNull null
            val name = node.personName() ?: return@mapNotNull null
            val id = node.get("id")?.takeIf { !it.isJsonNull }?.asInt
            AnimeCharacter(
                id = id,
                character = Person(name, node.personImage()),
                role = when (edge.get("role")?.takeIf { !it.isJsonNull }?.asString) {
                    "MAIN" -> CharacterRole.MAIN
                    "SUPPORTING" -> CharacterRole.SUPPORTING
                    "BACKGROUND" -> CharacterRole.BACKGROUND
                    else -> null
                },
                voiceActor = edge.get("voiceActors")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.firstNotNullOfOrNull { va ->
                        val o = va.asObjOrNull() ?: return@firstNotNullOfOrNull null
                        Person(o.personName() ?: return@firstNotNullOfOrNull null, o.personImage())
                    },
            )
        }
    }

    private fun JsonElement.asObjOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.str(k: String): String? =
        get(k)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.personName(): String? {
        val n = get("name")?.asObjOrNull() ?: return null
        return n.personNameFromObj()
    }

    private fun JsonObject.personNameFromObj(): String? =
        str("userPreferred") ?: str("full") ?: str("native")

    private fun JsonObject.personImage(): String? {
        val i = get("image")?.asObjOrNull() ?: return null
        return i.str("large") ?: i.str("medium")
    }
}
