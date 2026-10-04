package com.uwu.animex.data

/** Repository boundary used by ViewModels; keeps networking out of the UI layer. */
interface AnimeRepository {
    suspend fun home(force: Boolean = false): HomeData
    suspend fun schedule(force: Boolean = false): List<Movie>
    suspend fun search(query: String, page: Int = 0, force: Boolean = false): List<Movie>
    suspend fun detail(id: String): Movie?
}

/** Transitional adapter for the existing endpoint implementation. New screens should depend on this interface. */
class DefaultAnimeRepository : AnimeRepository {
    override suspend fun home(force: Boolean) = Api.home(force)
    override suspend fun schedule(force: Boolean) = Api.schedule(force)
    override suspend fun search(query: String, page: Int, force: Boolean) = Api.search(query, page, force)
    override suspend fun detail(id: String) = Api.detail(id)
}
