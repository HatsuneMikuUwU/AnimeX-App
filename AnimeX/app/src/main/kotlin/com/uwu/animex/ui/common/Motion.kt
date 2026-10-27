package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import kotlinx.coroutines.delay

/**
 * Token animasi yang mengikuti [MaterialTheme.motionScheme]
 * (Standar vs Ekspresif dari pengaturan tema).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class AppMotionTokens(
    val effectsFast: FiniteAnimationSpec<Float>,
    val effectsDefault: FiniteAnimationSpec<Float>,
    val effectsSlow: FiniteAnimationSpec<Float>,
    val spatialFast: FiniteAnimationSpec<Float>,
    val spatialDefault: FiniteAnimationSpec<Float>,
    val spatialSlow: FiniteAnimationSpec<Float>,
) {
    fun fadeScaleIn(): EnterTransition =
        fadeIn(effectsDefault) + scaleIn(spatialDefault, initialScale = 0.98f)

    fun fadeScaleOut(): ExitTransition =
        fadeOut(effectsFast) + scaleOut(spatialFast, targetScale = 0.98f)

    fun tabTransition(forward: Boolean): ContentTransform {
        val slideIn =
            if (forward) {
                slideInHorizontally(spatialDefault) { it / 8 }
            } else {
                slideInHorizontally(spatialDefault) { -it / 8 }
            }
        val slideOut =
            if (forward) {
                slideOutHorizontally(spatialFast) { -it / 10 }
            } else {
                slideOutHorizontally(spatialFast) { it / 10 }
            }
        val enterAnim = fadeIn(effectsDefault) + slideIn + scaleIn(spatialDefault, initialScale = 0.99f)
        val exitAnim = fadeOut(effectsFast) + slideOut + scaleOut(spatialFast, targetScale = 0.99f)
        return enterAnim togetherWith exitAnim
    }

    fun <S> stateTransition(
        scope: AnimatedContentTransitionScope<S>,
        isReady: (S) -> Boolean,
    ): ContentTransform {
        val enteringReady = isReady(scope.targetState)
        val leavingReady = isReady(scope.initialState)
        return when {
            enteringReady -> fadeScaleIn() togetherWith fadeOut(effectsFast)
            leavingReady -> fadeIn(effectsDefault) togetherWith fadeScaleOut()
            else -> fadeIn(effectsDefault) togetherWith fadeOut(effectsFast)
        }
    }
}

/** Ambil token animasi dari skema gerak tema saat ini. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberAppMotion(): AppMotionTokens {
    val scheme = MaterialTheme.motionScheme
    return remember(scheme) {
        AppMotionTokens(
            effectsFast = scheme.fastEffectsSpec(),
            effectsDefault = scheme.defaultEffectsSpec(),
            effectsSlow = scheme.slowEffectsSpec(),
            spatialFast = scheme.fastSpatialSpec(),
            spatialDefault = scheme.defaultSpatialSpec(),
            spatialSlow = scheme.slowSpatialSpec(),
        )
    }
}

/** @deprecated Gunakan [rememberAppMotion] agar mengikuti setting skema gerak. */
@Deprecated("Gunakan rememberAppMotion()", ReplaceWith("rememberAppMotion()"))
object AppMotion {
    const val Fast = 160
    const val Medium = 280
    const val Slow = 320
    val Ease = androidx.compose.animation.core.FastOutSlowInEasing

    fun fadeScaleIn(duration: Int = Medium) =
        fadeIn(androidx.compose.animation.core.tween(duration, easing = Ease)) +
            scaleIn(androidx.compose.animation.core.tween(duration, easing = Ease), initialScale = 0.98f)

    fun fadeScaleOut(duration: Int = Fast) =
        fadeOut(androidx.compose.animation.core.tween(duration, easing = Ease)) +
            scaleOut(androidx.compose.animation.core.tween(duration, easing = Ease), targetScale = 0.98f)

    fun tabTransition(forward: Boolean): ContentTransform {
        val slideIn =
            if (forward) {
                slideInHorizontally(androidx.compose.animation.core.tween(Medium, easing = Ease)) { it / 8 }
            } else {
                slideInHorizontally(androidx.compose.animation.core.tween(Medium, easing = Ease)) { -it / 8 }
            }
        val slideOut =
            if (forward) {
                slideOutHorizontally(androidx.compose.animation.core.tween(220, easing = Ease)) { -it / 10 }
            } else {
                slideOutHorizontally(androidx.compose.animation.core.tween(220, easing = Ease)) { it / 10 }
            }
        return (fadeIn(androidx.compose.animation.core.tween(Medium, easing = Ease)) + slideIn) togetherWith
            (fadeOut(androidx.compose.animation.core.tween(220, easing = Ease)) + slideOut)
    }

    fun <S> stateTransition(
        scope: AnimatedContentTransitionScope<S>,
        isReady: (S) -> Boolean,
    ): ContentTransform {
        val enteringReady = isReady(scope.targetState)
        val leavingReady = isReady(scope.initialState)
        return when {
            enteringReady -> fadeScaleIn(Slow) togetherWith fadeOut(androidx.compose.animation.core.tween(Fast, easing = Ease))
            leavingReady -> fadeIn(androidx.compose.animation.core.tween(220, easing = Ease)) togetherWith fadeScaleOut()
            else -> fadeIn(androidx.compose.animation.core.tween(220, easing = Ease)) togetherWith fadeOut(androidx.compose.animation.core.tween(Fast, easing = Ease))
        }
    }
}

private const val PLACEHOLDER_DELAY_MS = 220L

@Composable
fun DelayedPlaceholder(
    delayMs: Long = PLACEHOLDER_DELAY_MS,
    content: @Composable () -> Unit,
) {
    val motion = rememberAppMotion()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        show = true
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(motion.effectsDefault),
        exit = ExitTransition.None,
    ) { content() }
}

@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { GridPlaceholder() },
    error: @Composable (String) -> Unit = { msg -> ErrorState(msg, onRetry) },
    content: @Composable (T) -> Unit,
) {
    data class Phase(
        val key: String,
        val state: UiState<T>,
    )

    val motion = rememberAppMotion()
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
            motion.stateTransition(this) { it.key == "ready" }
        },
        label = "ui-state",
        contentKey = { it.key },
        modifier = modifier.fillMaxSize(),
    ) { p ->
        when (val s = p.state) {
            UiState.Loading -> {
                Box(
                    Modifier.fillMaxSize().clearAndSetSemantics {
                        contentDescription = "Memuat konten"
                        liveRegion = LiveRegionMode.Polite
                    },
                ) { DelayedPlaceholder { loading() } }
            }
            is UiState.Error -> Box(Modifier.fillMaxSize()) { error(s.msg) }
            is UiState.Ready -> content(s.value)
        }
    }
}
