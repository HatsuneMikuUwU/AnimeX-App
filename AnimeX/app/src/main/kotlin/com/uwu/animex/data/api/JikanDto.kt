package com.uwu.animex.data.api

internal data class JikanImageSet(
    val large_image_url: String? = null,
    val image_url: String? = null,
)

internal data class JikanImages(
    val jpg: JikanImageSet? = null,
)

internal data class JikanAired(
    val from: String? = null,
    val to: String? = null,
)

internal data class JikanTitle(
    val title: String? = null,
)

internal data class JikanAnime(
    val mal_id: Int = 0,
    val title: String? = null,
    val title_english: String? = null,
    val title_japanese: String? = null,
    val titles: List<JikanTitle>? = null,
    val title_synonyms: List<String>? = null,
    val score: Double? = null,
    val scored_by: Int? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val members: Int? = null,
    val source: String? = null,
    val type: String? = null,
    val aired: JikanAired? = null,
    val images: JikanImages? = null,
)

internal data class JikanSingle(
    val data: JikanAnime? = null,
)

internal data class JikanList(
    val data: List<JikanAnime>? = null,
)

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

private fun String.isoDate(): String? = take(10).takeIf { ISO_DATE.matches(it) }

internal fun JikanAnime.toInfo(): MalInfo =
    MalInfo(
        malId = mal_id,
        mean = score?.takeIf { it > 0.0 },
        scoringUsers = scored_by?.takeIf { it > 0 },
        rank = rank?.takeIf { it > 0 },
        popularity = popularity?.takeIf { it > 0 },
        members = members?.takeIf { it > 0 },
        startDate = aired?.from?.isoDate(),
        endDate = aired?.to?.isoDate(),
        source = prettyMalSource(source?.lowercase()),
        posterUrl = (images?.jpg?.large_image_url ?: images?.jpg?.image_url)?.takeIf { it.isNotBlank() },
        mediaType = type?.takeIf { it.isNotBlank() },
        fetchedAt = System.currentTimeMillis(),
    )

internal fun JikanAnime.titleList(): List<String> =
    (
        listOfNotNull(title, title_english, title_japanese) +
            titles.orEmpty().mapNotNull { it.title } +
            title_synonyms.orEmpty()
    ).filter { it.isNotBlank() }
        .distinct()
