package com.uwu.animex.data.tracker

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.data.local.db.TrackerEntryEntity
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.SyncWatchType

private val gson = Gson()
private val listType = object : TypeToken<List<String>>() {}.type

private fun List<String>?.toJson(): String? = this?.let { gson.toJson(it) }

private fun String?.toList(): List<String>? =
    this?.let { runCatching { gson.fromJson<List<String>>(it, listType) }.getOrNull() }

fun LibraryItem.toEntity(source: String) =
    TrackerEntryEntity(
        source = source,
        syncId = syncId,
        name = name,
        url = url,
        status = status.internalId,
        episodesCompleted = episodesCompleted,
        episodesTotal = episodesTotal,
        personalRating = personalRating,
        lastUpdatedUnixTime = lastUpdatedUnixTime,
        posterUrl = posterUrl,
        releaseDate = releaseDate,
        synonymsJson = synonyms.toJson().orEmpty(),
        startDate = startDate,
        finishDate = finishDate,
        crossMalId = crossMalId,
        mediaType = mediaType,
        genresJson = genres.toJson(),
        studiosJson = studios.toJson(),
        durationSeconds = durationSeconds,
        rewatching = rewatching,
        rewatchCount = rewatchCount,
    )

fun TrackerEntryEntity.toLibraryItem() =
    LibraryItem(
        name = name,
        url = url,
        syncId = syncId,
        status = SyncWatchType.fromInternalId(status),
        episodesCompleted = episodesCompleted,
        episodesTotal = episodesTotal,
        personalRating = personalRating,
        lastUpdatedUnixTime = lastUpdatedUnixTime,
        posterUrl = posterUrl,
        releaseDate = releaseDate,
        synonyms = synonymsJson.toList().orEmpty(),
        startDate = startDate,
        finishDate = finishDate,
        crossMalId = crossMalId,
        mediaType = mediaType,
        genres = genresJson.toList(),
        studios = studiosJson.toList(),
        durationSeconds = durationSeconds,
        rewatching = rewatching,
        rewatchCount = rewatchCount,
    )
