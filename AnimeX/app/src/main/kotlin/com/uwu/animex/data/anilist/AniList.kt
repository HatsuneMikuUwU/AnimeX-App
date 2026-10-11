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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
    private val preloadCache = ConcurrentHashMap<String, SyncResult>()
    private val mapType = object : TypeToken<HashMap<String, Int>>() {}.type

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
        if (_loggedIn.value) scope.launch { runCatching { refreshUser() } }
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
                refreshUser()
            } catch (e: Exception) {
                _message.value = "Gagal login AniList: ${e.toUserMessage()}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun logout() {
        repo.logout()
        prefs?.edit()?.remove("user")?.remove("map")?.apply()
        _loggedIn.value = false
        _user.value = null
        _links.value = emptyMap()
        preloadCache.clear()
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

    suspend fun update(id: Int, status: SyncStatus): Boolean {
        val ok = repo.updateStatus(id.toString(), status).getOrThrow()
        if (ok) preloadCache.entries.removeIf { it.value.id == id.toString() }
        return ok
    }

    suspend fun delete(id: Int) {
        repo.removeStatus(id.toString()).getOrThrow()
        preloadCache.entries.removeIf { it.value.id == id.toString() }
    }

    private fun cacheId(movieId: String, aniListId: Int) {
        val next = HashMap(_links.value)
        next[movieId] = aniListId
        _links.value = next
        prefs?.edit()?.putString("map", gson.toJson(next))?.apply()
    }

    fun aniListIdFor(movieId: String?): Int? = movieId?.let { _links.value[it] }

    fun preloaded(movieId: String?): SyncResult? = movieId?.let { preloadCache[it] }

    suspend fun preload(movie: Movie) {
        val id = movie.id ?: return
        resolve(movie)?.let { preloadCache[id] = it }
    }

    suspend fun resolve(movie: Movie): SyncResult? {
        val movieId = movie.id ?: return null
        aniListIdFor(movieId)?.let { id -> return repo.load(id.toString()).getOrThrow() }
        val candidates = buildList {
            movie.title?.takeIf { it.isNotBlank() }?.let(::add)
            movie.synonyms.orEmpty().split(',').map(String::trim).filter { it.length >= 3 }.take(5).forEach(::add)
        }.distinct()
        for (query in candidates) {
            val found = repo.search(query).getOrThrow().orEmpty()
            if (found.isEmpty()) continue
            val wanted = normalize(movie.title)
            val match = found.firstOrNull { result -> (listOf(result.name) + result.synonyms).any { normalize(it) == wanted } } ?: found.first()
            val id = match.syncId.toIntOrNull() ?: continue
            cacheId(movieId, id)
            return repo.load(id.toString()).getOrThrow()
        }
        return null
    }

    private fun normalize(value: String?) = value.orEmpty().lowercase().filter(Char::isLetterOrDigit)
}

object AniListTracker {
    fun episodeWatched(movie: Movie, episode: Int) {
        if (!AniList.loggedIn.value || !AniList.autoSync.value) return
        AniList.scope.launch {
            runCatching {
                val anime = AniList.resolve(movie) ?: return@launch
                val current = anime.myStatus
                val watched = current?.watchedEpisodes ?: 0
                if (episode <= watched) return@launch
                val total = anime.totalEpisodes ?: 0
                val complete = total > 0 && episode >= total
                AniList.update(
                    anime.id.toIntOrNull() ?: return@launch,
                    SyncStatus(
                        status = if (complete) SyncWatchType.COMPLETED else SyncWatchType.WATCHING,
                        watchedEpisodes = maxOf(watched, episode),
                        score = current?.score,
                    ),
                )
                if (complete) Bookmarks.setStatus(movie, WatchStatus.COMPLETED)
            }
        }
    }
}
