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
        val best = Api.episodes(movieId, force = true).newest() ?: return null
        val number = best.number() ?: return null
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
}
