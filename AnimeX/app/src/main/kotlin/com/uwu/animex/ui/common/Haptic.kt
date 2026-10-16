package com.uwu.animex.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.HapticStrength

private val TickTypes =
    setOf(
        HapticFeedbackType.SegmentTick,
        HapticFeedbackType.SegmentFrequentTick,
        HapticFeedbackType.TextHandleMove,
    )

/**
 * Uses the system's own tuned haptic effects so it feels like real feedback instead of a buzz:
 * Light = TICK, Medium = CLICK, Strong = HEAVY_CLICK (Android 10+). Scrub/drag ticks are one
 * step softer. Older devices use a short one-shot vibration. The Compose haptic is only a
 * fallback for devices without a vibrator.
 */
@Suppress("DEPRECATION")
private fun performWithStrength(
    context: Context,
    haptic: HapticFeedback,
    type: HapticFeedbackType,
    strength: HapticStrength,
) {
    val vibrator = context.getSystemService(Vibrator::class.java)
    if (vibrator == null || !vibrator.hasVibrator()) {
        haptic.performHapticFeedback(type)
        return
    }
    val tick = type in TickTypes
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect =
                when (strength) {
                    HapticStrength.LIGHT -> VibrationEffect.EFFECT_TICK
                    HapticStrength.MEDIUM -> if (tick) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK
                    HapticStrength.STRONG ->
                        if (tick) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_HEAVY_CLICK
                }
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            val ms =
                when (strength) {
                    HapticStrength.LIGHT -> 10L
                    HapticStrength.MEDIUM -> 20L
                    HapticStrength.STRONG -> 35L
                }
            val shortMs = if (tick) (ms * 0.6f).toLong() else ms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(shortMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(shortMs)
            }
        }
    }.onFailure { haptic.performHapticFeedback(type) }
}

/** Returns a function that vibrates using the strength chosen in settings (no-op when off). */
@Composable
private fun rememberHapticPerformer(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val enabled = settings.hapticsEnabled
    val strength = settings.hapticStrength
    return remember(haptic, context, enabled, strength) {
        { type -> if (enabled) performWithStrength(context, haptic, type, strength) }
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
