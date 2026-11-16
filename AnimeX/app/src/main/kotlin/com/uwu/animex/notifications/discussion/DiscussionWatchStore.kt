package com.uwu.animex.notifications.discussion

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.ui.viewmodel.DiscussionScope

data class WatchedThread(
    val targetId: String,
    val scope: String,
    val title: String = "",
    val lastSeenIds: Set<String> = emptySet(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object DiscussionWatchStore {
    private const val FILE = "discussion_watch"
    private const val KEY = "threads"
    private const val MAX_THREADS = 40
    private val gson = Gson()
    private var sp: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun all(): List<WatchedThread> {
        val raw = sp?.getString(KEY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<WatchedThread>>(raw, object : TypeToken<List<WatchedThread>>() {}.type)
        }.getOrNull().orEmpty()
    }

    @Synchronized
    fun watch(
        targetId: String,
        scope: DiscussionScope,
        title: String = "",
        knownIds: Collection<String> = emptyList(),
    ) {
        if (sp == null || targetId.isBlank() || scope == DiscussionScope.Cuplix) return
        val keyScope = scope.name
        val current = all().toMutableList()
        val idx = current.indexOfFirst { it.targetId == targetId && it.scope == keyScope }
        val mergedIds =
            (
                (if (idx >= 0) current[idx].lastSeenIds else emptySet()) +
                    knownIds.filter { it.isNotBlank() }
            ).toSet()
        val entry =
            WatchedThread(
                targetId = targetId,
                scope = keyScope,
                title = title.ifBlank { if (idx >= 0) current[idx].title else "" },
                lastSeenIds = mergedIds,
                updatedAt = System.currentTimeMillis(),
            )
        if (idx >= 0) current[idx] = entry else current.add(0, entry)
        persist(current.sortedByDescending { it.updatedAt }.take(MAX_THREADS))
    }

    @Synchronized
    fun markSeen(
        targetId: String,
        scope: DiscussionScope,
        ids: Collection<String>,
    ) {
        if (sp == null || targetId.isBlank() || ids.isEmpty()) return
        val keyScope = scope.name
        val current = all().toMutableList()
        val idx = current.indexOfFirst { it.targetId == targetId && it.scope == keyScope }
        if (idx < 0) return
        val old = current[idx]
        current[idx] =
            old.copy(
                lastSeenIds = old.lastSeenIds + ids.filter { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            )
        persist(current)
    }

    private fun persist(list: List<WatchedThread>) {
        sp?.edit()?.putString(KEY, gson.toJson(list))?.apply()
    }
}
