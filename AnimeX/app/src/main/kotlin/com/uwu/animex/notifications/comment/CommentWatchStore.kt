package com.uwu.animex.notifications.comment

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.data.user.UserApi

/** Komentar episode milik user yang dipantau balasannya. */
data class WatchedComment(
    val commentId: String,
    val episodeId: String,
    val userId: String,
    val title: String?,
    val snippet: String,
    val baselined: Boolean,
    val seenReplyIds: List<String>,
    val addedAt: Long,
)

object CommentWatchStore {
    private const val PREFS = "comment_watch"
    private const val K_ITEMS = "items"
    private const val K_NEXT_AT = "next_at"
    private const val MAX_ITEMS = 40
    private const val MAX_SEEN = 200
    private const val SNIPPET_LENGTH = 80
    private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    private val gson = Gson()
    private var sp: SharedPreferences? = null
    private var items: List<WatchedComment> = emptyList()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = prefs
        items =
            runCatching {
                gson.fromJson(prefs.getString(K_ITEMS, null), Array<WatchedComment>::class.java)?.toList()
            }.getOrNull().orEmpty()
    }

    @Synchronized
    fun forUser(userId: String): List<WatchedComment> = items.filter { it.userId == userId }

    /** @return true kalau ada komentar baru yang mulai dipantau. */
    @Synchronized
    fun register(
        userId: String,
        episodeId: String,
        title: String?,
        comments: List<UserApi.Discussion>,
    ): Boolean {
        val known = items.map { it.commentId }.toHashSet()
        val added =
            comments
                .filter { it.userId == userId && !it.id.isNullOrBlank() && it.id !in known }
                .map { c ->
                    WatchedComment(
                        commentId = c.id.orEmpty(),
                        episodeId = episodeId,
                        userId = userId,
                        title = title,
                        snippet = c.body().trim().take(SNIPPET_LENGTH),
                        // Kalau sudah ada balasan, baseline-nya diambil saat cek pertama.
                        baselined = c.replyCount?.toIntOrNull() == 0,
                        seenReplyIds = emptyList(),
                        addedAt = System.currentTimeMillis(),
                    )
                }
        if (added.isEmpty()) return false
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        items =
            (items + added)
                .filter { it.addedAt >= cutoff }
                .sortedByDescending { it.addedAt }
                .take(MAX_ITEMS)
        persist()
        return true
    }

    @Synchronized
    fun update(
        commentId: String,
        transform: (WatchedComment) -> WatchedComment,
    ) {
        var changed = false
        items =
            items.map {
                if (it.commentId == commentId) {
                    changed = true
                    transform(it)
                } else {
                    it
                }
            }
        if (changed) persist()
    }

    fun markSeen(
        commentId: String,
        replyIds: List<String>,
    ) {
        update(commentId) { it.withSeen(replyIds) }
    }

    @Synchronized
    fun nextAt(): Long = sp?.getLong(K_NEXT_AT, 0L) ?: 0L

    @Synchronized
    fun setNextAt(value: Long) {
        sp?.edit()?.putLong(K_NEXT_AT, value)?.apply()
    }

    private fun persist() {
        sp?.edit()?.putString(K_ITEMS, gson.toJson(items))?.apply()
    }
}

fun WatchedComment.withSeen(replyIds: List<String>): WatchedComment =
    copy(
        baselined = true,
        seenReplyIds = (seenReplyIds + replyIds).distinct().takeLast(200),
    )
