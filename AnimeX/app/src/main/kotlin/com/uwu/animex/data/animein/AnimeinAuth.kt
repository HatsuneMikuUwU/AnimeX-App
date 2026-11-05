package com.uwu.animex.data.animein

import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.security.NativeCore
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.toApiException
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Envelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Auth + profil + chat untuk backend Animein (API yang sama dengan yang dipakai AnimeX).
 *
 * Path mengikuti inventaris API Animein 5.2.2:
 * - POST auth/login, auth/register
 * - GET  3/2/user/profile/data
 * - GET  3/2/chat/data
 * - POST 3/2/chat/do (multipart)
 *
 * Field body login tidak ada di interface Retrofit (FieldMap), jadi kita coba kombinasi
 * field yang umum dipakai app sejenis: username/email + password.
 */
object AnimeinAuth {
    private val gson = Gson()
    private val http get() = NetworkModule.apiClient

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val apkVer: String
        get() = runCatching {
            // Samakan dengan versi app bila tersedia di runtime
            "1.2.0"
        }.getOrDefault("1.2.0")

    suspend fun login(
        identity: String,
        password: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            _busy.value = true
            try {
                val fields =
                    listOf(
                        mapOf("username" to identity, "password" to password),
                        mapOf("email" to identity, "password" to password),
                        mapOf("user" to identity, "password" to password),
                        mapOf("username" to identity, "pass" to password),
                    )
                var lastMsg = "Login gagal"
                for (body in fields) {
                    val result = postForm(NativeCore.path(NativeCore.AUTH_LOGIN), body)
                    val session = parseAuthSession(result.json)
                    if (session != null) {
                        AnimeinSession.save(session)
                        runCatching { refreshProfile() }
                        AnimeinSession.notify("Login berhasil")
                        return@withContext true
                    }
                    lastMsg = result.message ?: lastMsg
                    // Kalau server jelas bilang kredensial salah, jangan coba field lain
                    if (result.error && lastMsg.contains("password", ignoreCase = true)) break
                    if (result.error && lastMsg.contains("salah", ignoreCase = true)) break
                }
                AnimeinSession.notify(lastMsg)
                false
            } catch (e: Exception) {
                AnimeinSession.notify(e.message ?: "Login gagal")
                false
            } finally {
                _busy.value = false
            }
        }

    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            _busy.value = true
            try {
                val body =
                    mapOf(
                        "username" to username,
                        "email" to email,
                        "password" to password,
                    )
                val result = postForm(NativeCore.path(NativeCore.AUTH_REGISTER), body)
                val session = parseAuthSession(result.json)
                if (session != null) {
                    AnimeinSession.save(session)
                    runCatching { refreshProfile() }
                    AnimeinSession.notify("Registrasi berhasil")
                    return@withContext true
                }
                // Beberapa backend hanya mengembalikan sukses tanpa session — coba login
                if (!result.error) {
                    return@withContext login(username.ifBlank { email }, password)
                }
                AnimeinSession.notify(result.message ?: "Registrasi gagal")
                false
            } catch (e: Exception) {
                AnimeinSession.notify(e.message ?: "Registrasi gagal")
                false
            } finally {
                _busy.value = false
            }
        }

    suspend fun refreshProfile(): AnimeinUser? =
        withContext(Dispatchers.IO) {
            if (!AnimeinSession.isLoggedIn()) return@withContext null
            val result = get(NativeCore.path(NativeCore.USER_PROFILE_DATA))
            val user = parseUser(result.json)
            if (user != null) {
                AnimeinSession.updateUser(user)
            }
            user
        }

    suspend fun chatData(
        page: Int = 1,
        limit: Int = 30,
    ): List<AnimeinChatMessage> =
        withContext(Dispatchers.IO) {
            if (!AnimeinSession.isLoggedIn()) return@withContext emptyList()
            val result =
                get(
                    NativeCore.path(NativeCore.CHAT_DATA),
                    mapOf("page" to "$page", "limit" to "$limit"),
                )
            parseChatMessages(result.json)
        }

    suspend fun sendChat(text: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!AnimeinSession.isLoggedIn()) return@withContext false
            if (text.isBlank()) return@withContext false
            try {
                val result = postMultipart(NativeCore.path(NativeCore.CHAT_DO), mapOf("message" to text, "chat" to text, "text" to text))
                !result.error
            } catch (_: Exception) {
                false
            }
        }

    suspend fun deleteChat(id: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!AnimeinSession.isLoggedIn()) return@withContext false
            try {
                val result = postForm(NativeCore.path(NativeCore.CHAT_DELETE), mapOf("id" to id, "id_chat" to id))
                !result.error
            } catch (_: Exception) {
                false
            }
        }

    // --- Diskusi per anime (movie discussion) ---

    /**
     * List diskusi untuk satu anime.
     * Endpoint: GET data/movie/discussion/list
     * Param umum yang dicoba: id / id_movie / movie_id + page/limit.
     */
    suspend fun discussionList(
        movieId: String,
        page: Int = 1,
        limit: Int = 40,
    ): List<AnimeinDiscussionMessage> =
        withContext(Dispatchers.IO) {
            val params =
                mapOf(
                    "id" to movieId,
                    "id_movie" to movieId,
                    "movie_id" to movieId,
                    "page" to "$page",
                    "limit" to "$limit",
                )
            val result = get(NativeCore.path(NativeCore.MOVIE_DISCUSSION_LIST), params)
            parseDiscussionMessages(result.json)
        }

    /**
     * Kirim diskusi untuk anime.
     * Endpoint: POST data/movie/discussion/do
     */
    suspend fun discussionSend(
        movieId: String,
        text: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!AnimeinSession.isLoggedIn()) return@withContext false
            if (text.isBlank()) return@withContext false
            try {
                val fields =
                    mapOf(
                        "id" to movieId,
                        "id_movie" to movieId,
                        "movie_id" to movieId,
                        "message" to text,
                        "discussion" to text,
                        "text" to text,
                        "content" to text,
                    )
                // Coba form dulu; beberapa backend pakai multipart
                val form = postForm(NativeCore.path(NativeCore.MOVIE_DISCUSSION_DO), fields)
                if (!form.error) return@withContext true
                val multi = postMultipart(NativeCore.path(NativeCore.MOVIE_DISCUSSION_DO), fields)
                !multi.error
            } catch (_: Exception) {
                false
            }
        }

    suspend fun discussionDelete(id: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!AnimeinSession.isLoggedIn()) return@withContext false
            try {
                val result =
                    postForm(
                        NativeCore.path(NativeCore.MOVIE_DISCUSSION_DELETE),
                        mapOf("id" to id, "id_discussion" to id, "discussion_id" to id),
                    )
                !result.error
            } catch (_: Exception) {
                false
            }
        }

    fun logout() {
        AnimeinSession.logout()
        AnimeinSession.notify("Logout berhasil")
    }

    // --- HTTP helpers ---

    private data class ApiResult(
        val json: JsonObject?,
        val error: Boolean,
        val message: String?,
    )

    private suspend fun ensureBase(): String {
        // Pakai base URL yang sudah di-resolve Api (gate yang sama)
        Api.ensureBasePublic()
        val base = Api.baseUrl
        if (base.isBlank()) throw ApiException.Config("Base URL API belum siap")
        return base.trimEnd('/') + "/"
    }

    private fun withAuth(params: Map<String, String>): Map<String, String> {
        val auth = AnimeinSession.authParams(apkVer)
        if (auth.isEmpty()) return params
        return LinkedHashMap<String, String>().apply {
            putAll(params)
            putAll(auth)
        }
    }

    private suspend fun get(
        path: String,
        params: Map<String, String> = emptyMap(),
    ): ApiResult {
        val base = ensureBase()
        val all = withAuth(params)
        val url =
            (base + path.trimStart('/'))
                .toHttpUrl()
                .newBuilder()
                .apply { all.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
        return execute(Request.Builder().url(url).get().build())
    }

    private suspend fun postForm(
        path: String,
        fields: Map<String, String>,
    ): ApiResult {
        val base = ensureBase()
        val all = withAuth(fields)
        val body =
            FormBody
                .Builder()
                .apply { all.forEach { (k, v) -> add(k, v) } }
                .build()
        val url = (base + path.trimStart('/')).toHttpUrl()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private suspend fun postMultipart(
        path: String,
        fields: Map<String, String>,
    ): ApiResult {
        val base = ensureBase()
        val all = withAuth(fields)
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .apply {
                    all.forEach { (k, v) ->
                        addFormDataPart(k, v)
                    }
                }.build()
        val url = (base + path.trimStart('/')).toHttpUrl()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private fun execute(request: Request): ApiResult {
        try {
            http.newCall(request).execute().use { r ->
                val raw = r.body.string()
                if (!r.isSuccessful) throw ApiException.Http(r.code)
                val root =
                    runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                        ?: return ApiResult(null, true, "Respons tidak valid")
                val error = root.get("error")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) it.asBoolean else it.asString == "true" } ?: false
                val message = root.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                return ApiResult(root, error, message)
            }
        } catch (e: IOException) {
            throw e.toApiException()
        }
    }

    private fun parseAuthSession(root: JsonObject?): AnimeinAuthSession? {
        if (root == null) return null
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        fun pick(vararg keys: String): String? {
            for (k in keys) {
                val v = data.get(k) ?: continue
                if (v.isJsonPrimitive) {
                    val s = v.asString
                    if (s.isNotBlank()) return s
                }
            }
            // nested user object
            val user = data.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
            if (user != null) {
                for (k in keys) {
                    val v = user.get(k) ?: continue
                    if (v.isJsonPrimitive) {
                        val s = v.asString
                        if (s.isNotBlank()) return s
                    }
                }
            }
            return null
        }
        val idUser = pick("id_user", "idUser", "user_id", "id") ?: return null
        val keyClient = pick("key_client", "keyClient", "key", "token", "access_token") ?: return null
        val user = parseUser(root)
        return AnimeinAuthSession(idUser = idUser, keyClient = keyClient, user = user)
    }

    private fun parseUser(root: JsonObject?): AnimeinUser? {
        if (root == null) return null
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        val obj =
            data.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: data.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: data
        fun str(vararg keys: String): String? {
            for (k in keys) {
                val v = obj.get(k) ?: continue
                if (v.isJsonPrimitive) {
                    val s = v.asString
                    if (s.isNotBlank() && s != "null") return s
                }
            }
            return null
        }
        val id = str("id_user", "id", "user_id") ?: AnimeinSession.idUser ?: return null
        return AnimeinUser(
            id = id,
            username = str("username", "user_name", "user"),
            email = str("email"),
            name = str("name", "display_name", "fullname", "username"),
            avatar = str("avatar", "image", "photo", "picture", "profile_image"),
            coin = str("coin", "coins", "money"),
            level = str("level", "lvl"),
        )
    }

    private fun parseChatMessages(root: JsonObject?): List<AnimeinChatMessage> {
        if (root == null) return emptyList()
        val data = root.get("data") ?: return emptyList()
        val arr =
            when {
                data.isJsonArray -> data.asJsonArray
                data.isJsonObject -> {
                    val o = data.asJsonObject
                    sequenceOf("chat", "chats", "list", "data", "message", "messages")
                        .mapNotNull { o.get(it) }
                        .firstOrNull { it.isJsonArray }
                        ?.asJsonArray
                }
                else -> null
            } ?: return emptyList()
        return arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            fun str(vararg keys: String): String? {
                for (k in keys) {
                    val v = o.get(k) ?: continue
                    if (v.isJsonPrimitive) {
                        val s = v.asString
                        if (s.isNotBlank() && s != "null") return s
                    }
                }
                return null
            }
            AnimeinChatMessage(
                id = str("id", "id_chat", "chat_id") ?: return@mapNotNull null,
                userId = str("id_user", "user_id", "idUser"),
                username = str("username", "name", "user"),
                avatar = str("avatar", "image", "photo"),
                message = str("message", "chat", "text", "content") ?: "",
                time = str("time", "created_at", "date", "key_time"),
            )
        }
    }

    private fun parseDiscussionMessages(root: JsonObject?): List<AnimeinDiscussionMessage> {
        if (root == null) return emptyList()
        val data = root.get("data") ?: return emptyList()
        val arr =
            when {
                data.isJsonArray -> data.asJsonArray
                data.isJsonObject -> {
                    val o = data.asJsonObject
                    sequenceOf(
                        "discussion",
                        "discussions",
                        "list",
                        "data",
                        "message",
                        "messages",
                        "comment",
                        "comments",
                    ).mapNotNull { o.get(it) }
                        .firstOrNull { it.isJsonArray }
                        ?.asJsonArray
                }
                else -> null
            } ?: return emptyList()
        return arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val o = el.asJsonObject
            fun str(vararg keys: String): String? {
                for (k in keys) {
                    val v = o.get(k) ?: continue
                    if (v.isJsonPrimitive) {
                        val s = v.asString
                        if (s.isNotBlank() && s != "null") return s
                    }
                }
                return null
            }
            AnimeinDiscussionMessage(
                id = str("id", "id_discussion", "discussion_id", "id_comment") ?: return@mapNotNull null,
                userId = str("id_user", "user_id", "idUser"),
                username = str("username", "name", "user"),
                avatar = str("avatar", "image", "photo"),
                message = str("message", "discussion", "text", "content", "comment") ?: "",
                time = str("time", "created_at", "date", "key_time"),
            )
        }
    }
}

data class AnimeinChatMessage(
    val id: String,
    val userId: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val message: String,
    val time: String? = null,
)

data class AnimeinDiscussionMessage(
    val id: String,
    val userId: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val message: String,
    val time: String? = null,
)
