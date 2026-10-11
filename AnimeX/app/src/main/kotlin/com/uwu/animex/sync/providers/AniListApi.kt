package com.uwu.animex.sync.providers

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
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
import java.util.concurrent.TimeUnit

class AniListApi : SyncAPI() {
    override val name = "AniList"
    override val idPrefix = "anilist"
    override val hasOAuth2 = true
    override val redirectUrlIdentifier: String? = "anilist-auth"
    override val mainUrl = "https://anilist.co"
    override val createAccountUrl: String? = "$mainUrl/signup"

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
        val CLIENT_ID: String get() = Secrets.anilistClientId
        const val REDIRECT_URI = "animex://anilist-auth"
        const val PROFILE_URL = "https://anilist.co/user/"
        private const val GRAPHQL = "https://graphql.anilist.co"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private fun toAniListStatus(t: SyncWatchType?): String? =
        when (t) {
            SyncWatchType.WATCHING -> "CURRENT"
            SyncWatchType.COMPLETED -> "COMPLETED"
            SyncWatchType.ONHOLD -> "PAUSED"
            SyncWatchType.DROPPED -> "DROPPED"
            SyncWatchType.PLANTOWATCH -> "PLANNING"
            SyncWatchType.NONE, null -> null
        }

    private fun fromAniListStatus(s: String?): SyncWatchType =
        when (s?.uppercase()) {
            "CURRENT", "REPEATING" -> SyncWatchType.WATCHING
            "COMPLETED" -> SyncWatchType.COMPLETED
            "PAUSED" -> SyncWatchType.ONHOLD
            "DROPPED" -> SyncWatchType.DROPPED
            "PLANNING" -> SyncWatchType.PLANTOWATCH
            else -> SyncWatchType.NONE
        }

    /** AniList score is 0–100; MAL-style sheet uses 0–10. Convert both ways. */
    private fun scoreToAniList(score: Int?): Int? = score?.let { if (it <= 10) it * 10 else it }

    private fun scoreFromAniList(score: Int?): Int? =
        score?.let {
            when {
                it <= 0 -> 0
                it <= 10 -> it
                else -> ((it + 5) / 10).coerceIn(0, 10)
            }
        }

    override fun loginRequest(): AuthLoginPage? {
        if (CLIENT_ID.isBlank()) return null
        val url =
            "$mainUrl/api/v2/oauth/authorize" +
                "?client_id=$CLIENT_ID" +
                "&response_type=token"
        return AuthLoginPage(url = url, payload = null)
    }

    override suspend fun login(
        redirectUrl: String,
        payload: String?,
    ): AuthToken? {
        val uri = Uri.parse(redirectUrl)
        // Implicit grant puts tokens in the fragment: #access_token=...&expires_in=...
        val fragment = uri.fragment.orEmpty()
        val params =
            if (fragment.isNotEmpty()) {
                fragment.split("&").mapNotNull { part ->
                    val i = part.indexOf('=')
                    if (i <= 0) null else part.substring(0, i) to Uri.decode(part.substring(i + 1))
                }.toMap()
            } else {
                AuthAPI.splitRedirectUrl(redirectUrl)
            }
        val access = params["access_token"] ?: return null
        val expiresIn = params["expires_in"]?.toLongOrNull()
        return AuthToken(
            accessToken = access,
            accessTokenLifetime = expiresIn?.let { unixTime() + it },
        )
    }

    /** AniList implicit tokens cannot be refreshed. */
    override suspend fun refreshToken(token: AuthToken): AuthToken? = null

    override suspend fun user(token: AuthToken?): AuthUser? {
        val access = token?.accessToken ?: return null
        val query =
            """
            query {
              Viewer {
                id
                name
                avatar { medium large }
              }
            }
            """.trimIndent()
        val data = graphql(access, query) ?: return null
        val viewer = data.getAsJsonObject("Viewer") ?: return null
        return AuthUser(
            id = viewer.get("id")?.asInt ?: return null,
            name = viewer.get("name")?.asString,
            profilePicture =
                viewer
                    .getAsJsonObject("avatar")
                    ?.let { it.get("large")?.asString ?: it.get("medium")?.asString },
        )
    }

