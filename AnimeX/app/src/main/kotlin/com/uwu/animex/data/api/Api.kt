package com.uwu.animex.data.api

import android.content.Context
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.model.Trailer

object Api {
    const val API_LIMIT = 30

    class OldestEpisodes(
        val episodes: List<Episode>,
        val nextPage: Int,
        val hasMore: Boolean,
        val latest: Int,
    )

    data class CuplixPage(
        val clips: List<Cuplix>,
        val cursor: Map<String, String>,
        val idFyp: String = "",
    )

    data class DetailResult(
        val movie: Movie?,
        val seasons: List<Movie> = emptyList(),
        val episode: Episode? = null,
    )

    sealed class NextEpisodeLookup {
        data class Exists(
            val episode: Episode,
        ) : NextEpisodeLookup()

        data object NoNext : NextEpisodeLookup()

        data object Unknown : NextEpisodeLookup()
    }

    val activeDomain: String
        get() = ApiDomainManager.activeDomain

    val remoteConfig: RemoteConfig
        get() = ApiDomainManager.remoteConfig

    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext
        ApiTransport.init(app)
        ApiDomainManager.init(app)
    }

    fun cachedOldestEpisodes(id: String): OldestEpisodes? = ApiTransport.cachedOldestEpisodes(id)

    fun cacheOldestEpisodes(
        id: String,
        state: OldestEpisodes,
    ) = ApiTransport.cacheOldestEpisodes(id, state)

    fun clearCache() = ApiTransport.clearCache()

    fun fetchedThisSession(pathPart: String): Boolean = ApiTransport.fetchedThisSession(pathPart)

    fun peekDetail(id: String): DetailResult? = ApiTransport.peekDetail(id)

    fun peekEpisodes(id: String): List<Episode>? = ApiTransport.peekEpisodes(id)

    fun oldestEpisodesSizeBytes(): Long = ApiTransport.oldestEpisodesSizeBytes()

    fun trimCache() = ApiTransport.trimCache()

    suspend fun ensureReady() = ApiDomainManager.ensureReady()

    suspend fun <T> withFallback(block: suspend (domain: String) -> T): T = ApiDomainManager.withFallback(block)

    fun absUrl(path: String?): String? = ApiDomainManager.absUrl(path)

    suspend fun home(force: Boolean = false): HomeData = ApiHomeEndpoints.home(force)

    suspend fun homeMovies(
        section: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> = ApiHomeEndpoints.homeMovies(section, page, force)

    suspend fun newEpisodes(
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> = ApiHomeEndpoints.newEpisodes(page, force)

    suspend fun homeCuplix(
        force: Boolean = false,
        page: Int = 1,
    ): List<Cuplix> = ApiHomeEndpoints.homeCuplix(force, page)

    suspend fun cuplixScroll(
        sort: String,
        cursor: Map<String, String> = emptyMap(),
        keyIdFyp: String = "",
        idUserFyp: String = "",
        movieIds: String = "",
        force: Boolean = false,
    ): CuplixPage = ApiHomeEndpoints.cuplixScroll(sort, cursor, keyIdFyp, idUserFyp, movieIds, force)

    suspend fun cuplixServers(clipId: String): List<Server> = ApiHomeEndpoints.cuplixServers(clipId)

    suspend fun schedule(force: Boolean = false): List<Movie> = ApiHomeEndpoints.schedule(force)

    suspend fun search(
        q: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> = ApiCatalogEndpoints.search(q, page, force)

    suspend fun suggest(
        q: String,
        limit: Int = 6,
    ): List<Movie> = ApiCatalogEndpoints.suggest(q, limit)

    suspend fun detail(id: String): Movie? = ApiCatalogEndpoints.detail(id)

    suspend fun detailFull(
        id: String,
        force: Boolean = false,
    ): DetailResult = ApiCatalogEndpoints.detailFull(id, force)

    suspend fun episodes(
        id: String,
        page: Int? = null,
        force: Boolean = false,
    ): List<Episode> = ApiCatalogEndpoints.episodes(id, page, force)

    suspend fun trailers(
        movieId: String,
        force: Boolean = false,
    ): List<Trailer> = ApiCatalogEndpoints.trailers(movieId, force)

    suspend fun hasServers(episodeId: String?): Boolean = ApiCatalogEndpoints.hasServers(episodeId)

    suspend fun lookupNextEpisode(
        movieId: String,
        index: String?,
        requireServers: Boolean = false,
    ): NextEpisodeLookup = ApiCatalogEndpoints.lookupNextEpisode(movieId, index, requireServers)

    suspend fun nextEpisode(
        movieId: String,
        index: String?,
    ): Episode? = ApiCatalogEndpoints.nextEpisode(movieId, index)

    suspend fun previousEpisode(
        movieId: String,
        index: String?,
    ): Episode? = ApiCatalogEndpoints.previousEpisode(movieId, index)

    suspend fun findEpisode(
        movieId: String,
        index: String,
    ): Episode? = ApiCatalogEndpoints.findEpisode(movieId, index)

    suspend fun allEpisodes(id: String): List<Episode> = ApiCatalogEndpoints.allEpisodes(id)

    suspend fun episodesPage(
        id: String,
        page: Int,
    ): List<Episode> = ApiCatalogEndpoints.episodesPage(id, page)

    suspend fun lastEpisodePage(id: String): Int = ApiCatalogEndpoints.lastEpisodePage(id)

    suspend fun servers(
        episodeId: String,
        force: Boolean = false,
    ): List<Server> = ApiCatalogEndpoints.servers(episodeId, force)

    suspend fun explore(
        force: Boolean = false,
        preview: Boolean = true,
    ): ExploreData = ApiCatalogEndpoints.explore(force, preview)

    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> =
        ApiCatalogEndpoints.exploreGenres(force)

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> =
        ApiCatalogEndpoints.exploreYears(force)

    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> =
        ApiCatalogEndpoints.exploreStudios(force)

    suspend fun exploreMovies(
        kind: String,
        idOrName: String,
        title: String = "",
        page: Int = 0,
        force: Boolean = false,
        sort: String = "views",
        season: String = "",
        genreIn: String = "",
    ): List<Movie> = ApiCatalogEndpoints.exploreMovies(kind, idOrName, title, page, force, sort, season, genreIn)
}
