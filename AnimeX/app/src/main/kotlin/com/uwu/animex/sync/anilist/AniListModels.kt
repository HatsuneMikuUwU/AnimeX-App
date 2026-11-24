package com.uwu.animex.sync.anilist

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.uwu.animex.data.tracker.ListStat
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.sync.unixTime
import kotlin.math.roundToInt

data class AniListEditOptions(
    val scoreFormat: String = "POINT_100",
    val advancedScoringEnabled: Boolean = false,
    val advancedScoring: List<String> = emptyList(),
    val customLists: List<String> = emptyList(),
) {
    fun scoreToTen(value: Double): Int = aniScoreToTen(scoreFormat, value)
}

data class AniListEditEntry(
    val status: String? = null,
    val score: Double? = null,
    val progress: Int? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val repeat: Int? = null,
    val priority: Int? = null,
    val isPrivate: Boolean? = null,
    val hiddenFromStatusLists: Boolean? = null,
    val notes: String? = null,
    val advancedScores: Map<String, Double> = emptyMap(),
    val customLists: Set<String> = emptySet(),
)

data class AniListEditData(
    val mediaId: Int,
    val totalEpisodes: Int?,
    val entry: AniListEditEntry?,
    val options: AniListEditOptions,
)

data class AniListEditUpdate(
    val status: String?,
    val score: Double?,
    val progress: Int?,
    val startedAt: String?,
    val completedAt: String?,
    val repeat: Int?,
    val priority: Int?,
    val isPrivate: Boolean?,
    val hiddenFromStatusLists: Boolean?,
    val notes: String?,
    val advancedScores: List<Double>?,
    val customLists: List<String>?,
)

fun aniScoreToTen(
    format: String?,
    value: Double,
): Int {
    val scaled =
        when (format ?: "POINT_100") {
            "POINT_10_DECIMAL", "POINT_10" -> value
            "POINT_5" -> value * 2
            "POINT_3" -> value * (10.0 / 3.0)
            else -> value / 10.0
        }
    return scaled.roundToInt().coerceIn(0, 10)
}

internal fun isAniListOAuthStateValid(
    expectedState: String?,
    returnedState: String?,
): Boolean = expectedState != null && returnedState == expectedState

internal data class AniTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

internal data class AniCover(
    val large: String? = null,
    val extraLarge: String? = null,
)

internal data class AniEntry(
    val id: Int? = null,
    val status: String? = null,
    val score: Double? = null,
    val progress: Int? = null,
    val repeat: Int? = null,
    val notes: String? = null,
    val startedAt: AniDate? = null,
    val completedAt: AniDate? = null,
    val priority: Int? = null,
    @SerializedName("private") val isPrivate: Boolean? = null,
    val hiddenFromStatusLists: Boolean? = null,
    val advancedScores: Map<String, Double>? = null,
    val customLists: Map<String, Boolean>? = null,
)

internal data class AniDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

internal data class AniMedia(
    val id: Int = 0,
    val idMal: Int? = null,
    val title: AniTitle? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val description: String? = null,
    val coverImage: AniCover? = null,
    val mediaListEntry: AniEntry? = null,
    val format: String? = null,
    val seasonYear: Int? = null,
    val startDate: AniDate? = null,
)

internal data class AniPage(
    val media: List<AniMedia>? = null,
)

internal data class AniData(
    val Viewer: AniViewer? = null,
    val Page: AniPage? = null,
    val Media: AniMedia? = null,
    val SaveMediaListEntry: AniEntry? = null,
    val DeleteMediaListEntry: AniDeleted? = null,
)

internal data class AniViewer(
    val id: Int = 0,
    val name: String? = null,
    val avatar: AniCover? = null,
    val mediaListOptions: AniMediaListOptions? = null,
)

internal data class AniMediaListOptions(
    val scoreFormat: String? = null,
    val animeList: AniTypeOptions? = null,
)

internal data class AniTypeOptions(
    val advancedScoringEnabled: Boolean? = null,
    val advancedScoring: List<String>? = null,
    val customLists: List<String>? = null,
)

internal data class AniDeleted(
    val deleted: Boolean = false,
)

internal data class AniEnvelope(
    val data: AniData? = null,
    val errors: List<AniError>? = null,
)

internal data class AniError(
    val message: String? = null,
)

internal data class OAuthPayload(
    val state: String,
)

internal fun displayTitle(media: AniMedia): String =
    media.title?.english?.takeIf { it.isNotBlank() }
        ?: media.title?.romaji?.takeIf { it.isNotBlank() }
        ?: media.title?.native.orEmpty()

