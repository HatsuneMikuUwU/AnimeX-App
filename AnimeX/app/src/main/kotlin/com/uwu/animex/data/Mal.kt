package com.uwu.animex.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.sync.providers.MALApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MalStats(
    val num_items_watching: Int? = null,
    val num_items_completed: Int? = null,
    val num_items_on_hold: Int? = null,
    val num_items_dropped: Int? = null,
    val num_items_plan_to_watch: Int? = null,
    val num_items: Int? = null,
    val num_days: Double? = null,
    val num_episodes: Int? = null,
    val num_times_rewatched: Int? = null,
    val mean_score: Double? = null,
)

data class MalUser(
    val id: Long? = null,
    val name: String? = null,
    val picture: String? = null,
    val location: String? = null,
    val birthday: String? = null,
    val joined_at: String? = null,
    val anime_statistics: MalStats? = null,
)

val WatchStatus.malValue: String
    get() = when (this) {
        WatchStatus.WATCHING -> "watching"
        WatchStatus.COMPLETED -> "completed"
        WatchStatus.ON_HOLD -> "on_hold"
        WatchStatus.DROPPED -> "dropped"
        WatchStatus.PLAN_TO_WATCH -> "plan_to_watch"
    }

fun watchStatusFromMal(value: String?): WatchStatus? = WatchStatus.entries.firstOrNull { it.malValue == value }

object Mal {
    val CLIENT_ID: String get() = MALApi.CLIENT_ID
    val REDIRECT_URI: String get() = MALApi.REDIRECT_URI
    val PROFILE_URL: String get() = MALApi.PROFILE_URL

    private const val PREFS = "mal"

    private val gson = Gson()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val repo get() = AccountManager.malApi
    private val api get() = repo.api as MALApi

