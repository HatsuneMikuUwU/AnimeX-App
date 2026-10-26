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
import androidx.compose.material3.MotionScheme
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AppMotion {
    private val scheme = MotionScheme.expressive()

    fun <T> spatial(): FiniteAnimationSpec<T> = scheme.defaultSpatialSpec()

    fun <T> effects(): FiniteAnimationSpec<T> = scheme.defaultEffectsSpec()

    fun <T> fastEffects(): FiniteAnimationSpec<T> = scheme.fastEffectsSpec()

    val navEnter: EnterTransition =
        slideInHorizontally(spatial()) { it / 6 } + fadeIn(effects())

    val navExit: ExitTransition =
        slideOutHorizontally(spatial()) { -it / 6 } + fadeOut(fastEffects())

    val navPopEnter: EnterTransition =
        slideInHorizontally(spatial()) { -it / 6 } + fadeIn(effects())

    val navPopExit: ExitTransition =
        slideOutHorizontally(spatial()) { it / 6 } + fadeOut(fastEffects())

    fun fadeScaleIn() = fadeIn(effects()) + scaleIn(spatial(), initialScale = 0.92f)

    fun fadeScaleOut() = fadeOut(fastEffects()) + scaleOut(spatial(), targetScale = 0.94f)

    fun tabTransition(forward: Boolean): ContentTransform {
        val sign = if (forward) 1 else -1
        val enterAnim = fadeIn(effects()) + slideInHorizontally(spatial()) { sign * it / 6 }
        val exitAnim = fadeOut(fastEffects()) + slideOutHorizontally(spatial()) { -sign * it / 6 }
        return enterAnim togetherWith exitAnim
    }

    fun <S> stateTransition(
        scope: AnimatedContentTransitionScope<S>,
        isReady: (S) -> Boolean,
    ): ContentTransform {
        val enteringReady = isReady(scope.targetState)
        val leavingReady = isReady(scope.initialState)
        return when {
            enteringReady -> fadeScaleIn() togetherWith fadeOut(fastEffects())
            leavingReady -> fadeIn(effects()) togetherWith fadeScaleOut()
            else -> fadeIn(effects()) togetherWith fadeOut(fastEffects())
        }
    }
}

private const val PLACEHOLDER_DELAY_MS = 220L

@Composable
fun DelayedPlaceholder(
    delayMs: Long = PLACEHOLDER_DELAY_MS,
    content: @Composable () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        show = true
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(AppMotion.effects()),
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
            AppMotion.stateTransition(this) { it.key == "ready" }
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
