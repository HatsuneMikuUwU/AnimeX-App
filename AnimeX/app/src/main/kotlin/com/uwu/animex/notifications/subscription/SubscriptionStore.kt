package com.uwu.animex.notifications.subscription

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Satu entri riwayat notifikasi episode baru. */
data class SubscriptionStore(
    val title: String = "",
    val content: String = "",
    val mediaId: String = "",
    val episode: Int = 0,
    val image: String? = null,
    val time: Long = System.currentTimeMillis(),
)

/** Riwayat notifikasi (maks. 100) + jumlah yang belum dibuka, disimpan lokal. */
object NotificationHistory {
    private const val PREFS = "notification_history"
    private const val K_ITEMS = "items"
    private const val K_UNREAD = "unread"
    private const val MAX_ITEMS = 100

    private val gson = Gson()
    private var sp: SharedPreferences? = null
    private val _items = MutableStateFlow<List<SubscriptionStore>>(emptyList())
    private val _unread = MutableStateFlow(0)

    val items: StateFlow<List<SubscriptionStore>> = _items.asStateFlow()
    val unread: StateFlow<Int> = _unread.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = prefs
        _items.value =
            runCatching {
                gson.fromJson<List<SubscriptionStore>>(
                    prefs.getString(K_ITEMS, null),
                    object : TypeToken<List<SubscriptionStore>>() {}.type,
                )
            }.getOrNull().orEmpty().filter { it.mediaId.isNotBlank() }
        _unread.value = prefs.getInt(K_UNREAD, 0)
    }

    @Synchronized
    fun add(entry: SubscriptionStore) {
        val current = _items.value
        if (current.any { it.mediaId == entry.mediaId && it.content == entry.content }) return
        _items.value = (listOf(entry) + current).sortedByDescending { it.time }.take(MAX_ITEMS)
        _unread.value = _unread.value + 1
        persist()
    }

    @Synchronized
    fun remove(entry: SubscriptionStore) {
        _items.value = _items.value.filterNot { it.time == entry.time && it.mediaId == entry.mediaId }
        _unread.value = _unread.value.coerceAtMost(_items.value.size)
        persist()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        _unread.value = 0
        persist()
    }

    @Synchronized
    fun markAllRead() {
        if (_unread.value == 0) return
        _unread.value = 0
        persist()
    }

    private fun persist() {
        sp
            ?.edit()
            ?.putString(K_ITEMS, gson.toJson(_items.value))
            ?.putInt(K_UNREAD, _unread.value)
            ?.apply()
    }
}
