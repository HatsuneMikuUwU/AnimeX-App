package com.uwu.animex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance

/**
 * Returns a click lambda that performs haptic feedback (when enabled in settings)
 * then runs [block]. Mirrors aShellYou's withHaptic pattern.
 */
@Composable
fun withHaptic(
    type: HapticFeedbackType = HapticFeedbackType.ContextClick,
    block: () -> Unit,
): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val enabled = settings.hapticsEnabled
    val latestBlock by rememberUpdatedState(block)
    return remember(type, haptic, enabled) {
        {
            if (enabled) {
                haptic.performHapticFeedback(type)
            }
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
    val haptic = LocalHapticFeedback.current
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val enabled = settings.hapticsEnabled
    return remember(type, haptic, enabled) {
        {
            if (enabled) {
                haptic.performHapticFeedback(type)
            }
        }
    }
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

/** Convenience: ContextClick haptic + action. */
@Composable
fun hapticClick(block: () -> Unit): () -> Unit = withHaptic(HapticFeedbackType.ContextClick, block)

/** Convenience: LongPress haptic + action. */
@Composable
fun hapticLongClick(block: () -> Unit): () -> Unit = withHaptic(HapticFeedbackType.LongPress, block)
