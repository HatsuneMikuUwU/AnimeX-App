package com.uwu.animex.data.anilist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.toUserMessage
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
import com.uwu.animex.sync.providers.AniListEditUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** An anime on the user's AniList favourites (same data AniHyou's UserFavoritesAnime query returns). */
data class AniFavorite(
    val id: Int,
    val name: String,
    val posterUrl: String? = null,
    val synonyms: List<String>? = null,
)

/** One row of an AniList distribution (score, length, status, format, country, year). [key] is the raw AniList value. */
data class AniStat(
    val key: String? = null,
    val count: Int = 0,
    val minutes: Int = 0,
    val meanScore: Double = 0.0,
)

/** One entry of a top list (genre, tag, studio, staff, voice actor). */
data class AniRank(
    val name: String? = null,
    val image: String? = null,
    val count: Int = 0,
    val minutes: Int = 0,
    val meanScore: Double = 0.0,
)

/** Top list pre-sorted three ways, like the "titles / time / score" chips in AniHyou. */
data class AniRankings(
    val byCount: List<AniRank> = emptyList(),
    val byTime: List<AniRank> = emptyList(),
    val byScore: List<AniRank> = emptyList(),
)

/** Anime statistics, same data set as AniHyou's stats screen. Scores are raw AniList values. */
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
    /** Account creation time, unix seconds. */
    val createdAt: Long? = null,
    val stats: AniListStats? = null,
)

object AniList {
    val CLIENT_ID: String get() = AniListApi.CLIENT_ID
    val REDIRECT_URI: String get() = AniListApi.REDIRECT_URI
    val PROFILE_URL: String get() = AniListApi.PROFILE_URL

    private const val PREFS = "anilist"
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
    private val editCache = ConcurrentHashMap<String, AniListEditData>()
    private val mapType = object : TypeToken<HashMap<String, Int>>() {}.type

    /** Account-wide edit options (score format, advanced scoring, custom lists); same for every anime, so safe to prefill with. */
    @Volatile
    var lastEditOptions: AniListEditOptions? = null
        private set

