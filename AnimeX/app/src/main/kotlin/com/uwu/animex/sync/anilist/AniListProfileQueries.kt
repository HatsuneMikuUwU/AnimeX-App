package com.uwu.animex.sync.anilist

import com.google.gson.JsonObject
import com.uwu.animex.data.anilist.AniFavorite
import com.uwu.animex.data.anilist.AniListUser
import com.uwu.animex.data.history.HISTORY_TODAY
import com.uwu.animex.data.history.ProfileHistoryItem
import com.uwu.animex.data.history.historyGroup
import com.uwu.animex.data.history.historyTimeId
import com.uwu.animex.data.tracker.ListRank
import com.uwu.animex.data.tracker.ListRankings
import com.uwu.animex.data.tracker.ListStats
import com.uwu.animex.sync.AuthData
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.LibraryList
import com.uwu.animex.sync.LibraryMetadata
import com.uwu.animex.sync.ListSorting
import com.uwu.animex.sync.SyncWatchType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

private const val LIST_CHUNK_SIZE = 500
private const val MAX_CHUNKS = 40
private const val FAVORITE_PAGE_SIZE = 25
private const val MAX_FAVORITE_PAGES = 40

private fun overviewQuery(): String {
    fun dist(
        field: String,
        key: String,
    ) = "$field { $key count minutesWatched meanScore }"
    return "query { Viewer { id name avatar { large } createdAt statistics { anime { " +
        "count episodesWatched minutesWatched meanScore standardDeviation " +
        dist("scores(sort: MEAN_SCORE)", "score") + " " +
        dist("lengths", "length") + " " +
        dist("formats", "format") + " " +
        dist("statuses", "status") + " " +
        dist("countries", "country") + " " +
        dist("releaseYears", "releaseYear") + " " +
        dist("startYears", "startYear") +
        " } } } }"
}

private fun rankingsQuery(): String {
    val sorts = listOf("C" to "COUNT_DESC", "T" to "PROGRESS_DESC", "S" to "MEAN_SCORE_DESC")

    fun ranked(
        field: String,
        inner: String,
    ) = sorts.joinToString(" ") { (suffix, sort) ->
        "$field$suffix: $field(sort: $sort, limit: 10) { $inner count minutesWatched meanScore }"
    }
    val person = "name { userPreferred } image { medium }"
    return "query { Viewer { statistics { anime { " +
        ranked("genres", "genre") + " " +
        ranked("tags", "tag { name }") + " " +
        ranked("studios", "studio { name }") + " " +
        ranked("staff", "staff { $person }") + " " +
        ranked("voiceActors", "voiceActor { $person }") +
        " } } } }"
}

private suspend fun AniListApi.totalAnimeRewatches(
    userId: Int,
    token: String?,
): Int {
    var chunk = 1
    var total = 0
    val seenEntryIds = HashSet<Int>()

    while (true) {
        val query =
            "query { MediaListCollection(userId: $userId, type: ANIME, " +
                "sort: UPDATED_TIME_DESC, chunk: $chunk, perChunk: $LIST_CHUNK_SIZE) { " +
                "hasNextChunk lists { entries { id repeat } } } }"
        val collection =
            graphData(query, token).obj("MediaListCollection")
                ?: throw IllegalStateException("Data daftar AniList kosong")

        collection
            .list("lists")
            .flatMap { it.list("entries") }
            .forEach { entry ->
                val entryId = entry.int("id")
                if (entryId > 0 && seenEntryIds.add(entryId)) {
                    total += entry.int("repeat")
                }
            }

        val hasNext =
            collection
                .get("hasNextChunk")
                ?.takeIf { it.isJsonPrimitive }
                ?.asBoolean == true
        if (!hasNext || chunk >= MAX_CHUNKS) break
        chunk++
    }

    return total
}

