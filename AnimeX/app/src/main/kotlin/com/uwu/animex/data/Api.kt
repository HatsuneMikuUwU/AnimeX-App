package com.uwu.animex.data

import com.uwu.animex.data.repository.AnimeRepository

/**
 * Compatibility facade over [AnimeRepository].
 * Existing call-sites keep working while architecture migrates to ViewModels + repositories.
 * Prefer injecting [AnimeRepository] via AppContainer in new code.
 */
object Api {
    const val API_LIMIT = AnimeRepository.API_LIMIT

    @Volatile
    private var repo: AnimeRepository? = null

    fun bind(repository: AnimeRepository) {
        repo = repository
    }

    private fun r(): AnimeRepository =
        repo ?: error("Api belum di-bind. Panggil AppContainer / Api.bind di MainActivity.onCreate")

    val baseUrl: String get() = r().baseUrl

    fun absUrl(path: String?): String? = r().absUrl(path)

    suspend fun home(force: Boolean = false): HomeData = r().home(force)

    suspend fun homeMovies(section: String, page: Int = 0, force: Boolean = false): List<Movie> =
        r().homeMovies(section, page, force)

    suspend fun newEpisodes(page: Int = 0, force: Boolean = false): List<Movie> =
        r().newEpisodes(page, force)

    suspend fun schedule(force: Boolean = false): List<Movie> = r().schedule(force)

    suspend fun search(q: String, page: Int = 0, force: Boolean = false): List<Movie> =
        r().search(q, page, force)

    suspend fun detail(id: String): Movie? = r().detail(id)

    suspend fun detailFull(id: String): Pair<Movie?, List<Movie>> = r().detailFull(id)

    suspend fun episodes(id: String, page: Int? = null, force: Boolean = false): List<Episode> =
        r().episodes(id, page, force)

    suspend fun hasServers(episodeId: String?): Boolean = r().hasServers(episodeId)

    typealias NextEpisodeLookup = AnimeRepository.NextEpisodeLookup

    suspend fun lookupNextEpisode(
        movieId: String,
        index: String?,
        requireServers: Boolean = false,
    ): AnimeRepository.NextEpisodeLookup = r().lookupNextEpisode(movieId, index, requireServers)

    suspend fun nextEpisode(movieId: String, index: String?): Episode? =
        r().nextEpisode(movieId, index)

    suspend fun findEpisode(movieId: String, index: String): Episode? =
        r().findEpisode(movieId, index)

    suspend fun episodesPage(id: String, page: Int): List<Episode> = r().episodesPage(id, page)

    suspend fun lastEpisodePage(id: String): Int = r().lastEpisodePage(id)

    suspend fun servers(episodeId: String, force: Boolean = false): List<Server> =
        r().servers(episodeId, force)

    suspend fun explore(force: Boolean = false, preview: Boolean = false): ExploreData =
        r().explore(force, preview)

    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> = r().exploreGenres(force)

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> = r().exploreYears(force)

    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> = r().exploreStudios(force)

    suspend fun exploreMovies(
        kind: String,
        idOrName: String,
        title: String = "",
        page: Int = 0,
        force: Boolean = false,
        sort: String = "views",
        season: String = "",
        genreIn: String = "",
    ): List<Movie> = r().exploreMovies(kind, idOrName, title, page, force, sort, season, genreIn)
}
