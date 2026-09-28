package com.uwu.animex.data

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * Central MMKV init + named instances.
 * Call [init] once from MainActivity before Bookmarks/History/Progress/Api cache.
 */
object MmkvStore {
    @Volatile
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            MMKV.initialize(context.applicationContext)
            ready = true
        }
    }

    /** User data: bookmarks, history, progress */
    fun user(): MMKV = MMKV.mmkvWithID("animex_user", MMKV.MULTI_PROCESS_MODE)

    /** API response disk cache */
    fun apiCache(): MMKV = MMKV.mmkvWithID("animex_api_cache", MMKV.MULTI_PROCESS_MODE)
}
