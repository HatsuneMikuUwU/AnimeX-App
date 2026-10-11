package com.uwu.animex.sync.providers

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.anilist.AniListStats
import com.uwu.animex.data.anilist.AniListUser
import com.uwu.animex.sync.AuthAPI
import com.uwu.animex.sync.AuthData
import com.uwu.animex.sync.AuthLoginPage
import com.uwu.animex.sync.AuthToken
import com.uwu.animex.sync.AuthUser
import com.uwu.animex.sync.HttpException
import com.uwu.animex.sync.LibraryMetadata
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
        val MediaListCollection: AniListCollection? = null,
        val Media: AniMedia? = null,
        val SaveMediaListEntry: AniEntry? = null,
        val DeleteMediaListEntry: AniDeleted? = null,
    )
    private data class AniViewer(
        val id: Int = 0,
        val name: String? = null,
        val avatar: AniCover? = null,
        val mediaListOptions: AniMediaListOptions? = null,
        val createdAt: Long? = null,
        val statistics: AniUserStatistics? = null,
    )
    private data class AniUserStatistics(val anime: AniAnimeStatistics? = null)
    private data class AniAnimeStatistics(
        val count: Int? = null,
        val meanScore: Double? = null,
        val episodesWatched: Int? = null,
        val minutesWatched: Int? = null,
        val statuses: List<AniStatusCount>? = null,
    )
    private data class AniStatusCount(val status: String? = null, val count: Int? = null)
    private data class AniListGroup(val entries: List<AniEntry>? = null)
    private data class AniListCollection(val lists: List<AniListGroup>? = null)
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

    /** Profile with anime statistics, shaped like MAL's profile so both screens can share one UI. */
    suspend fun profile(auth: AuthData?): AniListUser {
        val token = auth?.token?.accessToken
        val gql = "query { Viewer { id name avatar { large } createdAt statistics { anime { count meanScore episodesWatched minutesWatched statuses { status count } } } } }"
        val viewer = graph(gql, token = token).Viewer ?: throw IllegalStateException("Respons AniList kosong")
        val anime = viewer.statistics?.anime
        fun count(vararg keys: String) = anime?.statuses.orEmpty().filter { it.status in keys }.sumOf { it.count ?: 0 }
        val rewatched =
            runCatching {
                val q = "query(\$userId: Int) { MediaListCollection(userId: \$userId, type: ANIME) { lists { entries { repeat } } } }"
                val vars = JsonObject().apply { addProperty("userId", viewer.id) }
                graph(q, vars, token).MediaListCollection?.lists.orEmpty()
                    .flatMap { it.entries.orEmpty() }
                    .sumOf { it.repeat ?: 0 }
            }.getOrDefault(0)
        return AniListUser(
            id = viewer.id,
            name = viewer.name,
            avatar = viewer.avatar?.large,
            createdAt = viewer.createdAt,
            stats =
                AniListStats(
                    watching = count("CURRENT", "REPEATING"),
                    completed = count("COMPLETED"),
                    onHold = count("PAUSED"),
                    dropped = count("DROPPED"),
                    planned = count("PLANNING"),
                    episodes = anime?.episodesWatched ?: 0,
                    days = (anime?.minutesWatched ?: 0) / 1440.0,
                    rewatched = rewatched,
                    meanScore = (anime?.meanScore ?: 0.0) / 10.0,
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

    override suspend fun library(auth: AuthData?): LibraryMetadata? = null

    override fun urlToId(url: String): String? = Regex("anilist\\.co/anime/(\\d+)").find(url)?.groupValues?.get(1)
}
