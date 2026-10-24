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
)

object EpisodeScheduleStore {
    private const val PREFS = "episode_schedule"
    private const val K_ITEMS = "items"
    private const val K_LAST_RUN = "last_run"
    private const val K_SIGNATURE = "signature"

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
}
