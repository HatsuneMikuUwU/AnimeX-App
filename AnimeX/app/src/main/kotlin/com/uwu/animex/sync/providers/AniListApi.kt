package com.uwu.animex.sync.providers

import android.net.Uri
import com.apollographql.apollo.api.Optional
import com.google.gson.Gson
import com.uwu.animex.anilist.DeleteMediaListEntryMutation
import com.uwu.animex.anilist.MediaByIdQuery
import com.uwu.animex.anilist.MediaListCollectionQuery
import com.uwu.animex.anilist.MediaListEntryQuery
import com.uwu.animex.anilist.SaveMediaListEntryMutation
import com.uwu.animex.anilist.SearchAnimeQuery
import com.uwu.animex.anilist.ToggleFavouriteMutation
import com.uwu.animex.anilist.UserActivitiesQuery
import com.uwu.animex.anilist.UserFavouritesQuery
import com.uwu.animex.anilist.ViewerQuery
import com.uwu.animex.anilist.ViewerRankingsQuery
import com.uwu.animex.anilist.ViewerStatsQuery
import com.uwu.animex.anilist.type.FuzzyDateInput
import com.uwu.animex.anilist.type.MediaListStatus
import com.uwu.animex.core.network.AniListApollo
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.anilist.AniFavorite
import com.uwu.animex.data.anilist.AniListStats
import com.uwu.animex.data.anilist.AniListUser
import com.uwu.animex.data.anilist.AniRank
import com.uwu.animex.data.anilist.AniRankings
import com.uwu.animex.data.anilist.AniStat
import com.uwu.animex.data.history.HISTORY_TODAY
import com.uwu.animex.data.history.ProfileHistoryItem
import com.uwu.animex.data.history.historyGroup
import com.uwu.animex.data.history.historyTimeId
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
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

/**
 * AniList [SyncAPI] fully backed by Apollo GraphQL (AniHyou-style).
 * No raw GraphQL strings / Gson parsing for API traffic.
 */
class AniListApi : SyncAPI() {
    override val name = "AniList"
    override val idPrefix = "anilist"
    override val hasOAuth2 = true
    override val redirectUrlIdentifier: String? = "anilist-auth"
    override val mainUrl = "https://anilist.co"

    override val supportedWatchTypes =
        setOf(
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
        private const val LIST_CHUNK_SIZE = 500
        private const val MAX_CHUNKS = 40
        private const val FAVORITE_PAGE_SIZE = 25
        private const val MAX_FAVORITE_PAGES = 40
    }

    /** Only used for OAuth state JSON, not GraphQL. */
    private val gson = Gson()
    private val apollo get() = AniListApollo.client

    private data class OAuthPayload(val state: String)

    @Volatile private var scoreFormat: String? = null

    private fun scoreToUi(score: Double?): Int? = score?.let { aniScoreToTen(scoreFormat, it) }

    private fun scoreToAniList(score: Int): Double {
        val value = score.coerceIn(0, 10).toDouble()
        return when (scoreFormat ?: "POINT_100") {
            "POINT_10_DECIMAL", "POINT_10" -> value
            "POINT_5" -> (value / 2.0).roundToInt().toDouble()
            "POINT_3" -> (value * 3.0 / 10.0).roundToInt().toDouble()
            else -> value * 10.0
        }
    }

    private fun bindToken(token: String?) {
        AniListApollo.setAccessToken(token)
    }

    private fun watchType(status: String?): SyncWatchType =
        when (status) {
            "CURRENT", "REPEATING" -> SyncWatchType.WATCHING
            "COMPLETED" -> SyncWatchType.COMPLETED
            "PAUSED" -> SyncWatchType.ONHOLD
            "DROPPED" -> SyncWatchType.DROPPED
            "PLANNING" -> SyncWatchType.PLANTOWATCH
            else -> SyncWatchType.NONE
        }

    private fun toMediaListStatus(type: SyncWatchType?): MediaListStatus? =
        when (type) {
            SyncWatchType.WATCHING -> MediaListStatus.CURRENT
            SyncWatchType.COMPLETED -> MediaListStatus.COMPLETED
            SyncWatchType.ONHOLD -> MediaListStatus.PAUSED
            SyncWatchType.DROPPED -> MediaListStatus.DROPPED
            SyncWatchType.PLANTOWATCH -> MediaListStatus.PLANNING
            else -> null
        }