    fun clearMessage() { _message.value = null }

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        AccountManager.init(app)
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        repo.onSessionExpired = { scope.launch { logout() } }
        _loggedIn.value = repo.authUser() != null
        _autoSync.value = p.getBoolean("auto_sync", true)
        _links.value = runCatching { gson.fromJson<HashMap<String, Int>>(p.getString("map", null), mapType) }.getOrNull().orEmpty()
        _user.value = runCatching { gson.fromJson(p.getString("user", null), AniListUser::class.java) }.getOrNull()
        _favorites.value =
            runCatching { gson.fromJson<List<AniFavorite>>(p.getString("favorites", null), favoritesType) }.getOrNull().orEmpty()
        _favoritesLoaded.value = p.contains("favorites")
        lastEditOptions =
            runCatching { gson.fromJson(p.getString("edit_options", null), AniListEditOptions::class.java) }.getOrNull()
        AniListLibrary.init(app)
        if (_loggedIn.value) {
            scope.launch { runCatching { refreshUser() } }
            scope.launch { runCatching { refreshFavorites() } }
        }
    }

    fun updateAutoSync(value: Boolean) {
        _autoSync.value = value
        prefs?.edit()?.putBoolean("auto_sync", value)?.apply()
    }

    fun startLogin(context: Context) {
        if (CLIENT_ID.isBlank()) {
            _message.value = "ANILIST_KEY belum diisi. Daftarkan aplikasi di anilist.co/settings/developer, lalu set env ANILIST_KEY atau anilist.key di local.properties."
            return
        }
        val page = repo.loginRequest() ?: run { _message.value = "Gagal menyiapkan login AniList"; return }
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
                    _message.value = "Gagal login AniList. Pastikan redirect URI di dashboard AniList adalah ${REDIRECT_URI}."
                    return@launch
                }
                _loggedIn.value = true
                // Enable auto-sync automatically on successful login (independent of MAL).
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
        prefs?.edit()?.remove("user")?.remove("map")?.remove("favorites")?.remove("edit_options")?.apply()
        lastEditOptions = null
        _loggedIn.value = false
        _favorites.value = emptyList()
        _favoritesLoaded.value = false
        _user.value = null
        _links.value = emptyMap()
        preloadCache.clear()
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

    /** Pulls the user's anime favourites from AniList (the source of truth when AniList is the sync source). */
    suspend fun refreshFavorites() {
        if (!_loggedIn.value) return
        saveFavorites(repo.withAuth { (repo.api as AniListApi).favorites(it) })
    }

    /** Adds/removes [movie] on the AniList favourites. AniList only has a toggle, so the API checks the current state first. */
    suspend fun setFavorite(movie: Movie, favorite: Boolean) {
        if (!_loggedIn.value) return
        val movieId = movie.id ?: return
        val aniId =
            aniListIdFor(movieId) ?: resolve(movie)?.id?.toIntOrNull()
                ?: throw IllegalStateException("Anime ini gak ketemu di AniList")
        repo.withAuth { (repo.api as AniListApi).setFavourite(it, aniId, favorite) }
        val current = _favorites.value
        saveFavorites(
            if (favorite) {
                if (current.any { it.id == aniId }) current else listOf(AniFavorite(aniId, movie.title.orEmpty())) + current
            } else {
                current.filterNot { it.id == aniId }
            },
        )
        scope.launch { runCatching { refreshFavorites() } }
    }

    suspend fun update(id: Int, status: SyncStatus): Boolean {
        val ok = repo.updateStatus(id.toString(), status).getOrThrow()
        if (ok) {
            preloadCache.entries.removeIf { it.value.id == id.toString() }
        editCache.entries.removeIf { it.value.mediaId == id }
            scope.launch { AniListLibrary.refresh(force = true) }
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
        editCache[movieId] = data
        rememberOptions(data.options)
        return data
    }

    suspend fun saveEntry(id: Int, update: AniListEditUpdate): Boolean {
        val ok = repo.withAuth { (repo.api as AniListApi).saveEntry(it, id, update) }
        if (ok) {
            preloadCache.entries.removeIf { it.value.id == id.toString() }
        editCache.entries.removeIf { it.value.mediaId == id }
            scope.launch { AniListLibrary.refresh(force = true) }
        }
        return ok
    }

    suspend fun delete(id: Int) {
        repo.removeStatus(id.toString()).getOrThrow()
        preloadCache.entries.removeIf { it.value.id == id.toString() }
        editCache.entries.removeIf { it.value.mediaId == id }
        scope.launch { AniListLibrary.refresh(force = true) }
    }

    private fun cacheId(movieId: String, aniListId: Int) {
        val next = HashMap(_links.value)
        next[movieId] = aniListId
        _links.value = next
        prefs?.edit()?.putString("map", gson.toJson(next))?.apply()
    }

    fun aniListIdFor(movieId: String?): Int? = movieId?.let { _links.value[it] }

    fun link(movieId: String, aniListId: Int) = cacheId(movieId, aniListId)

    fun movieIdFor(aniListId: Int): String? =
        _links.value.entries.lastOrNull { it.value == aniListId }?.key

    fun preloaded(movieId: String?): SyncResult? = movieId?.let { preloadCache[it] }

    fun editPreloaded(movieId: String?): AniListEditData? = movieId?.let { editCache[it] }

    /**
     * Same request count as [com.uwu.animex.data.mal.Mal.preload]: one media request, which here returns the [SyncResult]
     * and the full edit payload together (list entry, custom lists, advanced scores, score format...), so the status
     * sheet opens fully ready.
     */
    suspend fun preload(movie: Movie) {
        val movieId = movie.id ?: return
        val aniId = runCatching { findId(movie) }.getOrNull() ?: return
        val (result, edit) = runCatching { repo.withAuth { (repo.api as AniListApi).loadWithEdit(it, aniId) } }.getOrNull() ?: return
        preloadCache[movieId] = result
        editCache[movieId] = edit
        rememberOptions(edit.options)
    }

    private fun rememberOptions(options: AniListEditOptions) {
        lastEditOptions = options
        prefs?.edit()?.putString("edit_options", gson.toJson(options))?.apply()
    }

    private fun invalidatePreload(aniListId: Int) {
        preloadCache.entries.removeIf { it.value.id == aniListId.toString() }
    }

    /** Same normalization as MAL: letters+digits only, lowercased. */
    private fun normalize(value: String?) = value.orEmpty().lowercase().filter(Char::isLetterOrDigit)

    /**
     * Same query variants as MAL [com.uwu.animex.data.mal.Mal.titleQueries]:
     * raw title + cleaned (strip parentheses/brackets + "subtitle indonesia").
     */
    private fun titleQueries(title: String): List<String> {
        val noParen = title.replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        val clean = noParen.replace(Regex("(?i)subtitle indonesia|sub indo"), " ")
        return listOf(title, clean)
            .map { it.replace(Regex("""\s+"""), " ").trim().take(64) }
            .filter { it.length >= 3 }
            .distinct()
    }

    suspend fun resolve(movie: Movie): SyncResult? {
        val movieId = movie.id ?: return null
        aniListIdFor(movieId)?.let { id -> return repo.load(id.toString()).getOrThrow() }
        val id = findId(movie) ?: return null
        return repo.load(id.toString()).getOrThrow()
    }

    /** Searches AniList for [movie] and remembers the match. Only the search request; callers load the media themselves. */
    private suspend fun findId(movie: Movie): Int? {
        val movieId = movie.id ?: return null
        aniListIdFor(movieId)?.let { return it }
        val title = movie.title ?: return null
        // Prefer MAL-style cleaned title queries, then optional local synonyms.
        val queries =
            (
                titleQueries(title) +
                    movie.synonyms.orEmpty().split(',').map(String::trim).filter { it.length >= 3 }.take(5)
            ).distinct()
        val wantedNorms =
            setOf(normalize(title), normalize(titleQueries(title).getOrNull(1))).filter { it.length >= 3 }
        for (query in queries) {
            val found = repo.search(query).getOrThrow().orEmpty()
            if (found.isEmpty()) continue
            val match =
                found.firstOrNull { result ->
                    (listOf(result.name) + result.synonyms).any { normalize(it) in wantedNorms }
                } ?: found.first()
            val id = match.syncId.toIntOrNull() ?: continue
            cacheId(movieId, id)
            return id
        }
        return null
    }
}