private fun JsonObject.toRank(kind: String): ListRank {
    val nested = obj(kind)
    val name =
        when (kind) {
            "genre" -> str("genre")
            "tag", "studio" -> nested?.str("name")
            else -> nested?.obj("name")?.str("userPreferred")
        }
    return ListRank(
        name = name,
        image = nested?.obj("image")?.str("medium"),
        count = int("count"),
        minutes = int("minutesWatched"),
        meanScore = dbl("meanScore"),
    )
}

private fun JsonObject.rankings(
    field: String,
    kind: String,
): AniListRankingsHolder {
    fun by(suffix: String) = list("$field$suffix").map { it.toRank(kind) }
    return AniListRankingsHolder(by("C"), by("T"), by("S"))
}

private class AniListRankingsHolder(
    val c: List<ListRank>,
    val t: List<ListRank>,
    val s: List<ListRank>,
) {
    fun toModel() = ListRankings(byCount = c, byTime = t, byScore = s)
}

private fun lengthOrder(key: String?): Int = key?.takeWhile { it.isDigit() }?.toIntOrNull() ?: Int.MAX_VALUE

internal suspend fun AniListApi.fetchProfile(
    auth: AuthData?,
    previous: ListStats? = null,
): AniListUser {
    val token = auth?.token?.accessToken
    return coroutineScope {
        val rankingsJob =
            async {
                runCatching {
                    graphData(rankingsQuery(), token).obj("Viewer")?.obj("statistics")?.obj("anime")
                }.getOrNull()
            }
        val viewer =
            graphData(overviewQuery(), token).obj("Viewer")
                ?: throw IllegalStateException("Respons AniList kosong")
        val rewatchJob = async { runCatching { totalAnimeRewatches(viewer.int("id"), token) }.getOrNull() }
        val anime = viewer.obj("statistics")?.obj("anime")
        val rankings = rankingsJob.await()
        val totalRewatches = rewatchJob.await() ?: previous?.totalRewatches

        fun dist(
            field: String,
            key: String,
        ) = anime?.list(field).orEmpty().map { it.toStat(key) }

        val statuses = dist("statuses", "status")
        AniListUser(
            id = viewer.int("id"),
            name = viewer.str("name"),
            avatar = viewer.obj("avatar")?.str("large"),
            createdAt = viewer.get("createdAt")?.takeIf { it.isJsonPrimitive }?.asLong,
            stats =
                ListStats(
                    count = anime?.int("count") ?: 0,
                    episodes = anime?.int("episodesWatched") ?: 0,
                    totalRewatches = totalRewatches,
                    minutes = anime?.int("minutesWatched") ?: 0,
                    plannedMinutes = statuses.firstOrNull { it.key == "PLANNING" }?.minutes ?: 0,
                    meanScore = anime?.dbl("meanScore") ?: 0.0,
                    standardDeviation = anime?.dbl("standardDeviation") ?: 0.0,
                    scores =
                        dist("scores", "score")
                            .filter { it.key != null }
                            .sortedBy { it.key?.toDoubleOrNull() ?: 0.0 },
                    lengths = dist("lengths", "length").sortedBy { lengthOrder(it.key) },
                    statuses = statuses.filter { it.key != null },
                    formats = dist("formats", "format").filter { it.key != null },
                    countries = dist("countries", "country").filter { it.key != null },
                    releaseYears =
                        dist("releaseYears", "releaseYear")
                            .filter { it.key != null }
                            .sortedByDescending { it.key?.toIntOrNull() ?: 0 },
                    startYears =
                        dist("startYears", "startYear")
                            .filter { it.key != null }
                            .sortedByDescending { it.key?.toIntOrNull() ?: 0 },
                    genres = rankings?.rankings("genres", "genre")?.toModel() ?: previous?.genres ?: ListRankings(),
                    tags = rankings?.rankings("tags", "tag")?.toModel() ?: previous?.tags ?: ListRankings(),
                    studios =
                        rankings?.rankings("studios", "studio")?.toModel() ?: previous?.studios ?: ListRankings(),
                    staff = rankings?.rankings("staff", "staff")?.toModel() ?: previous?.staff ?: ListRankings(),
                    voiceActors =
                        rankings?.rankings("voiceActors", "voiceActor")?.toModel()
                            ?: previous?.voiceActors
                            ?: ListRankings(),
                ),
        )
    }
}

