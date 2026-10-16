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

/**
 * Satu-satunya sumber jenis haptic di app. Semua komponen WAJIB pakai konstanta di sini
 * (jangan panggil HapticFeedbackType langsung) biar rasa getarannya seragam di semua layar.
 */
object AppHaptic {
    /** Tap biasa: tombol, kartu, item list, chip, tab. */
    val Click = HapticFeedbackType.ContextClick

    /** Tekan lama. */
    val LongPress = HapticFeedbackType.LongPress

    /** Switch / checkbox / toggle button berubah jadi aktif. */
    val ToggleOn = HapticFeedbackType.ToggleOn

    /** Switch / checkbox / toggle button berubah jadi mati. */
    val ToggleOff = HapticFeedbackType.ToggleOff

    /** Slider / scrubber melewati satu step. */
    val Tick = HapticFeedbackType.SegmentTick

    /** Tick rapat untuk gesture drag yang sering berganti nilai (volume, brightness, seek). */
    val FrequentTick = HapticFeedbackType.SegmentFrequentTick

    /** Gesture mencapai threshold (double tap, dll). */
    val GestureActivate = HapticFeedbackType.GestureThresholdActivate

    /** Gesture selesai. */
    val GestureEnd = HapticFeedbackType.GestureEnd

    fun toggle(checked: Boolean): HapticFeedbackType = if (checked) ToggleOn else ToggleOff
}

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
    type: HapticFeedbackType = AppHaptic.Click,
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
    type: HapticFeedbackType = AppHaptic.Click,
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
    type: HapticFeedbackType = AppHaptic.Click,
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
        val click = hapticClick(type = AppHaptic.Click, block = onClick)
        val perform = rememberHapticAction(AppHaptic.LongPress)
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

/**
 * Klik untuk toggle (switch, checkbox, toggle button). Getarannya ToggleOn / ToggleOff
 * sesuai state BARU setelah diklik. [checked] = state saat ini (sebelum diklik).
 */
@Composable
fun hapticToggle(
    checked: Boolean,
    block: () -> Unit,
): () -> Unit = hapticClick(type = AppHaptic.toggle(!checked), block = block)

/**
 * Versi callback `onCheckedChange`: getar sesuai nilai baru lalu panggil [onChange].
 */
@Composable
fun hapticToggleChange(onChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val perform = rememberHapticPerformer()
    val latest by rememberUpdatedState(onChange)
    return remember(perform) {
        { value ->
            perform(AppHaptic.toggle(value))
            latest(value)
        }
    }
}

/**
 * Tick untuk Slider / stepper: panggil `tick()` tiap nilai berpindah step.
 * Aman dipakai di dalam lambda yang hidup lama (pointerInput, onValueChange).
 */
@Composable
fun rememberHapticTick(type: HapticFeedbackType = AppHaptic.Tick): () -> Unit = rememberStableHapticAction(type)
