package com.uwu.animex.data.user

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.toApiException
import com.uwu.animex.data.api.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Client for Animein-compatible auth / chat / discussion endpoints.
 * Paths mirror Animein (auth, 3/2/chat, data/movie/discussion).
 */
object UserApi {
    private val gson = Gson()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val http get() = NetworkModule.apiClient

    private fun base(): String {
        val b = Api.baseUrl.trimEnd('/')
        if (b.isBlank()) throw ApiException.Config("API base belum siap")
        return "$b/"
    }

    private fun authHeaders(): Map<String, String> {
        val token = UserSession.token() ?: return emptyMap()
        return mapOf(
            "Authorization" to "Bearer $token",
            "token" to token,
            "X-Auth-Token" to token,
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
        jsonBody: Any? = null,
        authed: Boolean = false,
    ): JsonObject =
        withContext(Dispatchers.IO) {
            val url =
                (base() + path.trimStart('/'))
                    .toHttpUrl()
                    .newBuilder()
                    .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .build()

            val body =
                when {
                    jsonBody != null -> gson.toJson(jsonBody).toRequestBody(jsonMedia)
                    form != null ->
                        FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
                    method == "POST" || method == "PUT" ->
                        ByteArray(0).toRequestBody(null)
                    else -> null
                }

            val builder =
                Request.Builder()
                    .url(url)
                    .method(method, body)

            if (authed) {
                authHeaders().forEach { (k, v) -> builder.header(k, v) }
            }

            try {
                http.newCall(builder.build()).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        val msg =
                            runCatching {
                                JsonParser.parseString(text).asJsonObject.get("message")?.asString
                            }.getOrNull()
                        throw ApiException.Remote(msg ?: "HTTP ${r.code}")
                    }
                    if (text.isBlank()) return@use JsonObject()
                    val root = JsonParser.parseString(text)
                    if (!root.isJsonObject) return@use JsonObject()
                    val obj = root.asJsonObject
                    // common envelope: { error: bool, message, data }
                    if (obj.has("error") && obj.get("error").asBoolean) {
                        throw ApiException.Remote(obj.get("message")?.asString ?: "API error")
                    }
                    obj
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw e.toApiException()
            }
        }

    private fun dataOf(obj: JsonObject): JsonObject? =
        obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    // ── Auth ──────────────────────────────────────────────────────────

    suspend fun login(
        emailOrUsername: String,
        password: String,
    ): UserProfile {
        val root =
            request(
                "POST",
                "auth/login",
                form =
                    mapOf(
                        "email" to emailOrUsername,
                        "username" to emailOrUsername,
                        "password" to password,
                        "login_email" to emailOrUsername,
                    ),
            )
        return parseUser(root)
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): UserProfile {
        val root =
            request(
                "POST",
                "auth/register",
                form =
                    mapOf(
                        "username" to username,
                        "email" to email,
                        "password" to password,
                    ),
            )
        return parseUser(root)
    }

    suspend fun refreshProfile(): UserProfile {
        val root = request("GET", "3/2/user/profile/data", authed = true)
        return parseUser(root)
    }

    private fun parseUser(root: JsonObject): UserProfile {
        val data = dataOf(root) ?: root
        // token sometimes at root
        val token =
            data.get("token")?.asString
                ?: data.get("login_token")?.asString
                ?: root.get("token")?.asString
                ?: UserSession.token()
        val profile =
            gson.fromJson(data, UserProfile::class.java).copy(token = token)
        if (profile.token.isNullOrBlank()) {
            throw ApiException.Remote("Login gagal: token tidak ditemukan")
        }
        return profile
    }

    // ── Chat ──────────────────────────────────────────────────────────

    data class ChatMessage(
        @com.google.gson.annotations.SerializedName("id") val id: String? = null,
        @com.google.gson.annotations.SerializedName("user_id") val userId: String? = null,
        @com.google.gson.annotations.SerializedName("username") val username: String? = null,
        @com.google.gson.annotations.SerializedName("image") val image: String? = null,
        @com.google.gson.annotations.SerializedName("text") val text: String? = null,
        @com.google.gson.annotations.SerializedName("message") val message: String? = null,
        @com.google.gson.annotations.SerializedName("created_at") val createdAt: String? = null,
        @com.google.gson.annotations.SerializedName("time") val time: String? = null,
    ) {
        fun body(): String = text ?: message ?: ""
    }