internal suspend fun AniListApi.fetchHistory(auth: AuthData?): List<ProfileHistoryItem> {
    val token = auth?.token?.accessToken ?: return emptyList()
    val userId = auth.user.id
    val query =
        "query { Page(page: 1, perPage: 50) { activities(userId: $userId, type: MEDIA_LIST, sort: ID_DESC) { " +
            "... on ListActivity { id status progress createdAt media { id type title { romaji english native } } } } } }"
    return graphData(query, token)
        .obj("Page")
        ?.list("activities")
        .orEmpty()
        .mapNotNull { a ->
            val media = a.obj("media") ?: return@mapNotNull null
            if (media.str("type") != "ANIME") return@mapNotNull null
            val mediaId = media.int("id").takeIf { it > 0 } ?: return@mapNotNull null
            val title = media.obj("title")
            val name =
                title?.str("romaji")?.takeIf { it.isNotBlank() }
                    ?: title?.str("english")?.takeIf { it.isNotBlank() }
                    ?: title?.str("native").orEmpty()
            val status = a.str("status").orEmpty()
            val progress = a.str("progress")?.takeIf { it.isNotBlank() }

            val action =
                if (progress != null && status.contains("episode", ignoreCase = true)) {
                    "ep. $progress"
                } else {
                    listOfNotNull(status.replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() }, progress)
                        .joinToString(" ")
                        .ifBlank { "Diperbarui" }
                }
            val epoch = a.get("createdAt")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            ProfileHistoryItem(
                key = "${a.int("id")}",
                mediaId = mediaId,
                title = name,
                action = action,
                time = if (epoch > 0) historyTimeId(epoch * 1000) else "",
                group = if (epoch > 0) historyGroup(epoch * 1000) else HISTORY_TODAY,
                url = "https://anilist.co/anime/$mediaId",
            )
        }
}

internal suspend fun AniListApi.fetchFavorites(auth: AuthData?): List<AniFavorite> {
    val token = auth?.token?.accessToken ?: return emptyList()
    val userId = auth.user.id
    val seen = HashSet<Int>()
    val out = ArrayList<AniFavorite>()
    var page = 1
    while (page <= MAX_FAVORITE_PAGES) {
        val query =
            "query { User(id: $userId) { favourites { anime(page: $page, perPage: $FAVORITE_PAGE_SIZE) { " +
                "pageInfo { hasNextPage } nodes { id title { romaji english native } synonyms " +
                "coverImage { large extraLarge } } } } } }"
        val anime = graphData(query, token).obj("User")?.obj("favourites")?.obj("anime")
        anime?.list("nodes").orEmpty().forEach { node ->
            val id = node.int("id").takeIf { it > 0 } ?: return@forEach
            if (!seen.add(id)) return@forEach
            val title = node.obj("title")
            val romaji = title?.str("romaji")
            val english = title?.str("english")
            val native = title?.str("native")
            val cover = node.obj("coverImage")
            out.add(
                AniFavorite(
                    id = id,
                    name =
                        romaji?.takeIf { it.isNotBlank() }
                            ?: english?.takeIf { it.isNotBlank() }
                            ?: native.orEmpty(),
                    posterUrl = cover?.str("extraLarge") ?: cover?.str("large"),
                    synonyms =
                        (
                            listOfNotNull(romaji, english, native) +
                                (
                                    node
                                        .get("synonyms")
                                        ?.takeIf { it.isJsonArray }
                                        ?.asJsonArray
                                        ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString }
                                        .orEmpty()
                                )
                        ).distinct(),
                ),
            )
        }
        val hasNext =
            anime
                ?.obj("pageInfo")
                ?.get("hasNextPage")
                ?.takeIf { it.isJsonPrimitive }
                ?.asBoolean == true
        if (!hasNext) break
        page++
    }
    return out
}

