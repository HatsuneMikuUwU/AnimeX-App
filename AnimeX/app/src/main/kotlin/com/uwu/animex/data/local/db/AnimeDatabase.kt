package com.uwu.animex.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        ProgressEntity::class,
        SearchHistoryEntity::class,
        EpisodeAlertEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao

    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        // Versi 1 dan 2 dihapus total saat upgrade (tidak ada migrasi). Mulai versi 3, WAJIB
        // menulis Migration baru (lihat folder schemas/) setiap menaikkan `version`.
        fun get(context: Context): AnimeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AnimeDatabase::class.java,
                        "animex.db",
                    ).fallbackToDestructiveMigrationFrom(true, 1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
