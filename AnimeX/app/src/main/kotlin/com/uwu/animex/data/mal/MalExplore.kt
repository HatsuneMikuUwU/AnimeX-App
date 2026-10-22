package com.uwu.animex.data.mal

import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.providers.MALApi
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class MalExploreItem(
    val id: Int,
    val title: String,
    val synonyms: List<String> = emptyList(),
    val posterUrl: String? = null,
    val mediaType: String? = null,
    val episodes: Int? = null,
    val score: Double? = null,
    val members: Int? = null,
    val genres: List<String> = emptyList(),
    val airingDay: String? = null,
    val airing: Boolean = false,
)

data class MalExploreData(
    val today: List<MalExploreItem> = emptyList(),
    val airing: List<MalExploreItem> = emptyList(),
    val thisSeason: List<MalExploreItem> = emptyList(),
    val recommended: List<MalExploreItem> = emptyList(),
    val topScore: List<MalExploreItem> = emptyList(),
    val popular: List<MalExploreItem> = emptyList(),
    val favorite: List<MalExploreItem> = emptyList(),
    val upcoming: List<MalExploreItem> = emptyList(),
)

/**
 * Data eksplorasi anime dari MAL (terinspirasi Home MoeList: tayang hari ini, musim ini, rekomendasi
 * dan ranking). Semua data diambil langsung dari API MAL.
 */
object MalExplore {
    private val seasonNames = listOf("winter", "spring", "summer", "fall")
    private val weekdays =
        listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

    private fun japanWeekday(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"), Locale.US)
        return weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    suspend fun load(): MalExploreData =
        coroutineScope {
            val repo = AccountManager.malApi
            val api = repo.api as MALApi
            val cal = Calendar.getInstance()
            val season = seasonNames[(cal.get(Calendar.MONTH) / 3).coerceIn(0, 3)]
            val year = cal.get(Calendar.YEAR)

            fun fetch(
                path: String,
                params: Map<String, String> = emptyMap(),
                limit: Int = 25,
            ) = async {
                runCatching { repo.withAuth { api.exploreList(it, path, params, limit) } }
            }

            val jobs =
                listOf(
                    fetch("anime/ranking", mapOf("ranking_type" to "airing"), limit = 100),
                    fetch("anime/season/$year/$season", mapOf("sort" to "anime_num_list_users")),
                    fetch("anime/suggestions"),
                    fetch("anime/ranking", mapOf("ranking_type" to "all")),
                    fetch("anime/ranking", mapOf("ranking_type" to "bypopularity")),
                    fetch("anime/ranking", mapOf("ranking_type" to "favorite")),
                    fetch("anime/ranking", mapOf("ranking_type" to "upcoming")),
                )
            val results = jobs.map { it.await() }
            if (results.all { it.isFailure }) {
                throw results.first().exceptionOrNull() ?: IllegalStateException("Gagal muat MAL")
            }

            fun at(i: Int) = results[i].getOrNull().orEmpty().distinctBy { it.id }

            val airing = at(0)
            val today = japanWeekday()
            MalExploreData(
                today = airing.filter { it.airing && it.airingDay.equals(today, ignoreCase = true) },
                airing = airing.take(25),
                thisSeason = at(1),
                recommended = at(2),
                topScore = at(3),
                popular = at(4),
                favorite = at(5),
                upcoming = at(6),
            )
        }
}