    override fun urlToId(url: String): String? =
        Regex("""anilist\.co/anime/(\d+)""").find(url)?.groupValues?.getOrNull(1)

    override suspend fun search(
        auth: AuthData?,
        query: String,
    ): List<SyncSearchResult>? {
        val q =
            """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 25) {
                media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  title { romaji english native userPreferred }
                  coverImage { large medium }
                  synonyms
                  siteUrl
                }
              }
            }
            """.trimIndent()
        val data = graphql(auth?.token?.accessToken, q, mapOf("search" to query)) ?: return null
        val media =
            data
                .getAsJsonObject("Page")
                ?.getAsJsonArray("media")
                ?: return emptyList()
        return media.mapNotNull { el ->
            val m = el.asJsonObject
            val id = m.get("id")?.asInt ?: return@mapNotNull null
            val title =
                m.getAsJsonObject("title")?.let {
                    it.get("userPreferred")?.asString
                        ?: it.get("english")?.asString
                        ?: it.get("romaji")?.asString
                        ?: it.get("native")?.asString
                } ?: return@mapNotNull null
            val cover =
                m.getAsJsonObject("coverImage")?.let {
                    it.get("large")?.asString ?: it.get("medium")?.asString
                }
            val synonyms =
                buildList {
                    m.getAsJsonObject("title")?.let { t ->
                        t.get("english")?.asString?.let { add(it) }
                        t.get("romaji")?.asString?.let { add(it) }
                        t.get("native")?.asString?.let { add(it) }
                    }
                    m.getAsJsonArray("synonyms")?.forEach { add(it.asString) }
                }.distinct()
            SyncSearchResult(
                name = title,
                syncId = id.toString(),
                url = m.get("siteUrl")?.asString ?: "$mainUrl/anime/$id",
                posterUrl = cover,
                synonyms = synonyms,
            )
        }
    }

    override suspend fun load(
        auth: AuthData?,
        id: String,
    ): SyncResult? {
        val q =
            """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english native userPreferred }
                coverImage { large medium }
                episodes
                averageScore
                description(asHtml: false)
                synonyms
                mediaListEntry {
                  status
                  score
                  progress
                  startedAt { year month day }
                  completedAt { year month day }
                  repeat
                  notes
                }
              }
            }
            """.trimIndent()
        val data = graphql(auth?.token?.accessToken, q, mapOf("id" to id.toInt())) ?: return null
        val m = data.getAsJsonObject("Media") ?: return null
        return mediaToResult(m)
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
        val alStatus = toAniListStatus(newStatus.status)
        val vars = mutableMapOf<String, Any?>("mediaId" to id.toInt())
        alStatus?.let { vars["status"] = it }
        newStatus.score?.let { vars["score"] = scoreToAniList(it) }
        newStatus.watchedEpisodes?.let { vars["progress"] = it }
        newStatus.rewatchCount?.let { vars["repeat"] = it }
        newStatus.comments?.let { vars["notes"] = it }
        newStatus.startDate?.let { vars["startedAt"] = dateToFuzzy(it) }
        newStatus.finishDate?.let { vars["completedAt"] = dateToFuzzy(it) }

        val q =
            """
            mutation (
              ${'$'}mediaId: Int,
              ${'$'}status: MediaListStatus,
              ${'$'}score: Float,
              ${'$'}progress: Int,
              ${'$'}repeat: Int,
              ${'$'}notes: String,
              ${'$'}startedAt: FuzzyDateInput,
              ${'$'}completedAt: FuzzyDateInput
            ) {
              SaveMediaListEntry(
                mediaId: ${'$'}mediaId,
                status: ${'$'}status,
                score: ${'$'}score,
                progress: ${'$'}progress,
                repeat: ${'$'}repeat,
                notes: ${'$'}notes,
                startedAt: ${'$'}startedAt,
                completedAt: ${'$'}completedAt
              ) { id }
            }
            """.trimIndent()
        graphql(auth?.token?.accessToken, q, vars) ?: return false
        return true
    }