internal suspend fun AniListApi.fetchLibrary(auth: AuthData?): LibraryMetadata? {
    val token = auth?.token?.accessToken ?: return null
    val userId = auth.user.id
    val date = "{ year month day }"
    val seen = HashSet<String>()
    val items = ArrayList<LibraryItem>()

    var chunk = 1
    while (true) {
        val query =
            "query { MediaListCollection(userId: $userId, type: ANIME, sort: UPDATED_TIME_DESC, " +
                "chunk: $chunk, perChunk: $LIST_CHUNK_SIZE) { hasNextChunk lists { isCustomList entries { " +
                "id status score(format: POINT_10) progress repeat updatedAt startedAt $date completedAt $date " +
                "media { id idMal episodes title { romaji english native } synonyms " +
                "coverImage { large extraLarge } startDate $date } } } } }"
        val collection = graphData(query, token).obj("MediaListCollection")
        val lists =
            collection
                ?.list("lists")
                .orEmpty()
                .filter { it.get("isCustomList")?.takeIf { v -> v.isJsonPrimitive }?.asBoolean != true }
        lists.flatMap { it.list("entries") }.forEach { e ->
            val media = e.obj("media") ?: return@forEach
            val id = media.int("id").takeIf { it > 0 } ?: return@forEach
            val type = watchType(e.str("status")) ?: return@forEach
            if (!seen.add(id.toString())) return@forEach
            e.int("id").takeIf { it > 0 }?.let { entryIds[id] = it }
            val title = media.obj("title")
            val romaji = title?.str("romaji")
            val english = title?.str("english")
            val native = title?.str("native")
            val cover = media.obj("coverImage")
            items.add(
                LibraryItem(
                    name =
                        romaji?.takeIf { it.isNotBlank() }
                            ?: english?.takeIf { it.isNotBlank() }
                            ?: native.orEmpty(),
                    url = "$mainUrl/anime/$id",
                    syncId = id.toString(),
                    status = type,
                    episodesCompleted = e.int("progress"),
                    episodesTotal = media.int("episodes").takeIf { it > 0 },
                    personalRating = e.dbl("score").roundToInt().takeIf { it > 0 },
                    lastUpdatedUnixTime = e.get("updatedAt")?.takeIf { it.isJsonPrimitive }?.asLong,
                    posterUrl = cover?.str("extraLarge") ?: cover?.str("large"),
                    releaseDate = media.fuzzyEpoch("startDate"),
                    synonyms =
                        (
                            listOfNotNull(romaji, english, native) +
                                media
                                    .get("synonyms")
                                    ?.takeIf { it.isJsonArray }
                                    ?.asJsonArray
                                    ?.mapNotNull { it.takeIf { x -> x.isJsonPrimitive }?.asString }
                                    .orEmpty()
                        ).distinct(),
                    startDate = e.fuzzyDate("startedAt"),
                    finishDate = e.fuzzyDate("completedAt"),
                    crossMalId = media.int("idMal").takeIf { it > 0 },
                    rewatching = e.str("status") == "REPEATING",
                    rewatchCount = e.int("repeat"),
                ),
            )
        }
        val hasNext = collection?.get("hasNextChunk")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        if (!hasNext || chunk >= MAX_CHUNKS) break
        chunk++
    }
    val grouped = items.groupBy { it.status }
    val result =
        SyncWatchType.entries
            .filter { it != SyncWatchType.NONE && it in supportedWatchTypes }
            .map { LibraryList(it.label, it, grouped[it].orEmpty()) }
    return LibraryMetadata(result, ListSorting.entries.toSet())
}
