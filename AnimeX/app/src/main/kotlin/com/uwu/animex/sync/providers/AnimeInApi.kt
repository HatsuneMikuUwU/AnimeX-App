package com.uwu.animex.sync.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
 * Flow (from AnimeIn APK):
 * 1. POST auth/google  form: email, token  → existing user gets id + key_client
 * 2. If data.user is null (first Google login) → POST auth/token
 *    form: username, email, token, pokemon
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

        /** Default starter pokemon id used by AnimeIn RegisterTokenActivity. */
        private const val DEFAULT_POKEMON = "152"
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
     * Login with Google ID token. Handles first-time registration via auth/token
     * when the account does not exist yet.
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
            val raw = postForm("auth/google", body)
            val parsed = parseEnvelope(raw)

            // Existing account
            extractUserToken(parsed.data)?.let { return@withContext it }

            // First-time Google login: AnimeIn returns success without user → register
            if (!parsed.error) {
                return@withContext registerWithGoogleToken(email, idToken)
            }

            throw IllegalStateException(parsed.message ?: "Login gagal")
        }

    /**
     * First-time Google account: POST auth/token with auto username from email.
     * Mirrors RegisterTokenActivity in the official app.
     */
    private suspend fun registerWithGoogleToken(
        email: String,
        idToken: String,
    ): AuthToken {
        val username = usernameFromEmail(email)
        val body =
            FormBody
                .Builder()
                .add("username", username)
                .add("email", email)
                .add("token", idToken)
                .add("pokemon", DEFAULT_POKEMON)
                .add("apk_ver", APK_VER)
                .build()
        val raw = postForm("auth/token", body)
        val parsed = parseEnvelope(raw)
        if (parsed.error) {
            throw IllegalStateException(parsed.message ?: "Gagal daftar akun AnimeIn")
        }
        return extractUserToken(parsed.data)
            ?: throw IllegalStateException(parsed.message ?: "Server tidak mengembalikan data user")
    }

    private fun usernameFromEmail(email: String): String {
        val local =
            email
                .substringBefore("@")
                .replace(Regex("[^A-Za-z0-9]"), "")
                .take(16)
                .ifBlank { "user" }
        // Keep alphanumeric only (AnimeIn validates this); append short suffix for uniqueness
        val suffix = (System.currentTimeMillis() % 10000).toString()
        return (local + suffix).take(20)
    }

    private fun postForm(
        path: String,
        body: FormBody,
    ): String {
        val req =
            Request
                .Builder()
                .url(resolveBase() + path)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val hint =
                    runCatching {
                        val root = JsonParser.parseString(raw).asJsonObject
                        root.get("message")?.asString
                            ?: root.getAsJsonObject("data")?.get("message")?.asString
                    }.getOrNull()
                throw HttpException(resp.code).also {
                    // attach server message via cause message pattern used by UI
                }.let { e ->
                    if (!hint.isNullOrBlank()) {
                        throw IllegalStateException("HTTP ${resp.code}: $hint", e)
                    }
                    throw IllegalStateException(
                        "HTTP ${resp.code}" +
                            if (raw.isNotBlank()) ": ${raw.take(180)}" else "",
                        e,
                    )
                }
            }
            return raw
        }
    }

    private data class ParsedEnvelope(
        val error: Boolean,
        val message: String?,
        val data: JsonObject?,
    )

    private fun parseEnvelope(raw: String): ParsedEnvelope {
        val root =
            runCatching { JsonParser.parseString(raw).asJsonObject }
                .getOrElse { throw IllegalStateException("Respons server tidak valid") }
        val error =
            when {
                !root.has("error") || root.get("error").isJsonNull -> false
                root.get("error").isJsonPrimitive -> {
                    val p = root.get("error").asJsonPrimitive
                    when {
                        p.isBoolean -> p.asBoolean
                        p.isNumber -> p.asInt != 0
                        p.isString -> p.asString.equals("true", true) || p.asString == "1"
                        else -> false
                    }
                }
                else -> false
            }
        val message =
            root.get("message")?.takeIf { !it.isJsonNull }?.asString
                ?: dataObj(root.get("data"))?.let { d ->
                    listOf("message", "error_message", "msg")
                        .firstNotNullOfOrNull { k ->
                            d.get(k)?.takeIf { !it.isJsonNull }?.asString
                        }
                }
        return ParsedEnvelope(error = error, message = message, data = dataObj(root.get("data")))
    }

    private fun dataObj(el: com.google.gson.JsonElement?): JsonObject? {
        if (el == null || el.isJsonNull) return null
        if (el.isJsonObject) return el.asJsonObject
        if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            return runCatching { JsonParser.parseString(el.asString).asJsonObject }.getOrNull()
        }
        return null
    }

    private fun extractUserToken(data: JsonObject?): AuthToken? {
        if (data == null) return null
        val user = objFlexible(data, "user") ?: return null
        val id =
            str(user, "id")
                ?: user.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asString
                ?: return null
        val keyClient = str(user, "key_client") ?: return null
        return AuthToken(
            accessToken = keyClient,
            refreshToken = id,
            payload = user.toString(),
        )
    }

    private fun objFlexible(
        obj: JsonObject,
        key: String,
    ): JsonObject? {
        val el = obj.get(key) ?: return null
        if (el.isJsonNull) return null
        if (el.isJsonObject) return el.asJsonObject
        if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            val s = el.asString.trim()
            if (s.startsWith("{") && s.endsWith("}")) {
                return runCatching { JsonParser.parseString(s).asJsonObject }.getOrNull()
            }
        }
        return null
    }

    private fun str(
        obj: JsonObject,
        key: String,
    ): String? {
        val el = obj.get(key) ?: return null
        if (el.isJsonNull) return null
        return try {
            when {
                el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString
                el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> el.asJsonPrimitive.asString
                else -> null
            }?.takeIf { it.isNotBlank() && !it.equals("null", true) }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        token ?: return null
        val userJson = token.payload
        if (userJson != null) {
            val user = runCatching { gson.fromJson(userJson, JsonObject::class.java) }.getOrNull()
            if (user != null) {
                val id = user.get("id")?.asInt ?: token.refreshToken?.toIntOrNull() ?: return null
                val name =
                    str(user, "username")
                        ?: str(user, "name")
                        ?: str(user, "email")
                        ?: "User $id"
                val picture =
                    str(user, "image")
                        ?: str(user, "avatar")
                        ?: str(user, "photo")
                return AuthUser(name = name, id = id, profilePicture = picture)
            }
        }
        val id = token.refreshToken?.toIntOrNull() ?: return null
        return AuthUser(name = "User $id", id = id, profilePicture = null)
    }

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
                    .header("Accept", "application/json")
                    .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext user(token)
                val raw = resp.body?.string().orEmpty()
                val data = parseEnvelope(raw).data
                val profile = data?.let { objFlexible(it, "user") } ?: data
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
