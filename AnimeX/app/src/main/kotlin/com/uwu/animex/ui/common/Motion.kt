package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AppMotion {
    private val scheme = MotionScheme.expressive()

    fun <T> spatial(): FiniteAnimationSpec<T> = scheme.defaultSpatialSpec()

    fun <T> effects(): FiniteAnimationSpec<T> = scheme.defaultEffectsSpec()

    fun <T> fastEffects(): FiniteAnimationSpec<T> = scheme.fastEffectsSpec()

    private const val ENTER_DAMPING = 0.76f
    private const val ENTER_STIFFNESS = 380f
    private const val EXIT_DAMPING = 0.88f
    private const val EXIT_STIFFNESS = 460f
    private const val DRAWER_DAMPING = 0.82f
    private const val DRAWER_STIFFNESS = 420f

    private fun enterOffset() = spring<IntOffset>(ENTER_DAMPING, ENTER_STIFFNESS, IntOffset.VisibilityThreshold)

    private fun exitOffset() = spring<IntOffset>(EXIT_DAMPING, EXIT_STIFFNESS, IntOffset.VisibilityThreshold)

    private fun enterScale() = spring<Float>(ENTER_DAMPING, ENTER_STIFFNESS)

    private fun exitScale() = spring<Float>(EXIT_DAMPING, EXIT_STIFFNESS)

    private fun enterFade() = tween<Float>(durationMillis = 220, easing = LinearOutSlowInEasing)

    private fun exitFade() = tween<Float>(durationMillis = 180, easing = FastOutLinearInEasing)

    val navEnter: EnterTransition =
        slideInHorizontally(enterOffset()) { it / 3 } +
            scaleIn(enterScale(), initialScale = 0.92f) +
            fadeIn(enterFade())

    val navExit: ExitTransition =
        slideOutHorizontally(exitOffset()) { -it / 5 } +
            scaleOut(exitScale(), targetScale = 0.94f) +
            fadeOut(exitFade())

    val navPopEnter: EnterTransition =
        slideInHorizontally(enterOffset()) { -it / 5 } +
            scaleIn(enterScale(), initialScale = 0.94f) +
            fadeIn(enterFade())

    val navPopExit: ExitTransition =
        slideOutHorizontally(exitOffset()) { it / 3 } +
            scaleOut(exitScale(), targetScale = 0.92f) +
            fadeOut(exitFade())

    val drawerEnter: EnterTransition =
        slideInHorizontally(spring<IntOffset>(DRAWER_DAMPING, DRAWER_STIFFNESS, IntOffset.VisibilityThreshold)) { it } +
            fadeIn(enterFade())

    val drawerExit: ExitTransition =
        slideOutHorizontally(exitOffset()) { it } + fadeOut(exitFade())

    val scrimEnter: EnterTransition = fadeIn(tween(durationMillis = 240))

    val scrimExit: ExitTransition = fadeOut(tween(durationMillis = 200))

    fun fadeScaleIn() = fadeIn(effects()) + scaleIn(spatial(), initialScale = 0.92f)

    fun fadeScaleOut() = fadeOut(fastEffects()) + scaleOut(spatial(), targetScale = 0.94f)

    fun tabTransition(forward: Boolean): ContentTransform {
        val sign = if (forward) 1 else -1
        val enterAnim = fadeIn(enterFade()) + slideInHorizontally(enterOffset()) { sign * it / 4 }
        val exitAnim = fadeOut(exitFade()) + slideOutHorizontally(exitOffset()) { -sign * it / 6 }
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
