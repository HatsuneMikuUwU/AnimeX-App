package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
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
import androidx.compose.ui.Modifier

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

    fun navEnter() =
        fadeIn(tween(Slow, easing = Ease)) +
            slideInHorizontally(tween(Slow, easing = Ease)) { it / 12 } +
            scaleIn(tween(Slow, easing = Ease), initialScale = 0.96f)

    fun navExit() =
        fadeOut(tween(220, easing = Ease)) +
            scaleOut(tween(220, easing = Ease), targetScale = 0.98f)

    fun navPopEnter() =
        fadeIn(tween(Medium, easing = Ease)) +
            scaleIn(tween(Medium, easing = Ease), initialScale = 0.98f)

    fun navPopExit() =
        fadeOut(tween(240, easing = Ease)) +
            slideOutHorizontally(tween(240, easing = Ease)) { it / 10 } +
            scaleOut(tween(240, easing = Ease), targetScale = 0.96f)
}

/**
 * Consistent Loading / Error / Ready transition for any screen that uses [UiState].
 */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { CenterLoading() },
    error: @Composable (String) -> Unit = { msg -> ErrorState(msg, onRetry) },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            AppMotion.stateTransition(this) { it is UiState.Ready<*> }
        },
        label = "ui-state",
        contentKey = {
            when (it) {
                UiState.Loading -> "loading"
                is UiState.Error -> "error"
                is UiState.Ready -> "ready"
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { s ->
        when (s) {
            UiState.Loading -> Box(Modifier.fillMaxSize()) { loading() }
            is UiState.Error -> Box(Modifier.fillMaxSize()) { error(s.msg) }
            is UiState.Ready -> content(s.value)
        }
    }
}
