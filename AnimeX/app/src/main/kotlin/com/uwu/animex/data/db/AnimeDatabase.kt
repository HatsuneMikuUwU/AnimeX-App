package com.uwu.animex.data.db

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
    ],
    version = 2,
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
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
