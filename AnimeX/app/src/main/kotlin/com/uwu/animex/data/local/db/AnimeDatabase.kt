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

        fun get(context: Context): AnimeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AnimeDatabase::class.java,
                        "animex.db",
                    )
                    // Schemas 1 and 2 were never exported, so no migration path can be
                    // verified for them; rebuild instead of crashing on open. From
                    // version 3 on, schemas are exported to app/schemas: add a real
                    // Migration for every future bump instead of editing this line.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
