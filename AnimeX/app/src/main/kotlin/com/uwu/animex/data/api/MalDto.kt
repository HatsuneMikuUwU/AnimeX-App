package com.uwu.animex.data.api

import java.util.Locale

internal data class MalPicture(
    val medium: String? = null,
    val large: String? = null,
)

internal data class MalAltTitles(
    val en: String? = null,
    val ja: String? = null,
    val synonyms: List<String>? = null,
)

internal data class MalDto(
    val id: Int = 0,
    val title: String? = null,
    val main_picture: MalPicture? = null,
    val alternative_titles: MalAltTitles? = null,
    val mean: Double? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val num_list_users: Int? = null,
    val num_scoring_users: Int? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val source: String? = null,
    val media_type: String? = null,
)

internal fun MalDto.toInfo(malId: Int): MalInfo =
    MalInfo(
        malId = malId,
        mean = mean?.takeIf { it > 0.0 },
        scoringUsers = num_scoring_users?.takeIf { it > 0 },
        rank = rank?.takeIf { it > 0 },
        popularity = popularity?.takeIf { it > 0 },
        members = num_list_users?.takeIf { it > 0 },
        startDate = start_date?.takeIf { it.isNotBlank() },
        endDate = end_date?.takeIf { it.isNotBlank() },
        source = prettyMalSource(source),
        posterUrl = (main_picture?.large ?: main_picture?.medium)?.takeIf { it.isNotBlank() },
        mediaType = media_type?.takeIf { it.isNotBlank() },
        fetchedAt = System.currentTimeMillis(),
    )

internal fun prettyMalSource(raw: String?): String? =
    raw
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("unknown", true) }
        ?.replace('_', ' ')
        ?.split(' ')
        ?.filter { it.isNotEmpty() }
        ?.joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
