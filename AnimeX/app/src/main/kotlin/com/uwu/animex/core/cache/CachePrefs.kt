package com.uwu.animex.core.cache

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

private const val MINUTE_MS = 60 * 1000L
private const val HOUR_MS = 60 * MINUTE_MS

enum class CacheTtl(
    val label: String,
    val ms: Long,
) {
    OFF("Mati", 0L),
    MIN_5("5 menit", 5 * MINUTE_MS),
    MIN_15("15 menit", 15 * MINUTE_MS),
    MIN_30("30 menit", 30 * MINUTE_MS),
    HOUR_1("1 jam", HOUR_MS),
    HOUR_3("3 jam", 3 * HOUR_MS),
    HOUR_6("6 jam", 6 * HOUR_MS),
    HOUR_12("12 jam", 12 * HOUR_MS),
    HOUR_24("24 jam", 24 * HOUR_MS),
    FOREVER("Selamanya", Long.MAX_VALUE),
}

object CachePrefs {
    private const val PREFS = "cache_prefs"
    private const val KEY_TTL = "ttl"

    private var sp: SharedPreferences? = null
    private val _ttl = MutableStateFlow(CacheTtl.MIN_30)
    val ttl: StateFlow<CacheTtl> = _ttl.asStateFlow()

    val ttlMs: Long get() = _ttl.value.ms

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = prefs
        _ttl.value =
            runCatching { CacheTtl.valueOf(prefs.getString(KEY_TTL, null).orEmpty()) }
                .getOrDefault(CacheTtl.MIN_30)
    }

    @Synchronized
    fun setTtl(value: CacheTtl) {
        _ttl.value = value
        sp?.edit()?.putString(KEY_TTL, value.name)?.apply()
    }

    fun exportJson(): JSONObject = JSONObject().put("ttl", _ttl.value.name)

    fun restoreJson(o: JSONObject) {
        runCatching { CacheTtl.valueOf(o.optString("ttl")) }.getOrNull()?.let(::setTtl)
    }
}
