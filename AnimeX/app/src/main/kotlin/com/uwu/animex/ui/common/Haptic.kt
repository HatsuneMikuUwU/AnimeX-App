package com.uwu.animex.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance

/** Returns a function that performs system haptic feedback (no-op when haptics are off). */
@Composable
private fun rememberHapticPerformer(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val enabled = settings.hapticsEnabled
    return remember(haptic, enabled) {
        { type -> if (enabled) haptic.performHapticFeedback(type) }
    }
}

/**
 * Returns a click lambda that performs haptic feedback (when enabled in settings)
 * then runs [block]. Mirrors aShellYou's hapticClick pattern.
 */
@Composable
fun hapticClick(
    type: HapticFeedbackType = HapticFeedbackType.ContextClick,
    block: () -> Unit,
): () -> Unit {
    val perform = rememberHapticPerformer()
    val latestBlock by rememberUpdatedState(block)
    return remember(type, perform) {
        {
            perform(type)
            latestBlock()
        }
    }
}

/**
 * Fire-and-forget haptic. Use when you already have a click handler
 * and only need the vibration side-effect (e.g. long-press, slider ticks).
 */
@Composable
fun rememberHapticAction(
    type: HapticFeedbackType = HapticFeedbackType.ContextClick,
): () -> Unit {
    val perform = rememberHapticPerformer()
    return remember(type, perform) { { perform(type) } }
}

/**
 * Haptic trigger that is safe to capture inside `pointerInput` / long-lived lambdas.
 * It always reads the latest haptics setting, so toggling it takes effect immediately.
 */
@Composable
fun rememberStableHapticAction(
    type: HapticFeedbackType = HapticFeedbackType.ContextClick,
): () -> Unit {
    val action = rememberHapticAction(type)
    val latest by rememberUpdatedState(action)
    return remember { { latest() } }
}

/**
 * Drop-in replacement for `combinedClickable`: vibrates on click (ContextClick) and on long click
 * (LongPress) with the strength chosen in settings. The system's own long-press haptic is turned
 * off so it never doubles up or ignores the setting. Callers pass plain lambdas.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticClick(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier =
    composed {
        val click = hapticClick(type = HapticFeedbackType.ContextClick, block = onClick)
        val perform = rememberHapticAction(HapticFeedbackType.LongPress)
        val latestLong by rememberUpdatedState(onLongClick)
        combinedClickable(
            onLongClick =
                if (onLongClick != null) {
                    {
                        perform()
                        latestLong?.invoke()
                    }
                } else {
                    null
                },
            hapticFeedbackEnabled = false,
            onClick = click,
        )
    }

/** Jenis haptic toggle. [checked] = state SEKARANG (sebelum diklik): kalau sekarang on, berarti jadi off. */
fun toggleHaptic(checked: Boolean): HapticFeedbackType =
    if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn

/** Klik untuk toggle (switch, checkbox, toggle button). */
@Composable
fun hapticToggle(
    checked: Boolean,
    block: () -> Unit,
): () -> Unit = hapticClick(type = toggleHaptic(checked), block = block)