    override suspend fun removeStatus(
        auth: AuthData?,
        id: String,
    ): Boolean {
        // Need list entry id — load first
        val qFind =
            """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                mediaListEntry { id }
              }
            }
            """.trimIndent()
        val data = graphql(auth?.token?.accessToken, qFind, mapOf("id" to id.toInt())) ?: return false
        val entryId =
            data
                .getAsJsonObject("Media")
                ?.getAsJsonObject("mediaListEntry")
                ?.get("id")
                ?.asInt ?: return false
        val qDel =
            """
            mutation (${'$'}id: Int) {
              DeleteMediaListEntry(id: ${'$'}id) { deleted }
            }
            """.trimIndent()
        graphql(auth?.token?.accessToken, qDel, mapOf("id" to entryId)) ?: return false
        return true
    }

    override suspend fun library(auth: AuthData?): LibraryMetadata? {
        val token = auth?.token?.accessToken ?: return null
        val userId = auth.user.id
        val q =
            """
            query (${'$'}userId: Int, ${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                mediaList(userId: ${'$'}userId, type: ANIME, sort: UPDATED_TIME_DESC) {
                  status
                  score
                  progress
                  updatedAt
                  media {
                    id
                    title { userPreferred romaji english }
                    coverImage { large medium }
                    episodes
                    startDate { year }
                  }
                }
              }
            }
            """.trimIndent()
        val items = ArrayList<LibraryItem>()
        var page = 1
        while (true) {
            val data = graphql(token, q, mapOf("userId" to userId, "page" to page)) ?: break
            val pageObj = data.getAsJsonObject("Page") ?: break
            val list = pageObj.getAsJsonArray("mediaList") ?: break
            list.forEach { el ->
                val e = el.asJsonObject
                val media = e.getAsJsonObject("media") ?: return@forEach
                val id = media.get("id")?.asInt ?: return@forEach
                val title =
                    media.getAsJsonObject("title")?.let {
                        it.get("userPreferred")?.asString
                            ?: it.get("english")?.asString
                            ?: it.get("romaji")?.asString
                    } ?: return@forEach
                items.add(
                    LibraryItem(
                        name = title,
                        url = "$mainUrl/anime/$id",
                        syncId = id.toString(),
                        status = fromAniListStatus(e.get("status")?.asString),
                        episodesCompleted = e.get("progress")?.asInt,
                        episodesTotal = media.get("episodes")?.asInt,
                        personalRating = scoreFromAniList(e.get("score")?.asInt),
                        lastUpdatedUnixTime = e.get("updatedAt")?.asLong,
                        posterUrl =
                            media.getAsJsonObject("coverImage")?.let {
                                it.get("large")?.asString ?: it.get("medium")?.asString
                            },
                        releaseDate = media.getAsJsonObject("startDate")?.get("year")?.asLong,
                    ),
                )
            }
            val hasNext = pageObj.getAsJsonObject("pageInfo")?.get("hasNextPage")?.asBoolean == true
            if (!hasNext) break
            page++
            if (page > 40) break
        }
        val grouped = items.groupBy { it.status }
        val lists =
            SyncWatchType.entries
                .filter { it != SyncWatchType.NONE && it in supportedWatchTypes }
                .map { t ->
                    LibraryList(
                        name = t.label,
                        status = t,
                        items = grouped[t].orEmpty(),
                    )
                }
        return LibraryMetadata(
            allLibraryLists = lists,
            supportedListSorting =
                setOf(
                    ListSorting.UpdatedNew,
                    ListSorting.AlphabeticalA,
                    ListSorting.RatingHigh,
                ),
        )
    }

