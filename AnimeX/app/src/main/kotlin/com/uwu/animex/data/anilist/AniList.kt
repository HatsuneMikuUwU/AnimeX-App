package com.uwu.animex.data.anilist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.cache.PersistentCache
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.MalIdLookup
import com.uwu.animex.data.api.TitleMatcher
import com.uwu.animex.data.history.ProfileHistoryState
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.tracker.ListStats
import com.uwu.animex.data.tracker.SubmitResult
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.TrackerDetail
import com.uwu.animex.data.tracker.TrackerDetailCache
import com.uwu.animex.data.tracker.TrackerOutbox
import com.uwu.animex.data.tracker.TrackerSource
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.HttpException
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStats
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.sync.anilist.AniListApi
import com.uwu.animex.sync.anilist.AniListDetail
import com.uwu.animex.sync.anilist.AniListEditData
import com.uwu.animex.sync.anilist.AniListEditOptions
import com.uwu.animex.sync.anilist.AniListEditUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

data class AniFavorite(
    val id: Int,
    val name: String,
    val posterUrl: String? = null,
    val synonyms: List<String>? = null,
)

data class AniListUser(
    val id: Int? = null,
    val name: String? = null,
    val avatar: String? = null,
    val createdAt: Long? = null,
    val stats: ListStats? = null,
)

object AniList {
    val CLIENT_ID: String get() = AniListApi.CLIENT_ID
    val REDIRECT_URI: String get() = AniListApi.REDIRECT_URI
    val PROFILE_URL: String get() = AniListApi.PROFILE_URL

    private const val PREFS = "anilist"
    private const val RATE_LIMIT_COOLDOWN_MS = 60 * 1000L
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
    private val statsInflight = AtomicInteger(0)
    private val _statsLoading = MutableStateFlow(false)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()

    private val _history = MutableStateFlow(ProfileHistoryState())
    val history: StateFlow<ProfileHistoryState> = _history.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _autoSync = MutableStateFlow(true)
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()
    private val _links = MutableStateFlow<Map<String, Int>>(emptyMap())
    val links: StateFlow<Map<String, Int>> = _links.asStateFlow()
    private val _favorites = MutableStateFlow<List<AniFavorite>>(emptyList())
    val favorites: StateFlow<List<AniFavorite>> = _favorites.asStateFlow()
    private val favoritesType = object : TypeToken<List<AniFavorite>>() {}.type
    // Semua data detail (stats, status list, data edit, favorit, id) ada di satu entri yang disimpan di disk.
    private val notFound =
        PersistentCache.of<Boolean>("ani_not_found_v1", { CachePolicy.ttlMs(CacheKind.DETAIL) })
    private val inflight = ConcurrentHashMap<String, Deferred<TrackerDetail?>>()

    private fun detailOf(movieId: String?): TrackerDetail? =
        TrackerDetailCache.peek(SyncSource.ANILIST, aniListIdFor(movieId))