    var loggedIn by mutableStateOf(false)
        private set
    var user by mutableStateOf<MalUser?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    var autoSync by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        AccountManager.init(app)
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        repo.onSessionExpired = { scope.launch(Dispatchers.Main) { logout() } }
        loggedIn = repo.authUser() != null
        autoSync = p.getBoolean("auto_sync", true)
        user = runCatching { gson.fromJson(p.getString("user", null), MalUser::class.java) }.getOrNull()
        MalLibrary.init(app)
        if (loggedIn) scope.launch { runCatching { refreshUser() }; MalLibrary.refresh() }
    }

    fun updateAutoSync(value: Boolean) {
        autoSync = value
        prefs?.edit()?.putBoolean("auto_sync", value)?.apply()
    }

    fun startLogin(context: Context) {
        if (CLIENT_ID.isBlank()) {
            message = "MAL_KEY belum diisi (env MAL_KEY atau mal.key di local.properties)"
            return
        }
        val page = repo.loginRequest() ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isRedirect(uri: Uri?): Boolean = uri != null && repo.isValidRedirectUrl(uri.toString())

    fun handleRedirect(uri: Uri) {
        val denied = uri.getQueryParameter("error")
        if (denied != null) {
            message = "Login dibatalkan ($denied)"
            return
        }
        scope.launch {
            busy = true
            try {
                if (!repo.login(uri.toString())) {
                    message = "Login MAL gagal"
                    return@launch
                }
                withContext(Dispatchers.Main) { loggedIn = true }
                refreshUser()
                MalLibrary.refresh(force = true)
            } catch (e: Exception) {
                message = "Login MAL gagal: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun logout() {
        repo.logout()
        prefs?.edit()?.remove("user")?.remove("map")?.remove("totals")?.apply()
        loggedIn = false
        user = null
        preloadCache.clear()
        MalLibrary.clear()
    }

    suspend fun refreshUser(): MalUser {
        val u = repo.withAuth { api.profile(it) }
        withContext(Dispatchers.Main) { user = u }
        prefs?.edit()?.putString("user", gson.toJson(u))?.apply()
        return u
    }

    suspend fun update(malId: Int, s: SyncStatus): Boolean {
        val ok = repo.updateStatus(malId.toString(), s).getOrThrow()
        if (ok) {
            invalidatePreload(malId)
            MalLibrary.patch(malId, s)
        }
        return ok
    }

    suspend fun delete(malId: Int) {
        repo.removeStatus(malId.toString()).getOrThrow()
        invalidatePreload(malId)
        MalLibrary.remove(malId)
    }

    private val mapType = object : TypeToken<HashMap<String, Int>>() {}.type

    private fun readMap(): HashMap<String, Int>? = runCatching {
        gson.fromJson<HashMap<String, Int>>(prefs?.getString("map", null), mapType)
    }.getOrNull()

    private fun cachedId(movieId: String): Int? = readMap()?.get(movieId)

    private fun cacheId(movieId: String, malId: Int) {
        val m = readMap() ?: HashMap()
        m[movieId] = malId
        prefs?.edit()?.putString("map", gson.toJson(m))?.apply()
    }

    fun link(movieId: String, malId: Int) = cacheId(movieId, malId)

    private fun readTotals(): HashMap<String, Int>? = runCatching {
        gson.fromJson<HashMap<String, Int>>(prefs?.getString("totals", null), mapType)
    }.getOrNull()

    fun cachedTotal(malId: Int): Int? = readTotals()?.get(malId.toString())

    private fun cacheTotal(malId: Int, total: Int) {
        val m = readTotals() ?: HashMap()
        if (m[malId.toString()] == total) return
        m[malId.toString()] = total
        prefs?.edit()?.putString("totals", gson.toJson(m))?.apply()
    }

    private suspend fun loadCached(id: String): SyncResult? {
        val r = repo.load(id).getOrThrow()
        val malId = r?.id?.toIntOrNull()

        if (malId != null) cacheTotal(malId, r.totalEpisodes ?: 0)
        return r
    }

    fun movieIdFor(malId: Int): String? = readMap()?.entries?.lastOrNull { it.value == malId }?.key

    fun malIdFor(movieId: String?): Int? = movieId?.let { cachedId(it) }

    private fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private fun titleQueries(title: String): List<String> {
        val noParen = title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        val clean = noParen.replace(Regex("(?i)subtitle indonesia|sub indo"), " ")
        return listOf(title, clean)
            .map { it.replace(Regex("\\s+"), " ").trim().take(64) }
            .filter { it.length >= 3 }
            .distinct()
    }

    private val preloadCache = java.util.concurrent.ConcurrentHashMap<String, SyncResult>()

    fun preloaded(movieId: String?): SyncResult? = movieId?.let { preloadCache[it] }

    suspend fun preload(movie: Movie) {
        val movieId = movie.id ?: return
        val r = runCatching { resolve(movie) }.getOrNull() ?: return
        preloadCache[movieId] = r
    }

    private fun invalidatePreload(malId: Int) {
        preloadCache.entries.removeIf { it.value.id == malId.toString() }
    }

    suspend fun resolve(movie: Movie): SyncResult? {
        val movieId = movie.id ?: return null
        cachedId(movieId)?.let { return loadCached(it.toString()) }
        val title = movie.title ?: return null
        for (q in titleQueries(title)) {
            val results = repo.search(q).getOrThrow().orEmpty()
            if (results.isEmpty()) continue
            val n = norm(title)
            val hit = results.firstOrNull { r -> (listOf(r.name) + r.synonyms).any { norm(it) == n } } ?: results.first()
            hit.syncId.toIntOrNull()?.let { cacheId(movieId, it) }
            return loadCached(hit.syncId)
        }
        return null
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

object MalTracker {
    fun episodeWatched(epId: String) {
        if (!Mal.loggedIn || !Mal.autoSync) return
        val movie = History.items.firstOrNull { it.episode_id == epId } ?: return
        val ep = movie.episode_index?.trim()?.toIntOrNull() ?: return
        Mal.scope.launch { runCatching { sync(movie, ep) } }
    }

    private suspend fun sync(movie: Movie, ep: Int) {
        val anime = Mal.resolve(movie) ?: return
        val malId = anime.id.toIntOrNull() ?: return
        val cur = anime.myStatus
        val watched = cur?.watchedEpisodes ?: 0
        if (cur?.status == SyncWatchType.COMPLETED && ep <= watched) return
        if (ep <= watched && cur?.status == SyncWatchType.WATCHING) return

        val total = anime.totalEpisodes ?: 0
        val newWatched = maxOf(ep, watched)
        val done = total > 0 && newWatched >= total
        val status = if (done) SyncWatchType.COMPLETED else SyncWatchType.WATCHING
        Mal.update(
            malId,
            SyncStatus(
                status = status,
                watchedEpisodes = newWatched,
                startDate = if (cur?.startDate == null) Mal.today() else null,
                finishDate = if (done && cur?.finishDate == null) Mal.today() else null,
            ),
        )
    }
}
