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
    exportSchema = false,
)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao

    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        fun get(context: Context): AnimeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "animex.db",
                )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
