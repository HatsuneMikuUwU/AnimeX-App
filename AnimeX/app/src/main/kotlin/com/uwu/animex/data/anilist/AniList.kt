package com.uwu.animex.data.anilist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.AuthUser
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AniListUser(
    val id: Int? = null,
    val name: String? = null,
    val avatar: String? = null,
)

object AniList {
    val CLIENT_ID: String get() = AniListApi.CLIENT_ID
    val REDIRECT_URI: String get() = AniListApi.REDIRECT_URI
    val PROFILE_URL: String get() = AniListApi.PROFILE_URL

    private const val PREFS = "anilist"

    private val gson = Gson()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val repo get() = AccountManager.anilistApi

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _user = MutableStateFlow<AniListUser?>(null)
    val user: StateFlow<AniListUser?> = _user.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _links = MutableStateFlow<Map<String, Int>>(emptyMap())
    val links: StateFlow<Map<String, Int>> = _links.asStateFlow()

    private val _autoSync = MutableStateFlow(true)
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    private val preloadCache = HashMap<String, SyncResult>()

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
        _links.value = readMap().orEmpty()
        _user.value =
            runCatching { gson.fromJson(p.getString("user", null), AniListUser::class.java) }.getOrNull()
        AniListLibrary.init(app)
        if (_loggedIn.value) {
            scope.launch {
                runCatching { refreshUser() }
                AniListLibrary.refresh()
            }
        }
    }

    fun updateAutoSync(value: Boolean) {
        _autoSync.value = value
        prefs?.edit()?.putBoolean("auto_sync", value)?.apply()
    }

    fun startLogin(context: Context) {
        if (CLIENT_ID.isBlank()) {
            _message.value = "ANILIST_CLIENT_ID belum diisi (env ANILIST_CLIENT_ID atau anilist.client.id di local.properties)"
            return
        }
        val page = repo.loginRequest() ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(page.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun isRedirect(uri: Uri?): Boolean = uri != null && repo.isValidRedirectUrl(uri.toString())

    fun handleRedirect(uri: Uri) {
        val denied = uri.getQueryParameter("error") ?: uri.fragment?.let { frag ->
            frag.split("&").firstOrNull { it.startsWith("error=") }?.substringAfter("=")
        }
        if (denied != null) {
            _message.value = "Login AniList dibatalkan ($denied)"
            return
        }
        scope.launch {
            _busy.value = true
            try {
                if (!repo.login(uri.toString())) {
                    _message.value = "Gagal login AniList"
                    return@launch
                }
                _loggedIn.value = true
                refreshUser()
                AniListLibrary.refresh(force = true)
                _message.value = "Berhasil login AniList"
            } catch (e: Exception) {
                _message.value = e.toUserMessage()
            } finally {
                _busy.value = false
            }
        }
    }

    fun logout() {
        repo.logout()
        _loggedIn.value = false
        _user.value = null
        prefs?.edit()?.remove("user")?.apply()
        preloadCache.clear()
        AniListLibrary.clear()
        _message.value = "Logout AniList"
    }

    suspend fun refreshUser(): AniListUser {
        val authUser = repo.withAuth { it.user }
        val u =
            AniListUser(
                id = authUser.id,
                name = authUser.name,
                avatar = authUser.profilePicture,
            )
        _user.value = u
        prefs?.edit()?.putString("user", gson.toJson(u))?.apply()
        _loggedIn.value = true
        return u
    }

    suspend fun update(
        alId: Int,
        status: SyncStatus,
        hint: SyncResult? = null,
    ) {
        repo.updateStatus(alId.toString(), status).getOrThrow()
        hint?.let { preloadCache[it.id] = it.copy(myStatus = status) }
        AniListLibrary.patch(alId, status, hint)
        invalidatePreload(alId)
    }

    suspend fun delete(alId: Int) {
        repo.removeStatus(alId.toString()).getOrThrow()
        AniListLibrary.remove(alId)
        invalidatePreload(alId)
    }

    private fun readMap(): HashMap<String, Int>? =
        runCatching {
            gson.fromJson<HashMap<String, Int>>(
                prefs?.getString("links", null),
                object : TypeToken<HashMap<String, Int>>() {}.type,
            )
        }.getOrNull()

    private fun cachedId(movieId: String): Int? = _links.value[movieId]

    private fun cacheId(
        movieId: String,
        alId: Int,
    ) {
        val next = _links.value.toMutableMap().apply { put(movieId, alId) }
        _links.value = next
        prefs?.edit()?.putString("links", gson.toJson(next))?.apply()
    }

    fun link(
        movieId: String,
        alId: Int,
    ) = cacheId(movieId, alId)

    fun alIdFor(movieId: String?): Int? = movieId?.let { cachedId(it) }

    fun movieIdFor(alId: Int): String? = _links.value.entries.firstOrNull { it.value == alId }?.key

    private fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private fun titleQueries(title: String): List<String> {
        val t = title.trim()
        if (t.isEmpty()) return emptyList()
        return listOf(t, t.substringBefore(':').trim(), t.substringBefore('(').trim())
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun preloaded(movieId: String?): SyncResult? = movieId?.let { preloadCache[it] }

    suspend fun preload(movie: Movie) {
        if (!_loggedIn.value) return
        val id = movie.id ?: return
        if (preloadCache.containsKey(id)) return
        runCatching { resolve(movie) }
    }

    private fun invalidatePreload(alId: Int) {
        preloadCache.entries.removeAll { it.value.id == alId.toString() }
    }

    suspend fun resolve(movie: Movie): SyncResult? {
        val movieId = movie.id ?: return null
        cachedId(movieId)?.let { id ->
            return repo.load(id.toString()).getOrNull()?.also { preloadCache[movieId] = it }
        }
        val titles =
            titleQueries(movie.title.orEmpty()) +
                movie.synonyms
                    .orEmpty()
                    .split(',', ';')
                    .map { it.trim() }
        for (q in titles.filter { it.isNotBlank() }.distinct()) {
            val results = repo.search(q).getOrNull().orEmpty()
            val nTitle = norm(movie.title)
            val match =
                results.firstOrNull { r ->
                    norm(r.name) == nTitle ||
                        r.synonyms.any { norm(it) == nTitle }
                } ?: results.firstOrNull()
            if (match != null) {
                val alId = match.syncId.toIntOrNull() ?: continue
                cacheId(movieId, alId)
                val loaded = repo.load(match.syncId).getOrNull() ?: continue
                preloadCache[movieId] = loaded
                return loaded
            }
        }
        return null
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Push status when an episode is watched (if auto-sync on). */
    fun episodeWatched(
        movie: Movie,
        ep: Int?,
    ) {
        if (!_loggedIn.value || !_autoSync.value) return
        if (ep == null) return
        scope.launch {
            runCatching {
                val anime = resolve(movie) ?: return@runCatching
                val alId = anime.id.toIntOrNull() ?: return@runCatching
                val cur = anime.myStatus
                val watched = cur?.watchedEpisodes ?: 0
                if (ep <= watched && cur?.status == SyncWatchType.WATCHING) return@runCatching
                val total = anime.totalEpisodes ?: 0
                val newWatched = maxOf(ep, watched)
                val done = total > 0 && newWatched >= total
                val status = if (done) SyncWatchType.COMPLETED else SyncWatchType.WATCHING
                update(
                    alId,
                    SyncStatus(
                        status = status,
                        watchedEpisodes = newWatched,
                        startDate = if (cur?.startDate == null) today() else null,
                        finishDate = if (done && cur?.finishDate == null) today() else null,
                    ),
                    hint = anime,
                )
                if (done) Bookmarks.setStatus(movie, WatchStatus.COMPLETED)
            }
        }
    }
}
