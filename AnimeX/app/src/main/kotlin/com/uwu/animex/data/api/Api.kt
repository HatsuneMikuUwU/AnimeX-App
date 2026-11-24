package com.uwu.animex.data.api

import android.content.Context
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.CuplixNext
import com.uwu.animex.data.model.Episode
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
        kind: String,
        page: Int = 1,
        force: Boolean = false,
    ): List<Movie> = ApiHomeEndpoints.homeMovies(kind, page, force)

    suspend fun newEpisodes(
        page: Int = 1,
        force: Boolean = false,
    ): List<Movie> = ApiHomeEndpoints.newEpisodes(page, force)

    suspend fun homeCuplix(
        limit: Int = 12,
        force: Boolean = false,
    ): List<Cuplix> = ApiHomeEndpoints.homeCuplix(limit, force)

    suspend fun cuplixScroll(
        sort: String,
        cursor: CuplixNext? = null,
        limit: Int = 10,
        force: Boolean = false,
    ): Pair<List<Cuplix>, CuplixNext> = ApiHomeEndpoints.cuplixScroll(sort, cursor, limit, force)

    suspend fun cuplixServers(clipId: String): List<Server> = ApiHomeEndpoints.cuplixServers(clipId)

    suspend fun schedule(force: Boolean = false): List<Movie> = ApiHomeEndpoints.schedule(force)

    suspend fun search(
        query: String,
        page: Int = 1,
        force: Boolean = false,
    ): List<Movie> = ApiCatalogEndpoints.search(query, page, force)

    suspend fun suggest(
        query: String,
        force: Boolean = false,
    ): List<Movie> = ApiCatalogEndpoints.suggest(query, force)

    suspend fun detail(id: String): Movie? = ApiCatalogEndpoints.detail(id)

    suspend fun detailFull(
        id: String,
        force: Boolean = false,
    ): DetailResult = ApiCatalogEndpoints.detailFull(id, force)

    suspend fun episodes(
        id: String,
        page: Int = 1,
        force: Boolean = false,
    ): List<Episode> = ApiCatalogEndpoints.episodes(id, page, force)

    suspend fun trailers(
        id: String,
        force: Boolean = false,
    ): List<Trailer> = ApiCatalogEndpoints.trailers(id, force)

    suspend fun hasServers(episodeId: String?): Boolean = ApiCatalogEndpoints.hasServers(episodeId)

    suspend fun lookupNextEpisode(
        movieId: String,
        currentEpisodeId: String,
        currentIndex: String?,
    ): Episode? = ApiCatalogEndpoints.lookupNextEpisode(movieId, currentEpisodeId, currentIndex)

    suspend fun nextEpisode(
        movieId: String,
        currentEpisodeId: String,
        currentIndex: String?,
    ): Episode? = ApiCatalogEndpoints.nextEpisode(movieId, currentEpisodeId, currentIndex)

    suspend fun previousEpisode(
        movieId: String,
        currentEpisodeId: String,
        currentIndex: String?,
    ): Episode? = ApiCatalogEndpoints.previousEpisode(movieId, currentEpisodeId, currentIndex)

    suspend fun findEpisode(
        movieId: String,
        episodeId: String?,
        episodeIndex: String?,
    ): Episode? = ApiCatalogEndpoints.findEpisode(movieId, episodeId, episodeIndex)

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
        kind: String,
        force: Boolean = false,
    ): List<ExploreItem> = ApiCatalogEndpoints.explore(kind, force)

    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> =
        ApiCatalogEndpoints.exploreGenres(force)

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> =
        ApiCatalogEndpoints.exploreYears(force)

    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> =
        ApiCatalogEndpoints.exploreStudios(force)

    suspend fun exploreMovies(
        kind: String,
        value: String,
        page: Int = 1,
        force: Boolean = false,
    ): List<Movie> = ApiCatalogEndpoints.exploreMovies(kind, value, page, force)
}
