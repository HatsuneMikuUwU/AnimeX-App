package com.uwu.animex.data.animein

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.data.animein.AnimeInSchema as S
import com.uwu.animex.data.api.Api

data class Discussion(
    val id: String,
    val userId: String?,
    val name: String,
    val avatar: String?,
    val text: String,
    val time: String?,
    val likes: Int?,
)

data class DiscussionPage(
    val items: List<Discussion>,
    val hasMore: Boolean,
)

/** ANIMEIN account + movie discussion client. Parsing is deliberately tolerant of field naming. */
object AnimeIn {
    const val PAGE_SIZE = 20

    suspend fun login(
        identifier: String,
        password: String,
    ): AnimeInUser {
        val fields =
            buildMap {
                S.LOGIN_IDENTIFIER_FIELDS.forEach { put(it, identifier) }
                put(S.LOGIN_PASSWORD_FIELD, password)
                put("apk_ver", S.APK_VER)
            }
        val root = Api.rawPost(S.LOGIN_PATH, fields)
        val data = root.objectOrNull("data") ?: root
        val id =
            data.find(S.USER_ID_KEYS)
                ?: throw ApiException.Remote(
                    root.stringOrNull("message") ?: "Login gagal, respons server gak dikenali",
                )
        val user =
            AnimeInUser(
                id = id,
                key = data.find(S.USER_KEY_KEYS).orEmpty(),
                name = data.find(S.USER_NAME_KEYS) ?: identifier,
                avatar = Api.absUrl(data.find(S.USER_AVATAR_KEYS)),
            )
        AnimeInSession.save(user)
        return user
    }

    fun logout() = AnimeInSession.clear()

    suspend fun discussions(
        movieId: String,
        page: Int,
    ): DiscussionPage {
        val root =
            Api.rawGet(
                S.DISCUSSION_LIST,
                buildMap {
                    put(S.MOVIE_ID_FIELD, movieId)
                    put("page", page.toString())
                    put("limit", PAGE_SIZE.toString())
                    authFields().forEach { (k, v) -> put(k, v) }
                },
            )
        val array = root.listArray()
        val items = array?.mapNotNull { (it as? JsonObject)?.toDiscussion() }.orEmpty()
        return DiscussionPage(items, hasMore = items.size >= PAGE_SIZE)
    }

    suspend fun post(
        movieId: String,
        text: String,
    ) {
        requireLogin()
        Api.rawPost(
            S.DISCUSSION_DO,
            buildMap {
                put(S.MOVIE_ID_FIELD, movieId)
                S.TEXT_FIELDS.forEach { put(it, text) }
                putAll(authFields())
            },
        )
    }

    suspend fun edit(
        discussionId: String,
        text: String,
    ) {
        requireLogin()
        Api.rawPost(
            S.DISCUSSION_EDIT,
            buildMap {
                S.DISCUSSION_ID_FIELDS.forEach { put(it, discussionId) }
                S.TEXT_FIELDS.forEach { put(it, text) }
                putAll(authFields())
            },
        )
    }

    suspend fun delete(discussionId: String) {
        requireLogin()
        Api.rawPost(
            S.DISCUSSION_DELETE,
            buildMap {
                S.DISCUSSION_ID_FIELDS.forEach { put(it, discussionId) }
                putAll(authFields())
            },
        )
    }

    suspend fun report(discussionId: String) {
        requireLogin()
        Api.rawPost(
            S.DISCUSSION_REPORT,
            buildMap {
                S.DISCUSSION_ID_FIELDS.forEach { put(it, discussionId) }
                putAll(authFields())
            },
        )
    }

    private fun requireLogin() {
        if (AnimeInSession.current == null) throw ApiException.Remote("Login ANIMEIN dulu ya")
    }

    private fun authFields(): Map<String, String> {
        val u = AnimeInSession.current ?: return emptyMap()
        return mapOf("id_user" to u.id, "key_client" to u.key, "apk_ver" to S.APK_VER)
    }

    private fun JsonObject.toDiscussion(): Discussion? {
        val id = find(S.ITEM_ID_KEYS) ?: return null
        val text = find(S.ITEM_TEXT_KEYS)?.trim().orEmpty()
        if (text.isEmpty()) return null
        val nested = objectOrNull("user")
        return Discussion(
            id = id,
            userId = find(S.ITEM_USER_ID_KEYS) ?: nested?.find(listOf("id", "id_user")),
            name = find(S.USER_NAME_KEYS) ?: nested?.find(S.USER_NAME_KEYS) ?: "Anonim",
            avatar = Api.absUrl(find(S.USER_AVATAR_KEYS) ?: nested?.find(S.USER_AVATAR_KEYS)),
            text = text,
            time = find(S.ITEM_TIME_KEYS),
            likes = find(S.ITEM_LIKE_KEYS)?.toIntOrNull(),
        )
    }

    /** The first array found under `data` (or `data.<known key>`), whatever the server called it. */
    private fun JsonObject.listArray(): JsonArray? {
        val data = get("data")
        if (data != null && data.isJsonArray) return data.asJsonArray
        val obj = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        for (k in S.LIST_KEYS) {
            val v = obj.get(k)
            if (v != null && v.isJsonArray) return v.asJsonArray
        }
        return obj.entrySet().firstNotNullOfOrNull { (_, v) -> v.takeIf { it.isJsonArray }?.asJsonArray }
    }

    private fun JsonElement?.primitiveString(): String? =
        this?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.stringOrNull(key: String): String? = get(key).primitiveString()

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    /** First non-blank primitive among [keys], looking at this object, then its direct child objects. */
    private fun JsonObject.find(keys: List<String>): String? {
        for (k in keys) stringOrNull(k)?.let { return it }
        for ((_, v) in entrySet()) {
            if (!v.isJsonObject) continue
            for (k in keys) v.asJsonObject.stringOrNull(k)?.let { return it }
        }
        return null
    }
}
