package com.uwu.animex.data.mal

import com.uwu.animex.data.tracker.ListRank
import com.uwu.animex.data.tracker.ListRankings
import com.uwu.animex.data.tracker.ListStat
import com.uwu.animex.data.tracker.ListStats
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.SyncWatchType
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.sqrt

private const val DEFAULT_EPISODE_MINUTES = 24

private const val RANKING_LIMIT = 10

private val lengthBuckets =
    listOf(
        "1" to 1..1,
        "2-6" to 2..6,
        "7-12" to 7..12,
        "13-25" to 13..25,
        "26-50" to 26..50,
        "51-100" to 51..100,
        "101+" to 101..Int.MAX_VALUE,
    )

fun List<LibraryItem>.hasStatsData(): Boolean = any { it.mediaType != null }

private fun LibraryItem.episodeMinutes(): Int =
    (durationSeconds ?: 0).div(60).takeIf { it > 0 } ?: DEFAULT_EPISODE_MINUTES

private fun LibraryItem.watchedMinutes(): Int = (episodesCompleted ?: 0) * episodeMinutes()

private fun List<LibraryItem>.meanRating(): Double =
    mapNotNull { it.personalRating }.let { if (it.isEmpty()) 0.0 else it.average() }

private fun List<LibraryItem>.toStat(key: String?) =
    ListStat(key = key, count = size, minutes = sumOf { it.watchedMinutes() }, meanScore = meanRating())

private fun List<LibraryItem>.toRank(name: String) =
    ListRank(name = name, count = size, minutes = sumOf { it.watchedMinutes() }, meanScore = meanRating())

private fun List<LibraryItem>.groupStats(key: (LibraryItem) -> String?): List<ListStat> =
    mapNotNull { item -> key(item)?.let { it to item } }
        .groupBy({ it.first }, { it.second })
        .map { (k, v) -> v.toStat(k) }

private fun List<LibraryItem>.rankings(names: (LibraryItem) -> List<String>?): ListRankings {
    val ranks =
        flatMap { item -> names(item).orEmpty().map { it to item } }
            .groupBy({ it.first }, { it.second })
            .map { (name, group) -> group.toRank(name) }
    return ListRankings(
        byCount = ranks.sortedByDescending { it.count }.take(RANKING_LIMIT),
        byTime = ranks.sortedByDescending { it.minutes }.take(RANKING_LIMIT),
        byScore = ranks.filter { it.meanScore > 0 }.sortedByDescending { it.meanScore }.take(RANKING_LIMIT),
    )
}

private fun releaseYear(millis: Long?): String? =
    millis?.let {
        Calendar
            .getInstance(TimeZone.getTimeZone("UTC"))
            .apply { timeInMillis = it }
            .get(Calendar.YEAR)
            .toString()
    }

private fun startYear(date: String?): String? = date?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

fun List<LibraryItem>.toListStats(): ListStats {
    val rated = mapNotNull { it.personalRating }
    val mean = if (rated.isEmpty()) 0.0 else rated.average()
    val deviation = if (rated.isEmpty()) 0.0 else sqrt(rated.sumOf { (it - mean) * (it - mean) } / rated.size)
    return ListStats(
        count = size,
        episodes = sumOf { it.episodesCompleted ?: 0 },
        minutes = sumOf { it.watchedMinutes() },
        plannedMinutes =
            filter { it.status == SyncWatchType.PLANTOWATCH }.sumOf { (it.episodesTotal ?: 0) * it.episodeMinutes() },
        meanScore = mean,
        standardDeviation = deviation,
        scores = groupStats { it.personalRating?.toString() }.sortedBy { it.key?.toIntOrNull() ?: 0 },
        lengths =
            groupStats { item ->
                item.episodesTotal?.let { total -> lengthBuckets.firstOrNull { total in it.second }?.first }
            }.sortedBy { st -> lengthBuckets.indexOfFirst { it.first == st.key } },
        formats = groupStats { it.mediaType?.uppercase() }.sortedByDescending { it.count },
        releaseYears = groupStats { releaseYear(it.releaseDate) }.sortedByDescending { it.key?.toIntOrNull() ?: 0 },
        startYears = groupStats { startYear(it.startDate) }.sortedByDescending { it.key?.toIntOrNull() ?: 0 },
        genres = rankings { it.genres },
        studios = rankings { it.studios },
    )
}
