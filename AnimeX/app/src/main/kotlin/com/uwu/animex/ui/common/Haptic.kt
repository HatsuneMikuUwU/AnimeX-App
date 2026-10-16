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

@Composable
private fun rememberHapticPerformer(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val enabled = settings.hapticsEnabled
    return remember(haptic, enabled) {
        { type -> if (enabled) haptic.performHapticFeedback(type) }
    }
}

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

@Composable
fun rememberHapticAction(type: HapticFeedbackType = HapticFeedbackType.ContextClick): () -> Unit {
    val perform = rememberHapticPerformer()
    return remember(type, perform) { { perform(type) } }
}

@Composable
fun rememberStableHapticAction(type: HapticFeedbackType = HapticFeedbackType.ContextClick): () -> Unit {
    val action = rememberHapticAction(type)
    val latest by rememberUpdatedState(action)
    return remember { { latest() } }
}

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

fun toggleHaptic(checked: Boolean): HapticFeedbackType =
    if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn

@Composable
fun hapticToggle(
    checked: Boolean,
    block: () -> Unit,
): () -> Unit = hapticClick(type = toggleHaptic(checked), block = block)
