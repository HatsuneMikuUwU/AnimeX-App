package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** Shared motion tokens so every screen feels consistent. */
object AppMotion {
    const val Fast = 160
    const val Medium = 280
    const val Slow = 320

    val Ease = FastOutSlowInEasing

    fun fadeScaleIn(duration: Int = Medium) =
        fadeIn(tween(duration, easing = Ease)) +
            scaleIn(tween(duration, easing = Ease), initialScale = 0.98f)

    fun fadeScaleOut(duration: Int = Fast) =
        fadeOut(tween(duration, easing = Ease)) +
            scaleOut(tween(duration, easing = Ease), targetScale = 0.98f)

    fun tabTransition(forward: Boolean): ContentTransform {
        val slideIn =
            if (forward) {
                slideInHorizontally(tween(Medium, easing = Ease)) { it / 8 }
            } else {
                slideInHorizontally(tween(Medium, easing = Ease)) { -it / 8 }
            }
        val slideOut =
            if (forward) {
                slideOutHorizontally(tween(220, easing = Ease)) { -it / 10 }
            } else {
                slideOutHorizontally(tween(220, easing = Ease)) { it / 10 }
            }
        return (fadeIn(tween(Medium, easing = Ease)) + slideIn + scaleIn(tween(Medium, easing = Ease), initialScale = 0.98f)) togetherWith
            (fadeOut(tween(Fast, easing = Ease)) + slideOut + scaleOut(tween(Fast, easing = Ease), targetScale = 0.98f))
    }

    fun <S> stateTransition(
        scope: AnimatedContentTransitionScope<S>,
        isReady: (S) -> Boolean,
    ): ContentTransform {
        val enteringReady = isReady(scope.targetState)
        val leavingReady = isReady(scope.initialState)
        return when {
            enteringReady -> fadeScaleIn(Slow) togetherWith fadeOut(tween(Fast, easing = Ease))
            leavingReady -> fadeIn(tween(220, easing = Ease)) togetherWith fadeScaleOut()
            else -> fadeIn(tween(220, easing = Ease)) togetherWith fadeOut(tween(Fast, easing = Ease))
        }
    }

}

private const val SKELETON_DELAY_MS = 150L

/**
 * Consistent Loading / Error / Ready transition for any screen that uses [UiState].
 */
/**
 * Renders Loading / Error / Ready. Pull-to-refresh never shows a skeleton: the current content
 * stays on screen and only the pull indicator spins, so refresh feels instant.
 */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { GridPlaceholder() },
    error: @Composable (String) -> Unit = { msg -> ErrorState(msg, onRetry) },
    content: @Composable (T) -> Unit,
) {
    data class Phase(val key: String, val state: UiState<T>)

    // Cached responses (ResponseCache) still load asynchronously: a disk read + JSON parse takes
    // a few dozen ms. Showing the skeleton for that long reads as a flash, so it only appears if
    // loading really takes a while, and Loading -> Ready is instant when it never showed.
    var skeletonShown by remember { mutableStateOf(false) }

    val phase =
        when {
            state is UiState.Loading -> Phase("loading", UiState.Loading)
            state is UiState.Error -> Phase("error", state)
            state is UiState.Ready -> Phase("ready", state)
            else -> Phase("loading", UiState.Loading)
        }

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            if (targetState.key == "ready" && !skeletonShown) {
                fadeIn(tween(120, easing = AppMotion.Ease)) togetherWith ExitTransition.None
            } else {
                AppMotion.stateTransition(this) { it.key == "ready" }
            }
        },
        label = "ui-state",
        contentKey = { it.key },
        modifier = modifier.fillMaxSize(),
    ) { p ->
        when (val s = p.state) {
            UiState.Loading -> {
                var show by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(SKELETON_DELAY_MS)
                    show = true
                    skeletonShown = true
                }
                if (show) Box(Modifier.fillMaxSize()) { loading() }
            }
            is UiState.Error -> Box(Modifier.fillMaxSize()) { error(s.msg) }
            is UiState.Ready -> content(s.value)
        }
    }
}
