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
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object UserApi {
    const val APK_VER = "5.2.2"

    private val gson = Gson()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val textMedia = "text/plain".toMediaType()
    private val http get() = NetworkModule.apiClient

    private fun base(): String {
        val b = Api.baseUrl.trimEnd('/')
        if (b.isBlank()) throw ApiException.Config("API base belum siap")
        return "$b/"
    }

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun dataOf(obj: JsonObject?): JsonObject? =
        obj?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    private fun messageOf(obj: JsonObject?): String? = obj?.str("message") ?: dataOf(obj)?.str("message")

    private fun authParams(): Map<String, String> {
        val id = UserSession.userId() ?: return emptyMap()
        val key = UserSession.keyClient() ?: return emptyMap()
        return mapOf("id_user" to id, "key_client" to key, "apk_ver" to APK_VER)
    }

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
        jsonBody: Any? = null,
        multipartForm: Boolean = false,
        authed: Boolean = false,
    ): JsonObject =
        withContext(Dispatchers.IO) {
            val creds = if (authed) authParams() else emptyMap()
            val readOnly = method == "GET" || method == "DELETE"
            val finalQuery = if (readOnly) query + creds else query
            val finalForm =
                when {
                    readOnly -> form
                    creds.isNotEmpty() -> form.orEmpty() + creds
                    else -> form
                }

            val url =
                (base() + path.trimStart('/'))
                    .toHttpUrl()
                    .newBuilder()
                    .apply { finalQuery.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .build()

            val body =
                when {
                    jsonBody != null -> gson.toJson(jsonBody).toRequestBody(jsonMedia)
                    multipartForm ->
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .apply {
                                finalForm.orEmpty().forEach { (k, v) ->
                                    addFormDataPart(k, null, v.toRequestBody(textMedia))
                                }
                            }
                            .build()
                    finalForm != null ->
                        FormBody.Builder().apply { finalForm.forEach { (k, v) -> add(k, v) } }.build()
                    method == "POST" || method == "PUT" ->
                        ByteArray(0).toRequestBody(null)
                    else -> null
                }

            val builder =
                Request.Builder()
                    .url(url)
                    .method(method, body)

            try {
                http.newCall(builder.build()).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    val obj =
                        runCatching {
                            JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
                        }.getOrNull()
                    val flagged =
                        runCatching { obj?.get("error")?.asBoolean }.getOrNull() == true
                    val message = messageOf(obj)
                    when {
                        !r.isSuccessful && message == null -> throw ApiException.Http(r.code)
                        !r.isSuccessful || flagged -> throw ApiException.Remote(message ?: "API error")
                    }
                    obj ?: throw ApiException.Parse()
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw e.toApiException()
            }
        }

    private fun avatarOf(user: JsonObject): String? {
        val image = user.str("image")
        val name = user.str("image_name")
        return when {
            name != null && image != null -> if (image.endsWith("/")) image + name else "$image/$name"
            image != null && !image.endsWith("/") -> image
            else -> user.str("image_masukotto")
        }
    }

    private suspend fun fetchProfile(
        id: String,
        key: String,
    ): UserProfile {
        val root =
            request(
                "GET",
                "3/2/user/profile/data",
                query = mapOf("id_user" to id, "key_client" to key, "apk_ver" to APK_VER),
            )
        val user =
            dataOf(root)?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw ApiException.Remote("Profil tidak ditemukan")
        return UserProfile(
            id = user.str("id") ?: id,
            username = user.str("username"),
            email = user.str("email"),
            image = avatarOf(user),
            keyClient = key,
            money = user.str("money_gem")?.toLongOrNull(),
            coin = user.str("money_coin")?.toLongOrNull(),
        )
    }

    private suspend fun sessionFrom(
        root: JsonObject,
        fallbackMessage: String,
    ): UserProfile {
        val user = dataOf(root)?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
        val id = user?.str("id")
        val key = user?.str("key_client")
        if (id == null || key == null) {
            throw ApiException.Remote(messageOf(root) ?: fallbackMessage)
        }
        return runCatching { fetchProfile(id, key) }
            .getOrElse { UserProfile(id = id, keyClient = key) }
    }

    suspend fun login(
        usernameOrEmail: String,
        password: String,
    ): UserProfile {
        val root =
            request(
                "POST",
                "auth/login",
                form = mapOf("username_or_email" to usernameOrEmail, "password" to password),
            )
        return sessionFrom(root, "Kredensial login tidak valid")
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
        val registered = dataOf(root)?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
        return if (registered?.str("key_client") != null) {
            sessionFrom(root, "Registrasi gagal")
        } else {
            login(username, password)
        }
    }

    suspend fun refreshProfile(): UserProfile {
        val id = UserSession.userId() ?: throw ApiException.Remote("Belum login")
        val key = UserSession.keyClient() ?: throw ApiException.Remote("Belum login")
        return fetchProfile(id, key)
    }

    data class Discussion(
        @com.google.gson.annotations.SerializedName("id") val id: String? = null,
        @com.google.gson.annotations.SerializedName(value = "user_id", alternate = ["id_user"]) val userId: String? = null,
        @com.google.gson.annotations.SerializedName(value = "username", alternate = ["user_name"]) val username: String? = null,
        @com.google.gson.annotations.SerializedName("image") val image: String? = null,
        @com.google.gson.annotations.SerializedName("user_pokemon") val pokemon: String? = null,
        @com.google.gson.annotations.SerializedName("text") val text: String? = null,
        @com.google.gson.annotations.SerializedName("message") val message: String? = null,
        @com.google.gson.annotations.SerializedName("content") val content: String? = null,
        @com.google.gson.annotations.SerializedName("created_at") val createdAt: String? = null,
        @com.google.gson.annotations.SerializedName("time") val time: String? = null,
        @com.google.gson.annotations.SerializedName("time_updated") val timeUpdated: String? = null,
        @com.google.gson.annotations.SerializedName("type") val type: String? = null,
        @com.google.gson.annotations.SerializedName("pro") val pro: String? = null,
        @com.google.gson.annotations.SerializedName("rank") val rank: String? = null,
        @com.google.gson.annotations.SerializedName("like") val likeCount: String? = null,
        @com.google.gson.annotations.SerializedName("dislike") val dislikeCount: String? = null,
        @com.google.gson.annotations.SerializedName("replay") val replyCount: String? = null,
        @com.google.gson.annotations.SerializedName("is_like") val reaction: String? = null,
    ) {
        fun body(): String = text ?: message ?: content ?: ""
    }

    suspend fun discussionList(
        movieId: String,
        page: Int = 1,
    ): List<Discussion> {
        val root =
            request(
                "GET",
                "data/movie/discussion/list",
                query =
                    mapOf(
                        "id_movie" to movieId,
                        "page" to page.toString(),
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
                form = mapOf("text" to text, "id_movie" to movieId),
                multipartForm = true,
                authed = true,
            )
        val data = dataOf(root) ?: root
        return gson.fromJson(data, Discussion::class.java)
    }

    suspend fun discussionDelete(id: String) {
        request(
            "POST",
            "data/movie/discussion/delete",
            form = mapOf("id_movie_discussion" to id),
            authed = true,
        )
    }

    suspend fun discussionEdit(
        movieId: String,
        id: String,
        text: String,
    ) {
        request(
            "POST",
            "data/movie/discussion/edit_do",
            form = mapOf("text" to text, "id_movie_discussion" to id, "id_movie" to movieId),
            authed = true,
        )
    }

    suspend fun discussionReport(id: String) {
        request(
            "POST",
            "data/movie/discussion/report",
            form = mapOf("id_discussion" to id),
            authed = true,
        )
    }

    // Komentar per episode (sama endpoint AnimeIn: 3/2/comment/...).
    suspend fun episodeCommentList(
        episodeId: String,
        page: Int = 0,
        sort: String = "new",
    ): List<Discussion> {
        val root =
            request(
                "GET",
                "3/2/comment/data",
                query =
                    mapOf(
                        "id_episode" to episodeId,
                        "page" to page.toString(),
                        "sort" to sort,
                    ),
                authed = UserSession.isLoggedIn(),
            )
        return parseList(root, Discussion::class.java)
    }

    suspend fun episodeCommentPost(
        episodeId: String,
        text: String,
    ) {
        request(
            "POST",
            "3/2/comment/do",
            form = mapOf("text" to text, "id_episode" to episodeId),
            multipartForm = true,
            authed = true,
        )
    }

    suspend fun episodeCommentDelete(id: String) {
        request(
            "POST",
            "3/2/comment/delete",
            form = mapOf("id_comment" to id),
            authed = true,
        )
    }

    suspend fun episodeCommentEdit(
        episodeId: String,
        id: String,
        text: String,
    ) {
        request(
            "POST",
            "3/2/comment/edit_do",
            form = mapOf("text" to text, "id_comment" to id, "id_episode" to episodeId),
            authed = true,
        )
    }

    suspend fun episodeCommentReport(id: String) {
        request(
            "POST",
            "3/2/comment/report",
            form = mapOf("id_comment" to id),
            authed = true,
        )
    }

    /** AnimeIn uses type=Y for like, N for dislike and - to clear a reaction. */
    suspend fun episodeCommentAction(commentId: String, type: String) {
        request(
            "POST",
            "3/2/comment/action",
            form = mapOf("id_comment" to commentId, "type" to type),
            authed = true,
        )
    }

    suspend fun episodeCommentReplies(commentId: String, page: Int = 1, sort: String = "new"): List<Discussion> {
        val root = request(
            "GET",
            "data/comment_replay/data",
            query = mapOf("id_comment" to commentId, "page" to page.toString(), "sort" to sort),
            authed = UserSession.isLoggedIn(),
        )
        return parseList(root, Discussion::class.java)
    }

    suspend fun episodeCommentReply(commentId: String, text: String) {
        request(
            "POST",
            "data/comment_replay/do",
            form = mapOf("id_comment" to commentId, "text" to text),
            multipartForm = true,
            authed = true,
        )
    }

    suspend fun episodeCommentReplyAction(replyId: String, type: String) {
        request(
            "POST",
            "data/comment_replay/action",
            form = mapOf("id_comment_replay" to replyId, "type" to type),
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
                        ?: o.get("discussion")?.asJsonArray
                        ?: o.get("movie_discussion")?.asJsonArray
                        ?: o.get("comment")?.asJsonArray
                        ?: o.get("comment_replay")?.asJsonArray
                }
                else -> null
            } ?: root.getAsJsonArray("list")
        if (arr == null) return emptyList()
        val type = TypeToken.getParameterized(List::class.java, clazz).type
        return gson.fromJson(arr, type) ?: emptyList()
    }
}
