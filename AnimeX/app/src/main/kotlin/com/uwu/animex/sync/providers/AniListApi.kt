package com.uwu.animex.sync.providers

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.anilist.AniRank
import com.uwu.animex.data.anilist.AniRankings
import com.uwu.animex.data.anilist.AniStat
import com.uwu.animex.data.anilist.AniListStats
import com.uwu.animex.data.anilist.AniListUser
import com.uwu.animex.sync.AuthAPI
import com.uwu.animex.sync.AuthData
import com.uwu.animex.sync.AuthLoginPage
import com.uwu.animex.sync.AuthToken
import com.uwu.animex.sync.AuthUser
import com.uwu.animex.sync.HttpException
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.LibraryList
import com.uwu.animex.sync.LibraryMetadata
import com.uwu.animex.sync.ListSorting
import com.uwu.animex.sync.SyncAPI
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncSearchResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.sync.unixTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** AniList's mobile-friendly implicit OAuth flow and GraphQL list API. */
class AniListApi : SyncAPI() {
    override val name = "AniList"
    override val idPrefix = "anilist"
    override val hasOAuth2 = true
    override val redirectUrlIdentifier: String? = "anilist-auth"
    override val mainUrl = "https://anilist.co"

    override val supportedWatchTypes = setOf(
        SyncWatchType.WATCHING,
        SyncWatchType.COMPLETED,
        SyncWatchType.ONHOLD,
        SyncWatchType.DROPPED,
        SyncWatchType.PLANTOWATCH,
        SyncWatchType.NONE,
    )

