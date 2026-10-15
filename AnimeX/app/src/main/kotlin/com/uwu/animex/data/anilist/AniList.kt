package com.uwu.animex.data.anilist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.TitleMatcher
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.sync.providers.AniListApi
import com.uwu.animex.sync.providers.AniListEditData
import com.uwu.animex.sync.providers.AniListEditOptions
import com.uwu.animex.sync.providers.AniListEditUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class AniFavorite(
    val id: Int,
    val name: String,
    val posterUrl: String? = null,
    val synonyms: List<String>? = null,
)

data class AniStat(
    val key: String? = null,
    val count: Int = 0,
    val minutes: Int = 0,
    val meanScore: Double = 0.0,
)

data class AniRank(
    val name: String? = null,
    val image: String? = null,
    val count: Int = 0,
    val minutes: Int = 0,
    val meanScore: Double = 0.0,
)

data class AniRankings(
    val byCount: List<AniRank> = emptyList(),
    val byTime: List<AniRank> = emptyList(),
    val byScore: List<AniRank> = emptyList(),
)

data class AniListStats(
    val count: Int = 0,
    val episodes: Int = 0,
    val minutes: Int = 0,
    val plannedMinutes: Int = 0,
    val meanScore: Double = 0.0,
    val standardDeviation: Double = 0.0,
    val scores: List<AniStat> = emptyList(),
    val lengths: List<AniStat> = emptyList(),
    val statuses: List<AniStat> = emptyList(),
    val formats: List<AniStat> = emptyList(),
    val countries: List<AniStat> = emptyList(),
    val releaseYears: List<AniStat> = emptyList(),
    val startYears: List<AniStat> = emptyList(),
    val genres: AniRankings = AniRankings(),
    val tags: AniRankings = AniRankings(),
    val studios: AniRankings = AniRankings(),
    val staff: AniRankings = AniRankings(),
    val voiceActors: AniRankings = AniRankings(),
)

data class AniListUser(
    val id: Int? = null,
    val name: String? = null,
    val avatar: String? = null,
    val createdAt: Long? = null,
    val stats: AniListStats? = null,
)

object AniList {
    val CLIENT_ID: String get() = AniListApi.CLIENT_ID
    val REDIRECT_URI: String get() = AniListApi.REDIRECT_URI
    val PROFILE_URL: String get() = AniListApi.PROFILE_URL

    private const val PREFS = "anilist"
    private const val NOT_FOUND_TTL_MS = 10 * 60 * 1000L
    private val gson = Gson()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private val repo get() = AccountManager.aniListApi

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()
    private val _user = MutableStateFlow<AniListUser?>(null)
    val user: StateFlow<AniListUser?> = _user.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _autoSync = MutableStateFlow(true)
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()
    private val _links = MutableStateFlow<Map<String, Int>>(emptyMap())
    val links: StateFlow<Map<String, Int>> = _links.asStateFlow()
    private val _favorites = MutableStateFlow<List<AniFavorite>>(emptyList())
    val favorites: StateFlow<List<AniFavorite>> = _favorites.asStateFlow()
    private val _favoritesLoaded = MutableStateFlow(false)
    val favoritesLoaded: StateFlow<Boolean> = _favoritesLoaded.asStateFlow()
    private val favoritesType = object : TypeToken<List<AniFavorite>>() {}.type
    private val preloadCache = ConcurrentHashMap<String, SyncResult>()
    private val notFound = TtlCache<String, Boolean>(maxEntries = 128, ttlMs = NOT_FOUND_TTL_MS)
    private val editCache = ConcurrentHashMap<String, AniListEditData>()
    private val mapType = object : TypeToken<HashMap<String, Int>>() {}.type

    @Volatile
    var lastEditOptions: AniListEditOptions? = null
        private set

