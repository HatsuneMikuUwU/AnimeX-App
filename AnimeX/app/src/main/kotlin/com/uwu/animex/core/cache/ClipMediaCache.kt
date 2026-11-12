package com.uwu.animex.core.cache

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
object ClipMediaCache {
    private const val MAX_BYTES = 500L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: create(context.applicationContext).also { cache = it }
        }

    fun clear(context: Context) {
        val c = get(context)
        c.keys.toList().forEach { key -> runCatching { c.removeResource(key) } }
    }

    private fun create(app: Context): SimpleCache =
        SimpleCache(
            File(app.cacheDir, "cuplix_media"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(app),
        )
}
