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
        TrackerEntryEntity::class,
        TrackerMetaEntity::class,
        TrackerPendingOpEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao

    abstract fun trackerDao(): TrackerDao

    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        fun get(context: Context): AnimeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AnimeDatabase::class.java,
                        "animex.db",
                    ).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