    private fun titleOf(english: String?, romaji: String?, native: String?): String =
        english?.takeIf { it.isNotBlank() }
            ?: romaji?.takeIf { it.isNotBlank() }
            ?: native.orEmpty()

    private fun fuzzyToIso(year: Int?, month: Int?, day: Int?): String? {
        val y = year?.takeIf { it > 0 } ?: return null
        val m = month?.takeIf { it > 0 } ?: return "%04d".format(y)
        val d = day?.takeIf { it > 0 } ?: return "%04d-%02d".format(y, m)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    private fun isoToFuzzy(iso: String?): FuzzyDateInput? {
        val parts = iso?.split("-")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (parts.isEmpty() || parts[0] <= 0) return null
        return FuzzyDateInput(
            year = Optional.presentIfNotNull(parts.getOrNull(0)),
            month = Optional.presentIfNotNull(parts.getOrNull(1)?.takeIf { it > 0 }),
            day = Optional.presentIfNotNull(parts.getOrNull(2)?.takeIf { it > 0 }),
        )
    }

    private suspend fun <D : com.apollographql.apollo.api.Operation.Data> apolloCall(
        token: String?,
        block: suspend () -> com.apollographql.apollo.api.ApolloResponse<D>,
    ): D =
        withContext(Dispatchers.IO) {
            bindToken(token)
            val response = block()
            response.exception?.let { throw it }
            response.errors?.firstOrNull()?.message?.let { throw IllegalStateException(it) }
            response.data ?: throw IllegalStateException("Respons AniList kosong")
        }

    // ── OAuth (bukan GraphQL) ──────────────────────────────────────────────

    override fun loginRequest(): AuthLoginPage? {
        if (CLIENT_ID.isBlank()) return null
        val state = AuthAPI.generateCodeVerifier().take(32)
        val url =
            "https://anilist.co/api/v2/oauth/authorize"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("response_type", "token")
                .addQueryParameter("state", state)
                .build()
                .toString()
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
        throw HttpException(401)
    }

    // ── User / profile ─────────────────────────────────────────────────────

    override suspend fun user(token: AuthToken?): AuthUser? {
        val data = apolloCall(token?.accessToken) { apollo.query(ViewerQuery()).execute() }
        val viewer = data.Viewer ?: return null
        scoreFormat = viewer.mediaListOptions?.scoreFormat?.rawValue ?: "POINT_100"
        return AuthUser(viewer.name, viewer.id, viewer.avatar?.large)
    }

    suspend fun profile(auth: AuthData?, previous: AniListStats? = null): AniListUser {
        val token = auth?.token?.accessToken
        return coroutineScope {
            val rankingsJob =
                async {
                    runCatching {
                        apolloCall(token) { apollo.query(ViewerRankingsQuery()).execute() }
                            .Viewer?.statistics?.anime
                    }.getOrNull()
                }
            val statsData = apolloCall(token) { apollo.query(ViewerStatsQuery()).execute() }
            val viewer = statsData.Viewer ?: throw IllegalStateException("Respons AniList kosong")
            val anime = viewer.statistics?.anime
            val rewatchJob = async { runCatching { totalAnimeRewatches(viewer.id, token) }.getOrNull() }

            fun statsFrom(key: String?, count: Int, minutes: Int, mean: Double) =
                AniStat(key = key, count = count, minutes = minutes, meanScore = mean)

            val scores =
                anime?.scores.orEmpty().mapNotNull { item ->
                    item ?: return@mapNotNull null
                    statsFrom(item.score?.toString(), item.count, item.minutesWatched, item.meanScore)
                }
            val lengths =
                anime?.lengths.orEmpty().mapNotNull { item ->
                    item ?: return@mapNotNull null
                    statsFrom(item.length, item.count, item.minutesWatched, item.meanScore)
                }.sortedBy { it.key?.takeWhile { ch -> ch.isDigit() }?.toIntOrNull() ?: Int.MAX_VALUE }
            val statuses =
                anime?.statuses.orEmpty().mapNotNull { item ->
                    item ?: return@mapNotNull null
                    statsFrom(item.status?.rawValue, item.count, item.minutesWatched, item.meanScore)
                }
            val formats =
                anime?.formats.orEmpty().mapNotNull { item ->
                    item ?: return@mapNotNull null
                    statsFrom(item.format?.rawValue, item.count, item.minutesWatched, item.meanScore)
                }
            val countries =
                anime?.countries.orEmpty().mapNotNull { item ->
                    item ?: return@mapNotNull null
                    statsFrom(item.country?.toString(), item.count, item.minutesWatched, item.meanScore)
                }
            val releaseYears =
                anime?.releaseYears.orEmpty().mapNotNull { item ->
                    item ?: return@mapNotNull null
                    statsFrom(item.releaseYear?.toString(), item.count, item.minutesWatched, item.meanScore)
                }
            val startYears =
                anime?.startYears.orEmpty().mapNotNull { item ->
                    item ?: return@mapNotNull null
                    statsFrom(item.startYear?.toString(), item.count, item.minutesWatched, item.meanScore)
                }

            val r = rankingsJob.await()

            fun toGenreRanks(
                c: List<ViewerRankingsQuery.GenresC?>?,
                t: List<ViewerRankingsQuery.GenresT?>?,
                s: List<ViewerRankingsQuery.GenresS?>?,
            ) = AniRankings(
                byCount = c.orEmpty().mapNotNull { it?.let { x -> AniRank(x.genre, null, x.count, x.minutesWatched, x.meanScore) } },
                byTime = t.orEmpty().mapNotNull { it?.let { x -> AniRank(x.genre, null, x.count, x.minutesWatched, x.meanScore) } },
                byScore = s.orEmpty().mapNotNull { it?.let { x -> AniRank(x.genre, null, x.count, x.minutesWatched, x.meanScore) } },
            )

            fun toTagRanks(
                c: List<ViewerRankingsQuery.TagsC?>?,
                t: List<ViewerRankingsQuery.TagsT?>?,
                s: List<ViewerRankingsQuery.TagsS?>?,
            ) = AniRankings(
                byCount = c.orEmpty().mapNotNull { it?.let { x -> AniRank(x.tag?.name, null, x.count, x.minutesWatched, x.meanScore) } },
                byTime = t.orEmpty().mapNotNull { it?.let { x -> AniRank(x.tag?.name, null, x.count, x.minutesWatched, x.meanScore) } },
                byScore = s.orEmpty().mapNotNull { it?.let { x -> AniRank(x.tag?.name, null, x.count, x.minutesWatched, x.meanScore) } },
            )

            fun toStudioRanks(
                c: List<ViewerRankingsQuery.StudiosC?>?,
                t: List<ViewerRankingsQuery.StudiosT?>?,
                s: List<ViewerRankingsQuery.StudiosS?>?,
            ) = AniRankings(
                byCount = c.orEmpty().mapNotNull { it?.let { x -> AniRank(x.studio?.name, null, x.count, x.minutesWatched, x.meanScore) } },
                byTime = t.orEmpty().mapNotNull { it?.let { x -> AniRank(x.studio?.name, null, x.count, x.minutesWatched, x.meanScore) } },
                byScore = s.orEmpty().mapNotNull { it?.let { x -> AniRank(x.studio?.name, null, x.count, x.minutesWatched, x.meanScore) } },
            )

            fun toStaffRanks(
                c: List<ViewerRankingsQuery.StaffC?>?,
                t: List<ViewerRankingsQuery.StaffT?>?,
                s: List<ViewerRankingsQuery.StaffByScore?>?,
            ) = AniRankings(
                byCount = c.orEmpty().mapNotNull {
                    it?.let { x ->
                        AniRank(x.staff?.name?.userPreferred, x.staff?.image?.medium, x.count, x.minutesWatched, x.meanScore)
                    }
                },
                byTime = t.orEmpty().mapNotNull {
                    it?.let { x ->
                        AniRank(x.staff?.name?.userPreferred, x.staff?.image?.medium, x.count, x.minutesWatched, x.meanScore)
                    }
                },
                byScore = s.orEmpty().mapNotNull {
                    it?.let { x ->
                        AniRank(x.staff?.name?.userPreferred, x.staff?.image?.medium, x.count, x.minutesWatched, x.meanScore)
                    }
                },
            )

            fun toVaRanks(
                c: List<ViewerRankingsQuery.VoiceActorsC?>?,
                t: List<ViewerRankingsQuery.VoiceActorsT?>?,
                s: List<ViewerRankingsQuery.VoiceActorsS?>?,
            ) = AniRankings(
                byCount = c.orEmpty().mapNotNull {
                    it?.let { x ->
                        AniRank(
                            x.voiceActor?.name?.userPreferred,
                            x.voiceActor?.image?.medium,
                            x.count,
                            x.minutesWatched,
                            x.meanScore,
                        )
                    }
                },
                byTime = t.orEmpty().mapNotNull {
                    it?.let { x ->
                        AniRank(
                            x.voiceActor?.name?.userPreferred,
                            x.voiceActor?.image?.medium,
                            x.count,
                            x.minutesWatched,
                            x.meanScore,
                        )
                    }
                },
                byScore = s.orEmpty().mapNotNull {
                    it?.let { x ->
                        AniRank(
                            x.voiceActor?.name?.userPreferred,
                            x.voiceActor?.image?.medium,
                            x.count,
                            x.minutesWatched,
                            x.meanScore,
                        )
                    }
                },
            )

            AniListUser(
                id = viewer.id,
                name = viewer.name.orEmpty(),
                avatar = viewer.avatar?.large,
                createdAt = viewer.createdAt?.toLong(),
                stats =
                    AniListStats(
                        count = anime?.count ?: 0,
                        episodes = anime?.episodesWatched ?: 0,
                        totalRewatches = rewatchJob.await() ?: previous?.totalRewatches,
                        minutes = anime?.minutesWatched ?: 0,
                        meanScore = anime?.meanScore ?: 0.0,
                        standardDeviation = anime?.standardDeviation ?: 0.0,
                        scores = scores,
                        lengths = lengths,
                        statuses = statuses,
                        formats = formats,
                        countries = countries,
                        releaseYears = releaseYears,
                        startYears = startYears,
                        genres = toGenreRanks(r?.genresC, r?.genresT, r?.genresS),
                        tags = toTagRanks(r?.tagsC, r?.tagsT, r?.tagsS),
                        studios = toStudioRanks(r?.studiosC, r?.studiosT, r?.studiosS),
                        staff = toStaffRanks(r?.staffC, r?.staffT, r?.staffByScore),
                        voiceActors = toVaRanks(r?.voiceActorsC, r?.voiceActorsT, r?.voiceActorsS),
                    ),
            )
        }
    }

    private suspend fun totalAnimeRewatches(userId: Int, token: String?): Int {
        var chunk = 1
        var total = 0
        val seen = HashSet<Int>()
        while (true) {
            val data =
                apolloCall(token) {
                    apollo.query(
                        MediaListCollectionQuery(
                            userId = Optional.present(userId),
                            chunk = Optional.present(chunk),
                            perChunk = Optional.present(LIST_CHUNK_SIZE),
                        ),
                    ).execute()
                }
            val collection = data.MediaListCollection ?: break
            collection.lists.orEmpty().flatMap { it?.entries.orEmpty() }.forEach { entry ->
                val id = entry?.id ?: return@forEach
                if (seen.add(id)) total += entry.repeat ?: 0
            }
            if (collection.hasNextChunk != true || chunk >= MAX_CHUNKS) break
            chunk++
        }
        return total
    }

    // ── Search / load / status ─────────────────────────────────────────────

    override suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? {
        val data =
            apolloCall(auth?.token?.accessToken) {
                apollo.query(SearchAnimeQuery(Optional.present(query.take(64)))).execute()
            }
        return data.Page?.media.orEmpty().mapNotNull { media ->
            media ?: return@mapNotNull null
            SyncSearchResult(
                name = titleOf(media.title?.english, media.title?.romaji, media.title?.native),
                syncId = media.id.toString(),
                url = "$mainUrl/anime/${media.id}",
                posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                synonyms = listOfNotNull(media.title?.romaji, media.title?.english, media.title?.native).distinct(),
            )
        }
    }

    override suspend fun load(auth: AuthData?, id: String): SyncResult? {
        val mediaId = id.toIntOrNull() ?: return null
        if (scoreFormat == null && auth?.token?.accessToken != null) {
            runCatching { user(auth.token) }
        }
        val data =
            apolloCall(auth?.token?.accessToken) {
                apollo.query(MediaByIdQuery(Optional.present(mediaId))).execute()
            }
        val media = data.Media ?: return null
        val entry = media.mediaListEntry
        return SyncResult(
            id = media.id.toString(),
            title = titleOf(media.title?.english, media.title?.romaji, media.title?.native),
            totalEpisodes = media.episodes?.takeIf { it > 0 },
            synonyms = listOfNotNull(media.title?.romaji, media.title?.english, media.title?.native).distinct(),
            posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
            publicScore = media.averageScore?.toDouble(),
            synopsis = media.description,
            myStatus =
                entry?.let {
                    SyncStatus(
                        status = watchType(it.status?.rawValue),
                        score = scoreToUi(it.score),
                        watchedEpisodes = it.progress,
                        maxEpisodes = media.episodes?.takeIf { e -> e > 0 },
                        startDate = fuzzyToIso(it.startedAt?.year, it.startedAt?.month, it.startedAt?.day),
                        finishDate = fuzzyToIso(it.completedAt?.year, it.completedAt?.month, it.completedAt?.day),
                        isRewatching = it.status == MediaListStatus.REPEATING,
                        rewatchCount = it.repeat,
                        comments = it.notes,
                    )
                },
        )
    }

    override suspend fun status(auth: AuthData?, id: String): SyncStatus? = load(auth, id)?.myStatus

    override suspend fun updateStatus(auth: AuthData?, id: String, newStatus: SyncStatus): Boolean {
        if (newStatus.status == SyncWatchType.NONE) return removeStatus(auth, id)
        if (scoreFormat == null && auth?.token?.accessToken != null) {
            runCatching { user(auth.token) }
        }
        val mediaId = id.toIntOrNull() ?: return false
        val listStatus =
            if (newStatus.isRewatching == true && newStatus.status == SyncWatchType.WATCHING) {
                MediaListStatus.REPEATING
            } else {
                toMediaListStatus(newStatus.status) ?: MediaListStatus.CURRENT
            }
        apolloCall(auth?.token?.accessToken) {
            apollo.mutation(
                SaveMediaListEntryMutation(
                    mediaId = Optional.present(mediaId),
                    status = Optional.present(listStatus),
                    progress = Optional.presentIfNotNull(newStatus.watchedEpisodes),
                    score = Optional.presentIfNotNull(newStatus.score?.let { scoreToAniList(it) }),
                    startedAt = Optional.presentIfNotNull(isoToFuzzy(newStatus.startDate)),
                    completedAt = Optional.presentIfNotNull(isoToFuzzy(newStatus.finishDate)),
                    repeat = Optional.presentIfNotNull(newStatus.rewatchCount),
                    notes = Optional.presentIfNotNull(newStatus.comments),
                ),
            ).execute()
        }
        return true
    }

    suspend fun editData(auth: AuthData?, id: Int): AniListEditData? {
        val data =
            apolloCall(auth?.token?.accessToken) {
                apollo.query(MediaListEntryQuery(Optional.present(id))).execute()
            }
        val media = data.Media ?: return null
        val options = data.Viewer?.mediaListOptions
        scoreFormat = options?.scoreFormat?.rawValue ?: scoreFormat ?: "POINT_100"
        val animeList = options?.animeList
        val entry = media.mediaListEntry
        return AniListEditData(
            mediaId = media.id,
            totalEpisodes = media.episodes,
            entry =
                entry?.let {
                    val advanced =
                        when (val raw = it.advancedScores) {
                            is Map<*, *> ->
                                raw.entries.mapNotNull { e ->
                                    val k = e.key as? String ?: return@mapNotNull null
                                    val v = (e.value as? Number)?.toDouble() ?: return@mapNotNull null
                                    k to v
                                }.toMap()
                            else -> emptyMap()
                        }
                    val customs =
                        when (val raw = it.customLists) {
                            is Map<*, *> ->
                                raw.filterValues { on -> on == true }.keys.mapNotNull { k -> k as? String }.toSet()
                            else -> emptySet()
                        }
                    AniListEditEntry(
                        status = it.status?.rawValue,
                        score = it.score,
                        progress = it.progress,
                        startedAt = fuzzyToIso(it.startedAt?.year, it.startedAt?.month, it.startedAt?.day),
                        completedAt = fuzzyToIso(it.completedAt?.year, it.completedAt?.month, it.completedAt?.day),
                        repeat = it.repeat,
                        priority = it.priority,
                        isPrivate = it.private,
                        hiddenFromStatusLists = it.hiddenFromStatusLists,
                        notes = it.notes,
                        advancedScores = advanced,
                        customLists = customs,
                    )
                },
            options =
                AniListEditOptions(
                    scoreFormat = scoreFormat ?: "POINT_100",
                    advancedScoringEnabled = animeList?.advancedScoringEnabled == true,
                    advancedScoring = animeList?.advancedScoring.orEmpty().filterNotNull(),
                    customLists = animeList?.customLists.orEmpty().filterNotNull(),
                ),
        )
    }

    suspend fun loadWithEdit(auth: AuthData?, id: Int): Pair<SyncResult, AniListEditData>? {
        val result = load(auth, id.toString()) ?: return null
        val edit = editData(auth, id) ?: return null
        return result to edit
    }

    suspend fun saveEntry(auth: AuthData?, id: Int, update: AniListEditUpdate): Boolean {
        apolloCall(auth?.token?.accessToken) {
            apollo.mutation(
                SaveMediaListEntryMutation(
                    mediaId = Optional.present(id),
                    status = Optional.presentIfNotNull(update.status?.let { MediaListStatus.safeValueOf(it) }),
                    score = Optional.presentIfNotNull(update.score),
                    progress = Optional.presentIfNotNull(update.progress),
                    startedAt = Optional.presentIfNotNull(isoToFuzzy(update.startedAt)),
                    completedAt = Optional.presentIfNotNull(isoToFuzzy(update.completedAt)),
                    repeat = Optional.presentIfNotNull(update.repeat),
                    private = Optional.presentIfNotNull(update.isPrivate),
                    hiddenFromStatusLists = Optional.presentIfNotNull(update.hiddenFromStatusLists),
                    notes = Optional.presentIfNotNull(update.notes),
                    advancedScores = Optional.presentIfNotNull(update.advancedScores),
                    priority = Optional.presentIfNotNull(update.priority),
                    customLists = Optional.presentIfNotNull(update.customLists),
                ),
            ).execute()
        }
        return true
    }

    override suspend fun removeStatus(auth: AuthData?, id: String): Boolean {
        val mediaId = id.toIntOrNull() ?: return false
        val data =
            apolloCall(auth?.token?.accessToken) {
                apollo.query(MediaByIdQuery(Optional.present(mediaId))).execute()
            }
        val entryId = data.Media?.mediaListEntry?.id ?: return true
        val deleted =
            apolloCall(auth?.token?.accessToken) {
                apollo.mutation(DeleteMediaListEntryMutation(Optional.present(entryId))).execute()
            }
        return deleted.DeleteMediaListEntry?.deleted == true
    }

    // ── History / favorites ────────────────────────────────────────────────

    suspend fun history(auth: AuthData?): List<ProfileHistoryItem> {
        val token = auth?.token?.accessToken ?: return emptyList()
        val userId = auth.user.id
        val data =
            apolloCall(token) {
                apollo.query(UserActivitiesQuery(Optional.present(userId))).execute()
            }
        return data.Page?.activities.orEmpty().mapNotNull { activity ->
            val list = activity?.onListActivity ?: return@mapNotNull null
            val media = list.media ?: return@mapNotNull null
            if (media.type?.rawValue != "ANIME") return@mapNotNull null
            val mediaId = media.id
            val name = titleOf(media.title?.english, media.title?.romaji, media.title?.native)
            val status = list.status.orEmpty()
            val progress = list.progress?.takeIf { it.isNotBlank() }
            val action =
                if (progress != null && status.contains("episode", ignoreCase = true)) {
                    "ep. $progress"
                } else {
                    listOfNotNull(
                        status.replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() },
                        progress,
                    ).joinToString(" ").ifBlank { "Diperbarui" }
                }
            val epoch = list.createdAt?.toLong() ?: 0L
            ProfileHistoryItem(
                key = "${list.id}",
                mediaId = mediaId,
                title = name,
                action = action,
                time = if (epoch > 0) historyTimeId(epoch * 1000) else "",
                group = if (epoch > 0) historyGroup(epoch * 1000) else HISTORY_TODAY,
                url = "https://anilist.co/anime/$mediaId",
            )
        }
    }

