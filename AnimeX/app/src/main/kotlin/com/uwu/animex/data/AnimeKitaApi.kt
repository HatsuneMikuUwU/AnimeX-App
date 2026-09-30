package com.uwu.animex.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parser + path helpers untuk AnimeKita.
 * Fetch/cache/http client dipakai dari [Api] (sama persis dengan original).
 */
internal object AnimeKitaApi {
    const val BASE = "https://apps.animekita.org/api/v1.2.5/"

    private fun parseRoot(json: String): JsonElement? =
        runCatching { JsonParser.parseString(json) }.getOrNull()

    private fun JsonElement.asObj(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonElement.asArr(): JsonArray? = takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.str(vararg keys: String): String? {
        for (k in keys) {
            val el = get(k) ?: continue
            if (el.isJsonNull || !el.isJsonPrimitive) continue
            val s = runCatching { el.asString.trim() }.getOrNull() ?: continue
            if (s.isNotEmpty() && s != "null") return s
        }
        return null
    }

    private fun findArray(root: JsonElement?, vararg keys: String): JsonArray? {
        if (root == null) return null
        root.asArr()?.let { return it }
        val obj = root.asObj() ?: return null
        for (k in keys) {
            val el = obj.get(k) ?: continue
            el.asArr()?.let { return it }
            el.asObj()?.let { nested ->
                for (nk in listOf("data", "list", "items", "results", "anime", "series", "movie")) {
                    nested.get(nk)?.asArr()?.let { return it }
                }
            }
        }
        obj.get("data")?.asArr()?.let { return it }
        return null
    }

    fun movieFrom(el: JsonElement): Movie? {
        val o = el.asObj() ?: return null
        val id = o.str(
            "url", "slug", "link", "id", "anime_id", "series_id", "post_id", "endpoint",
        ) ?: return null
        val title = o.str("title", "judul", "name", "anime", "series") ?: id
        val image = o.str(
            "image", "gambar", "thumbnail", "thumb", "poster", "image_poster",
            "cover", "image_cover", "img", "foto",
        )
        return Movie(
            id = id,
            title = title,
            synopsis = o.str("synopsis", "sinopsis", "deskripsi", "description"),
            image_poster = image,
            image_cover = image,
            type = o.str("type", "tipe", "jenis"),
            year = o.str("year", "tahun", "release"),
            status = o.str("status", "status_anime"),
            genre = o.str("genre", "genres", "kategori"),
            episode_index = o.str("episode", "episode_index", "eps", "episode_number", "nomor_episode", "ep"),
            episode_title = o.str("episode_title", "judul_episode", "subtitle"),
            day = o.str("day", "hari", "schedule_day"),
            time = o.str("time", "jam", "schedule_time"),
        )
    }

    fun moviesFrom(json: String, vararg arrayKeys: String): List<Movie> {
        val root = parseRoot(json) ?: return emptyList()
        val arr = findArray(root, *arrayKeys) ?: return emptyList()
        return arr.mapNotNull { movieFrom(it) }
    }

    suspend fun parseHome(
        ongoingJson: String,
        baruJson: String,
        recoJson: String,
        listJson: String,
    ): HomeData = withContext(Dispatchers.Default) {
        val ongoing = moviesFrom(ongoingJson, "data", "list", "anime", "series", "ongoing")
        val baru = moviesFrom(baruJson, "data", "list", "anime", "series")
        val reco = moviesFrom(recoJson, "data", "list", "anime", "series", "rekomendasi")
        val listAll = moviesFrom(listJson, "data", "list", "anime", "series")
        HomeData(
            slider = emptyList(),
            history = emptyList(),
            update = baru.ifEmpty { ongoing }.take(Api.API_LIMIT),
            hot = reco.ifEmpty { ongoing }.take(Api.API_LIMIT),
            new = baru.take(Api.API_LIMIT),
            today = emptyList(),
            random = listAll.shuffled().take(Api.API_LIMIT).ifEmpty { ongoing.shuffled().take(Api.API_LIMIT) },
            waiting = ongoing.take(Api.API_LIMIT),
            popular = reco.ifEmpty { listAll }.take(Api.API_LIMIT),
        )
    }

    fun parseSchedule(json: String): List<Movie> {
        val root = parseRoot(json) ?: return emptyList()
        val out = mutableListOf<Movie>()
        val days = listOf(
            "SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU",
            "senin", "selasa", "rabu", "kamis", "jumat", "sabtu", "minggu",
        )
        val obj = root.asObj()
        if (obj != null) {
            val data = obj.get("data")?.asObj() ?: obj
            for (day in days) {
                val arr = data.get(day)?.asArr() ?: data.get(day.lowercase())?.asArr() ?: continue
                arr.mapNotNull { movieFrom(it)?.copy(day = day.uppercase()) }.let { out += it }
            }
            if (out.isEmpty()) {
                findArray(root, "data", "jadwal", "schedule", "list")
                    ?.mapNotNull { movieFrom(it) }
                    ?.let { out += it }
            }
        }
        return out.distinctBy { it.id to it.day }
    }

    fun parseDetail(json: String, fallbackId: String): Pair<Movie?, List<Movie>> {
        val root = parseRoot(json) ?: return Movie(id = fallbackId, title = fallbackId) to emptyList()
        val data = root.asObj()?.get("data")?.asObj() ?: root.asObj()
        val movie = data?.let { movieFrom(it) } ?: movieFrom(root) ?: Movie(id = fallbackId, title = fallbackId)
        val related = data?.let {
            findArray(it, "related", "terkait", "anime_terkait", "similar")?.mapNotNull { e -> movieFrom(e) }
        }.orEmpty()
        return movie to related
    }

    fun parseEpisodes(json: String, movieId: String): List<Episode> {
        val root = parseRoot(json) ?: return emptyList()
        val data = root.asObj()?.get("data")?.asObj() ?: root.asObj() ?: return emptyList()
        val arr = findArray(data, "episode", "episodes", "list_episode", "eps", "chapter")
            ?: findArray(root, "episode", "episodes", "list_episode")
            ?: return emptyList()
        return arr.mapIndexedNotNull { index, el ->
            val o = el.asObj() ?: return@mapIndexedNotNull null
            val epId = o.str("url", "slug", "link", "id", "episode_id", "endpoint")
                ?: return@mapIndexedNotNull null
            val idx = o.str("episode", "index", "number", "eps", "nomor") ?: (index + 1).toString()
            Episode(
                id = epId,
                index = idx,
                title = o.str("title", "judul", "name", "episode_title") ?: "Episode $idx",
                image = o.str("image", "gambar", "thumbnail", "thumb"),
                id_movie = movieId,
            )
        }
    }

    fun parseServers(json: String): List<Server> {
        val root = parseRoot(json) ?: return emptyList()
        val data = root.asObj()?.get("data")?.asObj() ?: root.asObj() ?: return emptyList()
        val out = mutableListOf<Server>()

        fun add(name: String?, link: String?, quality: String? = null, type: String? = null) {
            if (link.isNullOrBlank()) return
            out += Server(
                id = link,
                link = link,
                quality = quality,
                name = name ?: "Server",
                type = type ?: if (".m3u8" in link.lowercase()) "hls" else "direct",
            )
        }

        for (key in listOf("stream", "streams", "server", "servers", "video", "videos", "player", "link")) {
            val el = data.get(key) ?: continue
            when {
                el.isJsonArray -> el.asJsonArray.forEach { item ->
                    val o = item.asObj() ?: return@forEach
                    add(
                        o.str("name", "nama", "server", "label", "title"),
                        o.str("link", "url", "file", "src", "stream", "video"),
                        o.str("quality", "kualitas", "reso", "resolution"),
                        o.str("type", "tipe"),
                    )
                }
                el.isJsonObject -> el.asJsonObject.entrySet().forEach { (k, v) ->
                    when {
                        v.isJsonPrimitive -> add(k, runCatching { v.asString }.getOrNull(), k)
                        v.isJsonObject -> {
                            val n = v.asJsonObject
                            add(
                                n.str("name", "nama") ?: k,
                                n.str("link", "url", "file", "src"),
                                n.str("quality") ?: k,
                            )
                        }
                    }
                }
                el.isJsonPrimitive -> add("Default", runCatching { el.asString }.getOrNull())
            }
        }

        if (out.isEmpty()) {
            fun walk(el: JsonElement) {
                when {
                    el.isJsonPrimitive -> {
                        val s = runCatching { el.asString }.getOrNull() ?: return
                        if (".m3u8" in s.lowercase() || s.startsWith("http")) add("Stream", s)
                    }
                    el.isJsonArray -> el.asJsonArray.forEach { walk(it) }
                    el.isJsonObject -> el.asJsonObject.entrySet().forEach { walk(it.value) }
                }
            }
            walk(data)
        }
        return out.distinctBy { it.link }.filter { !it.link.isNullOrBlank() }
    }

    fun parseExploreGenres(json: String): List<ExploreItem> {
        val root = parseRoot(json) ?: return emptyList()
        return findArray(root, "data", "genre", "genres", "list")?.mapNotNull { el ->
            val o = el.asObj() ?: return@mapNotNull null
            val name = o.str("name", "genre", "title", "label") ?: return@mapNotNull null
            ExploreItem(id = o.str("id", "slug", "url") ?: name, name = name)
        }.orEmpty()
    }
}
