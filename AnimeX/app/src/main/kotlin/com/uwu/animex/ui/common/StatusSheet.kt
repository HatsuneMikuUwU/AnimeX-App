package com.uwu.animex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.uwu.animex.data.model.Movie

val LocalOpenStatusSheet = staticCompositionLocalOf<(Movie) -> Unit> { {} }

@Composable
fun rememberStatusLongClick(m: Movie): (() -> Unit)? {
    val open = LocalOpenStatusSheet.current
    val haptic = LocalHapticFeedback.current
    if (m.id == null) return null
    return remember(m, open, haptic) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            open(m)
        }
    }
}
