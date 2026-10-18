package com.uwu.animex.sync.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.api.Api
import com.uwu.animex.sync.AuthAPI
import com.uwu.animex.sync.AuthToken
import com.uwu.animex.sync.AuthUser
import com.uwu.animex.sync.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Auth provider for AnimeIn server.
 * Only Google login is supported (manual email/password API is currently broken).
 *
 * Uses the same API base as [Api] / [Secrets] (gate + domain_api).
 * apk_ver & User-Agent match official AnimeIn so the server accepts the request.
 *
 * POST auth/google  form: email, token (Google ID token), apk_ver
 * Response: { error, message, data: { user: { id, key_client, ... } } }
 */
class AnimeInApi : AuthAPI() {
    override val name = "AnimeIn"
    override val idPrefix = "animein"
    override val hasOAuth2 = false
    override val requiresLogin = true
    override val createAccountUrl: String? = "https://animein.net"

    private val gson = Gson()
    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    companion object {
        /** Same Web client ID AnimeIn uses as serverClientId for Google ID tokens. */
        const val GOOGLE_SERVER_CLIENT_ID =
            "49740216870-nuusdrbp4586715nttl6gnjged1d7pn6.apps.googleusercontent.com"

        const val PROFILE_URL = "https://animein.net"

        /** Match official AnimeIn APK so server does not reject. */
        const val APK_VER = "5.2.2"
        const val USER_AGENT = "ANIMEIN/5.2.2"
    }

    /**
     * Prefer resolved [Api.baseUrl] (from gate/domain_api), then [Secrets.apiBaseUrl].
     * Same source as the rest of the app content API — no hardcoded host.
     */
    private fun resolveBase(): String {
        val raw =
            Api.baseUrl.trim().ifBlank { Secrets.apiBaseUrl.trim() }
        if (raw.isBlank()) {
            throw IllegalStateException(
                "API base belum siap. Buka Home dulu atau cek API_BASE_URL / api.base di local.properties.",
            )
        }
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    /**
     * Login with Google ID token from Google Sign-In.
     * accessToken = key_client, refreshToken = user id (string).
     */
    suspend fun loginWithGoogle(
        email: String,
        idToken: String,
    ): AuthToken? =
        withContext(Dispatchers.IO) {
            val body =
                FormBody
                    .Builder()
                    .add("email", email)
                    .add("token", idToken)
                    .add("apk_ver", APK_VER)
                    .build()
            val req =
                Request
                    .Builder()
                    .url(resolveBase() + "auth/google")
                    .post(body)
                    .header("User-Agent", USER_AGENT)
                    .build()
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw HttpException(resp.code)
                parseAuthResponse(raw)
            }
        }

    private fun parseAuthResponse(raw: String): AuthToken? {
        val root = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull() ?: return null
        if (root.has("error") && root.get("error").asBoolean) {
            val msg = root.get("message")?.asString ?: "Login gagal"
            throw IllegalStateException(msg)
        }
        val data = root.getAsJsonObject("data") ?: return null
        val user = data.getAsJsonObject("user") ?: return null
        val id = user.get("id")?.asString ?: return null
        val keyClient = user.get("key_client")?.asString ?: return null
        return AuthToken(
            accessToken = keyClient,
            refreshToken = id,
            payload = user.toString(),
        )
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        token ?: return null
        val userJson = token.payload
        if (userJson != null) {
            val user = runCatching { gson.fromJson(userJson, JsonObject::class.java) }.getOrNull()
            if (user != null) {
                val id = user.get("id")?.asInt ?: token.refreshToken?.toIntOrNull() ?: return null
                val name =
                    user.get("username")?.asString
                        ?: user.get("name")?.asString
                        ?: user.get("email")?.asString
                        ?: "User $id"
                val picture =
                    user.get("image")?.asString
                        ?: user.get("avatar")?.asString
                        ?: user.get("photo")?.asString
                return AuthUser(name = name, id = id, profilePicture = picture)
            }
        }
        val id = token.refreshToken?.toIntOrNull() ?: return null
        return AuthUser(name = "User $id", id = id, profilePicture = null)
    }

    /** Optional profile refresh via 3/2/user/profile/data. */
    suspend fun fetchProfile(token: AuthToken): AuthUser? =
        withContext(Dispatchers.IO) {
            val idUser = token.refreshToken ?: return@withContext user(token)
            val keyClient = token.accessToken ?: return@withContext user(token)
            val url =
                (resolveBase() + "3/2/user/profile/data")
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("id_user", idUser)
                    .addQueryParameter("key_client", keyClient)
                    .addQueryParameter("apk_ver", APK_VER)
                    .build()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext user(token)
                val raw = resp.body?.string().orEmpty()
                val root = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()
                val data = root?.getAsJsonObject("data")
                val profile = data?.getAsJsonObject("user") ?: data
                if (profile != null) {
                    val merged =
                        runCatching { gson.fromJson(token.payload ?: "{}", JsonObject::class.java) }
                            .getOrElse { JsonObject() }
                    profile.entrySet().forEach { (k, v) -> merged.add(k, v) }
                    user(token.copy(payload = merged.toString()))
                } else {
                    user(token)
                }
            }
        }

    override fun loginRequest() = null

    override suspend fun login(
        redirectUrl: String,
        payload: String?,
    ): AuthToken? = null

    override suspend fun refreshToken(token: AuthToken): AuthToken? = token
}
