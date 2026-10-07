package com.uwu.animex.sync.providers

import com.google.gson.Gson
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.mal.MalUser
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
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MALApi : SyncAPI() {
    override val name = "MAL"
    override val idPrefix = "mal"
    override val hasOAuth2 = true
    override val redirectUrlIdentifier: String? = "mal-auth"
    override val mainUrl = "https://myanimelist.net"
    override val createAccountUrl: String? = "$mainUrl/register.php"

    override val supportedWatchTypes =
        setOf(
            SyncWatchType.WATCHING,
            SyncWatchType.COMPLETED,
            SyncWatchType.ONHOLD,
            SyncWatchType.DROPPED,
            SyncWatchType.PLANTOWATCH,
            SyncWatchType.NONE,
        )

    private val gson = Gson()
    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    companion object {
        val CLIENT_ID: String get() = Secrets.malClientId
        const val REDIRECT_URI = "animex://mal-auth"
        const val PROFILE_URL = "https://myanimelist.net/profile/"

        private const val API = "https://api.myanimelist.net/v2"

        private const val MAL_MAX_SEARCH_LIMIT = 25

        private const val ANIME_FIELDS =
            "num_episodes,alternative_titles,main_picture,mean,synopsis,start_date," +
                "my_list_status{start_date,finish_date,num_times_rewatched,is_rewatching," +
                "rewatch_value,priority,tags,comments}"
        private const val LIBRARY_FIELDS =
            "list_status,num_episodes,alternative_titles,main_picture,start_date,mean"

        private val OFFSET_REGEX = Regex("offset=(\\d+)")
        private val ANIME_ID_REGEX = Regex("""/anime/(\d+)""")

        private val malStatusAsString =
            arrayOf(
                "watching",
                "completed",
                "on_hold",
                "dropped",
                "plan_to_watch",
            )

        enum class MalStatusType(
            val value: Int,
        ) {
            Watching(0),
            Completed(1),
            OnHold(2),
            Dropped(3),
            PlanToWatch(4),
            None(-1),
            ;

            fun toApiString(): String? = if (value in malStatusAsString.indices) malStatusAsString[value] else null

            companion object {
                fun fromApiString(s: String?): MalStatusType =
                    when (s) {
                        "watching" -> Watching
                        "completed" -> Completed
                        "on_hold" -> OnHold
                        "dropped" -> Dropped
                        "plan_to_watch" -> PlanToWatch
                        else -> None
                    }

                fun fromSync(type: SyncWatchType?): MalStatusType =
                    when (type) {
                        null, SyncWatchType.NONE -> None
                        SyncWatchType.WATCHING -> Watching
                        SyncWatchType.COMPLETED -> Completed
                        SyncWatchType.ONHOLD -> OnHold
                        SyncWatchType.DROPPED -> Dropped
                        SyncWatchType.PLANTOWATCH -> PlanToWatch
                    }
            }

            fun toSync(): SyncWatchType = SyncWatchType.fromInternalId(value)
        }

        data class MalPicture(
            val medium: String? = null,
            val large: String? = null,
        )

        data class MalAltTitles(
            val en: String? = null,
            val ja: String? = null,
            val synonyms: List<String>? = null,
        )

        data class MalListStatus(
            val status: String? = null,
            val score: Int? = null,
            val num_episodes_watched: Int? = null,
            val is_rewatching: Boolean? = null,
            val start_date: String? = null,
            val finish_date: String? = null,
            val priority: Int? = null,
            val num_times_rewatched: Int? = null,
            val rewatch_value: Int? = null,
            val tags: List<String>? = null,
            val comments: String? = null,
            val updated_at: String? = null,
        )

        data class MalNode(
            val id: Int = 0,
            val title: String? = null,
            val main_picture: MalPicture? = null,
            val alternative_titles: MalAltTitles? = null,
            val num_episodes: Int? = null,
            val start_date: String? = null,
            val mean: Double? = null,
            val synopsis: String? = null,
            val my_list_status: MalListStatus? = null,
        )

        data class MalListItem(
            val node: MalNode? = null,
            val list_status: MalListStatus? = null,
        )

        data class MalPaging(
            val next: String? = null,
        )

        data class MalListResponse(
            val data: List<MalListItem>? = null,
            val paging: MalPaging? = null,
        )

        data class MalSearchResponse(
            val data: List<MalListItem>? = null,
        )

        data class ResponseToken(
            val access_token: String? = null,
            val refresh_token: String? = null,
            val expires_in: Long? = null,
        )

        data class Payload(
            val state: String,
            val codeVerifier: String,
        )
    }

    private fun utcFormat(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseTime(value: String?): Long? =
        runCatching { utcFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(value!!)!!.time / 1000L }.getOrNull()

    private fun parseRelease(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val pattern =
            when (value.length) {
                4 -> "yyyy"
                7 -> "yyyy-MM"
                else -> "yyyy-MM-dd"
            }
        return runCatching { utcFormat(pattern).parse(value)?.time }.getOrNull()
    }

    private suspend fun call(
        token: String?,
        build: (Request.Builder) -> Request.Builder,
    ): String =
        withContext(Dispatchers.IO) {
            val bearer = token ?: throw IllegalStateException("Kamu belum login")
            val request = build(Request.Builder()).header("Authorization", "Bearer $bearer").build()
            http.newCall(request).execute().use { r ->
                val text = r.body.string()
                if (!r.isSuccessful) throw HttpException(r.code)
                text
            }
        }

    private suspend fun tokenRequest(body: RequestBody): ResponseToken =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url("$mainUrl/v1/oauth2/token")
                    .post(body)
                    .build()
            http.newCall(request).execute().use { r ->
                val text = r.body.string()
                if (!r.isSuccessful) throw HttpException(r.code)
                gson.fromJson(text, ResponseToken::class.java)
            }
        }

    private fun toToken(
        t: ResponseToken,
        fallbackRefresh: String? = null,
    ): AuthToken {
        val access = t.access_token ?: throw IllegalStateException("Tokennya kosong")
        val expires = t.expires_in ?: 0L
        return AuthToken(
            accessToken = access,
            refreshToken = t.refresh_token ?: fallbackRefresh,
            accessTokenLifetime = unixTime() + expires,
        )
    }

    private fun MalNode.synonymList(): List<String> =
        listOfNotNull(alternative_titles?.en, alternative_titles?.ja).filter { it.isNotBlank() } +
            alternative_titles?.synonyms.orEmpty()

    private fun MalListStatus.toSync(max: Int?): SyncStatus {
        val st = MalStatusType.fromApiString(status)
        return SyncStatus(
            status = st.toSync().takeIf { it != SyncWatchType.NONE },
            score = score,
            watchedEpisodes = num_episodes_watched,
            maxEpisodes = max,
            startDate = start_date,
            finishDate = finish_date,
            isRewatching = is_rewatching,
            rewatchCount = num_times_rewatched,
            rewatchValue = rewatch_value,
            priority = priority,
            tags = tags,
            comments = comments,
        )
    }

    private fun MalNode.toLibraryItem(listStatus: MalListStatus?): LibraryItem {
        val st = MalStatusType.fromApiString(listStatus?.status)
        return LibraryItem(
            name = title.orEmpty(),
            url = "$mainUrl/anime/$id",
            syncId = id.toString(),
            status = st.toSync(),
            episodesCompleted = listStatus?.num_episodes_watched,
            episodesTotal = num_episodes?.takeIf { it > 0 },
            personalRating = listStatus?.score?.takeIf { it > 0 },
            lastUpdatedUnixTime = parseTime(listStatus?.updated_at),
            posterUrl = main_picture?.large ?: main_picture?.medium,
            releaseDate = parseRelease(start_date),
            synonyms = synonymList(),
            startDate = listStatus?.start_date,
            finishDate = listStatus?.finish_date,
        )
    }

    override fun loginRequest(): AuthLoginPage? {
        val codeVerifier = AuthAPI.generateCodeVerifier()
        val state = AuthAPI.generateCodeVerifier().take(32)

        val url =
            "$mainUrl/v1/oauth2/authorize"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("response_type", "code")
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("code_challenge", codeVerifier)
                .addQueryParameter("code_challenge_method", "plain")
                .addQueryParameter("state", state)
                .build()
                .toString()
        return AuthLoginPage(
            url = url,
            payload = gson.toJson(Payload(state, codeVerifier)),
        )
    }

    override suspend fun login(
        redirectUrl: String,
        payload: String?,
    ): AuthToken? {
        val saved =
            payload?.let {
                runCatching { gson.fromJson(it, Payload::class.java) }.getOrNull()
            } ?: return null
        val uri = android.net.Uri.parse(redirectUrl)
        val state = uri.getQueryParameter("state")
        if (state == null || state != saved.state) return null
        val code = uri.getQueryParameter("code") ?: return null
        val body =
            FormBody
                .Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", saved.codeVerifier)
                .build()
        return toToken(tokenRequest(body))
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        val refresh = token.refreshToken ?: return null
        val body =
            FormBody
                .Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .build()
        return toToken(tokenRequest(body), refresh)
    }

    suspend fun profile(auth: AuthData?): MalUser {
        val url =
            "$API/users/@me"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("fields", "anime_statistics")
                .build()
        return gson.fromJson(call(auth?.token?.accessToken) { it.url(url) }, MalUser::class.java)
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val url = "$API/users/@me".toHttpUrl()
        val u = gson.fromJson(call(token?.accessToken) { it.url(url) }, MalUser::class.java)
        return AuthUser(
            name = u.name,
            id = u.id?.toInt() ?: return null,
            profilePicture = u.picture,
        )
    }

    override fun urlToId(url: String): String? = ANIME_ID_REGEX.find(url)?.groupValues?.getOrNull(1)

    override suspend fun search(
        auth: AuthData?,
        query: String,
    ): List<SyncSearchResult>? {
        val url =
            "$API/anime"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query.take(64))
                .addQueryParameter("limit", MAL_MAX_SEARCH_LIMIT.toString())
                .addQueryParameter("fields", "alternative_titles,main_picture")
                .addQueryParameter("nsfw", "1")
                .build()
        val res = gson.fromJson(call(auth?.token?.accessToken) { it.url(url) }, MalSearchResponse::class.java)
        return res.data.orEmpty().mapNotNull { it.node }.map { n ->
            SyncSearchResult(
                name = n.title.orEmpty(),
                syncId = n.id.toString(),
                url = "$mainUrl/anime/${n.id}",
                posterUrl = n.main_picture?.large ?: n.main_picture?.medium,
                synonyms = n.synonymList(),
            )
        }
    }

    override suspend fun load(
        auth: AuthData?,
        id: String,
    ): SyncResult? {
        val url =
            "$API/anime/$id"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("fields", ANIME_FIELDS)
                .build()
        val n = gson.fromJson(call(auth?.token?.accessToken) { it.url(url) }, MalNode::class.java) ?: return null
        val total = n.num_episodes?.takeIf { it > 0 }
        return SyncResult(
            id = n.id.toString(),
            title = n.title,
            totalEpisodes = total,
            synonyms = n.synonymList(),
            posterUrl = n.main_picture?.large ?: n.main_picture?.medium,
            publicScore = n.mean,
            synopsis = n.synopsis,
            myStatus = n.my_list_status?.toSync(total),
        )
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
        val malStatus = MalStatusType.fromSync(newStatus.status)
        val body =
            FormBody
                .Builder()
                .apply {
                    malStatus.toApiString()?.let { add("status", it) }
                    newStatus.score?.let { add("score", it.toString()) }
                    newStatus.watchedEpisodes?.let { add("num_watched_episodes", it.toString()) }
                    newStatus.startDate?.let { add("start_date", it) }
                    newStatus.finishDate?.let { add("finish_date", it) }
                    newStatus.isRewatching?.let { add("is_rewatching", it.toString()) }
                    newStatus.rewatchCount?.let { add("num_times_rewatched", it.toString()) }
                    newStatus.rewatchValue?.let { add("rewatch_value", it.toString()) }
                    newStatus.priority?.let { add("priority", it.toString()) }
                    newStatus.tags?.let { add("tags", it.joinToString(",")) }
                    newStatus.comments?.let { add("comments", it) }
                }.build()
        call(auth?.token?.accessToken) { it.url("$API/anime/$id/my_list_status").patch(body) }
        return true
    }

    override suspend fun removeStatus(
        auth: AuthData?,
        id: String,
    ): Boolean {
        call(auth?.token?.accessToken) { it.url("$API/anime/$id/my_list_status").delete() }
        return true
    }

    override suspend fun library(auth: AuthData?): LibraryMetadata? {
        val token = auth?.token?.accessToken ?: return null
        val items = ArrayList<LibraryItem>()
        var offset = 0
        while (true) {
            val url =
                "$API/users/@me/animelist"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("fields", LIBRARY_FIELDS)
                    .addQueryParameter("sort", "list_updated_at")
                    .addQueryParameter("nsfw", "1")
                    .addQueryParameter("limit", "100")
                    .addQueryParameter("offset", offset.toString())
                    .build()
            val page = gson.fromJson(call(token) { it.url(url) }, MalListResponse::class.java)
            page.data.orEmpty().forEach { d ->
                d.node?.let { items.add(it.toLibraryItem(d.list_status ?: it.my_list_status)) }
            }
            val next = page.paging?.next ?: break
            offset = OFFSET_REGEX
                .find(next)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: break
        }
        val grouped = items.groupBy { it.status }
        val lists =
            SyncWatchType.entries
                .filter { it != SyncWatchType.NONE && it in supportedWatchTypes }
                .map { LibraryList(it.label, it, grouped[it].orEmpty()) }
        return LibraryMetadata(lists, ListSorting.entries.toSet())
    }
}