    fun cachedStats(movieId: String?): SyncStats? = detailOf(movieId)?.result?.stats
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
        lastEditOptions =
            runCatching { gson.fromJson(p.getString("edit_options", null), AniListEditOptions::class.java) }.getOrNull()
        AniListLibrary.init(app)
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
        _history.value = ProfileHistoryState()
        _user.value = null
        _links.value = emptyMap()
        TrackerDetailCache.clear(SyncSource.ANILIST)
        notFound.clear()
        AniListLibrary.clear()
    }

    suspend fun refreshHistory() {
        if (_history.value.loading) return
        _history.value = _history.value.copy(loading = true, error = null)
        runCatching { repo.withAuth { (repo.api as AniListApi).history(it) } }
            .onSuccess { _history.value = ProfileHistoryState(items = it, loaded = true) }
            .onFailure { _history.value = _history.value.copy(loading = false, error = it.toUserMessage()) }
    }

    suspend fun refreshUser(): AniListUser {
        val authUser = repo.authUser() ?: throw IllegalStateException("Kamu belum login")
        val fallback = AniListUser(authUser.id, authUser.name, authUser.profilePicture)
        val previous = _user.value
        _statsLoading.value = statsInflight.incrementAndGet() > 0
        try {
            val u =
                runCatching { repo.withAuth { (repo.api as AniListApi).profile(it, previous?.stats) } }.getOrNull()
                    ?: previous?.copy(id = fallback.id, name = fallback.name, avatar = fallback.avatar)
                    ?: fallback
            if (_loggedIn.value) {
                _user.value = u
                prefs?.edit()?.putString("user", gson.toJson(u))?.apply()
            }
            return u
        } finally {
            _statsLoading.value = statsInflight.decrementAndGet() > 0
        }
    }

    private fun saveFavorites(list: List<AniFavorite>) {
        _favorites.value = list
        prefs?.edit()?.putString("favorites", gson.toJson(list))?.apply()
    }

    suspend fun refreshFavorites() {
        if (!_loggedIn.value) return
        saveFavorites(repo.withAuth { (repo.api as AniListApi).favorites(it) })
    }

    suspend fun setFavorite(
        movie: Movie,
        favorite: Boolean,
        knownCurrent: Boolean? = null,
    ) {
        if (!_loggedIn.value) return
        val movieId = movie.id ?: return
        val aniId =
            aniListIdFor(movieId) ?: resolve(movie)?.id?.toIntOrNull()
                ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        repo.withAuth { (repo.api as AniListApi).setFavourite(it, aniId, favorite, knownCurrent) }
        // simpan status favorit baru di data detail (disk) biar tombol & app berikutnya langsung benar
        TrackerDetailCache.peek(SyncSource.ANILIST, aniId)?.let {
            TrackerDetailCache.put(SyncSource.ANILIST, it.copy(favorite = favorite))
        }
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
        val known = TrackerDetailCache.peek(SyncSource.ANILIST, aniId)?.favorite
        val current = known ?: repo.withAuth { (repo.api as AniListApi).isFavourite(it, aniId) }
        setFavorite(movie, !current, knownCurrent = current)
        return !current
    }

    suspend fun update(
        id: Int,
        status: SyncStatus,
    ): Boolean {
        val ok = TrackerOutbox.submitStatus(TrackerSource.ANILIST, id.toString(), status) != SubmitResult.Rejected
        if (ok) {
            val hint = TrackerDetailCache.fresh(SyncSource.ANILIST, id)?.result
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
                    rewatching =
                        status.status?.let { status.isRewatching == true && it == SyncWatchType.WATCHING },
                    rewatchCount = status.rewatchCount,
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
        TrackerDetailCache.fresh(SyncSource.ANILIST, aniId)?.edit?.let { return it }
        val data =
            repo.withAuth { (repo.api as AniListApi).editData(it, aniId) }
                ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        cacheId(movieId, aniId)
        cacheTotal(aniId, data.totalEpisodes ?: 0)
        rememberOptions(data.options)
        return data
    }

    suspend fun saveEntry(
        id: Int,
        update: AniListEditUpdate,
    ): Boolean {
        val ok = TrackerOutbox.submitAniListEntry(id, update) != SubmitResult.Rejected
        if (ok) {
            val hint = TrackerDetailCache.fresh(SyncSource.ANILIST, id)?.result
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
                    rewatching = update.status?.let { it == "REPEATING" },
                    rewatchCount = update.repeat,
                )
            if (needsRefresh) scope.launch { AniListLibrary.refresh(force = true) }
        }
        return ok
    }

    suspend fun delete(id: Int) {
        TrackerOutbox.submitDelete(TrackerSource.ANILIST, id.toString())
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
        TrackerDetailCache.remove(SyncSource.ANILIST, id)
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

    fun preloaded(movieId: String?): SyncResult? = detailOf(movieId)?.result

    fun editPreloaded(movieId: String?): AniListEditData? = detailOf(movieId)?.edit

    /** Status favorit dari data detail yang sudah di-load (null = belum diketahui). */
    fun favoriteFor(movieId: String?): Boolean? = detailOf(movieId)?.favorite

    /** True kalau data detail anime ini masih segar di disk/memori, jadi buka detail nggak perlu request. */
    fun hasFresh(movieId: String?): Boolean = TrackerDetailCache.fresh(SyncSource.ANILIST, aniListIdFor(movieId)) != null

    @Volatile
    private var cooldownUntil = 0L

    // Kena 429 → berhenti preload sebentar, biar nggak nembak terus tiap buka detail
    private fun noteFailure(e: Throwable) {
        if ((e as? HttpException)?.code == 429) {
            cooldownUntil = SystemClock.elapsedRealtime() + RATE_LIMIT_COOLDOWN_MS
        }
    }

    /**
     * Ambil semua data AniList buat detail screen (stats, status list, data edit, favorit, id MAL)
     * dalam SATU request GraphQL lalu simpan ke disk. Kalau datanya masih segar (juga setelah
     * app ditutup & dibuka lagi), nggak ada request sama sekali.
     * [force] = abaikan cache (pull-to-refresh / abis edit status).
     */
    suspend fun preload(
        movie: Movie,
        force: Boolean = false,
    ) {
        val movieId = movie.id ?: return
        if (!force && hasFresh(movieId)) return
        if (SystemClock.elapsedRealtime() < cooldownUntil) return
        // Jalan di scope app biar request yang sudah dikirim tetap tuntas walau user keburu back,
        // dan buka detail berkali-kali pada anime yang sama nggak nembak dobel.
        val mine =
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    fetchDetail(movie, force)
                } finally {
                    inflight.remove(movieId)
                }
            }
        val running = inflight.putIfAbsent(movieId, mine)
        val job = running ?: mine.also { it.start() }
        if (running != null) mine.cancel()
        try {
            job.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            noteFailure(e)
        }
    }

    private suspend fun fetchDetail(
        movie: Movie,
        force: Boolean,
    ): TrackerDetail? {
        val movieId = movie.id ?: return null
        val api = repo.api as AniListApi
        val linked = aniListIdFor(movieId)
        val fetched: AniListDetail =
            if (linked != null) {
                repo.withAuth { api.loadDetail(it, linked) } ?: return null
            } else {
                if (movie.title == null) return null
                if (!force && notFound.get(movieId) == true) return null
                findDetailed(movie, movieId) ?: return null
            }
        val aniId = fetched.edit.mediaId
        val detail =
            TrackerDetail(
                trackerId = aniId,
                result = fetched.result,
                edit = fetched.edit,
                favorite = fetched.favorite,
                malId = fetched.malId,
                aniId = aniId,
            )
        TrackerDetailCache.put(SyncSource.ANILIST, detail)
        cacheId(movieId, aniId)
        cacheTotal(aniId, fetched.result.totalEpisodes ?: 0)
        rememberOptions(fetched.edit.options)
        // id lintas-situs sudah ketemu → AniZip / AniSkip / pemutar nggak perlu nyari lagi
        MalIdLookup.seed(movieId, MalIdLookup.Ids(fetched.malId, aniId))
        return detail
    }

    /** Cari + ambil detail dalam satu request per query: hasil search sudah bawa data lengkap tiap kandidat. */
    private suspend fun findDetailed(
        movie: Movie,
        movieId: String,
    ): AniListDetail? {
        val api = repo.api as AniListApi
        val found =
            TitleMatcher.match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q -> repo.withAuth { api.searchDetailed(it, q) } },
                idOf = { it.search.syncId },
                titlesOf = { listOf(it.search.name) + it.search.synonyms },
                typeOf = { it.search.type },
                yearOf = { it.search.year },
                wantedType = movie.type,
                wantedYear = TitleMatcher.yearOf(movie),
            )
        val hit =
            found.guess ?: run {
                notFound.put(movieId, true)
                return null
            }
        return hit.detail
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

    private fun rememberOptions(options: AniListEditOptions) {
        lastEditOptions = options
        prefs?.edit()?.putString("edit_options", gson.toJson(options))?.apply()
    }

    suspend fun resolve(movie: Movie): SyncResult? {
        val movieId = movie.id ?: return null
        aniListIdFor(movieId)?.let { id ->
            TrackerDetailCache.fresh(SyncSource.ANILIST, id)?.let { return it.result }
        }
        return fetchDetail(movie, force = false)?.result
    }

    private suspend fun findId(movie: Movie): Int? {
        val movieId = movie.id ?: return null
        aniListIdFor(movieId)?.let { return it }
        return fetchDetail(movie, force = false)?.trackerId
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
