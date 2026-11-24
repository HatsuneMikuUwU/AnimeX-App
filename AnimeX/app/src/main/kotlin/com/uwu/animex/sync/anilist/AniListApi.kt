package com.uwu.animex.sync.anilist

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.anilist.AniFavorite
import com.uwu.animex.data.anilist.AniListUser
import com.uwu.animex.data.history.HISTORY_TODAY
import com.uwu.animex.data.history.ProfileHistoryItem
import com.uwu.animex.data.history.historyGroup
import com.uwu.animex.data.history.historyTimeId
import com.uwu.animex.data.tracker.ListRank
import com.uwu.animex.data.tracker.ListRankings
import com.uwu.animex.data.tracker.ListStat
import com.uwu.animex.data.tracker.ListStats
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
import com.uwu.animex.sync.isAnimexRedirect
import com.uwu.animex.sync.unixTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.uwu.animex.core.network.awaitResponse

class AniListApi : SyncAPI() {
    override val name = "AniList"
    override val idPrefix = "anilist"
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
    private val http = NetworkModule.client
        .newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var scoreFormat: String? = null

    private fun scoreToUi(score: Double?): Int? = score?.let { aniScoreToTen(scoreFormat, it) }

    private fun scoreToAniList(score: Int): Float {
        val value = score.coerceIn(0, 10).toDouble()
        return when (scoreFormat ?: "POINT_100") {
            "POINT_10_DECIMAL", "POINT_10" -> value
            "POINT_5" -> (value / 2.0).roundToInt().toDouble()
            "POINT_3" -> (value * 3.0 / 10.0).roundToInt().toDouble()
            else -> value * 10.0
        }.toFloat()
    }

    override fun loginRequest(): AuthLoginPage? {
        if (CLIENT_ID.isBlank()) return null
        val state = AuthAPI.generateCodeVerifier().take(32)
        val url = "https://anilist.co/api/v2/oauth/authorize"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("response_type", "token")
            .addQueryParameter("state", state)
            .build()
            .toString()
        return AuthLoginPage(url, gson.toJson(OAuthPayload(state)))
    }

    override fun isValidRedirectUrl(url: String): Boolean = isAnimexRedirect(url, "anilist-auth")

    override suspend fun login(
        redirectUrl: String,
        payload: String?,
    ): AuthToken? {
        val expected = payload?.let { runCatching { gson.fromJson(it, OAuthPayload::class.java) }.getOrNull() }?.state
        val uri = Uri.parse(redirectUrl)
        val fragment = Uri.parse("https://callback.invalid/?${uri.encodedFragment.orEmpty()}")
        val token = uri.getQueryParameter("access_token") ?: fragment.getQueryParameter("access_token")
        val returnedState = uri.getQueryParameter("state") ?: fragment.getQueryParameter("state")
        if (token.isNullOrBlank() || !isAniListOAuthStateValid(expected, returnedState)) return null
        val expires = (uri.getQueryParameter("expires_in") ?: fragment.getQueryParameter("expires_in"))?.toLongOrNull()
        return AuthToken(accessToken = token, accessTokenLifetime = expires?.let { unixTime() + it })
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        throw HttpException(401)
    }

    private suspend fun executeWithRetry(
        request: Request,
        retrySafe: Boolean,
    ): okhttp3.Response {
        var attempt = 0
        var response = http.newCall(request).awaitResponse()
        while (retrySafe && response.code == 429 && attempt < 2) {
            val wait = (response.header("Retry-After")?.toLongOrNull() ?: 2L).coerceIn(1L, 15L)
            response.close()
            attempt++
            delay(wait * 1000L)
            response = http.newCall(request).awaitResponse()
        }
        return response
    }

