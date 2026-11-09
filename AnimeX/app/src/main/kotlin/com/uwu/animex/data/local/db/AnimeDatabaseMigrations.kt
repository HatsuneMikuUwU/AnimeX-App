package com.uwu.animex.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

internal object AnimeDatabaseMigrations {
    private data class Table(
        val name: String,
        val createSql: String,
        val columns: List<String>,
        val indexes: List<String> = emptyList(),
    )

    private val tables = listOf(
        Table(
            name = "bookmarks",
            createSql = """
                CREATE TABLE `bookmarks` (
                    `movieId` TEXT NOT NULL,
                    `title` TEXT,
                    `imagePoster` TEXT,
                    `imageCover` TEXT,
                    `type` TEXT,
                    `year` TEXT,
                    `genre` TEXT,
                    `studio` TEXT,
                    `status` TEXT,
                    `favorite` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`movieId`)
                )
            """.trimIndent(),
            columns = listOf(
                "movieId",
                "title",
                "imagePoster",
                "imageCover",
                "type",
                "year",
                "genre",
                "studio",
                "status",
                "favorite",
                "updatedAt",
            ),
            indexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_bookmarks_updatedAt` ON `bookmarks` (`updatedAt`)",
                "CREATE INDEX IF NOT EXISTS `index_bookmarks_status` ON `bookmarks` (`status`)",
                "CREATE INDEX IF NOT EXISTS `index_bookmarks_favorite` ON `bookmarks` (`favorite`)",
            ),
        ),
        Table(
            name = "history",
            createSql = """
                CREATE TABLE `history` (
                    `movieId` TEXT NOT NULL,
                    `title` TEXT,
                    `imagePoster` TEXT,
                    `imageCover` TEXT,
                    `type` TEXT,
                    `year` TEXT,
                    `status` TEXT,
                    `genre` TEXT,
                    `studio` TEXT,
                    `views` TEXT,
                    `favorites` TEXT,
                    `airedStart` TEXT,
                    `airedEnd` TEXT,
                    `day` TEXT,
                    `time` TEXT,
                    `episodeIndex` TEXT,
                    `episodeId` TEXT,
                    `watchedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`movieId`)
                )
            """.trimIndent(),
            columns = listOf(
                "movieId",
                "title",
                "imagePoster",
                "imageCover",
                "type",
                "year",
                "status",
                "genre",
                "studio",
                "views",
                "favorites",
                "airedStart",
                "airedEnd",
                "day",
                "time",
                "episodeIndex",
                "episodeId",
                "watchedAt",
            ),
            indexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_history_watchedAt` ON `history` (`watchedAt`)",
            ),
        ),
        Table(
            name = "watch_progress",
            createSql = """
                CREATE TABLE `watch_progress` (
                    `episodeId` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL DEFAULT 0,
                    `durationMs` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`episodeId`)
                )
            """.trimIndent(),
            columns = listOf("episodeId", "positionMs", "durationMs", "updatedAt"),
            indexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_watch_progress_updatedAt` ON `watch_progress` (`updatedAt`)",
            ),
        ),
        Table(
            name = "search_history",
            createSql = """
                CREATE TABLE `search_history` (
                    `query` TEXT NOT NULL,
                    `searchedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`query`)
                )
            """.trimIndent(),
            columns = listOf("query", "searchedAt"),
            indexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_search_history_searchedAt` ON `search_history` (`searchedAt`)",
            ),
        ),
        Table(
            name = "episode_alerts",
            createSql = """
                CREATE TABLE `episode_alerts` (
                    `movieId` TEXT NOT NULL,
                    `title` TEXT,
                    `imagePoster` TEXT,
                    `lastEpisode` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`movieId`)
                )
            """.trimIndent(),
            columns = listOf("movieId", "title", "imagePoster", "lastEpisode", "createdAt"),
            indexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_episode_alerts_createdAt` ON `episode_alerts` (`createdAt`)",
            ),
        ),
        Table(
            name = "tracker_entries",
            createSql = """
                CREATE TABLE `tracker_entries` (
                    `source` TEXT NOT NULL DEFAULT '',
                    `syncId` TEXT NOT NULL DEFAULT '',
                    `name` TEXT NOT NULL DEFAULT '',
                    `url` TEXT NOT NULL DEFAULT '',
                    `status` INTEGER NOT NULL DEFAULT 0,
                    `episodesCompleted` INTEGER,
                    `episodesTotal` INTEGER,
                    `personalRating` INTEGER,
                    `lastUpdatedUnixTime` INTEGER,
                    `posterUrl` TEXT,
                    `releaseDate` INTEGER,
                    `synonymsJson` TEXT NOT NULL DEFAULT '[]',
                    `startDate` TEXT,
                    `finishDate` TEXT,
                    `crossMalId` INTEGER,
                    `mediaType` TEXT,
                    `genresJson` TEXT,
                    `studiosJson` TEXT,
                    `durationSeconds` INTEGER,
                    `rewatching` INTEGER NOT NULL DEFAULT 0,
                    `rewatchCount` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`source`, `syncId`)
                )
            """.trimIndent(),
            columns = listOf(
                "source",
                "syncId",
                "name",
                "url",
                "status",
                "episodesCompleted",
                "episodesTotal",
                "personalRating",
                "lastUpdatedUnixTime",
                "posterUrl",
                "releaseDate",
                "synonymsJson",
                "startDate",
                "finishDate",
                "crossMalId",
                "mediaType",
                "genresJson",
                "studiosJson",
                "durationSeconds",
                "rewatching",
                "rewatchCount",
            ),
            indexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_tracker_entries_source_status` " +
                    "ON `tracker_entries` (`source`, `status`)",
                "CREATE INDEX IF NOT EXISTS `index_tracker_entries_source_lastUpdatedUnixTime` " +
                    "ON `tracker_entries` (`source`, `lastUpdatedUnixTime`)",
            ),
        ),
        Table(
            name = "tracker_meta",
            createSql = """
                CREATE TABLE `tracker_meta` (
                    `source` TEXT NOT NULL,
                    `lastRefreshAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`source`)
                )
            """.trimIndent(),
            columns = listOf("source", "lastRefreshAt"),
        ),
        Table(
            name = "tracker_pending_ops",
            createSql = """
                CREATE TABLE `tracker_pending_ops` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `source` TEXT NOT NULL DEFAULT '',
                    `syncId` TEXT NOT NULL DEFAULT '',
                    `kind` TEXT NOT NULL DEFAULT '',
                    `payload` TEXT,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `attempts` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            columns = listOf("id", "source", "syncId", "kind", "payload", "createdAt", "attempts"),
            indexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_tracker_pending_ops_source_syncId` " +
                    "ON `tracker_pending_ops` (`source`, `syncId`)",
            ),
        ),
        Table(
            name = "cuplix_seen",
            createSql = """
                CREATE TABLE `cuplix_seen` (
                    `clipKey` TEXT NOT NULL,
                    `seenAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`clipKey`)
                )
            """.trimIndent(),
            columns = listOf("clipKey", "seenAt"),
        ),
    )

    // Versi 1..5 membangun ulang semua tabel; versi 6 sudah lengkap kecuali cuplix_seen.
    val all: Array<Migration> = (1..6)
        .map { from ->
            object : Migration(from, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val target = if (from < 6) tables else tables.filter { it.name == "cuplix_seen" }
                    target.forEach { table -> rebuildTable(db, table) }
                    target.flatMap { it.indexes }.forEach { db.execSQL(it) }
                }
            }
        }.toTypedArray()

    private fun rebuildTable(
        db: SupportSQLiteDatabase,
        table: Table,
    ) {
        val exists = db
            .query(
                SimpleSQLiteQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                    arrayOf(table.name),
                ),
            ).use { it.moveToFirst() }
        val backup = "__animex_migration_${table.name}"
        val oldColumns = if (exists) {
            db.query(SimpleSQLiteQuery("PRAGMA table_info(`${table.name}`)")).use { cursor ->
                buildSet {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
        } else {
            emptySet()
        }

        if (exists) {
            db.execSQL("DROP TABLE IF EXISTS `$backup`")
            db.execSQL("ALTER TABLE `${table.name}` RENAME TO `$backup`")
        }
        db.execSQL(table.createSql)

        val commonColumns = table.columns.filter { it in oldColumns }
        if (commonColumns.isNotEmpty()) {
            val names = commonColumns.joinToString(", ") { "`$it`" }
            db.execSQL("INSERT OR IGNORE INTO `${table.name}` ($names) SELECT $names FROM `$backup`")
        }
        if (exists) db.execSQL("DROP TABLE `$backup`")
    }
}
