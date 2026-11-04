package com.uwu.animex.data.local.db

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import java.util.concurrent.TimeUnit

/**
 * Perawatan database di background. Hanya memangkas data turunan (riwayat pencarian, progres lama,
 * riwayat melebihi batas); bookmark dan pengingat episode milik user tidak pernah dihapus di sini.
 */
object DbMaintenance {
    private val SEARCH_TTL_MS = TimeUnit.DAYS.toMillis(90)
    private val PROGRESS_TTL_MS = TimeUnit.DAYS.toMillis(365)

    suspend fun run(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ) {
        val db = AnimeDatabase.get(context)
        val dao = db.animeDao()
        db.withTransaction {
            dao.deleteSearchOlderThan(now - SEARCH_TTL_MS)
            dao.deleteProgressOlderThan(now - PROGRESS_TTL_MS)
            dao.trimHistory()
            dao.trimProgress()
            dao.trimSearch()
        }
        // Di luar transaksi: PRAGMA tidak boleh dijalankan di dalamnya.
        runCatching { db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA optimize")).close() }
        runCatching { db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(PASSIVE)")).close() }
    }
}
