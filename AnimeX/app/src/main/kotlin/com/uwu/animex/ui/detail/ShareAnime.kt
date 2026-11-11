package com.uwu.animex.ui.detail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.uwu.animex.data.model.Movie

private const val APP_LINK = "https://github.com/HatsuneMikuUwU/AnimeX-App"

private const val SHARE_LINK = "https://hatsunemikuuwu.github.io/AnimeX-App/anime.html?id="

internal fun shareAnime(
    context: Context,
    movie: Movie,
) {
    val title = movie.title?.trim().orEmpty()
    if (title.isEmpty()) return
    val meta =
        listOfNotNull(
            movie.type?.trim()?.takeIf { it.isNotEmpty() },
            movie.year?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" • ")
    val genres = movie.genre?.trim()?.takeIf { it.isNotEmpty() }
    val text =
        buildString {
            append("Nonton \"$title\" di AnimeX")
            if (meta.isNotEmpty()) append("\n$meta")
            if (genres != null) append("\n$genres")
            val id = movie.id?.trim()?.takeIf { it.isNotEmpty() }
            if (id != null) append("\n\nBuka di AnimeX: ${SHARE_LINK + Uri.encode(id)}")
            append("\n\n$APP_LINK")
        }
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    val chooser = Intent.createChooser(send, "Bagikan anime")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

internal fun shareEpisode(
    context: Context,
    animeTitle: String,
    movieId: String,
    epId: String,
    epIndex: String?,
) {
    val title = animeTitle.trim()
    if (title.isEmpty() || movieId.isBlank() || epId.isBlank()) return
    val idx = epIndex?.trim()?.takeIf { it.isNotEmpty() }
    val label = if (idx != null) "$title - Ep $idx" else title
    val link =
        buildString {
            append(SHARE_LINK).append(Uri.encode(movieId))
            append("&ep=").append(Uri.encode(epId))
            if (idx != null) append("&idx=").append(Uri.encode(idx))
            append("&t=").append(Uri.encode(title))
        }
    val text = "Nonton \"$label\" di AnimeX\n\nBuka episode ini: $link\n\n$APP_LINK"
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, label)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    val chooser = Intent.createChooser(send, "Bagikan episode")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}
