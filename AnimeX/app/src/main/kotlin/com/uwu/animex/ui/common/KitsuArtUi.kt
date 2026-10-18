package com.uwu.animex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.KitsuArt
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.model.Movie

@Composable
private fun rememberKitsuArt(m: Movie?): KitsuArt.Art? {
    val enabled by Appearance.settings.collectAsStateWithLifecycle()
    val id = m?.id
    if (!enabled.kitsuArt || m == null || id == null) return null
    LaunchedEffect(id, m.title) { KitsuArt.request(m) }
    return KitsuArt.peek(id)
}

/** Poster anime: dari Kitsu bila fitur aktif dan ditemukan, selain itu poster bawaan. */
@Composable
fun Movie?.posterOrKitsu(): String? = rememberKitsuArt(this)?.poster ?: this?.image_poster

/** Cover/banner anime: Kitsu, lalu cover bawaan, lalu poster Kitsu, lalu poster bawaan. */
@Composable
fun Movie?.coverOrPosterKitsu(): String? {
    val art = rememberKitsuArt(this)
    return art?.cover
        ?: this?.image_cover?.takeIf { it.isNotBlank() }
        ?: art?.poster
        ?: this?.image_poster?.takeIf { it.isNotBlank() }
}