    private suspend fun graph(
        query: String,
        variables: JsonObject = JsonObject(),
        token: String? = null,
    ): AniData =
        withContext(Dispatchers.IO) {
            val requestJson = JsonObject().apply {
                addProperty("query", query)
                add("variables", variables)
            }
            val request = Request
                .Builder()
                .url(API)
                .header("Accept", "application/json")
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .post(requestJson.toString().toRequestBody(JSON))
                .build()
            executeWithRetry(
                request,
                retrySafe = query.trimStart().startsWith("query", ignoreCase = true),
            ).use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw HttpException(response.code)
                val envelope = gson.fromJson(body, AniEnvelope::class.java)
                envelope.errors
                    ?.firstOrNull()
                    ?.message
                    ?.let { throw IllegalStateException(it) }
                envelope.data ?: throw IllegalStateException("Respons AniList kosong")
            }
        }

    private val entryIds = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    private fun variableInt(value: Int): JsonObject = JsonObject().apply { addProperty("id", value) }

    private suspend fun viewer(token: String?): AniViewer? =
        graph("query { Viewer { id name avatar { large } mediaListOptions { scoreFormat } } }", token = token).Viewer

    override suspend fun user(token: AuthToken?): AuthUser? {
        val viewer = viewer(token?.accessToken) ?: return null
        scoreFormat = viewer.mediaListOptions?.scoreFormat ?: "POINT_100"
        return AuthUser(viewer.name, viewer.id, viewer.avatar?.large)
    }

    internal suspend fun graphData(
        query: String,
        token: String?,
    ): JsonObject =
        withContext(Dispatchers.IO) {
            val requestJson = JsonObject().apply {
                addProperty("query", query)
                add("variables", JsonObject())
            }
            val request = Request
                .Builder()
                .url(API)
                .header("Accept", "application/json")
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .post(requestJson.toString().toRequestBody(JSON))
                .build()
            executeWithRetry(request, retrySafe = false).use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw HttpException(response.code)
                val root = gson.fromJson(body, JsonObject::class.java)
                root
                    .list("errors")
                    .firstOrNull()
                    ?.str("message")
                    ?.let { throw IllegalStateException(it) }
                root.obj("data") ?: throw IllegalStateException("Respons AniList kosong")
            }
        }

    override suspend fun search(
        auth: AuthData?,
        query: String,
    ): List<SyncSearchResult>? {
        val gql =
            "query(\$search: String) { Page(page: 1, perPage: 25) { media(search: \$search, type: ANIME) { " +
                "id format episodes seasonYear startDate { year } title { romaji english native } " +
                "coverImage { large extraLarge } } } }"
        val vars = JsonObject().apply { addProperty("search", query.take(64)) }
        return graph(gql, vars, auth?.token?.accessToken).Page?.media.orEmpty().map { media ->
            SyncSearchResult(
                name = displayTitle(media),
                syncId = media.id.toString(),
                url = "$mainUrl/anime/${media.id}",
                posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                synonyms = listOfNotNull(media.title?.romaji, media.title?.english, media.title?.native).distinct(),
                type = media.format,
                year = (media.seasonYear ?: media.startDate?.year)?.takeIf { it in 1900..2100 },
                episodes = media.episodes?.takeIf { it > 0 },
            )
        }
    }

    override suspend fun load(
        auth: AuthData?,
        id: String,
    ): SyncResult? {
        if (scoreFormat == null && auth?.token?.accessToken != null) {
            scoreFormat = viewer(auth.token.accessToken)?.mediaListOptions?.scoreFormat ?: "POINT_100"
        }
        val gql =
            "query(\$id: Int) { Media(id: \$id, type: ANIME) { id idMal episodes averageScore description(asHtml: " +
                "false) title { romaji english native } coverImage { large extraLarge } mediaListEntry { id status score " +
                "progress repeat notes startedAt { year month day } completedAt { year month day } } } }"
        val data = graph(gql, variableInt(id.toIntOrNull() ?: return null), auth?.token?.accessToken)
        return data.Media?.toResult()
    }

    override suspend fun status(
        auth: AuthData?,
        id: String,
    ): SyncStatus? = load(auth, id)?.myStatus

    override suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: SyncStatus,
    ): Boolean {
        if (newStatus.status == SyncWatchType.NONE) return removeStatus(auth, id)
        if (scoreFormat == null && auth?.token?.accessToken != null) {
            scoreFormat = viewer(auth.token.accessToken)?.mediaListOptions?.scoreFormat ?: "POINT_100"
        }

        val mutation = "mutation(\$mediaId: Int, \$status: MediaListStatus, \$progress: Int, \$score: Float, " +
            "\$repeat: Int, \$notes: String, \$startedAt: FuzzyDateInput, \$completedAt: FuzzyDateInput) { " +
            "SaveMediaListEntry(mediaId: \$mediaId, status: \$status, progress: \$progress, score: \$score, " +
            "repeat: \$repeat, notes: \$notes, startedAt: \$startedAt, completedAt: \$completedAt) { id status " +
            "progress score } }"
        val mediaId = id.toIntOrNull() ?: return false
        val vars = JsonObject().apply {
            addProperty("mediaId", mediaId)
            newStatus.status?.let { status ->
                addProperty(
                    "status",
                    if (newStatus.isRewatching == true && (status == SyncWatchType.WATCHING || status == SyncWatchType.COMPLETED)) {
                        "REPEATING"
                    } else {
                        listStatus(status)
                    },
                )
            }
            newStatus.watchedEpisodes?.let { addProperty("progress", it) }
            newStatus.score?.let { addProperty("score", scoreToAniList(it)) }
            newStatus.rewatchCount?.let { addProperty("repeat", it) }
            newStatus.comments?.let { addProperty("notes", it) }
            newStatus.startDate.toFuzzyInput()?.let { add("startedAt", it) }
            newStatus.finishDate.toFuzzyInput()?.let { add("completedAt", it) }
        }
        return graph(mutation, vars, auth?.token?.accessToken).SaveMediaListEntry != null
    }

    private val editEntryFields =
        "id status score progress repeat priority private hiddenFromStatusLists notes " +
            "advancedScores customLists startedAt { year month day } completedAt { year month day }"
    private val viewerOptionsFields =
        "Viewer { mediaListOptions { scoreFormat animeList { advancedScoringEnabled advancedScoring customLists } } }"

    suspend fun editData(
        auth: AuthData?,
        id: Int,
    ): AniListEditData? {
        val gql = "query(\$id: Int) { $viewerOptionsFields " +
            "Media(id: \$id, type: ANIME) { id episodes mediaListEntry { $editEntryFields } } }"
        return buildEditData(graph(gql, variableInt(id), auth?.token?.accessToken))
    }

    suspend fun loadWithEdit(
        auth: AuthData?,
        id: Int,
    ): Pair<SyncResult, AniListEditData>? {
        val gql = "query(\$id: Int) { $viewerOptionsFields " +
            "Media(id: \$id, type: ANIME) { id idMal episodes averageScore description(asHtml: false) " +
            "title { romaji english native } coverImage { large extraLarge } mediaListEntry { $editEntryFields } } }"
        val data = graph(gql, variableInt(id), auth?.token?.accessToken)
        val edit = buildEditData(data) ?: return null
        val result = data.Media?.toResult() ?: return null
        return result to edit
    }

    private fun buildEditData(data: AniData): AniListEditData? {
        val media = data.Media ?: return null
        val listOptions = data.Viewer?.mediaListOptions
        val format = listOptions?.scoreFormat ?: "POINT_100"
        scoreFormat = format
        val anime = listOptions?.animeList
        val entry = media.mediaListEntry
        return AniListEditData(
            mediaId = media.id,
            totalEpisodes = media.episodes?.takeIf { it > 0 },
            entry =
                entry?.let {
                    AniListEditEntry(
                        status = it.status,
                        score = it.score,
                        progress = it.progress,
                        startedAt = it.startedAt.toIso(),
                        completedAt = it.completedAt.toIso(),
                        repeat = it.repeat,
                        priority = it.priority,
                        isPrivate = it.isPrivate,
                        hiddenFromStatusLists = it.hiddenFromStatusLists,
                        notes = it.notes,
                        advancedScores = it.advancedScores.orEmpty(),
                        customLists = it.customLists
                            .orEmpty()
                            .filterValues { on -> on }
                            .keys,
                    )
                },
            options =
                AniListEditOptions(
                    scoreFormat = format,
                    advancedScoringEnabled = anime?.advancedScoringEnabled == true,
                    advancedScoring = anime?.advancedScoring.orEmpty(),
                    customLists = (anime?.customLists.orEmpty() + entry?.customLists?.keys.orEmpty()).distinct(),
                ),
        )
    }

    suspend fun saveEntry(
        auth: AuthData?,
        id: Int,
        update: AniListEditUpdate,
    ): Boolean {
        val mutation = "mutation(\$mediaId: Int, \$status: MediaListStatus, \$score: Float, \$progress: Int, " +
            "\$startedAt: FuzzyDateInput, \$completedAt: FuzzyDateInput, \$repeat: Int, \$priority: Int, " +
            "\$private: Boolean, \$hiddenFromStatusLists: Boolean, \$notes: String, \$advancedScores: [Float], " +
            "\$customLists: [String]) { SaveMediaListEntry(mediaId: \$mediaId, status: \$status, score: \$score, " +
            "progress: \$progress, startedAt: \$startedAt, completedAt: \$completedAt, repeat: \$repeat, " +
            "priority: \$priority, private: \$private, hiddenFromStatusLists: \$hiddenFromStatusLists, notes: " +
            "\$notes, advancedScores: \$advancedScores, customLists: \$customLists) { id status progress score } " +
            "}"
        val vars = JsonObject().apply {
            addProperty("mediaId", id)
            update.status?.let { addProperty("status", it) }
            update.score?.let { addProperty("score", it) }
            update.progress?.let { addProperty("progress", it) }
            add("startedAt", update.startedAt.toFuzzyOrClear())
            add("completedAt", update.completedAt.toFuzzyOrClear())
            update.repeat?.let { addProperty("repeat", it) }
            update.priority?.let { addProperty("priority", it) }
            update.isPrivate?.let { addProperty("private", it) }
            update.hiddenFromStatusLists?.let { addProperty("hiddenFromStatusLists", it) }
            update.notes?.let { addProperty("notes", it) }
            update.advancedScores?.let { list -> add("advancedScores", JsonArray().apply { list.forEach { add(it) } }) }
            update.customLists?.let { list -> add("customLists", JsonArray().apply { list.forEach { add(it) } }) }
        }
        return graph(mutation, vars, auth?.token?.accessToken).SaveMediaListEntry != null
    }

    override suspend fun removeStatus(
        auth: AuthData?,
        id: String,
    ): Boolean {
        val mediaId = id.toIntOrNull() ?: return false
        val token = auth?.token?.accessToken
        val mutation = "mutation(\$id: Int) { DeleteMediaListEntry(id: \$id) { deleted } }"
        val cached = entryIds[mediaId]
        if (cached != null) {
            val done =
                runCatching {
                    graph(mutation, variableInt(cached), token).DeleteMediaListEntry?.deleted == true
                }.getOrDefault(false)
            if (done) {
                entryIds.remove(mediaId)
                return true
            }
        }
        val query = "query(\$id: Int) { Media(id: \$id, type: ANIME) { mediaListEntry { id } } }"
        val entryId = graph(query, variableInt(mediaId), token).Media?.mediaListEntry?.id
        entryIds.remove(mediaId)
        if (entryId == null) return true
        return graph(mutation, variableInt(entryId), token).DeleteMediaListEntry?.deleted == true
    }

    suspend fun isFavourite(
        auth: AuthData?,
        id: Int,
    ): Boolean =
        graphData("query { Media(id: $id, type: ANIME) { isFavourite } }", auth?.token?.accessToken)
            .obj("Media")
            ?.get("isFavourite")
            ?.takeIf { it.isJsonPrimitive }
            ?.asBoolean == true

    suspend fun setFavourite(
        auth: AuthData?,
        id: Int,
        favorite: Boolean,
    ): Boolean {
        if (isFavourite(auth, id) == favorite) return true
        graphData(
            "mutation { ToggleFavourite(animeId: $id) { anime { pageInfo { currentPage } } } }",
            auth?.token?.accessToken,
        )
        return true
    }

    suspend fun profile(auth: AuthData?): Pair<AniListUser, ListStats>? = fetchProfile(auth)

    suspend fun history(auth: AuthData?): List<ProfileHistoryItem> = fetchHistory(auth)

    suspend fun favorites(auth: AuthData?): List<AniFavorite> = fetchFavorites(auth)

    override suspend fun library(auth: AuthData?): LibraryMetadata? = fetchLibrary(auth)
}
