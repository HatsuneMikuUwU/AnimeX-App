package com.uwu.animex.data.tracker

data class ListStat(
    val key: String? = null,
    val count: Int = 0,
    val minutes: Int = 0,
    val meanScore: Double = 0.0,
)

data class ListRank(
    val name: String? = null,
    val image: String? = null,
    val count: Int = 0,
    val minutes: Int = 0,
    val meanScore: Double = 0.0,
)

data class ListRankings(
    val byCount: List<ListRank> = emptyList(),
    val byTime: List<ListRank> = emptyList(),
    val byScore: List<ListRank> = emptyList(),
)

data class ListStats(
    val count: Int = 0,
    val episodes: Int = 0,
    val totalRewatches: Int? = null,
    val minutes: Int = 0,
    val plannedMinutes: Int = 0,
    val meanScore: Double = 0.0,
    val standardDeviation: Double = 0.0,
    val scores: List<ListStat> = emptyList(),
    val lengths: List<ListStat> = emptyList(),
    val statuses: List<ListStat> = emptyList(),
    val formats: List<ListStat> = emptyList(),
    val countries: List<ListStat> = emptyList(),
    val releaseYears: List<ListStat> = emptyList(),
    val startYears: List<ListStat> = emptyList(),
    val genres: ListRankings = ListRankings(),
    val tags: ListRankings = ListRankings(),
    val studios: ListRankings = ListRankings(),
    val staff: ListRankings = ListRankings(),
    val voiceActors: ListRankings = ListRankings(),
)
