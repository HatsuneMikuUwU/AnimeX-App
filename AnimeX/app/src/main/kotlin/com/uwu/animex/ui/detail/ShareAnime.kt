package com.uwu.animex.ui.detail

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.uwu.animex.data.model.Movie

private const val APP_LINK = "https://github.com/HatsuneMikuUwU/AnimeX-App"

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
