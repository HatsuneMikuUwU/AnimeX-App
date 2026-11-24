package com.uwu.animex.data.local

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.BookmarkEntity
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.data.local.db.HistoryEntity
import com.uwu.animex.data.local.db.ProgressEntity
import com.uwu.animex.data.local.db.SearchHistoryEntity
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.ui.background.BackgroundType
import com.uwu.animex.ui.background.RandomInterval
import com.uwu.animex.ui.theme.coerceToColorAnimMs
import com.uwu.animex.ui.theme.coerceToFontScale
import com.uwu.animex.ui.theme.coerceToHomeCardScale
import com.uwu.animex.ui.theme.coerceToUiScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataBackup {
    private const val APP = "AnimeX"
    private const val FORMAT = 1
    private val gson = Gson()

    data class Summary(
        val bookmarks: Int,
        val history: Int,
        val progress: Int,
        val searches: Int,
        val alerts: Int,
    ) {
        val total get() = bookmarks + history + progress + searches + alerts
    }

    class InvalidBackupException(
        message: String,
    ) : Exception(message)

    fun suggestedFileName(): String =
        "AnimeX-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

    suspend fun export(
        context: Context,
        uri: Uri,
    ): Summary =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val dao = AnimeDatabase.get(app).animeDao()

            val bookmarks = dao.getAllBookmarks()
            val history = dao.getAllHistory()
            val progress = dao.getAllProgress()
            val searches = dao.getAllSearchHistory()
            val alerts = dao.getEpisodeAlerts()
            val a = Appearance.settings.value
            val n = NotificationPrefs.state.value

            val root =
                JsonObject().apply {
                    addProperty("app", APP)
                    addProperty("format", FORMAT)
                    addProperty("createdAt", System.currentTimeMillis())
                    add(
                        "appearance",
                        JsonObject().apply {
                            addProperty("mode", a.mode.name)
                            addProperty("dynamicColor", a.dynamicColor)
                            addProperty("accent", a.accent.name)
                            addProperty("amoled", a.amoled)
                            addProperty("blur", a.blur)
                            addProperty("barEffect", a.barEffect.name)
                            addProperty("paletteStyle", a.paletteStyle.name)
                            addProperty("colorSpec", a.colorSpec.name)
                            addProperty("contrast", a.contrast.toDouble())
                            addProperty("customSeed", a.customSeed)
                            addProperty("cmfTertiarySeed", a.cmfTertiarySeed)
                            add(
                                "roleOverrides",
                                JsonObject().apply {
                                    addProperty("primary", a.roleOverrides.primary)
                                    addProperty("secondary", a.roleOverrides.secondary)
                                    addProperty("tertiary", a.roleOverrides.tertiary)
                                    addProperty("neutral", a.roleOverrides.neutral)
                                    addProperty("neutralVariant", a.roleOverrides.neutralVariant)
                                    addProperty("error", a.roleOverrides.error)
                                },
                            )
                            addProperty("animateColorChange", a.animateColorChange)
                            addProperty("colorAnimationMs", a.colorAnimationMs)
                            addProperty("uiScale", a.uiScale.toDouble())
                            addProperty("fontScale", a.fontScale.toDouble())
                            addProperty("autoScaleUi", a.autoScaleUi)
                            addProperty("homeCardScale", a.homeCardScale.toDouble())
                            addProperty("compactNumbers", a.compactNumbers)
                            addProperty("hideStats", a.hideStats)
                            addProperty("hapticsEnabled", a.hapticsEnabled)
                            addProperty("backgroundType", a.backgroundType.name)
                            addProperty("backgroundParallax", a.backgroundParallax)
                            addProperty("randomInterval", a.randomInterval.name)
                            addProperty("seasonalThemes", a.seasonalThemes)
                        },
                    )
                    add("player", PlayerPrefs.exportJson())
                    add("homeSections", HomeSectionPrefs.exportJson())
                    add(
                        "notifications",
                        JsonObject().apply {
                            addProperty("enabled", n.enabled)
                            addProperty("episodes", n.episodes)
                            addProperty("appUpdates", n.appUpdates)
                            addProperty("prereleases", n.prereleases)
                            addProperty("discussions", n.discussions)
                        },
                    )
                    add(
                        "cuplix",
                        JsonObject().apply {
                            addProperty("enabled", CuplixPrefs.enabled.value)
                            addProperty("muted", CuplixPrefs.muted.value)
                        },
                    )
                    add(
                        "sync",
                        JsonObject().apply {
                            addProperty("source", SyncSourcePref.source.value.name)
                            addProperty("mirror", SyncSourcePref.mirror.value)
                        },
                    )
                    add("bookmarks", JsonArray().apply { bookmarks.forEach { add(it.toJson()) } })
                    add("history", JsonArray().apply { history.forEach { add(it.toJson()) } })
                    add("progress", JsonArray().apply { progress.forEach { add(it.toJson()) } })
                    add("searchHistory", JsonArray().apply { searches.forEach { add(it.toJson()) } })
                    add("episodeAlerts", JsonArray().apply { alerts.forEach { add(it.toJson()) } })
                }

            val out =
                app.contentResolver.openOutputStream(uri, "wt")
                    ?: throw java.io.IOException("Gagal membuka berkas tujuan")
            out.use { it.write(gson.toJson(root).toByteArray(Charsets.UTF_8)) }

            Summary(bookmarks.size, history.size, progress.size, searches.size, alerts.size)
        }

    suspend fun restore(
        context: Context,
        uri: Uri,
    ): Summary =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val text =
                app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw java.io.IOException("Gagal membuka berkas backup")

            val root =
                try {
                    JsonParser.parseString(text).asJsonObject
                } catch (_: Exception) {
                    throw InvalidBackupException("Berkas bukan backup AnimeX yang valid")
                }
            if (root.str("app") != APP) throw InvalidBackupException("Berkas bukan backup AnimeX yang valid")
            if ((root.intOrNull("format") ?: 0) > FORMAT) {
                throw InvalidBackupException(
                    "Backup dibuat dari versi AnimeX yang lebih baru, perbarui aplikasinya dulu",
                )
            }

            val bookmarks = root.objects("bookmarks").mapNotNull { it.toBookmark() }
            val history = root.objects("history").mapNotNull { it.toHistory() }
            val progress = root.objects("progress").mapNotNull { it.toProgress() }
            val searches = root.objects("searchHistory").mapNotNull { it.toSearch() }
            val alerts = root.objects("episodeAlerts").mapNotNull { it.toAlert() }
            val appearanceObj = root.objOrNull("appearance")
            val appearance = appearanceObj?.toAppearance()
            val player = root.objOrNull("player")
            val homeSections = root.objOrNull("homeSections")
            val notifications = root.objOrNull("notifications")
            val cuplix = root.objOrNull("cuplix")
            val sync = root.objOrNull("sync")

            val database = AnimeDatabase.get(app)
            val dao = database.animeDao()
            database.withTransaction {
                dao.upsertBookmarks(bookmarks)
                dao.upsertHistoryAll(history)
                dao.upsertProgressAll(progress)
                dao.upsertSearches(searches)
                dao.upsertEpisodeAlerts(alerts)
                dao.trimHistory()
                dao.trimProgress()
                dao.trimSearch()
            }
            if (appearance != null) {
                Appearance.restore(appearance)
                if (appearanceObj.has("uiScale")) Appearance.setUiScale(appearance.uiScale)
                if (appearanceObj.has("fontScale")) Appearance.setFontScale(appearance.fontScale)
                if (appearanceObj.has("autoScaleUi")) Appearance.setAutoScaleUi(appearance.autoScaleUi)
            }
            if (player != null) PlayerPrefs.restoreJson(player)
            if (homeSections != null) HomeSectionPrefs.restoreJson(homeSections)
            if (notifications != null) {
                NotificationPrefs.update {
                    copy(
                        enabled = notifications.boolOr("enabled", enabled),
                        episodes = notifications.boolOr("episodes", episodes),
                        appUpdates = notifications.boolOr("appUpdates", appUpdates),
                        prereleases = notifications.boolOr("prereleases", prereleases),
                        discussions = notifications.boolOr("discussions", discussions),
                    )
                }
            }
            if (cuplix != null) {
                cuplix.boolOrNull("enabled")?.let(CuplixPrefs::setEnabled)
                cuplix.boolOrNull("muted")?.let(CuplixPrefs::setMuted)
            }
            if (sync != null) {
                sync.str("source")?.let { s -> runCatching { SyncSource.valueOf(s) }.getOrNull() }?.let(SyncSourcePref::set)
                sync.boolOrNull("mirror")?.let(SyncSourcePref::setMirror)
            }

            Summary(bookmarks.size, history.size, progress.size, searches.size, alerts.size)
        }

    private fun JsonObject.str(key: String): String? {
        val el = get(key) ?: return null
        return if (el.isJsonNull || !el.isJsonPrimitive) null else el.asString
    }

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.intOr(
        key: String,
        fallback: Int,
    ): Int = intOrNull(key) ?: fallback

    private fun JsonObject.longOr(
        key: String,
        fallback: Long,
    ): Long = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: fallback

    private fun JsonObject.doubleOr(
        key: String,
        fallback: Double,
    ): Double = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() } ?: fallback

    private fun JsonObject.boolOrNull(key: String): Boolean? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }

    private fun JsonObject.boolOr(
        key: String,
        fallback: Boolean,
    ): Boolean = boolOrNull(key) ?: fallback

    private fun JsonObject.objOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.objects(key: String): List<JsonObject> {
        val arr = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { it.takeIf { el -> el.isJsonObject }?.asJsonObject }
    }

    private fun BookmarkEntity.toJson() =
        JsonObject().apply {
            addProperty("movieId", movieId)
            addProperty("title", title)
            addProperty("imagePoster", imagePoster)
            addProperty("imageCover", imageCover)
            addProperty("type", type)
            addProperty("year", year)
            addProperty("genre", genre)
            addProperty("studio", studio)
            addProperty("status", status)
            addProperty("updatedAt", updatedAt)
        }

    private fun JsonObject.toBookmark(): BookmarkEntity? {
        val id = str("movieId") ?: return null
        val status = str("status")?.takeIf { it.isNotBlank() } ?: return null
        return BookmarkEntity(
            movieId = id,
            title = str("title"),
            imagePoster = str("imagePoster"),
            imageCover = str("imageCover"),
            type = str("type"),
            year = str("year"),
            genre = str("genre"),
            studio = str("studio"),
            status = status,
            updatedAt = longOr("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun HistoryEntity.toJson() =
        JsonObject().apply {
            addProperty("movieId", movieId)
            addProperty("title", title)
            addProperty("imagePoster", imagePoster)
            addProperty("imageCover", imageCover)
            addProperty("type", type)
            addProperty("year", year)
            addProperty("status", status)
            addProperty("genre", genre)
            addProperty("studio", studio)
            addProperty("views", views)
            addProperty("favorites", favorites)
            addProperty("airedStart", airedStart)
            addProperty("airedEnd", airedEnd)
            addProperty("day", day)
            addProperty("time", time)
            addProperty("episodeIndex", episodeIndex)
            addProperty("episodeId", episodeId)
            addProperty("watchedAt", watchedAt)
        }

    private fun JsonObject.toHistory(): HistoryEntity? {
        val id = str("movieId") ?: return null
        return HistoryEntity(
            movieId = id,
            title = str("title"),
            imagePoster = str("imagePoster"),
            imageCover = str("imageCover"),
            type = str("type"),
            year = str("year"),
            status = str("status"),
            genre = str("genre"),
            studio = str("studio"),
            views = str("views"),
            favorites = str("favorites"),
            airedStart = str("airedStart"),
            airedEnd = str("airedEnd"),
            day = str("day"),
            time = str("time"),
            episodeIndex = str("episodeIndex"),
            episodeId = str("episodeId"),
            watchedAt = longOr("watchedAt", System.currentTimeMillis()),
        )
    }

    private fun ProgressEntity.toJson() =
        JsonObject().apply {
            addProperty("episodeId", episodeId)
            addProperty("positionMs", positionMs)
            addProperty("durationMs", durationMs)
            addProperty("updatedAt", updatedAt)
        }

    private fun JsonObject.toProgress(): ProgressEntity? {
        val id = str("episodeId") ?: return null
        return ProgressEntity(
            episodeId = id,
            positionMs = longOr("positionMs", 0L),
            durationMs = longOr("durationMs", 0L),
            updatedAt = longOr("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun SearchHistoryEntity.toJson() =
        JsonObject().apply {
            addProperty("query", query)
            addProperty("searchedAt", searchedAt)
        }

    private fun JsonObject.toSearch(): SearchHistoryEntity? {
        val q = str("query")?.takeIf { it.isNotBlank() } ?: return null
        return SearchHistoryEntity(query = q, searchedAt = longOr("searchedAt", System.currentTimeMillis()))
    }

    private fun EpisodeAlertEntity.toJson() =
        JsonObject().apply {
            addProperty("movieId", movieId)
            addProperty("title", title)
            addProperty("imagePoster", imagePoster)
            addProperty("lastEpisode", lastEpisode)
            addProperty("createdAt", createdAt)
        }

    private fun JsonObject.toAlert(): EpisodeAlertEntity? {
        val id = str("movieId") ?: return null
        return EpisodeAlertEntity(
            movieId = id,
            title = str("title"),
            imagePoster = str("imagePoster"),
            lastEpisode = intOr("lastEpisode", 0),
            createdAt = longOr("createdAt", System.currentTimeMillis()),
        )
    }

    private fun JsonObject.toAppearance(): AppearanceSettings {
        val d = AppearanceSettings()
        return AppearanceSettings(
            mode = str("mode")?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.mode,
            dynamicColor = boolOr("dynamicColor", d.dynamicColor),
            accent = str("accent")?.let { runCatching { AccentPalette.valueOf(it) }.getOrNull() } ?: d.accent,
            amoled = boolOr("amoled", d.amoled),
            blur = boolOr("blur", d.blur),
            barEffect = str("barEffect")?.let { runCatching { BarEffect.valueOf(it) }.getOrNull() } ?: d.barEffect,
            paletteStyle = str("paletteStyle")?.let { runCatching { PaletteStyle.valueOf(it) }.getOrNull() } ?: d.paletteStyle,
            colorSpec = str("colorSpec")?.let { runCatching { ColorSpec.valueOf(it) }.getOrNull() } ?: d.colorSpec,
            contrast = doubleOr("contrast", d.contrast.toDouble()).toFloat().coerceToContrast(),
            customSeed = intOr("customSeed", d.customSeed),
            cmfTertiarySeed = intOr("cmfTertiarySeed", d.cmfTertiarySeed),
            roleOverrides =
                objOrNull("roleOverrides")?.let {
                    RoleOverrides(
                        primary = it.intOr("primary", 0),
                        secondary = it.intOr("secondary", 0),
                        tertiary = it.intOr("tertiary", 0),
                        neutral = it.intOr("neutral", 0),
                        neutralVariant = it.intOr("neutralVariant", 0),
                        error = it.intOr("error", 0),
                    )
                } ?: d.roleOverrides,
            animateColorChange = boolOr("animateColorChange", d.animateColorChange),
            colorAnimationMs = intOr("colorAnimationMs", d.colorAnimationMs).coerceToColorAnimMs(),
            uiScale = doubleOr("uiScale", d.uiScale.toDouble()).toFloat().coerceToUiScale(),
            fontScale = doubleOr("fontScale", d.fontScale.toDouble()).toFloat().coerceToFontScale(),
            autoScaleUi = boolOr("autoScaleUi", d.autoScaleUi),
            homeCardScale = doubleOr("homeCardScale", d.homeCardScale.toDouble()).toFloat().coerceToHomeCardScale(),
            compactNumbers = boolOr("compactNumbers", d.compactNumbers),
            hideStats = boolOr("hideStats", d.hideStats),
            hapticsEnabled = boolOr("hapticsEnabled", d.hapticsEnabled),
            backgroundType =
                str("backgroundType")?.let { runCatching { BackgroundType.valueOf(it) }.getOrNull() } ?: d.backgroundType,
            backgroundParallax = boolOr("backgroundParallax", d.backgroundParallax),
            randomInterval =
                str("randomInterval")?.let { runCatching { RandomInterval.valueOf(it) }.getOrNull() } ?: d.randomInterval,
            seasonalThemes = boolOr("seasonalThemes", d.seasonalThemes),
        )
    }
}
