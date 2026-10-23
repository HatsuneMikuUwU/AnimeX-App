package com.uwu.animex.notifications.subscription

import android.content.Context
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie

object SubscriptionHelper {
    data class LatestEpisode(
        val number: Int,
        val title: String?,
        val thumbnail: String?,
    )

    suspend fun getSubscriptions(context: Context): List<EpisodeAlertEntity> = EpisodeAlerts.all(context)

    fun saveSubscription(
        movie: Movie,
        latestEpisode: Int,
    ) = EpisodeAlerts.enable(movie, latestEpisode)

    fun deleteSubscription(id: String?) = EpisodeAlerts.disable(id)

    suspend fun getLatestEpisode(movieId: String): LatestEpisode? {
        val page1 =
            runCatching { Api.episodes(movieId, force = true) }
                .getOrNull()
                .orEmpty()
        val best1 = page1.newest()

        val best: Episode? =
            if (best1 == null) {
                val lastPage = runCatching { Api.lastEpisodePage(movieId) }.getOrNull() ?: return null
                if (lastPage <= 0) return null
                runCatching { Api.episodesPage(movieId, lastPage) }.getOrNull().orEmpty().newest()
            } else if ((best1.number() ?: 0) < PAGE_SIZE) {
                val lastPage = runCatching { Api.lastEpisodePage(movieId) }.getOrNull()
                val bestLast =
                    if (lastPage != null && lastPage > 1) {
                        runCatching { Api.episodesPage(movieId, lastPage) }.getOrNull().orEmpty().newest()
                    } else {
                        null
                    }
                if (bestLast != null && (bestLast.number() ?: 0) > (best1.number() ?: 0)) bestLast else best1
            } else {
                best1
            }

        val number = best?.number() ?: return null
        return LatestEpisode(
            number = number,
            title = best.title?.takeIf { it.isRealTitle(number) },
            thumbnail = best.image?.takeIf { it.isNotBlank() },
        )
    }

    private fun Episode.number(): Int? = index?.toIntOrNull()

    private fun List<Episode>.newest(): Episode? = filter { it.number() != null }.maxByOrNull { it.number() ?: 0 }

    private fun String.isRealTitle(number: Int): Boolean {
        val t = trim()
        if (t.isEmpty()) return false
        val generic = Regex("^(episode|eps?|ep\\.)\\s*0*$number$", RegexOption.IGNORE_CASE)
        return !generic.matches(t) && t != number.toString()
    }

    private const val PAGE_SIZE = 30
}