    suspend fun favorites(auth: AuthData?): List<AniFavorite> {
        val token = auth?.token?.accessToken ?: return emptyList()
        val userId = auth.user.id
        val seen = HashSet<Int>()
        val out = ArrayList<AniFavorite>()
        var page = 1
        while (page <= MAX_FAVORITE_PAGES) {
            val data =
                apolloCall(token) {
                    apollo.query(
                        UserFavouritesQuery(
                            userId = Optional.present(userId),
                            page = Optional.present(page),
                            perPage = Optional.present(FAVORITE_PAGE_SIZE),
                        ),
                    ).execute()
                }
            val connection = data.User?.favourites?.anime ?: break
            connection.nodes.orEmpty().forEach { node ->
                node ?: return@forEach
                if (!seen.add(node.id)) return@forEach
                out +=
                    AniFavorite(
                        id = node.id,
                        name = titleOf(node.title?.english, node.title?.romaji, node.title?.native),
                        posterUrl = node.coverImage?.extraLarge ?: node.coverImage?.large,
                        synonyms = node.synonyms?.filterNotNull(),
                    )
            }
            if (connection.pageInfo?.hasNextPage != true) break
            page++
        }
        return out
    }

    suspend fun isFavourite(auth: AuthData?, id: Int): Boolean {
        val data =
            apolloCall(auth?.token?.accessToken) {
                apollo.query(MediaByIdQuery(Optional.present(id))).execute()
            }
        return data.Media?.isFavourite == true
    }