    fun clearMessage() {
        _message.value = null
    }

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        AccountManager.init(app)
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        repo.onSessionExpired = { scope.launch { logout() } }
        _loggedIn.value = repo.authUser() != null
        _autoSync.value = p.getBoolean("auto_sync", true)
        _links.value =
            runCatching { gson.fromJson<HashMap<String, Int>>(p.getString("map", null), mapType) }.getOrNull().orEmpty()
        _user.value = runCatching { gson.fromJson(p.getString("user", null), AniListUser::class.java) }.getOrNull()
        _favorites.value =
            runCatching { gson.fromJson<List<AniFavorite>>(p.getString("favorites", null), favoritesType) }
                .getOrNull()
                .orEmpty()
        _favoritesLoaded.value = p.contains("favorites")
        lastEditOptions =
            runCatching { gson.fromJson(p.getString("edit_options", null), AniListEditOptions::class.java) }.getOrNull()
        AniListLibrary.init(app)
        if (_loggedIn.value) {
            scope.launch {
                runCatching { refreshUser() }
                AniListLibrary.refresh()
            }
            scope.launch { runCatching { refreshFavorites() } }
        }
    }

    fun updateAutoSync(value: Boolean) {
        _autoSync.value = value
        prefs?.edit()?.putBoolean("auto_sync", value)?.apply()
    }

    fun startLogin(context: Context) {
        if (CLIENT_ID.isBlank()) {
            _message.value =
                "ANILIST_KEY belum diisi. Daftarkan aplikasi di anilist.co/settings/developer, lalu set env " +
                "ANILIST_KEY atau anilist.key di local.properties."
            return
        }
        val page = repo.loginRequest() ?: run {
            _message.value = "Gagal menyiapkan login AniList"
            return
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isRedirect(uri: Uri?): Boolean = uri != null && repo.isValidRedirectUrl(uri.toString())

    fun handleRedirect(uri: Uri) {
        val error = uri.getQueryParameter("error") ?: uri.fragment?.substringAfter("error=", "")?.substringBefore('&')
        if (!error.isNullOrBlank()) {
            _message.value = "Login AniList dibatalkan ($error)"
            return
        }
        scope.launch {
            _busy.value = true
            try {
                if (!repo.login(uri.toString())) {
                    _message.value =
                        "Gagal login AniList. Pastikan redirect URI di dashboard AniList adalah ${REDIRECT_URI}."
                    return@launch
                }
                _loggedIn.value = true
                notFound.clear()

                updateAutoSync(true)
                refreshUser()
                runCatching { AniListLibrary.refresh(force = true) }
                runCatching { refreshFavorites() }
            } catch (e: Exception) {
                _message.value = "Gagal login AniList: ${e.toUserMessage()}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun logout() {
        repo.logout()
        prefs
            ?.edit()
            ?.remove("user")
            ?.remove("map")
            ?.remove("totals")
            ?.remove("favorites")
            ?.remove("edit_options")
            ?.apply()
        lastEditOptions = null
        _loggedIn.value = false
        _favorites.value = emptyList()
        _favoritesLoaded.value = false
        _user.value = null
        _links.value = emptyMap()
        preloadCache.clear()
        notFound.clear()
        editCache.clear()
        AniListLibrary.clear()
    }

    suspend fun refreshUser(): AniListUser {
        val authUser = repo.authUser() ?: throw IllegalStateException("Kamu belum login")
        val fallback = AniListUser(authUser.id, authUser.name, authUser.profilePicture)
        val u =
            runCatching { repo.withAuth { (repo.api as AniListApi).profile(it) } }.getOrNull()
                ?: _user.value?.copy(id = fallback.id, name = fallback.name, avatar = fallback.avatar)
                ?: fallback
        _user.value = u
        prefs?.edit()?.putString("user", gson.toJson(u))?.apply()
        return u
    }

    private fun saveFavorites(list: List<AniFavorite>) {
        _favorites.value = list
        _favoritesLoaded.value = true
        prefs?.edit()?.putString("favorites", gson.toJson(list))?.apply()
    }

    suspend fun refreshFavorites() {
        if (!_loggedIn.value) return
        saveFavorites(repo.withAuth { (repo.api as AniListApi).favorites(it) })
    }

    suspend fun setFavorite(
        movie: Movie,
        favorite: Boolean,
    ) {
        if (!_loggedIn.value) return
        val movieId = movie.id ?: return
        val aniId =
            aniListIdFor(movieId) ?: resolve(movie)?.id?.toIntOrNull()
                ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        repo.withAuth { (repo.api as AniListApi).setFavourite(it, aniId, favorite) }
        val current = _favorites.value
        saveFavorites(
            if (favorite) {
                if (current.any { it.id == aniId }) {
                    current
                } else {
                    listOf(AniFavorite(aniId, movie.title.orEmpty())) + current
                }
            } else {
                current.filterNot { it.id == aniId }
            },
        )
        scope.launch { runCatching { refreshFavorites() } }
    }

    suspend fun toggleFavorite(movie: Movie): Boolean {
        if (!_loggedIn.value) throw IllegalStateException("Belum login ke AniList")
        val movieId = movie.id ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        val aniId =
            aniListIdFor(movieId) ?: findId(movie)
                ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        val current = repo.withAuth { (repo.api as AniListApi).isFavourite(it, aniId) }
        setFavorite(movie, !current)
        return !current
    }

    suspend fun update(
        id: Int,
        status: SyncStatus,
    ): Boolean {
        val ok = repo.updateStatus(id.toString(), status).getOrThrow()
        if (ok) {
            val hint = preloadCache.values.firstOrNull { it.id == id.toString() }
            invalidate(id)
            val needsRefresh =
                AniListLibrary.patch(
                    id = id,
                    status = status.status,
                    progress = status.watchedEpisodes,
                    rating = status.score?.let { nativeScore(it.toDouble(), fromTen = true) },
                    startDate = status.startDate,
                    finishDate = status.finishDate,
                    hint = hint,
                )
            if (needsRefresh) scope.launch { AniListLibrary.refresh(force = true) }
        }
        return ok
    }

    suspend fun editData(movie: Movie): AniListEditData {
        val movieId = movie.id ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        val aniId =
            aniListIdFor(movieId) ?: resolve(movie)?.id?.toIntOrNull()
                ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        val data =
            repo.withAuth { (repo.api as AniListApi).editData(it, aniId) }
                ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        cacheId(movieId, aniId)
        cacheTotal(aniId, data.totalEpisodes ?: 0)
        editCache[movieId] = data
        rememberOptions(data.options)
        return data
    }

    suspend fun saveEntry(
        id: Int,
        update: AniListEditUpdate,
    ): Boolean {
        val ok = repo.withAuth { (repo.api as AniListApi).saveEntry(it, id, update) }
        if (ok) {
            val hint = preloadCache.values.firstOrNull { it.id == id.toString() }
            invalidate(id)
            val needsRefresh =
                AniListLibrary.patch(
                    id = id,
                    status = aniWatchType(update.status),
                    progress = update.progress,
                    rating = update.score?.let { nativeScore(it, fromTen = false) },
                    startDate = update.startedAt,
                    finishDate = update.completedAt,
                    hint = hint,
                )
            if (needsRefresh) scope.launch { AniListLibrary.refresh(force = true) }
        }
        return ok
    }

    suspend fun delete(id: Int) {
        repo.removeStatus(id.toString()).getOrThrow()
        invalidate(id)
        AniListLibrary.remove(id)
    }

    private fun aniWatchType(status: String?): SyncWatchType? =
        when (status) {
            "CURRENT", "REPEATING" -> SyncWatchType.WATCHING
            "PLANNING" -> SyncWatchType.PLANTOWATCH
            "COMPLETED" -> SyncWatchType.COMPLETED
            "PAUSED" -> SyncWatchType.ONHOLD
            "DROPPED" -> SyncWatchType.DROPPED
            else -> null
        }

    private fun invalidate(id: Int) {
        preloadCache.entries.removeIf { it.value.id == id.toString() }
        editCache.entries.removeIf { it.value.mediaId == id }
    }

    private fun nativeScore(
        value: Double,
        fromTen: Boolean,
    ): Int {
        if (!fromTen) return value.roundToInt()
        return when (lastEditOptions?.scoreFormat ?: "POINT_100") {
            "POINT_10_DECIMAL", "POINT_10" -> value
            "POINT_5" -> value / 2.0
            "POINT_3" -> value * 3.0 / 10.0
            else -> value * 10.0
        }.roundToInt()
    }

    private fun cacheId(
        movieId: String,
        aniListId: Int,
    ) {
        val next = HashMap(_links.value)
        next[movieId] = aniListId
        _links.value = next
        prefs?.edit()?.putString("map", gson.toJson(next))?.apply()
    }

    fun aniListIdFor(movieId: String?): Int? = movieId?.let { _links.value[it] }

    fun link(
        movieId: String,
        aniListId: Int,
    ) = cacheId(movieId, aniListId)

    fun movieIdFor(aniListId: Int): String? =
        _links.value.entries
            .lastOrNull { it.value == aniListId }
            ?.key

    fun preloaded(movieId: String?): SyncResult? = movieId?.let { preloadCache[it] }

    fun editPreloaded(movieId: String?): AniListEditData? = movieId?.let { editCache[it] }

    suspend fun preload(movie: Movie) {
        val movieId = movie.id ?: return
        val aniId = runCatching { findId(movie) }.getOrNull() ?: return
        val (result, edit) =
            runCatching { repo.withAuth { (repo.api as AniListApi).loadWithEdit(it, aniId) } }.getOrNull() ?: return
        preloadCache[movieId] = result
        editCache[movieId] = edit
        cacheTotal(aniId, result.totalEpisodes ?: 0)
        rememberOptions(edit.options)
    }

    private fun readTotals(): HashMap<String, Int>? =
        runCatching { gson.fromJson<HashMap<String, Int>>(prefs?.getString("totals", null), mapType) }.getOrNull()

    fun cachedTotal(aniListId: Int): Int? = readTotals()?.get(aniListId.toString())

    private fun cacheTotal(
        aniListId: Int,
        total: Int?,
    ) {
        val value = total ?: return
        val m = readTotals() ?: HashMap()
        if (m[aniListId.toString()] == value) return
        m[aniListId.toString()] = value
        prefs?.edit()?.putString("totals", gson.toJson(m))?.apply()
    }

    private suspend fun loadCached(id: String): SyncResult? {
        val r = repo.load(id).getOrThrow()
        val aniId = r?.id?.toIntOrNull()
        if (aniId != null) cacheTotal(aniId, r.totalEpisodes ?: 0)
        return r
    }

    private fun rememberOptions(options: AniListEditOptions) {
        lastEditOptions = options
        prefs?.edit()?.putString("edit_options", gson.toJson(options))?.apply()
    }

    private fun invalidatePreload(aniListId: Int) {
        preloadCache.entries.removeIf { it.value.id == aniListId.toString() }
    }

    suspend fun resolve(movie: Movie): SyncResult? {
        val movieId = movie.id ?: return null
        aniListIdFor(movieId)?.let { id -> return loadCached(id.toString()) }
        val id = findId(movie) ?: return null
        return loadCached(id.toString())
    }

    private suspend fun findId(movie: Movie): Int? {
        val movieId = movie.id ?: return null
        aniListIdFor(movieId)?.let { return it }
        if (movie.title == null) return null
        if (notFound.get(movieId) == true) return null
        val found =
            TitleMatcher.match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q -> repo.search(q).getOrThrow().orEmpty() },
                idOf = { it.syncId },
                titlesOf = { listOf(it.name) + it.synonyms },
            )
        val id = found.guess?.syncId?.toIntOrNull()
        if (id == null) {
            if (found.guess == null) notFound.put(movieId, true)
            return null
        }
        cacheId(movieId, id)
        return id
    }
}

object AniListTracker {
    fun episodeWatched(epId: String) {
        val movie = History.items.value.firstOrNull { it.episode_id == epId } ?: return
        val ep = movie.episode_index?.trim()?.toIntOrNull()

        val local = Bookmarks.status(movie.id)
        if (local != WatchStatus.COMPLETED) {
            Bookmarks.setStatus(movie, WatchStatus.WATCHING)
        }

        if (ep == null) return
        if (!AniList.loggedIn.value || !AniList.autoSync.value) return

        AniList.scope.launch {
            runCatching {
                val status = sync(movie, ep)
                if (status == SyncWatchType.COMPLETED) {
                    Bookmarks.setStatus(movie, WatchStatus.COMPLETED)
                }
            }
        }
    }

    fun episodeWatched(
        movie: Movie,
        episode: Int,
    ) {
        if (!AniList.loggedIn.value || !AniList.autoSync.value) return
        AniList.scope.launch {
            runCatching {
                val status = sync(movie, episode)
                if (status == SyncWatchType.COMPLETED) {
                    Bookmarks.setStatus(movie, WatchStatus.COMPLETED)
                }
            }
        }
    }

    private suspend fun syncRewatch(
        id: Int,
        anime: SyncResult,
        cur: SyncStatus,
        ep: Int,
    ): SyncWatchType? {
        val total = anime.totalEpisodes ?: 0

        if (total > 0 && ep >= total && ep == 1) {
            AniList.update(
                id,
                SyncStatus(
                    status = SyncWatchType.COMPLETED,
                    isRewatching = false,
                    rewatchCount = (cur.rewatchCount ?: 0) + 1,
                ),
            )
            return SyncWatchType.COMPLETED
        }

        if (ep == 1) {
            AniList.update(id, SyncStatus(status = SyncWatchType.WATCHING, watchedEpisodes = 1, isRewatching = true))
        }
        return null
    }

    private suspend fun sync(
        movie: Movie,
        ep: Int,
    ): SyncWatchType? {
        val anime = AniList.resolve(movie) ?: return null
        val aniId = anime.id.toIntOrNull() ?: return null
        val cur = anime.myStatus
        val watched = cur?.watchedEpisodes ?: 0
        val rewatching = cur?.isRewatching == true
        if (cur != null && cur.status == SyncWatchType.COMPLETED && ep <= watched) {
            return syncRewatch(aniId, anime, cur, ep)
        }
        if (ep <= watched && cur?.status == SyncWatchType.WATCHING && !rewatching) return null

        val total = anime.totalEpisodes ?: 0
        val newWatched = maxOf(ep, watched)
        val done = total > 0 && newWatched >= total
        val status = if (done) SyncWatchType.COMPLETED else SyncWatchType.WATCHING
        AniList.update(
            aniId,
            SyncStatus(
                status = status,
                watchedEpisodes = newWatched,
                startDate = if (cur?.startDate == null) today() else null,
                finishDate = if (done && cur?.finishDate == null) today() else null,
                isRewatching = if (rewatching) !done else null,
                rewatchCount = if (rewatching && done) (cur.rewatchCount ?: 0) + 1 else null,
            ),
        )
        return status
    }

    private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
}
