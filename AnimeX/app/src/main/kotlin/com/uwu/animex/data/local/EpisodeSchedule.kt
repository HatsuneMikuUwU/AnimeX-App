package com.uwu.animex.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class EpisodeSource { WATCHING, PLAN_TO_WATCH, SUBSCRIBED }

data class ScheduledEpisode(
    val movieId: String = "",
    val title: String? = null,
    val poster: String? = null,
    val source: EpisodeSource = EpisodeSource.SUBSCRIBED,
    val triggerAt: Long = 0L,
    val episode: Int? = null,
    val misses: Int = 0,
)

object EpisodeScheduleStore {
    private const val PREFS = "episode_schedule"
    private const val K_ITEMS = "items"
    private const val K_LAST_RUN = "last_run"
    private const val K_SIGNATURE = "signature"
    private const val K_NOTIFIED = "notified"
    private const val MAX_NOTIFIED = 1000

    private val gson = Gson()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun items(context: Context): Map<String, ScheduledEpisode> =
        runCatching {
            gson.fromJson<List<ScheduledEpisode>>(
                prefs(context).getString(K_ITEMS, null),
                object : TypeToken<List<ScheduledEpisode>>() {}.type,
            )
        }.getOrNull().orEmpty().filter { it.movieId.isNotBlank() }.associateBy { it.movieId }

    @Synchronized
    fun save(
        context: Context,
        items: Collection<ScheduledEpisode>,
    ) {
        prefs(context).edit().putString(K_ITEMS, gson.toJson(items.toList())).apply()
    }

    @Synchronized
    fun saveUnlessChanged(
        context: Context,
        previous: Map<String, ScheduledEpisode>,
        planned: List<ScheduledEpisode>,
    ): List<ScheduledEpisode> {
        val current = items(context)
        val merged =
            planned.map { item ->
                val now = current[item.movieId]
                if (now != null && now != previous[item.movieId]) now else item
            }
        save(context, merged)
        return merged
    }

    @Synchronized
    fun put(
        context: Context,
        item: ScheduledEpisode,
    ) = save(context, items(context).plus(item.movieId to item).values)

    fun lastRun(context: Context): Long = prefs(context).getLong(K_LAST_RUN, 0L)

    fun signature(context: Context): String? = prefs(context).getString(K_SIGNATURE, null)

    fun markRun(
        context: Context,
        signature: String,
    ) {
        prefs(context)
            .edit()
            .putLong(K_LAST_RUN, System.currentTimeMillis())
            .putString(K_SIGNATURE, signature)
            .apply()
    }

    @Synchronized
    fun lastNotified(
        context: Context,
        movieId: String,
    ): Int? = notified(context)[movieId]

    @Synchronized
    fun markNotified(
        context: Context,
        movieId: String,
        episode: Int,
    ) {
        val map = LinkedHashMap(notified(context))
        map.remove(movieId)
        map[movieId] = maxOf(episode, notified(context)[movieId] ?: Int.MIN_VALUE)
        while (map.size > MAX_NOTIFIED) map.remove(map.keys.first())
        prefs(context).edit().putString(K_NOTIFIED, gson.toJson(map)).apply()
    }

    private fun notified(context: Context): Map<String, Int> =
        runCatching {
            gson.fromJson<Map<String, Int>>(
                prefs(context).getString(K_NOTIFIED, null),
                object : TypeToken<Map<String, Int>>() {}.type,
            )
        }.getOrNull().orEmpty()
}