    suspend fun setFavourite(auth: AuthData?, id: Int, favorite: Boolean): Boolean {
        if (isFavourite(auth, id) == favorite) return true
        apolloCall(auth?.token?.accessToken) {
            apollo.mutation(ToggleFavouriteMutation(Optional.present(id))).execute()
        }
        return true
    }

    // ── Library ────────────────────────────────────────────────────────────

    override suspend fun library(auth: AuthData?): LibraryMetadata? {
        val token = auth?.token?.accessToken ?: return null
        val userId = auth.user.id
        val items = ArrayList<LibraryItem>()
        var chunk = 1
        while (true) {
            val data =
                apolloCall(token) {
                    apollo.query(
                        MediaListCollectionQuery(
                            userId = Optional.present(userId),
                            chunk = Optional.present(chunk),
                            perChunk = Optional.present(LIST_CHUNK_SIZE),
                        ),
                    ).execute()
                }
            val collection = data.MediaListCollection ?: break
            collection.lists.orEmpty().forEach { list ->
                list ?: return@forEach
                list.entries.orEmpty().forEach { entry ->
                    entry ?: return@forEach
                    val media = entry.media ?: return@forEach
                    val type = watchType(entry.status?.rawValue)
                    items +=
                        LibraryItem(
                            name = titleOf(media.title?.english, media.title?.romaji, media.title?.native),
                            url = "$mainUrl/anime/${media.id}",
                            syncId = media.id.toString(),
                            status = type,
                            episodesCompleted = entry.progress,
                            episodesTotal = media.episodes?.takeIf { it > 0 },
                            personalRating = entry.score?.roundToInt()?.takeIf { it > 0 },
                            lastUpdatedUnixTime = entry.updatedAt?.toLong(),
                            posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                            releaseDate = null,
                            synonyms =
                                listOfNotNull(media.title?.romaji, media.title?.english, media.title?.native)
                                    .distinct(),
                            startDate = null,
                            finishDate = null,
                            crossMalId = media.idMal?.takeIf { it > 0 },
                            rewatching = entry.status == MediaListStatus.REPEATING,
                            rewatchCount = entry.repeat ?: 0,
                        )
                }
            }
            if (collection.hasNextChunk != true || chunk >= MAX_CHUNKS) break
            chunk++
        }
        val grouped = items.groupBy { it.status }
        val result =
            SyncWatchType.entries
                .filter { it != SyncWatchType.NONE && it in supportedWatchTypes }
                .map { LibraryList(it.label, it, grouped[it].orEmpty()) }
        return LibraryMetadata(result, ListSorting.entries.toSet())
    }

    override fun urlToId(url: String): String? =
        Regex("anilist\\.co/anime/(\\d+)").find(url)?.groupValues?.get(1)
}
