package com.uwu.animex.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ---------- Model ----------

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
)

data class MalAltTitles(val en: String? = null, val ja: String? = null, val synonyms: List<String>? = null)

data class MalAnime(
    val id: Int = 0,
    val title: String? = null,
    val alternative_titles: MalAltTitles? = null,
    val num_episodes: Int? = null,
    val my_list_status: MalListStatus? = null,
)

/** Semua field opsional: hanya yang non-null yang dikirim ke MAL. */
data class MalUpdate(
    val status: String? = null,
    val score: Int? = null,
    val watched: Int? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val isRewatching: Boolean? = null,
    val rewatchCount: Int? = null,
    val rewatchValue: Int? = null,
    val priority: Int? = null,
    val tags: String? = null,
    val comments: String? = null,
)

private data class TokenResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
)

private data class SearchResponse(val data: List<SearchItem>? = null)
private data class SearchItem(val node: MalAnime? = null)

val WatchStatus.malValue: String
    get() = when (this) {
        WatchStatus.WATCHING -> "watching"
        WatchStatus.COMPLETED -> "completed"
        WatchStatus.ON_HOLD -> "on_hold"
        WatchStatus.DROPPED -> "dropped"
        WatchStatus.PLAN_TO_WATCH -> "plan_to_watch"
    }

fun watchStatusFromMal(value: String?): WatchStatus? = WatchStatus.entries.firstOrNull { it.malValue == value }

// ---------- Auth + API ----------

object Mal {
    /**
     * WAJIB diganti dengan Client ID milik sendiri.
     * Daftar di https://myanimelist.net/apiconfig -> Create ID
     *   App Type      : other
     *   Redirect URL  : animex://mal-auth
     */
    const val CLIENT_ID = "GANTI_DENGAN_CLIENT_ID_MAL"
    const val REDIRECT_URI = "animex://mal-auth"
    const val PROFILE_URL = "https://myanimelist.net/profile/"

    private const val AUTH_URL = "https://myanimelist.net/v1/oauth2/authorize"
    private const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    private const val API = "https://api.myanimelist.net/v2"
    private const val PREFS = "mal"

    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private var prefs: SharedPreferences? = null

