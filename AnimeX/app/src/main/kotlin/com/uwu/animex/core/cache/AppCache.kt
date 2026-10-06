package com.uwu.animex.core.cache

import android.content.Context
import coil3.imageLoader
import com.uwu.animex.core.image.DominantColor
import com.uwu.animex.data.api.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Satu pintu buat lihat ukuran & hapus semua cache aplikasi:
 * poster (Coil memory + disk), respons API, hue dominan poster, dan file update yang sudah diunduh.
 *
 * Yang TIDAK disentuh: bookmark, riwayat nonton, login MAL, dan pengaturan (bukan cache).
 */
object AppCache {
    /** Total ukuran folder cache aplikasi, dalam byte. */
    suspend fun sizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        dirSize(context.applicationContext.cacheDir)
    }

    /** Hapus semua cache. @return jumlah byte yang dibebaskan. */
    suspend fun clearAll(context: Context): Long = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val before = dirSize(app.cacheDir)

        val loader = app.imageLoader
        loader.memoryCache?.clear()
        loader.diskCache?.clear()

        Api.clearCache()
        DominantColor.clear(app)
        File(app.cacheDir, "updates").deleteRecursively()

        (before - dirSize(app.cacheDir)).coerceAtLeast(0L)
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
}
