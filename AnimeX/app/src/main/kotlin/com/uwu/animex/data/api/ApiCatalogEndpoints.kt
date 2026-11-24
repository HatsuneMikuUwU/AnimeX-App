package com.uwu.animex.data.api

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.ApiTransport.movieArray
import com.uwu.animex.core.security.NativeCore
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.EpisodeListData
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.MovieDetailData
import com.uwu.animex.data.model.MovieListData
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.model.StreamData
import com.uwu.animex.data.model.Trailer
import com.uwu.animex.data.model.TrailerListData
import com.uwu.animex.data.model.toEpisodeIndex
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal object ApiCatalogEndpoints {
    private const val MAX_EPISODE_PAGES = 100
    private const val MAX_NEIGHBOR_PAGES = 6

    suspend fun search(
        q: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()
        val p = page.coerceAtLeast(0)

        val attempts =
            buildList<suspend () -> List<Movie>> {
                for (entry in NativeCore.searchPlan()) {
                    val (searchPath, key, sortFlag) = entry.split('|')
                    add {
                        val params = linkedMapOf(key to query, "page" to "$p")
                        if (sortFlag == "1") params["sort"] = "views"
                        ApiTransport.getData(searchPath, params, force)?.movieArray().orEmpty()
                    }
                }
            }
        return ApiTransport.firstNonEmpty(attempts).distinctBy { it.id }
    }

    suspend fun suggest(
        q: String,
        limit: Int = 6,
    ): List<Movie> {
        val query = q.trim()
        if (query.length < 2) return emptyList()
        return runSuspendCatching {
            ApiTransport.getData(
                NativeCore.path(NativeCore.EXPLORE_MOVIE),
                mapOf("keyword" to query, "page" to "0", "sort" to "views"),
            )?.movieArray().orEmpty()
        }.getOrNull()
            .orEmpty()
            .filter { !it.id.isNullOrBlank() && !it.title.isNullOrBlank() }
            .distinctBy {
                it.title
                    .orEmpty()
                    .trim()
                    .lowercase()
            }.take(limit)
    }

    suspend fun detail(id: String): Movie? = detailFull(id).movie

    suspend fun detailFull(
        id: String,
        force: Boolean = false,
    ): Api.DetailResult {
        val data =
            ApiTransport.get<MovieDetailData>(NativeCore.path(NativeCore.DETAIL, id), MovieDetailData::class.java, force = force)
        return DetailResult(
            movie = data?.movie,
            seasons = data?.season.orEmpty(),
            episode = data?.episode,
        )
    }

    suspend fun episodes(
        id: String,
        page: Int? = null,
        force: Boolean = false,
    ): List<Episode> {
        val params = if (page != null && page > 0) mapOf("page" to "$page") else emptyMap()
        return ApiTransport.get<EpisodeListData>(
            NativeCore.path(NativeCore.EPISODE_LIST, id),
            EpisodeListData::class.java,
            params,
            force,
        )?.episode.orEmpty()
    }

    suspend fun trailers(
        movieId: String,
        force: Boolean = false,
    ): List<Trailer> {
        val params = mapOf("id_movie" to movieId)
        return ApiTransport.firstNonEmpty(
            {
                ApiTransport.get<TrailerListData>(
                    NativeCore.path(NativeCore.TRAILER_MOVIE),
                    TrailerListData::class.java,
                    params,
                    force,
                )?.trailer
                    .orEmpty()
            },
            {
                ApiTransport.getData(NativeCore.path(NativeCore.TRAILER_MOVIE), params, force)?.trailerArray().orEmpty()
            },
            {
                ApiTransport.get<TrailerListData>(
                    NativeCore.path(NativeCore.TRAILER_GLOBAL),
                    TrailerListData::class.java,
                    params,
                    force,
                )?.trailer
                    .orEmpty()
            },
            {
                ApiTransport.getData(NativeCore.path(NativeCore.TRAILER_GLOBAL), params, force)?.trailerArray().orEmpty()
            },
        )
    }

    private fun JsonObject.trailerArray(): List<Trailer> {
        val arr =
            listOf("trailer", "trailers", "list", "items", "results", "data")
                .firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
                ?: return emptyList()
        return runCatching { ApiTransport.gson.fromJson(arr, Array<Trailer>::class.java)?.toList() }.getOrNull().orEmpty()
    }

    suspend fun hasServers(episodeId: String?): Boolean =
        episodeId != null && runSuspendCatching { servers(episodeId) }.getOrNull()?.isNotEmpty() == true

    private fun Episode.number(): Double? = index.toEpisodeIndex()?.toDoubleOrNull()

    private fun Episode.matches(target: String): Boolean {
        val own = index?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (own == target) return true
        val ownNum = own.toDoubleOrNull() ?: return false
        return ownNum == target.toDoubleOrNull()
    }

    private suspend fun neighborEpisode(
        movieId: String,
        current: Double,
        forward: Boolean,
        newest: List<Episode>,
    ): Episode? {
        val newestMax = newest.mapNotNull { it.number() }.maxOrNull() ?: return null
        val start = ((newestMax - current) / Api.API_LIMIT).toInt().coerceAtLeast(0)
        val pool = LinkedHashMap<String, Episode>()

        suspend fun load(page: Int): Boolean {
            val batch = if (page == 0) newest else episodesPage(movieId, page)
            if (batch.isEmpty()) return false
            for (ep in batch) {
                val key = ep.id ?: continue
                pool.putIfAbsent(key, ep)
            }
            return true
        }

        var low = start
        var high = start
        if (!load(start)) return null
        var attempts = 0
        while (attempts < MAX_NEIGHBOR_PAGES) {
            attempts++
            val numbers = pool.values.mapNotNull { it.number() }
            val needOlder =
                if (forward) numbers.none { it <= current } else numbers.none { it < current }
            val needNewer =
                if (forward) numbers.none { it > current } else numbers.none { it >= current }
            when {
                needOlder -> if (load(high + 1)) high++ else break
                needNewer -> if (low > 0 && load(low - 1)) low-- else break
                else -> break
            }
        }
        val numbered = pool.values.mapNotNull { ep -> ep.number()?.let { ep to it } }
        return if (forward) {
            numbered.filter { it.second > current }.minByOrNull { it.second }?.first
        } else {
            numbered.filter { it.second < current }.maxByOrNull { it.second }?.first
        }
    }

    suspend fun lookupNextEpisode(
        movieId: String,
        index: String?,
        requireServers: Boolean = false,
    ): Api.NextEpisodeLookup {
        val current = index.toEpisodeIndex()?.toDoubleOrNull() ?: return Api.NextEpisodeLookup.Unknown
        val key = "$movieId:$current:${if (requireServers) "s" else "c"}"
        ApiTransport.nextCache.get(key)?.let { hit ->
            if (hit.episode != null) return Api.NextEpisodeLookup.Exists(hit.episode)
        }

        var newest =
            runSuspendCatching { episodes(movieId) }.getOrElse {
                return Api.NextEpisodeLookup.Unknown
            }
        var newestNum = newest.mapNotNull { it.number() }.maxOrNull() ?: return Api.NextEpisodeLookup.Unknown
        if (current >= newestNum) {
            val fresh = runSuspendCatching { episodes(movieId, force = true) }.getOrNull().orEmpty()
            val freshNum = fresh.mapNotNull { it.number() }.maxOrNull()
            if (freshNum != null) {
                newest = fresh
                newestNum = freshNum
            }
        }
        if (current >= newestNum) return Api.NextEpisodeLookup.NoNext

        val found =
            runSuspendCatching { neighborEpisode(movieId, current, forward = true, newest = newest) }.getOrNull()
                ?: return Api.NextEpisodeLookup.Unknown
        if (requireServers && !hasServers(found.id)) {
            return Api.NextEpisodeLookup.Unknown
        }

        ApiTransport.nextCache.put(key, ApiTransport.NextHit(found))
        return Api.NextEpisodeLookup.Exists(found)
    }

    suspend fun nextEpisode(
        movieId: String,
        index: String?,
    ): Episode? =
        when (val r = lookupNextEpisode(movieId, index, requireServers = true)) {
            is Api.NextEpisodeLookup.Exists -> r.episode
            else -> null
        }

    suspend fun previousEpisode(
        movieId: String,
        index: String?,
    ): Episode? {
        val current = index.toEpisodeIndex()?.toDoubleOrNull() ?: return null
        val newest = runSuspendCatching { episodes(movieId) }.getOrNull() ?: return null
        return runSuspendCatching { neighborEpisode(movieId, current, forward = false, newest = newest) }.getOrNull()
    }

    suspend fun findEpisode(
        movieId: String,
        index: String,
    ): Episode? {
        val target = index.trim()
        if (target.isBlank()) return null

        fun inList(list: List<Episode>) = list.firstOrNull { it.matches(target) }

        val newest = episodes(movieId)
        inList(newest)?.let { return it }

        val targetNum = target.toDoubleOrNull()
        val newestNum = newest.mapNotNull { it.number() }.maxOrNull()
        if (targetNum != null && newestNum != null && newestNum > 0) {
            val approxPage = ((newestNum - targetNum) / Api.API_LIMIT).toInt().coerceAtLeast(1)
            val triedPages = HashSet<Int>()
            for (delta in listOf(0, -1, 1, -2, 2, -3, 3)) {
                val page = (approxPage + delta).coerceAtLeast(1)
                if (!triedPages.add(page)) continue
                val batch = episodes(movieId, page = page)
                inList(batch)?.let { return it }
                if (batch.isEmpty() && delta > 0) break
            }
        }

        var lo = 1
        var hi = 80
        var lastNonEmpty = 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val page = episodes(movieId, page = mid)
            if (page.isEmpty()) {
                hi = mid - 1
            } else {
                lastNonEmpty = mid
                inList(page)?.let { return it }
                lo = mid + 1
            }
        }
        for (p in 1..lastNonEmpty) {
            inList(episodes(movieId, page = p))?.let { return it }
        }
        return null
    }

    suspend fun allEpisodes(id: String): List<Episode> {
        val seen = LinkedHashMap<String, Episode>()
        var page = 0
        var stale = 0
        while (page <= MAX_EPISODE_PAGES && stale < 2) {
            val batch = episodesPage(id, page)
            if (batch.isEmpty()) break
            var added = 0
            for (ep in batch) {
                val key = ep.id ?: continue
                if (seen.putIfAbsent(key, ep) == null) added++
            }
            stale = if (added == 0) stale + 1 else 0
            page++
        }
        return seen.values.toList()
    }

    suspend fun episodesPage(
        id: String,
        page: Int,
    ): List<Episode> = if (page <= 0) episodes(id) else episodes(id, page = page)

    suspend fun lastEpisodePage(id: String): Int {
        ApiTransport.lastPageCache.get(id)?.let { return it }
        if (episodes(id).isEmpty()) return 0
        var lo = 0
        var hi = 1
        while (hi <= 1024 && episodesPage(id, hi).isNotEmpty()) {
            lo = hi
            hi *= 2
        }
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (episodesPage(id, mid).isEmpty()) hi = mid else lo = mid
        }
        ApiTransport.lastPageCache.put(id, lo)
        return lo
    }

    suspend fun servers(
        episodeId: String,
        force: Boolean = false,
    ): List<Server> {
        return ApiTransport.get<StreamData>(NativeCore.path(NativeCore.STREAM, episodeId), StreamData::class.java, force = force)
            ?.server
            .orEmpty()
            .filter { !it.link.isNullOrBlank() }
            .mapNotNull { server ->
                UrlSecurity.secureOrNull(server.link)?.let { secureLink -> server.copy(link = secureLink) }
            }
    }

    private fun JsonObject.exploreItems(vararg keys: String): List<ExploreItem> {
        for (key in keys) {
            val el = get(key) ?: continue
            val list =
                when {
                    el.isJsonArray ->
                        runCatching {
                            ApiTransport.gson.fromJson(el, Array<ExploreItem>::class.java)?.toList()
                        }.getOrNull()
                    el.isJsonObject -> {
                        el.asJsonObject.entrySet().mapNotNull { (k, v) ->
                            runCatching {
                                val item = ApiTransport.gson.fromJson(v, ExploreItem::class.java)
                                if (item.displayName.isBlank()) item.copy(name = k) else item
                            }.getOrNull()
                        }
                    }
                    else -> null
                }
            if (!list.isNullOrEmpty()) return list.filter { it.displayName.isNotBlank() }
        }
        return emptyList()
    }

    suspend fun explore(
        force: Boolean = false,
        preview: Boolean = true,
    ): ExploreData {
        val params = if (preview) mapOf("limit" to "3") else mapOf("limit" to "${Api.API_LIMIT}")
        val primary = runSuspendCatching { ApiTransport.getData(NativeCore.path(NativeCore.EXPLORE_DATA), params, force) }
        val d = primary.getOrNull()
        if (d != null) {
            return ExploreData(
                type = d.exploreItems("tipe", "type", "types", "movie_type"),
                genre = d.exploreItems("genre", "genres", "kategori"),
                studio = d.exploreItems("studio", "studios"),
                year = d.exploreItems("year", "years", "tahun"),
            )
        }

        primary.exceptionOrNull()?.let { if (it is ApiException.Offline) throw it }

        val genres =
            runSuspendCatching {
                ApiTransport.getData(
                    NativeCore.path(NativeCore.EXPLORE_GENRE),
                    force = force,
                )?.exploreItems("genre", "genres", "list", "data")
            }
        val years =
            runSuspendCatching {
                ApiTransport.getData(
                    NativeCore.path(NativeCore.EXPLORE_YEAR),
                    force = force,
                )?.exploreItems("year", "years", "list", "data")
            }
        if (genres.isFailure && years.isFailure) {
            throw primary.exceptionOrNull() ?: genres.exceptionOrNull() ?: ApiException.Parse()
        }
        return ExploreData(
            type = emptyList(),
            genre = genres.getOrNull().orEmpty(),
            studio = emptyList(),
            year = years.getOrNull().orEmpty(),
        )
    }

    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> =
        ApiTransport.firstNonEmpty(
            {
                ApiTransport.getData(
                    NativeCore.path(NativeCore.EXPLORE_GENRE),
                    force = force,
                )?.exploreItems("genre", "genres", "list", "data").orEmpty()
            },
            { explore(force = force, preview = false).genre },
        )

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> =
        ApiTransport.firstNonEmpty(
            {
                ApiTransport.getData(
                    NativeCore.path(NativeCore.EXPLORE_YEAR),
                    force = force,
                )?.exploreItems("year", "years", "list", "data").orEmpty()
            },
            { explore(force = force, preview = false).year },
        )

    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> =
        explore(force = force, preview = false).studio

    suspend fun exploreMovies(
        kind: String,
        idOrName: String,
        title: String = "",
        page: Int = 0,
        force: Boolean = false,
        sort: String = "views",
        season: String = "",
        genreIn: String = "",
    ): List<Movie> {
        val value = idOrName.trim()
        if (value.isBlank()) return emptyList()
        val p = page.coerceAtLeast(0)
        val (path, filterKey, yearFlag) = NativeCore.exploreTarget(kind)

        val params =
            linkedMapOf(
                filterKey to value,
                "page" to "$p",
                "sort" to sort.lowercase(),
                "genre_in" to genreIn.trim(),
                "limit" to "${Api.API_LIMIT}",
            )
        if (yearFlag == "1") {
            params["season"] = season.lowercase().trim()
        }

        return ApiTransport.getData(path, params, force)?.movieArray().orEmpty()
    }
}