    private fun mediaToResult(m: JsonObject): SyncResult {
        val id = m.get("id").asInt
        val title =
            m.getAsJsonObject("title")?.let {
                it.get("userPreferred")?.asString
                    ?: it.get("english")?.asString
                    ?: it.get("romaji")?.asString
                    ?: it.get("native")?.asString
            }
        val synonyms =
            buildList {
                m.getAsJsonObject("title")?.let { t ->
                    t.get("english")?.asString?.let { add(it) }
                    t.get("romaji")?.asString?.let { add(it) }
                    t.get("native")?.asString?.let { add(it) }
                }
                m.getAsJsonArray("synonyms")?.forEach { add(it.asString) }
            }.distinct()
        val entry = m.getAsJsonObject("mediaListEntry")
        val myStatus =
            entry?.let { e ->
                SyncStatus(
                    status = fromAniListStatus(e.get("status")?.asString),
                    score = scoreFromAniList(e.get("score")?.asInt),
                    watchedEpisodes = e.get("progress")?.asInt,
                    maxEpisodes = m.get("episodes")?.asInt,
                    startDate = fuzzyToDate(e.getAsJsonObject("startedAt")),
                    finishDate = fuzzyToDate(e.getAsJsonObject("completedAt")),
                    rewatchCount = e.get("repeat")?.asInt,
                    comments = e.get("notes")?.asString,
                )
            }
        return SyncResult(
            id = id.toString(),
            title = title,
            totalEpisodes = m.get("episodes")?.asInt,
            synonyms = synonyms,
            posterUrl =
                m.getAsJsonObject("coverImage")?.let {
                    it.get("large")?.asString ?: it.get("medium")?.asString
                },
            publicScore = m.get("averageScore")?.asDouble?.div(10.0),
            synopsis = m.get("description")?.asString,
            myStatus = myStatus,
        )
    }

    private fun fuzzyToDate(obj: JsonObject?): String? {
        if (obj == null) return null
        val y = obj.get("year")?.asInt ?: return null
        val mo = obj.get("month")?.asInt ?: 1
        val d = obj.get("day")?.asInt ?: 1
        return "%04d-%02d-%02d".format(y, mo, d)
    }

    private fun dateToFuzzy(date: String): Map<String, Int> {
        val parts = date.split("-")
        return mapOf(
            "year" to (parts.getOrNull(0)?.toIntOrNull() ?: 0),
            "month" to (parts.getOrNull(1)?.toIntOrNull() ?: 1),
            "day" to (parts.getOrNull(2)?.toIntOrNull() ?: 1),
        )
    }

    private suspend fun graphql(
        token: String?,
        query: String,
        variables: Map<String, Any?> = emptyMap(),
    ): JsonObject? =
        withContext(Dispatchers.IO) {
            val bodyMap = mutableMapOf<String, Any>("query" to query)
            if (variables.isNotEmpty()) bodyMap["variables"] = variables
            val body = gson.toJson(bodyMap).toRequestBody(JSON)
            val req =
                Request
                    .Builder()
                    .url(GRAPHQL)
                    .post(body)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                    .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw HttpException(resp.code)
                val root = JsonParser.parseString(text).asJsonObject
                if (root.has("errors")) {
                    val msg =
                        root
                            .getAsJsonArray("errors")
                            ?.firstOrNull()
                            ?.asJsonObject
                            ?.get("message")
                            ?.asString
                    throw HttpException(resp.code.takeIf { it >= 400 } ?: 400).also {
                        // surface GraphQL error via message chain when needed
                    }.let { e ->
                        throw Exception(msg ?: "AniList GraphQL error", e)
                    }
                }
                root.getAsJsonObject("data")
            }
        }
}
