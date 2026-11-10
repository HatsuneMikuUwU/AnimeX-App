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
        CuplixWatchedEntity::class,
        CuplixSavedEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao

    abstract fun trackerDao(): TrackerDao

    abstract fun cuplixDao(): CuplixDao

    companion object {
        @Volatile
        private var instance: AnimeDatabase? = null

        fun get(context: Context): AnimeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AnimeDatabase::class.java,
                        "animex.db",
                    ).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(*AnimeDatabaseMigrations.all)
                    .build()
                    .also { instance = it }
            }
    }
}