internal fun AniMedia.toResult(): SyncResult {
    mediaListEntry?.id?.let { entryIds[id] = it }
    return buildResult()
}

internal fun AniMedia.buildResult(): SyncResult =
    SyncResult(
        id = id.toString(),
        title = displayTitle(this),
        totalEpisodes = episodes?.takeIf { it > 0 },
        synonyms = listOfNotNull(title?.romaji, title?.english, title?.native).distinct(),
        posterUrl = coverImage?.extraLarge ?: coverImage?.large,
        publicScore = averageScore?.toDouble(),
        synopsis = description,
        myStatus = mediaListEntry?.let { entry ->
            SyncStatus(
                status = watchType(entry.status),
                score = scoreToUi(entry.score),
                watchedEpisodes = entry.progress,
                maxEpisodes = episodes?.takeIf { it > 0 },
                startDate = entry.startedAt.toIso(),
                finishDate = entry.completedAt.toIso(),
                isRewatching = entry.status == "REPEATING",
                rewatchCount = entry.repeat,
                comments = entry.notes,
            )
        },
    )

internal fun AniDate?.toIso(): String? {
    val y = this?.year?.takeIf { it > 0 } ?: return null
    val m = month?.takeIf { it > 0 } ?: return "%04d".format(y)
    val d = day?.takeIf { it > 0 } ?: return "%04d-%02d".format(y, m)
    return "%04d-%02d-%02d".format(y, m, d)
}

internal fun String?.toFuzzyInput(): JsonObject? {
    val parts = this?.split("-")?.mapNotNull { it.toIntOrNull() } ?: return null
    if (parts.size != 3 || parts[0] <= 0) return null
    return JsonObject().apply {
        addProperty("year", parts[0])
        addProperty("month", parts[1])
        addProperty("day", parts[2])
    }
}

internal fun String?.toFuzzyOrClear(): JsonObject {
    val parts = this?.split("-")?.mapNotNull { it.toIntOrNull() }.orEmpty()
    return JsonObject().apply {
        val names = listOf("year", "month", "day")
        names.forEachIndexed { index, name ->
            val value = parts.getOrNull(index)?.takeIf { parts[0] > 0 }
            if (value == null) add(name, JsonNull.INSTANCE) else addProperty(name, value)
        }
    }
}

internal fun watchType(status: String?): SyncWatchType? =
    when (status) {
        "CURRENT", "REPEATING" -> SyncWatchType.WATCHING
        "COMPLETED" -> SyncWatchType.COMPLETED
        "PAUSED" -> SyncWatchType.ONHOLD
        "DROPPED" -> SyncWatchType.DROPPED
        "PLANNING" -> SyncWatchType.PLANTOWATCH
        else -> null
    }

internal fun listStatus(status: SyncWatchType?): String? =
    when (status) {
        SyncWatchType.WATCHING -> "CURRENT"
        SyncWatchType.COMPLETED -> "COMPLETED"
        SyncWatchType.ONHOLD -> "PAUSED"
        SyncWatchType.DROPPED -> "DROPPED"
        SyncWatchType.PLANTOWATCH -> "PLANNING"
        else -> null
    }

internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonObject.list(key: String): List<JsonObject> =
    get(key)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.mapNotNull { e -> e.takeIf { it.isJsonObject }?.asJsonObject }
        .orEmpty()

internal fun JsonObject.str(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }

internal fun JsonObject.int(key: String): Int =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: 0

internal fun JsonObject.dbl(key: String): Double =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0

internal fun JsonObject.toStat(keyField: String) =
    ListStat(str(keyField), int("count"), int("minutesWatched"), dbl("meanScore"))

internal fun JsonObject.fuzzyDate(key: String): String? {
    val d = obj(key) ?: return null
    val y = d.int("year").takeIf { it > 0 } ?: return null
    val m = d.int("month").takeIf { it > 0 } ?: return "%04d".format(y)
    val day = d.int("day").takeIf { it > 0 } ?: return "%04d-%02d".format(y, m)
    return "%04d-%02d-%02d".format(y, m, day)
}

internal fun JsonObject.fuzzyEpoch(key: String): Long? {
    val d = obj(key) ?: return null
    val y = d.int("year").takeIf { it > 0 } ?: return null
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(y, (d.int("month").takeIf { it > 0 } ?: 1) - 1, d.int("day").takeIf { it > 0 } ?: 1)
    return cal.timeInMillis
}