    suspend fun chatList(
        page: Int = 1,
        limit: Int = 30,
    ): List<ChatMessage> {
        val root =
            request(
                "GET",
                "3/2/chat/data",
                query = mapOf("page" to page.toString(), "limit" to limit.toString()),
                // baca boleh tanpa login; kirim/hapus tetap butuh auth
                authed = UserSession.isLoggedIn(),
            )
        return parseList(root, ChatMessage::class.java)
    }

    suspend fun chatSend(text: String): ChatMessage {
        val root =
            request(
                "POST",
                "3/2/chat/do",
                form = mapOf("text" to text, "message" to text),
                authed = true,
            )
        val data = dataOf(root) ?: root
        return gson.fromJson(data, ChatMessage::class.java)
    }

    suspend fun chatDelete(id: String) {
        request(
            "POST",
            "3/2/chat/delete",
            form = mapOf("id" to id),
            authed = true,
        )
    }

    // ── Discussion (per anime) ────────────────────────────────────────

    data class Discussion(
        @com.google.gson.annotations.SerializedName("id") val id: String? = null,
        @com.google.gson.annotations.SerializedName("user_id") val userId: String? = null,
        @com.google.gson.annotations.SerializedName("username") val username: String? = null,
        @com.google.gson.annotations.SerializedName("image") val image: String? = null,
        @com.google.gson.annotations.SerializedName("text") val text: String? = null,
        @com.google.gson.annotations.SerializedName("message") val message: String? = null,
        @com.google.gson.annotations.SerializedName("content") val content: String? = null,
        @com.google.gson.annotations.SerializedName("created_at") val createdAt: String? = null,
        @com.google.gson.annotations.SerializedName("time") val time: String? = null,
    ) {
        fun body(): String = text ?: message ?: content ?: ""
    }

    suspend fun discussionList(
        movieId: String,
        page: Int = 1,
        limit: Int = 30,
    ): List<Discussion> {
        val root =
            request(
                "GET",
                "data/movie/discussion/list",
                query =
                    mapOf(
                        "id" to movieId,
                        "movie_id" to movieId,
                        "page" to page.toString(),
                        "limit" to limit.toString(),
                    ),
                authed = UserSession.isLoggedIn(),
            )
        return parseList(root, Discussion::class.java)
    }

    suspend fun discussionPost(
        movieId: String,
        text: String,
    ): Discussion {
        val root =
            request(
                "POST",
                "data/movie/discussion/do",
                form =
                    mapOf(
                        "id" to movieId,
                        "movie_id" to movieId,
                        "text" to text,
                        "message" to text,
                        "content" to text,
                    ),
                authed = true,
            )
        val data = dataOf(root) ?: root
        return gson.fromJson(data, Discussion::class.java)
    }

    suspend fun discussionDelete(id: String) {
        request(
            "POST",
            "data/movie/discussion/delete",
            form = mapOf("id" to id),
            authed = true,
        )
    }

    private fun <T> parseList(
        root: JsonObject,
        clazz: Class<T>,
    ): List<T> {
        val data = root.get("data")
        val arr =
            when {
                data == null -> null
                data.isJsonArray -> data.asJsonArray
                data.isJsonObject -> {
                    val o = data.asJsonObject
                    o.get("list")?.asJsonArray
                        ?: o.get("data")?.asJsonArray
                        ?: o.get("items")?.asJsonArray
                        ?: o.get("chat")?.asJsonArray
                        ?: o.get("discussion")?.asJsonArray
                }
                else -> null
            } ?: root.getAsJsonArray("list")
        if (arr == null) return emptyList()
        val type = TypeToken.getParameterized(List::class.java, clazz).type
        return gson.fromJson(arr, type) ?: emptyList()
    }
}
