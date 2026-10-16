package com.uwu.animex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.uwu.animex.data.model.Movie

val LocalOpenStatusSheet = staticCompositionLocalOf<(Movie) -> Unit> { {} }

@Composable
fun rememberStatusLongClick(m: Movie): (() -> Unit)? {
    val open = LocalOpenStatusSheet.current
    if (m.id == null) return null
    return remember(open, m) { { open(m) } }
}
