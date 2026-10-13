package com.uwu.animex.data.animein

/**
 * Every request/response field name of the ANIMEIN account + discussion endpoints lives here.
 * The endpoint list does not document parameters, so these are the best-known names; if the server
 * rejects a call, adjust only this file (aliases are all sent / tried in order).
 */
internal object AnimeInSchema {
    const val APK_VER = "115"

    const val LOGIN_PATH = "auth/login"
    val LOGIN_IDENTIFIER_FIELDS = listOf("email", "username")
    const val LOGIN_PASSWORD_FIELD = "password"

    const val DISCUSSION_LIST = "data/movie/discussion/list"
    const val DISCUSSION_DO = "data/movie/discussion/do"
    const val DISCUSSION_EDIT = "data/movie/discussion/edit_do"
    const val DISCUSSION_DELETE = "data/movie/discussion/delete"
    const val DISCUSSION_REPORT = "data/movie/discussion/report"

    const val MOVIE_ID_FIELD = "id_movie"
    val TEXT_FIELDS = listOf("comment", "text")
    val DISCUSSION_ID_FIELDS = listOf("id_discussion", "id")

    val USER_ID_KEYS = listOf("id_user", "user_id", "id")
    val USER_KEY_KEYS = listOf("key_client", "key", "token", "access_token")
    val USER_NAME_KEYS = listOf("username", "name", "nama", "user_name")
    val USER_AVATAR_KEYS = listOf("image", "avatar", "photo", "picture", "foto", "user_image")

    val LIST_KEYS = listOf("list", "discussion", "discussions", "items", "results", "data", "movie_discussion")
    val ITEM_ID_KEYS = listOf("id", "id_discussion", "id_movie_discussion")
    val ITEM_USER_ID_KEYS = listOf("id_user", "user_id")
    val ITEM_TEXT_KEYS = listOf("comment", "text", "message", "content", "discussion", "body", "isi")
    val ITEM_TIME_KEYS = listOf("time_ago", "created_at", "date", "time", "created", "tanggal")
    val ITEM_LIKE_KEYS = listOf("like", "likes", "total_like")
}
