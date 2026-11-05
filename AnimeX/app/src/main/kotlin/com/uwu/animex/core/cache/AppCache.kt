package com.uwu.animex.core.cache

import android.content.Context
import coil3.imageLoader
import com.uwu.animex.core.network.DohDns
import com.uwu.animex.data.api.AniSkip
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.CharacterRepo
import com.uwu.animex.data.api.MalIdLookup
import com.uwu.animex.data.api.SimilarRepo
import com.uwu.animex.data.api.Translator
import com.uwu.animex.data.local.db.DbMaintenance
import com.uwu.animex.data.update.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppCache {
    suspend fun sizeBytes(context: Context): Long =
        withContext(Dispatchers.IO) {
            dirSize(context.applicationContext.cacheDir)
        }

    suspend fun maintain(context: Context) =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            runCatching { Api.trimCache() }
            runCatching { AniZip.trim() }
            runCatching { PersistentCache.trimAll() }
            runCatching { DbMaintenance.run(app) }
        }

    suspend fun clearAll(context: Context): Long =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val before = dirSize(app.cacheDir)

            val loader = app.imageLoader
            loader.memoryCache?.clear()
            loader.diskCache?.clear()

            Api.clearCache()
            AniZip.clearCache()
            Translator.clearCache()
            SimilarRepo.clearCache()
            CharacterRepo.clearCache()
            MalIdLookup.clearCache()
            AniSkip.clearCache()
            PersistentCache.clearAll()
            DohDns.clearAnswers()
            File(app.cacheDir, "updates").deleteRecursively()
            AppUpdate.onCacheCleared()

            (before - dirSize(app.cacheDir)).coerceAtLeast(0L)
        }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
}
