package com.uwu.animex.data.wibutime

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.core.cache.ResponseCache
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.model.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** Wibutime Supabase API. Isolasi endpoint/cache dijaga terpisah dari Api (AnimeIn). */
object WibutimeApi {
    private const val BASE_URL = "https://paivcxbhxyytczbpyzla.supabase.co/rest/v1/"

    // Supabase anon key bersifat public dan memang dipakai oleh client untuk endpoint publik.
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBhaXZjeGJoeHl5dGN6YnB5emxhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk1MDM0NjIsImV4cCI6MjEwNTA3OTQ2Mn0.UzXU9_Z8wBj_nE7FVukW1UB64j5mI2CoFKb0dTLssfc"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private const val TABLE_LIMIT = 1000
    private const val PAGE_SIZE = Api.API_LIMIT
    private val gson = Gson()
    private val cache = ResponseCache()

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            cache.init(File(context.applicationContext.cacheDir, "wibutime_api_cache"))
            initialized = true
        }
    }

    fun clearCache() = cache.clear()

    private suspend fun json(
        path: String,
        params: Map<String, String> = emptyMap(),
        body: String? = null,
        force: Boolean = false,
    ): JsonElement {
        val cacheKey = "$path?${params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }}"
        if (!force) {
            cache.getMemory(cacheKey, CACHE_TTL_MS)?.let { return JsonParser.parseString(it) }
            withContext(
                Dispatchers.IO,
            ) { cache.getDisk(cacheKey, CACHE_TTL_MS) }?.let { return JsonParser.parseString(it) }
        }
        val url =
            (BASE_URL + path.trimStart('/'))
                .toHttpUrl()
                .newBuilder()
                .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
                .build()
        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request =
            Request
                .Builder()
                .url(url)
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .header("Accept", "application/json")
                .apply { if (body != null) post(requestBody!!) }
                .build()
        val responseText =
            withContext(Dispatchers.IO) {
                NetworkModule.apiClient.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) throw ApiException.Http(response.code)
                    text
                }
            }
        if (!force) withContext(Dispatchers.IO) { cache.put(cacheKey, responseText) }
        return JsonParser.parseString(responseText)
    }

    private fun JsonElement?.objOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? =
        get(name)
            ?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun JsonObject.numberString(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString

    private fun JsonObject.stringList(name: String): List<String> {
        val value = get(name) ?: return emptyList()
        if (value.isJsonArray) {
            return value.asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonPrimitive }?.asString
            }
        }
        if (value.isJsonPrimitive) {
            return value.asString
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
        }
        return emptyList()
    }

    private fun JsonObject.toMovie(day: String? = null): Movie {
        val id = string("slug") ?: string("id") ?: string("anime_id")
        val genres = stringList("genres").joinToString(", ").ifBlank { string("genre").orEmpty() }
        val episodeNumber = string("latest_episode_number") ?: string("total_episodes")
        return Movie(
            id = id,
            title = string("title") ?: string("name"),
            synopsis = string("synopsis"),
            synonyms = stringList("alt_titles").joinToString(", ").ifBlank { string("title_lower") },
            image_poster = string("poster_url") ?: string("poster") ?: string("image_poster"),
            image_cover = string("poster_url") ?: string("poster_art") ?: string("image_cover"),
            type = string("type"),
            year = numberString("year"),
            status = string("status"),
            genre = genres,
            studio = string("studio"),
            views = numberString("view_count") ?: "0",
            favorites = numberString("favorite_count") ?: "0",
            aired_start = string("created_at"),
            day = day ?: string("day_of_week") ?: string("air_day"),
            time = string("air_time"),
            episode_index = episodeNumber,
            episode_id = string("latest_episode_id"),
            episode_title = string("episode_count_label"),
            key_time_update = string("latest_episode_created_at") ?: string("updated_at"),
            key_time = string("updated_at") ?: string("created_at"),
            score = numberString("rating"),
            duration = numberString("duration_minutes"),
        )
    }

    private fun JsonElement.arrayObjects(): List<JsonObject> =
        if (isJsonArray) {
            asJsonArray.mapNotNull { it.objOrNull() }
        } else {
            listOfNotNull(objOrNull())
        }

    private suspend fun animes(
        params: Map<String, String>,
        force: Boolean = false,
    ): List<JsonObject> = json("animes", params, force = force).arrayObjects()

    suspend fun home(force: Boolean = false): HomeData {
        val root = json("rpc/get_home_feed", body = "{}", force = force)
        val data = root.arrayObjects().firstOrNull() ?: root.objOrNull() ?: JsonObject()

        fun movies(key: String): List<Movie> =
            data
                .get(key)
                ?.arrayObjects()
                ?.map { it.toMovie() }
                .orEmpty()
        val hero = movies("hero_pool")
        val sliders = hero.mapNotNull { movie ->
            val id = movie.id ?: return@mapNotNull null
            val image = movie.image_cover ?: movie.image_poster ?: return@mapNotNull null
            Slider(id = id, image = image, type = "anime", link = id)
        }
        val latestAnime = movies("latest_anime")
        val latestAll = movies("latest_semua").ifEmpty { latestAnime }
        val trending = runCatching {
            json("rpc/get_trending_series", body = "{}", force = force).arrayObjects().mapNotNull { wrapper ->
                wrapper.get("card")?.objOrNull()?.let { card ->
                    card.toMovie().copy(views = wrapper.numberString("views") ?: "0")
                } ?: wrapper.toMovie()
            }
        }.getOrDefault(emptyList())
        val scheduleToday = runCatching { schedule(force) }.getOrDefault(emptyList())
        return HomeData(
            slider = sliders,
            update = latestAnime,
            hot = trending.ifEmpty { hero.take(18) },
            new = movies("animes"),
            today = scheduleToday,
            random = movies("latest_donghua"),
            waiting = movies("completed_anime"),
            popular = trending.ifEmpty { hero },
        ).let { base -> base.copy(update = latestAll.ifEmpty { base.update }) }
    }

    suspend fun homeMovies(
        section: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val feed = home(force)
        val sectionRows = when (section.lowercase()) {
            "update", "latest", "latest_anime" -> feed.update
            "hot", "trending" -> feed.hot
            "new", "animes", "latest_semua" -> feed.new.ifEmpty { feed.update }
            "today", "schedule" -> feed.today
            "waiting", "completed" -> feed.waiting
            "popular", "top" -> feed.popular
            "history" -> emptyList()
            else -> feed.new.ifEmpty { feed.update }
        }
        val start = page.coerceAtLeast(0) * Api.API_LIMIT
        return sectionRows.drop(start).take(Api.API_LIMIT)
    }

    suspend fun newEpisodes(
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> = homeMovies("update", page, force)

    suspend fun search(
        q: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val query = q.trim().lowercase()
        if (query.isBlank()) return emptyList()
        val rows = animes(
            mapOf(
                "select" to "*",
                "title_lower" to "ilike.*$query*",
                "limit" to PAGE_SIZE.toString(),
                "offset" to (page.coerceAtLeast(0) * PAGE_SIZE).toString(),
                "order" to "title.asc",
            ),
            force,
        )
        return rows.map { it.toMovie() }
    }

    suspend fun suggest(
        q: String,
        limit: Int = 6,
    ): List<Movie> = search(q).take(limit)

    suspend fun detailFull(
        id: String,
        force: Boolean = false,
    ): Api.DetailResult {
        val slug = id.trim()
        val row = animes(mapOf("select" to "*", "slug" to "eq.$slug", "limit" to "1"), force).firstOrNull()
        val movie = row?.toMovie()
        val latest = row?.let { anime ->
            val episodeId = anime.string("latest_episode_id")
            val episodeNumber = anime.string("latest_episode_number")
            if (episodeId != null || episodeNumber != null) {
                Episode(
                    id = episodeId,
                    index = episodeNumber,
                    title = anime.string("episode_count_label"),
                    image = anime.string("poster_url"),
                    id_movie = slug,
                )
            } else {
                null
            }
        }
        return Api.DetailResult(movie = movie, episode = latest)
    }

    suspend fun episodes(
        id: String,
        page: Int? = null,
        force: Boolean = false,
    ): List<Episode> {
        val params = linkedMapOf(
            "select" to "*",
            "anime_id" to "eq.${id.trim()}",
            "order" to "episode_number.asc",
            "limit" to TABLE_LIMIT.toString(),
        )
        if (page != null && page > 1) params["offset"] = ((page - 1) * TABLE_LIMIT).toString()
        return json("episodes", params, force = force).arrayObjects().map { row ->
            val number = row.numberString("episode_number") ?: row.string("episode_number")
            Episode(
                id = row.string("id"),
                index = number,
                title = row.string("title") ?: row.string("episode_title") ?: number?.let { "Episode $it" },
                image = row.string("poster_url") ?: row.string("image"),
                views = row.numberString("view_count"),
                key_time = row.string("created_at"),
                id_movie = row.string("anime_id") ?: id,
            )
        }
    }

    suspend fun servers(
        episodeId: String,
        force: Boolean = false,
    ): List<Server> {
        val row = json(
            "episodes",
            mapOf(
                "select" to "id,video_embed_url,video_qualities,video_qualities_fallback",
                "id" to "eq.$episodeId",
                "limit" to "1",
            ),
            force = force,
        ).arrayObjects().firstOrNull() ?: return emptyList()
        val result = mutableListOf<Server>()
        val qualities = row.get("video_qualities")?.objOrNull()
        qualities?.entrySet()?.forEach { (quality, value) ->
            val url = value.takeIf { it.isJsonPrimitive }?.asString
            UrlSecurity.secureOrNull(url)?.let {
                result +=
                    Server(
                        id = "$episodeId-$quality",
                        link = it,
                        quality = quality,
                        name = "Wibutime",
                        type = playbackType(it),
                    )
            }
        }
        val fallback = row.get("video_qualities_fallback")?.objOrNull()
        fallback?.entrySet()?.forEach { (quality, value) ->
            val list = if (value.isJsonArray) {
                value.asJsonArray.mapNotNull {
                    it
                        .takeIf { v ->
                            v.isJsonPrimitive
                        }?.asString
                }
            } else {
                listOfNotNull(value.takeIf { it.isJsonPrimitive }?.asString)
            }
            list.forEachIndexed { index, url ->
                UrlSecurity.secureOrNull(url)?.let {
                    result +=
                        Server(
                            id = "$episodeId-$quality-fallback-$index",
                            link = it,
                            quality = quality,
                            name = "Wibutime fallback",
                            type = playbackType(it),
                        )
                }
            }
        }
        if (result.isEmpty()) {
            UrlSecurity.secureOrNull(row.string("video_embed_url"))?.let {
                result +=
                    Server(
                        id = "$episodeId-default",
                        link = it,
                        quality = null,
                        name = "Wibutime",
                        type = playbackType(it),
                    )
            }
        }
        return result.distinctBy { it.link }
    }

    private fun playbackType(url: String): String {
        val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        return if (extension in setOf("mp4", "m3u8", "webm", "mkv")) "direct" else "embed"
    }

    suspend fun schedule(force: Boolean = false): List<Movie> {
        val rows = json(
            "schedules",
            mapOf("select" to "*", "limit" to TABLE_LIMIT.toString()),
            force = force,
        ).arrayObjects()
        if (rows.isEmpty()) return emptyList()
        val ids = rows.mapNotNull { it.string("anime_id") }.distinct()
        val inValue = ids.joinToString(",", "(", ")")
        val animeRows = if (ids.isEmpty()) {
            emptyList()
        } else {
            animes(
                mapOf(
                    "select" to "*",
                    "slug" to "in.$inValue",
                    "limit" to TABLE_LIMIT.toString(),
                ),
                force,
            )
        }
        val bySlug = animeRows.associateBy { it.string("slug") }
        return rows
            .mapNotNull { row ->
                val slug = row.string("anime_id") ?: return@mapNotNull null
                val anime = row.get("anime")?.objOrNull() ?: bySlug[slug] ?: return@mapNotNull null
                anime.toMovie(day = row.string("day_of_week"))
            }.distinctBy { it.id to it.day }
    }

    suspend fun explore(
        force: Boolean = false,
        preview: Boolean = true,
    ): ExploreData {
        val rows = animes(mapOf("select" to "type,genres,studio,year", "limit" to TABLE_LIMIT.toString()), force)

        fun items(
            values: List<String>,
            type: String,
        ) = values
            .distinct()
            .filter(
                String::isNotBlank,
            ).sorted()
            .map { value -> ExploreItem(id = value, name = value, title = value, type = type) }
        val types = rows.flatMap { it.stringList("type") + listOfNotNull(it.string("type")) }
        val genres = rows.flatMap { it.stringList("genres") }
        val studios = rows.mapNotNull { it.string("studio") }
        val years = rows.mapNotNull { it.numberString("year") }
        val data =
            ExploreData(
                type = items(types, "type"),
                genre = items(genres, "genre"),
                studio = items(studios, "studio"),
                year = items(years, "year"),
            )
        return if (preview) {
            data.copy(
                type = data.type.take(12),
                genre = data.genre.take(24),
                studio = data.studio.take(12),
                year = data.year.take(20),
            )
        } else {
            data
        }
    }

    suspend fun exploreMovies(
        kind: String,
        idOrName: String,
        page: Int = 0,
        force: Boolean = false,
        sort: String = "views",
        season: String = "",
        genreIn: String = "",
    ): List<Movie> {
        val value = idOrName.trim()
        if (value.isBlank()) return emptyList()
        val params = linkedMapOf(
            "select" to "*",
            "limit" to PAGE_SIZE.toString(),
            "offset" to (page.coerceAtLeast(0) * PAGE_SIZE).toString(),
        )
        when (kind.lowercase()) {
            "genre" -> params["genres"] = "cs.{${value.replace(",", "")}}"
            "studio" -> params["studio"] = "ilike.*$value*"
            "year" -> params["year"] = "eq.${value.toIntOrNull() ?: value}"
            "type", "tipe" -> params["type"] = "ilike.*$value*"
            "status" -> params["status"] = "ilike.*$value*"
            else -> params["title_lower"] = "ilike.*$value*"
        }
        if (genreIn.isNotBlank() && kind.lowercase() != "genre") params["genres"] = "cs.{${genreIn.replace(",", "")}}"
        val order = when (sort.lowercase()) {
            "rating", "score" -> "rating.desc.nullslast"
            "year" -> "year.desc.nullslast"
            else -> "updated_at.desc.nullslast"
        }
        params["order"] = order
        return animes(params, force).map { it.toMovie() }
    }
}
