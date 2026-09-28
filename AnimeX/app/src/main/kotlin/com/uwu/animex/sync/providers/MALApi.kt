package com.uwu.animex.sync.providers

import com.google.gson.Gson
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalUser
import com.uwu.animex.data.malValue
import com.uwu.animex.data.watchStatusFromMal
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private data class MalPicture(val medium: String? = null, val large: String? = null)

private data class MalAltTitles(
    val en: String? = null,
    val ja: String? = null,
    val synonyms: List<String>? = null,
)

private data class MalListStatus(
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

private data class MalNode(
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

private data class MalListItem(val node: MalNode? = null, val list_status: MalListStatus? = null)

private data class MalPaging(val next: String? = null)

private data class MalListResponse(val data: List<MalListItem>? = null, val paging: MalPaging? = null)

private data class MalSearchResponse(val data: List<MalListItem>? = null)

private data class ResponseToken(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
)

private data class Payload(val state: String, val codeVerifier: String)

class MALApi : SyncAPI() {
    override val name = "MAL"
    override val idPrefix = "mal"
    override val hasOAuth2 = true
    override val redirectUrlIdentifier: String? = "mal-auth"
    override val mainUrl = "https://myanimelist.net"
    override val createAccountUrl: String? = "https://myanimelist.net/register.php"

    override val supportedWatchTypes = setOf(
        SyncWatchType.WATCHING,
        SyncWatchType.COMPLETED,
        SyncWatchType.ONHOLD,
        SyncWatchType.DROPPED,
        SyncWatchType.PLANTOWATCH,
    )

    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private companion object {
        const val API = "https://api.myanimelist.net/v2"
        const val ANIME_FIELDS =
            "num_episodes,alternative_titles,main_picture,mean,synopsis,start_date," +
                "my_list_status{start_date,finish_date,num_times_rewatched,is_rewatching,rewatch_value,priority,tags,comments}"
        const val LIBRARY_FIELDS = "list_status,num_episodes,alternative_titles,start_date"
        val OFFSET_REGEX = Regex("offset=(\\d+)")
    }

    private fun SyncWatchType.malString(): String? = toWatchStatus()?.malValue

    private fun malWatchType(value: String?): SyncWatchType = SyncWatchType.from(watchStatusFromMal(value))

    private fun utcFormat(pattern: String) =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseTime(value: String?): Long? =
        runCatching { utcFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(value!!)!!.time / 1000L }.getOrNull()

    private fun parseRelease(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val pattern = when (value.length) {
            4 -> "yyyy"
            7 -> "yyyy-MM"
            else -> "yyyy-MM-dd"
        }
        return runCatching { utcFormat(pattern).parse(value)?.time }.getOrNull()
    }

    private suspend fun call(token: String?, build: (Request.Builder) -> Request.Builder): String =
        withContext(Dispatchers.IO) {
            val bearer = token ?: throw IllegalStateException("Belum login")
            val request = build(Request.Builder()).header("Authorization", "Bearer $bearer").build()
            http.newCall(request).execute().use { r ->
                val text = r.body.string()
                if (!r.isSuccessful) throw HttpException(r.code)
                text
            }
        }

    private suspend fun tokenRequest(body: RequestBody): ResponseToken = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$mainUrl/v1/oauth2/token").post(body).build()
        http.newCall(request).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw HttpException(r.code)
            gson.fromJson(text, ResponseToken::class.java)
        }
    }

    private fun toToken(t: ResponseToken, fallbackRefresh: String? = null) = AuthToken(
        accessToken = t.access_token ?: throw IllegalStateException("token kosong"),
        refreshToken = t.refresh_token ?: fallbackRefresh,
        accessTokenLifetime = unixTime() + (t.expires_in ?: 3600L),
    )

    override fun isValidRedirectUrl(url: String): Boolean = url.startsWith(Mal.REDIRECT_URI)

    override fun loginRequest(): AuthLoginPage {
        val verifier = generateCodeVerifier()
        val state = generateCodeVerifier()
        val url = "$mainUrl/v1/oauth2/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", Mal.CLIENT_ID)
            .addQueryParameter("code_challenge", verifier)
            .addQueryParameter("code_challenge_method", "plain")
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", Mal.REDIRECT_URI)
            .build()
        return AuthLoginPage(url.toString(), gson.toJson(Payload(state, verifier)))
    }

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val saved = runCatching { gson.fromJson(payload, Payload::class.java) }.getOrNull() ?: return null
        val params = splitRedirectUrl(redirectUrl)
        val code = params["code"] ?: return null
        if (params["state"] != saved.state) return null
        val body = FormBody.Builder()
            .add("client_id", Mal.CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", Mal.REDIRECT_URI)
            .add("code_verifier", saved.codeVerifier)
            .build()
        return toToken(tokenRequest(body))
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        val refresh = token.refreshToken ?: return null
        val body = FormBody.Builder()
            .add("client_id", Mal.CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        return toToken(tokenRequest(body), refresh)
    }

    suspend fun profile(auth: AuthData?): MalUser {
        val url = "$API/users/@me".toHttpUrl().newBuilder().addQueryParameter("fields", "anime_statistics").build()
        return gson.fromJson(call(auth?.token?.accessToken) { it.url(url) }, MalUser::class.java)
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val url = "$API/users/@me".toHttpUrl()
        val u = gson.fromJson(call(token?.accessToken) { it.url(url) }, MalUser::class.java)
        return AuthUser(name = u.name, id = u.id?.toInt() ?: return null, profilePicture = u.picture)
    }

    private fun MalNode.synonymList(): List<String> =
        listOfNotNull(alternative_titles?.en, alternative_titles?.ja).filter { it.isNotBlank() } +
            alternative_titles?.synonyms.orEmpty()

    private fun MalListStatus.toSync(max: Int?) = SyncStatus(
        status = malWatchType(status),
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

    private fun MalNode.toLibraryItem(l: MalListStatus?) = LibraryItem(
        name = title.orEmpty(),
        url = "$mainUrl/anime/$id",
        syncId = id.toString(),
        status = malWatchType(l?.status),
        episodesCompleted = l?.num_episodes_watched,
        episodesTotal = num_episodes?.takeIf { it > 0 },
        personalRating = l?.score?.takeIf { it > 0 },
        lastUpdatedUnixTime = parseTime(l?.updated_at),
        posterUrl = main_picture?.large ?: main_picture?.medium,
        releaseDate = parseRelease(start_date),
        synonyms = synonymList(),
        startDate = l?.start_date,
        finishDate = l?.finish_date,
    )

    override suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? {
        val url = "$API/anime".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "8")
            .addQueryParameter("fields", "alternative_titles,main_picture")
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

    override suspend fun load(auth: AuthData?, id: String): SyncResult? {
        val url = "$API/anime/$id".toHttpUrl().newBuilder().addQueryParameter("fields", ANIME_FIELDS).build()
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

    override suspend fun status(auth: AuthData?, id: String): SyncStatus? = load(auth, id)?.myStatus

    override suspend fun updateStatus(auth: AuthData?, id: String, newStatus: SyncStatus): Boolean {
        val body = FormBody.Builder().apply {
            newStatus.status?.malString()?.let { add("status", it) }
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

    override suspend fun removeStatus(auth: AuthData?, id: String): Boolean {
        call(auth?.token?.accessToken) { it.url("$API/anime/$id/my_list_status").delete() }
        return true
    }

    override suspend fun library(auth: AuthData?): LibraryMetadata? {
        val token = auth?.token?.accessToken ?: return null
        val items = ArrayList<LibraryItem>()
        var offset = 0
        while (true) {
            val url = "$API/users/@me/animelist".toHttpUrl().newBuilder()
                .addQueryParameter("fields", LIBRARY_FIELDS)
                .addQueryParameter("sort", "list_updated_at")
                .addQueryParameter("nsfw", "1")
                .addQueryParameter("limit", "100")
                .addQueryParameter("offset", offset.toString())
                .build()
            val page = gson.fromJson(call(token) { it.url(url) }, MalListResponse::class.java)
            page.data.orEmpty().forEach { d -> d.node?.let { items.add(it.toLibraryItem(d.list_status)) } }
            val next = page.paging?.next ?: break
            offset = OFFSET_REGEX.find(next)?.groupValues?.get(1)?.toIntOrNull() ?: break
        }
        val grouped = items.groupBy { it.status }
        val lists = SyncWatchType.entries
            .filter { it != SyncWatchType.NONE && it in supportedWatchTypes }
            .map { LibraryList(it.label, it, grouped[it].orEmpty()) }
        return LibraryMetadata(lists, ListSorting.entries.toSet())
    }
}