object AniListTracker {
    /**
     * Independent AniList auto-sync. Only updates AniList when the user is logged in
     * and has auto-sync enabled. Does not depend on MAL or SyncSourcePref.
     */
    fun episodeWatched(epId: String) {
        val movie = History.items.value.firstOrNull { it.episode_id == epId } ?: return
        val episode = movie.episode_index?.trim()?.toIntOrNull() ?: return

        val local = Bookmarks.status(movie.id)
        if (local != WatchStatus.COMPLETED) {
            Bookmarks.setStatus(movie, WatchStatus.WATCHING)
        }

        if (!AniList.loggedIn.value || !AniList.autoSync.value) return

        AniList.scope.launch {
            runCatching {
                val anime = AniList.resolve(movie) ?: return@launch
                val current = anime.myStatus
                val watched = current?.watchedEpisodes ?: 0
                if (episode <= watched && current?.status == SyncWatchType.WATCHING) return@launch
                val total = anime.totalEpisodes ?: 0
                val newWatched = maxOf(watched, episode)
                val complete = total > 0 && newWatched >= total
                AniList.update(
                    anime.id.toIntOrNull() ?: return@launch,
                    SyncStatus(
                        status = if (complete) SyncWatchType.COMPLETED else SyncWatchType.WATCHING,
                        watchedEpisodes = newWatched,
                        score = current?.score,
                    ),
                )
                if (complete) Bookmarks.setStatus(movie, WatchStatus.COMPLETED)
            }
        }
    }

    /** Convenience overload for any remaining direct callers. */
    fun episodeWatched(movie: Movie, episode: Int) {
        if (!AniList.loggedIn.value || !AniList.autoSync.value) return
        AniList.scope.launch {
            runCatching {
                val anime = AniList.resolve(movie) ?: return@launch
                val current = anime.myStatus
                val watched = current?.watchedEpisodes ?: 0
                if (episode <= watched && current?.status == SyncWatchType.WATCHING) return@launch
                val total = anime.totalEpisodes ?: 0
                val newWatched = maxOf(watched, episode)
                val complete = total > 0 && newWatched >= total
                AniList.update(
                    anime.id.toIntOrNull() ?: return@launch,
                    SyncStatus(
                        status = if (complete) SyncWatchType.COMPLETED else SyncWatchType.WATCHING,
                        watchedEpisodes = newWatched,
                        score = current?.score,
                    ),
                )
                if (complete) Bookmarks.setStatus(movie, WatchStatus.COMPLETED)
            }
        }
    }
}
