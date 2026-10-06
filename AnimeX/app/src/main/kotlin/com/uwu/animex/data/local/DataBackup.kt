package com.uwu.animex.data.local

import android.content.Context
import android.net.Uri
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.BookmarkEntity
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.data.local.db.HistoryEntity
import com.uwu.animex.data.local.db.ProgressEntity
import com.uwu.animex.data.local.db.SearchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & restore data pengguna ke satu berkas JSON (lewat Storage Access Framework).
 *
 * Yang disimpan: bookmark/favorit, riwayat nonton, progres episode, riwayat pencarian,
 * pengingat episode, dan pengaturan tampilan.
 * Yang TIDAK disimpan: token login MAL / akun (sensitif), unduhan offline, dan cache.
 *
 * Restore bersifat menggabungkan (upsert): data yang ada di perangkat tidak dihapus,
 * data dengan id yang sama ditimpa dengan versi dari backup.
 *
 * Pakai org.json bawaan Android (bukan Gson reflection) supaya aman dari R8/minify.
 */
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

    class InvalidBackupException(message: String) : Exception(message)

    fun suggestedFileName(): String =
        "AnimeX-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

    suspend fun export(context: Context, uri: Uri): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = AnimeDatabase.get(app).animeDao()

        val bookmarks = dao.getAllBookmarks()
        val history = dao.getAllHistory()
        val progress = dao.getAllProgress()
        val searches = dao.getAllSearchHistory()
        val alerts = dao.getEpisodeAlerts()
        val a = Appearance.settings.value

        val root = JSONObject()
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
                    .put("coverTheme", a.coverTheme)
                    .put("blur", a.blur)
                    .put("paletteStyle", a.paletteStyle.name)
                    .put("colorSpec", a.colorSpec.name),
            )
            .put("bookmarks", JSONArray(bookmarks.map { it.toJson() }))
            .put("history", JSONArray(history.map { it.toJson() }))
            .put("progress", JSONArray(progress.map { it.toJson() }))
            .put("searchHistory", JSONArray(searches.map { it.toJson() }))
            .put("episodeAlerts", JSONArray(alerts.map { it.toJson() }))

        val out = app.contentResolver.openOutputStream(uri, "wt")
            ?: throw java.io.IOException("Gagal membuka berkas tujuan")
        out.use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }

        Summary(bookmarks.size, history.size, progress.size, searches.size, alerts.size)
    }

    suspend fun restore(context: Context, uri: Uri): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw java.io.IOException("Gagal membuka berkas backup")

        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            throw InvalidBackupException("Berkas bukan backup AnimeX yang valid")
        }
        if (root.optString("app") != APP) throw InvalidBackupException("Berkas bukan backup AnimeX yang valid")
        if (root.optInt("format", 0) > FORMAT) {
            throw InvalidBackupException("Backup dibuat dari versi AnimeX yang lebih baru, perbarui aplikasinya dulu")
        }

        // Parse semua dulu sebelum nulis apa pun, supaya berkas rusak nggak bikin data setengah jalan.
        val bookmarks = root.objects("bookmarks").mapNotNull { it.toBookmark() }
        val history = root.objects("history").mapNotNull { it.toHistory() }
        val progress = root.objects("progress").mapNotNull { it.toProgress() }
        val searches = root.objects("searchHistory").mapNotNull { it.toSearch() }
        val alerts = root.objects("episodeAlerts").mapNotNull { it.toAlert() }
        val appearance = root.optJSONObject("appearance")?.toAppearance()

        val dao = AnimeDatabase.get(app).animeDao()
        bookmarks.forEach { dao.upsertBookmark(it) }
        history.forEach { dao.upsertHistory(it) }
        progress.forEach { dao.upsertProgress(it) }
        searches.forEach { dao.upsertSearch(it) }
        alerts.forEach { dao.upsertEpisodeAlert(it) }
        dao.trimHistory()
        dao.trimProgress()
        dao.trimSearch()
        if (appearance != null) Appearance.restore(appearance)

        Summary(bookmarks.size, history.size, progress.size, searches.size, alerts.size)
    }

    /* ----------------------------------- JSON helpers ----------------------------------- */

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { has(key) }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun BookmarkEntity.toJson() = JSONObject()
        .put("movieId", movieId).put("title", title).put("imagePoster", imagePoster)
        .put("imageCover", imageCover).put("type", type).put("year", year).put("genre", genre)
        .put("studio", studio).put("status", status).put("favorite", favorite).put("updatedAt", updatedAt)

    private fun JSONObject.toBookmark(): BookmarkEntity? {
        val id = str("movieId") ?: return null
        return BookmarkEntity(
            movieId = id, title = str("title"), imagePoster = str("imagePoster"),
            imageCover = str("imageCover"), type = str("type"), year = str("year"),
            genre = str("genre"), studio = str("studio"), status = str("status"),
            favorite = optBoolean("favorite", false),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun HistoryEntity.toJson() = JSONObject()
        .put("movieId", movieId).put("title", title).put("imagePoster", imagePoster)
        .put("imageCover", imageCover).put("type", type).put("year", year).put("status", status)
        .put("genre", genre).put("studio", studio).put("views", views).put("favorites", favorites)
        .put("airedStart", airedStart).put("airedEnd", airedEnd).put("day", day).put("time", time)
        .put("episodeIndex", episodeIndex).put("episodeId", episodeId).put("watchedAt", watchedAt)

    private fun JSONObject.toHistory(): HistoryEntity? {
        val id = str("movieId") ?: return null
        return HistoryEntity(
            movieId = id, title = str("title"), imagePoster = str("imagePoster"),
            imageCover = str("imageCover"), type = str("type"), year = str("year"),
            status = str("status"), genre = str("genre"), studio = str("studio"),
            views = str("views"), favorites = str("favorites"), airedStart = str("airedStart"),
            airedEnd = str("airedEnd"), day = str("day"), time = str("time"),
            episodeIndex = str("episodeIndex"), episodeId = str("episodeId"),
            watchedAt = optLong("watchedAt", System.currentTimeMillis()),
        )
    }

    private fun ProgressEntity.toJson() = JSONObject()
        .put("episodeId", episodeId).put("positionMs", positionMs)
        .put("durationMs", durationMs).put("updatedAt", updatedAt)

    private fun JSONObject.toProgress(): ProgressEntity? {
        val id = str("episodeId") ?: return null
        return ProgressEntity(
            episodeId = id,
            positionMs = optLong("positionMs", 0L),
            durationMs = optLong("durationMs", 0L),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun SearchHistoryEntity.toJson() = JSONObject()
        .put("query", query).put("searchedAt", searchedAt)

    private fun JSONObject.toSearch(): SearchHistoryEntity? {
        val q = str("query")?.takeIf { it.isNotBlank() } ?: return null
        return SearchHistoryEntity(query = q, searchedAt = optLong("searchedAt", System.currentTimeMillis()))
    }

    private fun EpisodeAlertEntity.toJson() = JSONObject()
        .put("movieId", movieId).put("title", title).put("imagePoster", imagePoster)
        .put("lastEpisode", lastEpisode).put("createdAt", createdAt)

    private fun JSONObject.toAlert(): EpisodeAlertEntity? {
        val id = str("movieId") ?: return null
        return EpisodeAlertEntity(
            movieId = id, title = str("title"), imagePoster = str("imagePoster"),
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
            coverTheme = optBoolean("coverTheme", d.coverTheme),
            blur = optBoolean("blur", d.blur),
            paletteStyle = runCatching { PaletteStyle.valueOf(optString("paletteStyle")) }.getOrDefault(d.paletteStyle),
            colorSpec = runCatching { ColorSpec.valueOf(optString("colorSpec")) }.getOrDefault(d.colorSpec),
        )
    }
}