    var loggedIn by mutableStateOf(false)
        private set
    var user by mutableStateOf<MalUser?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** Sinkron otomatis progress ke MAL saat episode selesai ditonton. */
    var autoSync by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        loggedIn = p.getString("refresh", null) != null
        autoSync = p.getBoolean("auto_sync", true)
        user = runCatching { gson.fromJson(p.getString("user", null), MalUser::class.java) }.getOrNull()
        if (loggedIn) scope.launch { runCatching { refreshUser() } }
    }

    fun setAutoSync(value: Boolean) {
        autoSync = value
        prefs?.edit()?.putBoolean("auto_sync", value)?.apply()
    }

    // ----- Login (OAuth2 + PKCE, method "plain" seperti yang diwajibkan MAL) -----

    fun startLogin(context: Context) {
        if (CLIENT_ID.startsWith("GANTI")) {
            message = "Client ID MAL belum diisi (lihat Mal.kt)"
            return
        }
        val verifier = randomString()
        val state = randomString()
        prefs?.edit()?.putString("verifier", verifier)?.putString("state", state)?.apply()
        val url = AUTH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("code_challenge", verifier)
            .addQueryParameter("code_challenge_method", "plain")
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isRedirect(uri: Uri?): Boolean = uri != null && uri.scheme == "animex" && uri.host == "mal-auth"

    fun handleRedirect(uri: Uri) {
        val p = prefs ?: return
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val verifier = p.getString("verifier", null)
        if (code == null || verifier == null || state != p.getString("state", null)) {
            message = uri.getQueryParameter("error")?.let { "Login dibatalkan ($it)" } ?: "Login MAL gagal"
            return
        }
        p.edit().remove("verifier").remove("state").apply()
        scope.launch {
            busy = true
            try {
                val body = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", verifier)
                    .build()
                saveTokens(tokenRequest(body))
                withContext(Dispatchers.Main) { loggedIn = true }
                refreshUser()
            } catch (e: Exception) {
                message = "Login MAL gagal: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun logout() {
        prefs?.edit()?.remove("access")?.remove("refresh")?.remove("expires")?.remove("user")?.remove("map")?.apply()
        loggedIn = false
        user = null
    }

    private fun randomString(): String {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun tokenRequest(body: RequestBody): TokenResponse =
        http.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { r ->
            val text = r.body.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            gson.fromJson(text, TokenResponse::class.java)
        }

    private fun saveTokens(t: TokenResponse) {
        val access = t.access_token ?: error("token kosong")
        prefs?.edit()
            ?.putString("access", access)
            ?.putString("refresh", t.refresh_token ?: prefs?.getString("refresh", null))
            ?.putLong("expires", System.currentTimeMillis() + (t.expires_in ?: 3600L) * 1000L)
            ?.apply()
    }

    private suspend fun refreshToken(force: Boolean = false): String? = refreshLock.withLock {
        val p = prefs ?: return null
        val access = p.getString("access", null)
        val fresh = System.currentTimeMillis() < p.getLong("expires", 0L) - 60_000L
        if (access != null && fresh && !force) return access
        val refresh = p.getString("refresh", null) ?: return null
        try {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .build()
            withContext(Dispatchers.IO) { saveTokens(tokenRequest(body)) }
            p.getString("access", null)
        } catch (e: Exception) {
            // refresh token ditolak -> sesi berakhir
            if (e.message?.contains("400") == true || e.message?.contains("401") == true) {
                withContext(Dispatchers.Main) { logout() }
            }
            null
        }
    }

    // ----- HTTP dengan bearer token + retry sekali saat 401 -----

    private suspend fun call(build: (Request.Builder) -> Request.Builder): String = withContext(Dispatchers.IO) {
        var token = refreshToken() ?: error("Belum login")
        repeat(2) { attempt ->
            val req = build(Request.Builder()).header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { r ->
                val text = r.body.string().orEmpty()
                if (r.code == 401 && attempt == 0) {
                    token = refreshToken(force = true) ?: error("Sesi MAL berakhir, login ulang")
                } else {
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    return@withContext text
                }
            }
        }
        error("Gagal terhubung ke MAL")
    }

    // ----- Endpoint -----

    suspend fun refreshUser(): MalUser {
        val url = "$API/users/@me".toHttpUrl().newBuilder().addQueryParameter("fields", "anime_statistics").build()
        val u = gson.fromJson(call { it.url(url) }, MalUser::class.java)
        withContext(Dispatchers.Main) { user = u }
        prefs?.edit()?.putString("user", gson.toJson(u))?.apply()
        return u
    }

    private const val ANIME_FIELDS =
        "num_episodes,alternative_titles,my_list_status{start_date,finish_date,num_times_rewatched,is_rewatching,rewatch_value,priority,tags,comments}"

    suspend fun anime(id: Int): MalAnime {
        val url = "$API/anime/$id".toHttpUrl().newBuilder().addQueryParameter("fields", ANIME_FIELDS).build()
        return gson.fromJson(call { it.url(url) }, MalAnime::class.java)
    }

    private suspend fun search(q: String): List<MalAnime> {
        val url = "$API/anime".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("limit", "8")
            .addQueryParameter("fields", "alternative_titles")
            .build()
        return gson.fromJson(call { it.url(url) }, SearchResponse::class.java)
            .data.orEmpty().mapNotNull { it.node }
    }

    suspend fun update(malId: Int, u: MalUpdate): MalListStatus {
        val body = FormBody.Builder().apply {
            u.status?.let { add("status", it) }
            u.score?.let { add("score", it.toString()) }
            u.watched?.let { add("num_watched_episodes", it.toString()) }
            u.startDate?.let { add("start_date", it) }
            u.finishDate?.let { add("finish_date", it) }
            u.isRewatching?.let { add("is_rewatching", it.toString()) }
            u.rewatchCount?.let { add("num_times_rewatched", it.toString()) }
            u.rewatchValue?.let { add("rewatch_value", it.toString()) }
            u.priority?.let { add("priority", it.toString()) }
            u.tags?.let { add("tags", it) }
            u.comments?.let { add("comments", it) }
        }.build()
        val text = call { it.url("$API/anime/$malId/my_list_status").patch(body) }
        return gson.fromJson(text, MalListStatus::class.java)
    }

    suspend fun delete(malId: Int) {
        call { it.url("$API/anime/$malId/my_list_status").delete() }
    }

    // ----- Pemetaan anime AnimeX -> anime MAL (berdasarkan judul, hasil di-cache) -----

    private val mapType = object : TypeToken<HashMap<String, Int>>() {}.type

    private fun cachedId(movieId: String): Int? = runCatching {
        gson.fromJson<HashMap<String, Int>>(prefs?.getString("map", null), mapType)?.get(movieId)
    }.getOrNull()

    private fun cacheId(movieId: String, malId: Int) {
        val m = runCatching { gson.fromJson<HashMap<String, Int>>(prefs?.getString("map", null), mapType) }
            .getOrNull() ?: HashMap()
        m[movieId] = malId
        prefs?.edit()?.putString("map", gson.toJson(m))?.apply()
    }

    private fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private fun titleQueries(title: String): List<String> {
        val noParen = title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        val clean = noParen.replace(Regex("(?i)subtitle indonesia|sub indo"), " ")
        return listOf(title, clean)
            .map { it.replace(Regex("\\s+"), " ").trim().take(64) }
            .filter { it.length >= 3 }
            .distinct()
    }

    /** Cari anime MAL yang cocok dengan [movie]; null kalau tidak ketemu. */
    suspend fun resolve(movie: Movie): MalAnime? {
        val movieId = movie.id ?: return null
        cachedId(movieId)?.let { return anime(it) }
        val title = movie.title ?: return null
        for (q in titleQueries(title)) {
            val results = search(q)
            if (results.isEmpty()) continue
            val n = norm(title)
            val hit = results.firstOrNull { r ->
                val names = listOf(r.title, r.alternative_titles?.en, r.alternative_titles?.ja) +
                    r.alternative_titles?.synonyms.orEmpty()
                names.any { norm(it) == n }
            } ?: results.first()
            cacheId(movieId, hit.id)
            return anime(hit.id)
        }
        return null
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

// ---------- Auto tracker ----------

object MalTracker {
    /** Dipanggil saat sebuah episode pertama kali melewati ambang "selesai ditonton". */
    fun episodeWatched(epId: String) {
        if (!Mal.loggedIn || !Mal.autoSync) return
        val movie = History.items.firstOrNull { it.episode_id == epId } ?: return
        val ep = movie.episode_index?.trim()?.toIntOrNull() ?: return
        Mal.scope.launch { runCatching { sync(movie, ep) } }
    }

    private suspend fun sync(movie: Movie, ep: Int) {
        val anime = Mal.resolve(movie) ?: return
        val cur = anime.my_list_status
        val watched = cur?.num_episodes_watched ?: 0
        if (cur?.status == "completed" && ep <= watched) return
        if (ep <= watched && cur?.status == "watching") return

        val total = anime.num_episodes ?: 0
        val newWatched = maxOf(ep, watched)
        val done = total > 0 && newWatched >= total
        val status = if (done) "completed" else "watching"
        val res = Mal.update(
            anime.id,
            MalUpdate(
                status = status,
                watched = newWatched,
                startDate = if (cur?.start_date == null) Mal.today() else null,
                finishDate = if (done && cur?.finish_date == null) Mal.today() else null,
            ),
        )
        withContext(Dispatchers.Main) {
            Bookmarks.setStatus(movie, watchStatusFromMal(res.status ?: status))
        }
    }
}
