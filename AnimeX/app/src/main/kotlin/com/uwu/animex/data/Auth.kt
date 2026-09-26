package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Session & auth (mirip AnimeIn SessionManager).
 * Endpoint: auth/login, auth/register, auth/token, 3/2/user/profile/data
 */
object Auth {
    private const val PREFS = "auth_session"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USER = "user_json"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null
    private var deviceId: String = ""

    var token: String? by mutableStateOf(null)
        private set
    var username: String? by mutableStateOf(null)
        private set
    var email: String? by mutableStateOf(null)
        private set
    var user: UserProfile? by mutableStateOf(null)
        private set

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "animex-${System.currentTimeMillis()}"
        token = p.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        username = p.getString(KEY_USERNAME, null)
        email = p.getString(KEY_EMAIL, null)
        user = runCatching {
            gson.fromJson(p.getString(KEY_USER, null), UserProfile::class.java)
        }.getOrNull()
    }

    private fun persist() {
        prefs?.edit()?.apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USERNAME, username)
            putString(KEY_EMAIL, email)
            putString(KEY_USER, user?.let { gson.toJson(it) })
            apply()
        }
    }

    fun logout() {
        token = null
        username = null
        email = null
        user = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun applySession(accessToken: String, profile: UserProfile?) {
        token = accessToken
        user = profile
        username = profile?.username ?: profile?.name
        email = profile?.email
        persist()
    }

    private suspend fun postJson(path: String, body: Map<String, Any?>): JsonObject =
        withContext(Dispatchers.IO) {
            Api.ensureBasePublic()
            val url = Api.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
            val json = gson.toJson(body)
            val req = Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", "okhttp/4.12.0")
                .header("Content-Type", "application/json")
                .apply {
                    token?.let { header("Authorization", "Bearer $it") }
                }
                .build()
            Api.httpPublic.newCall(req).execute().use { r ->
                val text = r.body.string().orEmpty()
                if (!r.isSuccessful) {
                    val msg = runCatching {
                        JsonParser.parseString(text).asJsonObject.get("message")?.asString
                    }.getOrNull()
                    error(msg ?: "HTTP ${r.code}")
                }
                JsonParser.parseString(text).asJsonObject
            }
        }

    private fun parseUser(data: JsonObject?): UserProfile? {
        if (data == null) return null
        // data can be { user: {...} } or the user object itself
        val u = data.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: data.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: data
        return runCatching { gson.fromJson(u, UserProfile::class.java) }.getOrNull()
    }

    private fun extractToken(root: JsonObject, data: JsonObject?): String? {
        listOf("access_token", "token", "login_token", "auth_token").forEach { k ->
            data?.get(k)?.asString?.takeIf { it.isNotBlank() }?.let { return it }
            root.get(k)?.asString?.takeIf { it.isNotBlank() }?.let { return it }
        }
        data?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject?.let { u ->
            listOf("access_token", "token", "login_token").forEach { k ->
                u.get(k)?.asString?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    /**
     * Login seperti AnimeIn: username_or_email + password (+ device_id).
     */
    suspend fun login(usernameOrEmail: String, password: String): UserProfile {
        val root = postJson(
            "auth/login",
            mapOf(
                "username_or_email" to usernameOrEmail.trim(),
                "username" to usernameOrEmail.trim(),
                "email" to usernameOrEmail.trim(),
                "password" to password,
                "device_id" to deviceId,
            ),
        )
        if (root.get("error")?.asBoolean == true || root.get("error")?.asString == "true") {
            error(root.get("message")?.asString ?: "Login gagal")
        }
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val tok = extractToken(root, data) ?: error(root.get("message")?.asString ?: "Token tidak diterima")
        val profile = parseUser(data) ?: UserProfile(
            username = usernameOrEmail.trim(),
            email = if ("@" in usernameOrEmail) usernameOrEmail.trim() else null,
        )
        applySession(tok, profile)
        // refresh profile jika endpoint tersedia
        runCatching { refreshProfile() }
        return user ?: profile
    }

    /**
     * Register akun baru.
     */
    suspend fun register(username: String, email: String, password: String): UserProfile {
        val root = postJson(
            "auth/register",
            mapOf(
                "username" to username.trim(),
                "email" to email.trim(),
                "password" to password,
                "device_id" to deviceId,
            ),
        )
        if (root.get("error")?.asBoolean == true || root.get("error")?.asString == "true") {
            error(root.get("message")?.asString ?: "Registrasi gagal")
        }
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val tok = extractToken(root, data)
        val profile = parseUser(data) ?: UserProfile(username = username.trim(), email = email.trim())
        if (tok != null) {
            applySession(tok, profile)
            runCatching { refreshProfile() }
            return user ?: profile
        }
        // beberapa server hanya return success → user harus login manual
        return profile
    }

    suspend fun refreshProfile(): UserProfile? {
        if (!isLoggedIn) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                Api.ensureBasePublic()
                val url = Api.baseUrl.trimEnd('/') + "/3/2/user/profile/data"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "okhttp/4.12.0")
                    .header("Authorization", "Bearer ${token}")
                    .build()
                Api.httpPublic.newCall(req).execute().use { r ->
                    val text = r.body.string().orEmpty()
                    if (!r.isSuccessful) return@runCatching null
                    val root = JsonParser.parseString(text).asJsonObject
                    if (root.get("error")?.asBoolean == true) return@runCatching null
                    val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    val profile = parseUser(data) ?: return@runCatching null
                    user = profile
                    username = profile.username ?: profile.name ?: username
                    email = profile.email ?: email
                    persist()
                    profile
                }
            }.getOrNull()
        }
    }

    /** Header Authorization untuk request terautentikasi (dipakai Api interceptor). */
    fun authHeader(): Pair<String, String>? =
        token?.takeIf { it.isNotBlank() }?.let { "Authorization" to "Bearer $it" }
}

data class UserProfile(
    val id: String? = null,
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
    val image: String? = null,
    val avatar: String? = null,
    val coin: String? = null,
    val money: String? = null,
    val level: String? = null,
)