    companion object {
        val CLIENT_ID: String get() = Secrets.anilistClientId
        const val REDIRECT_URI = "animex://anilist-auth"
        const val PROFILE_URL = "https://anilist.co/user/"
        private const val API = "https://graphql.anilist.co"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val http = NetworkModule.client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class AniTitle(val romaji: String? = null, val english: String? = null, val native: String? = null)
    private data class AniCover(val large: String? = null, val extraLarge: String? = null)
    private data class AniEntry(
        val id: Int? = null,
        val status: String? = null,
        val score: Double? = null,
        val progress: Int? = null,
        val repeat: Int? = null,
    )
    private data class AniMedia(
        val id: Int = 0,
        val idMal: Int? = null,
        val title: AniTitle? = null,
        val episodes: Int? = null,
        val averageScore: Int? = null,
        val description: String? = null,
        val coverImage: AniCover? = null,
        val mediaListEntry: AniEntry? = null,
    )
    private data class AniPage(val media: List<AniMedia>? = null)
    private data class AniData(
        val Viewer: AniViewer? = null,
        val Page: AniPage? = null,
        val Media: AniMedia? = null,
        val SaveMediaListEntry: AniEntry? = null,
        val DeleteMediaListEntry: AniDeleted? = null,
    )
    private data class AniViewer(
        val id: Int = 0,
        val name: String? = null,
        val avatar: AniCover? = null,
        val mediaListOptions: AniMediaListOptions? = null,
    )
    private data class AniMediaListOptions(val scoreFormat: String? = null)
    private data class AniDeleted(val deleted: Boolean = false)
    private data class AniEnvelope(val data: AniData? = null, val errors: List<AniError>? = null)
    private data class AniError(val message: String? = null)
    private data class OAuthPayload(val state: String)
    @Volatile private var scoreFormat: String? = null

    private fun scoreToUi(score: Double?): Int? {
        val value = score ?: return null
        val scaled = when (scoreFormat ?: "POINT_100") {
            "POINT_10_DECIMAL", "POINT_10" -> value
            "POINT_5" -> value * 2
            "POINT_3" -> value * (10.0 / 3.0)
            else -> value / 10.0
        }
        return scaled.roundToInt().coerceIn(0, 10)
    }

    private fun scoreToAniList(score: Int): Float {
        val value = score.coerceIn(0, 10).toDouble()
        return when (scoreFormat ?: "POINT_100") {
            "POINT_10_DECIMAL", "POINT_10" -> value
            "POINT_5" -> (value / 2.0).roundToInt().toDouble()
            "POINT_3" -> (value * 3.0 / 10.0).roundToInt().toDouble()
            else -> value * 10.0
        }.toFloat()
    }

    private fun displayTitle(media: AniMedia): String =
        media.title?.english?.takeIf { it.isNotBlank() }
            ?: media.title?.romaji?.takeIf { it.isNotBlank() }
            ?: media.title?.native.orEmpty()

    private fun AniMedia.toResult(): SyncResult = SyncResult(
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
                rewatchCount = entry.repeat,
            )
        },
    )

    private fun watchType(status: String?): SyncWatchType? = when (status) {
        "CURRENT", "REPEATING" -> SyncWatchType.WATCHING
        "COMPLETED" -> SyncWatchType.COMPLETED
        "PAUSED" -> SyncWatchType.ONHOLD
        "DROPPED" -> SyncWatchType.DROPPED
        "PLANNING" -> SyncWatchType.PLANTOWATCH
        else -> null
    }

    private fun listStatus(status: SyncWatchType?): String? = when (status) {
        SyncWatchType.WATCHING -> "CURRENT"
        SyncWatchType.COMPLETED -> "COMPLETED"
        SyncWatchType.ONHOLD -> "PAUSED"
        SyncWatchType.DROPPED -> "DROPPED"
        SyncWatchType.PLANTOWATCH -> "PLANNING"
        else -> null
    }

    override fun loginRequest(): AuthLoginPage? {
        if (CLIENT_ID.isBlank()) return null
        val state = AuthAPI.generateCodeVerifier().take(32)
        val url = "https://anilist.co/api/v2/oauth/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("response_type", "token")
            .addQueryParameter("state", state)
            .build().toString()
        return AuthLoginPage(url, gson.toJson(OAuthPayload(state)))
    }

    override fun isValidRedirectUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        return uri.scheme == "animex" && uri.host == "anilist-auth"
    }

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val expected = payload?.let { runCatching { gson.fromJson(it, OAuthPayload::class.java) }.getOrNull() }?.state
        val uri = Uri.parse(redirectUrl)
        val fragment = Uri.parse("https://callback.invalid/?${uri.encodedFragment.orEmpty()}")
        val token = uri.getQueryParameter("access_token") ?: fragment.getQueryParameter("access_token")
        val returnedState = uri.getQueryParameter("state") ?: fragment.getQueryParameter("state")
        if (expected == null || token.isNullOrBlank()) return null
        if (returnedState != null && returnedState != expected) return null
        val expires = (uri.getQueryParameter("expires_in") ?: fragment.getQueryParameter("expires_in"))?.toLongOrNull()
        return AuthToken(accessToken = token, accessTokenLifetime = expires?.let { unixTime() + it })
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        // AniList tokens last one year and currently have no refresh-token flow.
        throw HttpException(401)
    }

    private suspend fun graph(
        query: String,
        variables: JsonObject = JsonObject(),
        token: String? = null,
    ): AniData = withContext(Dispatchers.IO) {
        val requestJson = JsonObject().apply {
            addProperty("query", query)
            add("variables", variables)
        }
        val request = Request.Builder().url(API)
            .header("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .post(requestJson.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw HttpException(response.code)
            val envelope = gson.fromJson(body, AniEnvelope::class.java)
            envelope.errors?.firstOrNull()?.message?.let { throw IllegalStateException(it) }
            envelope.data ?: throw IllegalStateException("Respons AniList kosong")
        }
    }

    private fun variableInt(value: Int): JsonObject = JsonObject().apply { addProperty("id", value) }

    private suspend fun viewer(token: String?): AniViewer? =
        graph("query { Viewer { id name avatar { large } mediaListOptions { scoreFormat } } }", token = token).Viewer

    override suspend fun user(token: AuthToken?): AuthUser? {
        val viewer = viewer(token?.accessToken) ?: return null
        scoreFormat = viewer.mediaListOptions?.scoreFormat ?: "POINT_100"
        return AuthUser(viewer.name, viewer.id, viewer.avatar?.large)
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.list(key: String): List<JsonObject> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { e -> e.takeIf { it.isJsonObject }?.asJsonObject }
            .orEmpty()

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(key: String): Int = get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0

    private fun JsonObject.dbl(key: String): Double = get(key)?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0

    private fun JsonObject.toStat(keyField: String) =
        AniStat(str(keyField), int("count"), int("minutesWatched"), dbl("meanScore"))

    private suspend fun graphData(query: String, token: String?): JsonObject = withContext(Dispatchers.IO) {
        val requestJson = JsonObject().apply {
            addProperty("query", query)
            add("variables", JsonObject())
        }
        val request = Request.Builder().url(API)
            .header("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .post(requestJson.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw HttpException(response.code)
            val root = gson.fromJson(body, JsonObject::class.java)
            root.list("errors").firstOrNull()?.str("message")?.let { throw IllegalStateException(it) }
            root.obj("data") ?: throw IllegalStateException("Respons AniList kosong")
        }
    }

    private fun overviewQuery(): String {
        fun dist(field: String, key: String) = "$field { $key count minutesWatched meanScore }"
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
        fun ranked(field: String, inner: String) =
            sorts.joinToString(" ") { (suffix, sort) ->
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

    private fun JsonObject.toRank(kind: String): AniRank {
        val nested = obj(kind)
        val name =
            when (kind) {
                "genre" -> str("genre")
                "tag", "studio" -> nested?.str("name")
                else -> nested?.obj("name")?.str("userPreferred")
            }
        return AniRank(
            name = name,
            image = nested?.obj("image")?.str("medium"),
            count = int("count"),
            minutes = int("minutesWatched"),
            meanScore = dbl("meanScore"),
        )
    }

    private fun JsonObject.rankings(field: String, kind: String): AniListRankingsHolder {
        fun by(suffix: String) = list("$field$suffix").map { it.toRank(kind) }
        return AniListRankingsHolder(by("C"), by("T"), by("S"))
    }

    private class AniListRankingsHolder(val c: List<AniRank>, val t: List<AniRank>, val s: List<AniRank>) {
        fun toModel() = AniRankings(byCount = c, byTime = t, byScore = s)
    }

    /** Profile plus the same anime statistics AniHyou shows (overview, genres, tags, studios, staff, voice actors). */
    suspend fun profile(auth: AuthData?): AniListUser {
        val token = auth?.token?.accessToken
        val viewer = graphData(overviewQuery(), token).obj("Viewer") ?: throw IllegalStateException("Respons AniList kosong")
        val anime = viewer.obj("statistics")?.obj("anime")
        val rankings =
            runCatching { graphData(rankingsQuery(), token).obj("Viewer")?.obj("statistics")?.obj("anime") }.getOrNull()

        fun dist(field: String, key: String) = anime?.list(field).orEmpty().map { it.toStat(key) }

        val statuses = dist("statuses", "status")
        return AniListUser(
            id = viewer.int("id"),
            name = viewer.str("name"),
            avatar = viewer.obj("avatar")?.str("large"),
            createdAt = viewer.get("createdAt")?.takeIf { it.isJsonPrimitive }?.asLong,
            stats =
                AniListStats(
                    count = anime?.int("count") ?: 0,
                    episodes = anime?.int("episodesWatched") ?: 0,
                    minutes = anime?.int("minutesWatched") ?: 0,
                    plannedMinutes = statuses.firstOrNull { it.key == "PLANNING" }?.minutes ?: 0,
                    meanScore = anime?.dbl("meanScore") ?: 0.0,
                    standardDeviation = anime?.dbl("standardDeviation") ?: 0.0,
                    scores =
                        dist("scores", "score")
                            .filter { it.key != null }
                            .sortedBy { it.key?.toDoubleOrNull() ?: 0.0 },
                    lengths =
                        dist("lengths", "length").sortedBy {
                            val k = it.key
                            if (k == null) Int.MAX_VALUE else if (k.contains('+')) k.length * 2 else k.length
                        },
                    statuses = statuses.filter { it.key != null },
                    formats = dist("formats", "format").filter { it.key != null },
                    countries = dist("countries", "country").filter { it.key != null },
                    releaseYears = dist("releaseYears", "releaseYear").filter { it.key != null }.sortedByDescending { it.key?.toIntOrNull() ?: 0 },
                    startYears = dist("startYears", "startYear").filter { it.key != null }.sortedByDescending { it.key?.toIntOrNull() ?: 0 },
                    genres = rankings?.rankings("genres", "genre")?.toModel() ?: AniRankings(),
                    tags = rankings?.rankings("tags", "tag")?.toModel() ?: AniRankings(),
                    studios = rankings?.rankings("studios", "studio")?.toModel() ?: AniRankings(),
                    staff = rankings?.rankings("staff", "staff")?.toModel() ?: AniRankings(),
                    voiceActors = rankings?.rankings("voiceActors", "voiceActor")?.toModel() ?: AniRankings(),
                ),
        )
    }

    override suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? {
        val gql = "query(\$search: String) { Page(page: 1, perPage: 25) { media(search: \$search, type: ANIME) { id title { romaji english native } coverImage { large extraLarge } } } }"
        val vars = JsonObject().apply { addProperty("search", query.take(64)) }
        return graph(gql, vars, auth?.token?.accessToken).Page?.media.orEmpty().map { media ->
            SyncSearchResult(
                name = displayTitle(media),
                syncId = media.id.toString(),
                url = "$mainUrl/anime/${media.id}",
                posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                synonyms = listOfNotNull(media.title?.romaji, media.title?.english, media.title?.native).distinct(),
            )
        }
    }

    override suspend fun load(auth: AuthData?, id: String): SyncResult? {
        if (scoreFormat == null && auth?.token?.accessToken != null) {
            scoreFormat = viewer(auth.token.accessToken)?.mediaListOptions?.scoreFormat ?: "POINT_100"
        }
        val gql = "query(\$id: Int) { Media(id: \$id, type: ANIME) { id idMal episodes averageScore description(asHtml: false) title { romaji english native } coverImage { large extraLarge } mediaListEntry { id status score progress repeat } } }"
        val data = graph(gql, variableInt(id.toIntOrNull() ?: return null), auth?.token?.accessToken)
        return data.Media?.toResult()
    }

    override suspend fun status(auth: AuthData?, id: String): SyncStatus? = load(auth, id)?.myStatus

    override suspend fun updateStatus(auth: AuthData?, id: String, newStatus: SyncStatus): Boolean {
        if (newStatus.status == SyncWatchType.NONE) return removeStatus(auth, id)
        val status = listStatus(newStatus.status) ?: "CURRENT"
        val mutation = "mutation(\$mediaId: Int, \$status: MediaListStatus, \$progress: Int, \$score: Float) { SaveMediaListEntry(mediaId: \$mediaId, status: \$status, progress: \$progress, score: \$score) { id status progress score } }"
        val vars = JsonObject().apply {
            addProperty("mediaId", id.toInt())
            addProperty("status", status)
            addProperty("progress", newStatus.watchedEpisodes ?: 0)
            val score = newStatus.score?.takeIf { it > 0 }
            if (score == null) add("score", JsonNull.INSTANCE) else addProperty("score", scoreToAniList(score))
        }
        return graph(mutation, vars, auth?.token?.accessToken).SaveMediaListEntry != null
    }

    override suspend fun removeStatus(auth: AuthData?, id: String): Boolean {
        val query = "query(\$mediaId: Int) { Media(id: \$mediaId, type: ANIME) { mediaListEntry { id } } }"
        val vars = variableInt(id.toIntOrNull() ?: return false)
        val entryId = graph(query, vars, auth?.token?.accessToken).Media?.mediaListEntry?.id ?: return true
        val mutation = "mutation(\$id: Int) { DeleteMediaListEntry(id: \$id) { deleted } }"
        return graph(mutation, variableInt(entryId), auth?.token?.accessToken).DeleteMediaListEntry?.deleted == true
    }

    private fun JsonObject.fuzzyDate(key: String): String? {
        val d = obj(key) ?: return null
        val y = d.int("year").takeIf { it > 0 } ?: return null
        val m = d.int("month").takeIf { it > 0 } ?: return "%04d".format(y)
        val day = d.int("day").takeIf { it > 0 } ?: return "%04d-%02d".format(y, m)
        return "%04d-%02d-%02d".format(y, m, day)
    }

    private fun JsonObject.fuzzyEpoch(key: String): Long? {
        val d = obj(key) ?: return null
        val y = d.int("year").takeIf { it > 0 } ?: return null
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(y, (d.int("month").takeIf { it > 0 } ?: 1) - 1, d.int("day").takeIf { it > 0 } ?: 1)
        return cal.timeInMillis / 1000L
    }

    override suspend fun library(auth: AuthData?): LibraryMetadata? {
        val token = auth?.token?.accessToken ?: return null
        val userId = auth.user.id
        val date = "{ year month day }"
        val query =
            "query { MediaListCollection(userId: $userId, type: ANIME) { lists { entries { " +
                "status score(format: POINT_10) progress updatedAt startedAt $date completedAt $date " +
                "media { id episodes title { romaji english native } synonyms " +
                "coverImage { large extraLarge } startDate $date } } } } }"
        val lists = graphData(query, token).obj("MediaListCollection")?.list("lists").orEmpty()
        val seen = HashSet<String>()
        val items = ArrayList<LibraryItem>()
        lists.flatMap { it.list("entries") }.forEach { e ->
            val media = e.obj("media") ?: return@forEach
            val id = media.int("id").takeIf { it > 0 } ?: return@forEach
            val type = watchType(e.str("status")) ?: return@forEach
            if (!seen.add(id.toString())) return@forEach
            val title = media.obj("title")
            val romaji = title?.str("romaji")
            val english = title?.str("english")
            val native = title?.str("native")
            val cover = media.obj("coverImage")
            items.add(
                LibraryItem(
                    name = english?.takeIf { it.isNotBlank() } ?: romaji?.takeIf { it.isNotBlank() } ?: native.orEmpty(),
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
                        (listOfNotNull(romaji, english, native) + media.get("synonyms")?.takeIf { it.isJsonArray }?.asJsonArray
                            ?.mapNotNull { it.takeIf { x -> x.isJsonPrimitive }?.asString }.orEmpty()).distinct(),
                    startDate = e.fuzzyDate("startedAt"),
                    finishDate = e.fuzzyDate("completedAt"),
                ),
            )
        }
        val grouped = items.groupBy { it.status }
        val result =
            SyncWatchType.entries
                .filter { it != SyncWatchType.NONE && it in supportedWatchTypes }
                .map { LibraryList(it.label, it, grouped[it].orEmpty()) }
        return LibraryMetadata(result, ListSorting.entries.toSet())
    }

    override fun urlToId(url: String): String? = Regex("anilist\\.co/anime/(\\d+)").find(url)?.groupValues?.get(1)
}
