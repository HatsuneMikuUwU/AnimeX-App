package com.uwu.animex.data.local

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.uwu.animex.core.cache.CachePrefs
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.BookmarkEntity
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.data.local.db.HistoryEntity
import com.uwu.animex.data.local.db.ProgressEntity
import com.uwu.animex.data.local.db.SearchHistoryEntity
import com.uwu.animex.data.nekopoi.NekopoiPrefs
import com.uwu.animex.ui.background.BackgroundType
import com.uwu.animex.ui.background.RandomInterval
import com.uwu.animex.ui.theme.coerceToColorAnimMs
import com.uwu.animex.ui.theme.coerceToHomeCardScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataBackup {
    private const val APP = "AnimeX"
    private const val FORMAT = 1

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

            val root =
                JSONObject()
                    .put("app", APP)
                    .put("format", FORMAT)
                    .put("createdAt", System.currentTimeMillis())
                    .put(
                        "appearance",
                        JSONObject()
                            .put("mode", a.mode.name)
                            .put("dynamicColor", a.dynamicColor)
                            .put("accent", a.accent.name)
                            .put("amoled", a.amoled)
                            .put("blur", a.blur)
                            .put("barEffect", a.barEffect.name)
                            .put("paletteStyle", a.paletteStyle.name)
                            .put("colorSpec", a.colorSpec.name)
                            .put("contrast", a.contrast.toDouble())
                            .put("customSeed", a.customSeed)
                            .put("cmfTertiarySeed", a.cmfTertiarySeed)
                            .put(
                                "roleOverrides",
                                JSONObject()
                                    .put("primary", a.roleOverrides.primary)
                                    .put("secondary", a.roleOverrides.secondary)
                                    .put("tertiary", a.roleOverrides.tertiary)
                                    .put("neutral", a.roleOverrides.neutral)
                                    .put("neutralVariant", a.roleOverrides.neutralVariant)
                                    .put("error", a.roleOverrides.error),
                            ).put("animateColorChange", a.animateColorChange)
                            .put("colorAnimationMs", a.colorAnimationMs)
                            .put("homeCardScale", a.homeCardScale.toDouble())
                            .put("compactNumbers", a.compactNumbers)
                            .put("hapticsEnabled", a.hapticsEnabled)
                            .put("backgroundType", a.backgroundType.name)
                            .put("backgroundParallax", a.backgroundParallax)
                            .put("randomInterval", a.randomInterval.name)
                            .put("seasonalThemes", a.seasonalThemes),
                    ).put("player", PlayerPrefs.exportJson())
                    .put("cache", CachePrefs.exportJson())
                    .put("nekopoi", NekopoiPrefs.exportJson())
                    .put("bookmarks", JSONArray(bookmarks.map { it.toJson() }))
                    .put("history", JSONArray(history.map { it.toJson() }))
                    .put("progress", JSONArray(progress.map { it.toJson() }))
                    .put("searchHistory", JSONArray(searches.map { it.toJson() }))
                    .put("episodeAlerts", JSONArray(alerts.map { it.toJson() }))

            val out =
                app.contentResolver.openOutputStream(uri, "wt")
                    ?: throw java.io.IOException("Gagal membuka berkas tujuan")
            out.use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }

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
                    JSONObject(text)
                } catch (_: Exception) {
                    throw InvalidBackupException("Berkas bukan backup AnimeX yang valid")
                }
            if (root.optString("app") != APP) throw InvalidBackupException("Berkas bukan backup AnimeX yang valid")
            if (root.optInt("format", 0) > FORMAT) {
                throw InvalidBackupException(
                    "Backup dibuat dari versi AnimeX yang lebih baru, perbarui aplikasinya dulu",
                )
            }

            val bookmarks = root.objects("bookmarks").mapNotNull { it.toBookmark() }
            val history = root.objects("history").mapNotNull { it.toHistory() }
            val progress = root.objects("progress").mapNotNull { it.toProgress() }
            val searches = root.objects("searchHistory").mapNotNull { it.toSearch() }
            val alerts = root.objects("episodeAlerts").mapNotNull { it.toAlert() }
            val appearance = root.optJSONObject("appearance")?.toAppearance()
            val player = root.optJSONObject("player")

            val database = AnimeDatabase.get(app)
            val dao = database.animeDao()
            database.withTransaction {
                bookmarks.forEach { dao.upsertBookmark(it) }
                history.forEach { dao.upsertHistory(it) }
                progress.forEach { dao.upsertProgress(it) }
                searches.forEach { dao.upsertSearch(it) }
                alerts.forEach { dao.upsertEpisodeAlert(it) }
                dao.trimHistory()
                dao.trimProgress()
                dao.trimSearch()
            }
            if (appearance != null) Appearance.restore(appearance)
            if (player != null) PlayerPrefs.restoreJson(player)
            root.optJSONObject("cache")?.let { CachePrefs.restoreJson(it) }
            root.optJSONObject("nekopoi")?.let { NekopoiPrefs.restoreJson(it) }

            Summary(bookmarks.size, history.size, progress.size, searches.size, alerts.size)
        }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { has(key) }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun BookmarkEntity.toJson() =
        JSONObject()
            .put("movieId", movieId)
            .put("title", title)
            .put("imagePoster", imagePoster)
            .put("imageCover", imageCover)
            .put("type", type)
            .put("year", year)
            .put("genre", genre)
            .put("studio", studio)
            .put("status", status)
            .put("updatedAt", updatedAt)

    private fun JSONObject.toBookmark(): BookmarkEntity? {
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
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun HistoryEntity.toJson() =
        JSONObject()
            .put("movieId", movieId)
            .put("title", title)
            .put("imagePoster", imagePoster)
            .put("imageCover", imageCover)
            .put("type", type)
            .put("year", year)
            .put("status", status)
            .put("genre", genre)
            .put("studio", studio)
            .put("views", views)
            .put("favorites", favorites)
            .put("airedStart", airedStart)
            .put("airedEnd", airedEnd)
            .put("day", day)
            .put("time", time)
            .put("episodeIndex", episodeIndex)
            .put("episodeId", episodeId)
            .put("watchedAt", watchedAt)

    private fun JSONObject.toHistory(): HistoryEntity? {
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
            watchedAt = optLong("watchedAt", System.currentTimeMillis()),
        )
    }

    private fun ProgressEntity.toJson() =
        JSONObject()
            .put("episodeId", episodeId)
            .put("positionMs", positionMs)
            .put("durationMs", durationMs)
            .put("updatedAt", updatedAt)

    private fun JSONObject.toProgress(): ProgressEntity? {
        val id = str("episodeId") ?: return null
        return ProgressEntity(
            episodeId = id,
            positionMs = optLong("positionMs", 0L),
            durationMs = optLong("durationMs", 0L),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun SearchHistoryEntity.toJson() =
        JSONObject()
            .put("query", query)
            .put("searchedAt", searchedAt)

    private fun JSONObject.toSearch(): SearchHistoryEntity? {
        val q = str("query")?.takeIf { it.isNotBlank() } ?: return null
        return SearchHistoryEntity(query = q, searchedAt = optLong("searchedAt", System.currentTimeMillis()))
    }

    private fun EpisodeAlertEntity.toJson() =
        JSONObject()
            .put("movieId", movieId)
            .put("title", title)
            .put("imagePoster", imagePoster)
            .put("lastEpisode", lastEpisode)
            .put("createdAt", createdAt)

    private fun JSONObject.toAlert(): EpisodeAlertEntity? {
        val id = str("movieId") ?: return null
        return EpisodeAlertEntity(
            movieId = id,
            title = str("title"),
            imagePoster = str("imagePoster"),
            lastEpisode = optInt("lastEpisode", 0),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
        )
    }

    private fun JSONObject.toAppearance(): AppearanceSettings {
        val d = AppearanceSettings()
        return AppearanceSettings(
            mode = runCatching { ThemeMode.valueOf(optString("mode")) }.getOrDefault(d.mode),
            dynamicColor = optBoolean("dynamicColor", d.dynamicColor),
            accent = runCatching { AccentPalette.valueOf(optString("accent")) }.getOrDefault(d.accent),
            amoled = optBoolean("amoled", d.amoled),
            blur = optBoolean("blur", d.blur),
            barEffect = runCatching { BarEffect.valueOf(optString("barEffect")) }.getOrDefault(d.barEffect),
            paletteStyle = runCatching { PaletteStyle.valueOf(optString("paletteStyle")) }.getOrDefault(d.paletteStyle),
            colorSpec = runCatching { ColorSpec.valueOf(optString("colorSpec")) }.getOrDefault(d.colorSpec),
            contrast = optDouble("contrast", d.contrast.toDouble()).toFloat().coerceToContrast(),
            customSeed = optInt("customSeed", d.customSeed),
            cmfTertiarySeed = optInt("cmfTertiarySeed", d.cmfTertiarySeed),
            roleOverrides =
                optJSONObject("roleOverrides")?.let {
                    RoleOverrides(
                        primary = it.optInt("primary", 0),
                        secondary = it.optInt("secondary", 0),
                        tertiary = it.optInt("tertiary", 0),
                        neutral = it.optInt("neutral", 0),
                        neutralVariant = it.optInt("neutralVariant", 0),
                        error = it.optInt("error", 0),
                    )
                } ?: d.roleOverrides,
            animateColorChange = optBoolean("animateColorChange", d.animateColorChange),
            colorAnimationMs = optInt("colorAnimationMs", d.colorAnimationMs).coerceToColorAnimMs(),
            homeCardScale = optDouble("homeCardScale", d.homeCardScale.toDouble()).toFloat().coerceToHomeCardScale(),
            compactNumbers = optBoolean("compactNumbers", d.compactNumbers),
            hapticsEnabled = optBoolean("hapticsEnabled", d.hapticsEnabled),
            backgroundType =
                runCatching { BackgroundType.valueOf(optString("backgroundType")) }.getOrDefault(d.backgroundType),
            backgroundParallax = optBoolean("backgroundParallax", d.backgroundParallax),
            randomInterval =
                runCatching { RandomInterval.valueOf(optString("randomInterval")) }.getOrDefault(d.randomInterval),
            seasonalThemes = optBoolean("seasonalThemes", d.seasonalThemes),
        )
    }
}
