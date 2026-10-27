package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/**
 * Semua animasi aplikasi memakai spec bawaan [MotionScheme] Material Expressive:
 * spatial untuk posisi/ukuran/skala, effects untuk alpha/warna.
 */
object AppMotion {
    fun fadeScaleIn(
        m: MotionScheme,
        slow: Boolean = false,
    ): EnterTransition =
        if (slow) {
            fadeIn(m.slowEffectsSpec<Float>()) + scaleIn(m.slowSpatialSpec<Float>(), initialScale = 0.98f)
        } else {
            fadeIn(m.defaultEffectsSpec<Float>()) + scaleIn(m.defaultSpatialSpec<Float>(), initialScale = 0.98f)
        }

    fun fadeScaleOut(m: MotionScheme): ExitTransition =
        fadeOut(m.fastEffectsSpec<Float>()) + scaleOut(m.fastSpatialSpec<Float>(), targetScale = 0.98f)

    fun crossfade(m: MotionScheme): ContentTransform =
        fadeIn(m.fastEffectsSpec<Float>()) togetherWith fadeOut(m.fastEffectsSpec<Float>())

    fun tabTransition(
        m: MotionScheme,
        forward: Boolean,
    ): ContentTransform {
        val dir = if (forward) 1 else -1
        val enterAnim =
            fadeIn(m.defaultEffectsSpec<Float>()) +
                slideInHorizontally(m.defaultSpatialSpec<IntOffset>()) { dir * it / 8 } +
                scaleIn(m.defaultSpatialSpec<Float>(), initialScale = 0.98f)
        val exitAnim =
            fadeOut(m.fastEffectsSpec<Float>()) +
                slideOutHorizontally(m.fastSpatialSpec<IntOffset>()) { -dir * it / 10 } +
                scaleOut(m.fastSpatialSpec<Float>(), targetScale = 0.98f)
        return enterAnim togetherWith exitAnim
    }

    fun <S> stateTransition(
        m: MotionScheme,
        scope: AnimatedContentTransitionScope<S>,
        isReady: (S) -> Boolean,
    ): ContentTransform {
        val enteringReady = isReady(scope.targetState)
        val leavingReady = isReady(scope.initialState)
        return when {
            enteringReady -> fadeScaleIn(m, slow = true) togetherWith fadeOut(m.fastEffectsSpec<Float>())
            leavingReady -> fadeIn(m.defaultEffectsSpec<Float>()) togetherWith fadeScaleOut(m)
            else -> fadeIn(m.defaultEffectsSpec<Float>()) togetherWith fadeOut(m.fastEffectsSpec<Float>())
        }
    }

    @Composable
    fun fadeEnter(): EnterTransition = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())

    @Composable
    fun fadeExit(): ExitTransition = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    @Composable
    fun expandEnter(): EnterTransition {
        val m = MaterialTheme.motionScheme
        return fadeIn(m.defaultEffectsSpec<Float>()) + expandVertically(m.defaultSpatialSpec<IntSize>())
    }

    @Composable
    fun expandExit(): ExitTransition {
        val m = MaterialTheme.motionScheme
        return fadeOut(m.fastEffectsSpec<Float>()) + shrinkVertically(m.fastSpatialSpec<IntSize>())
    }

    @Composable
    fun expandHorizontalEnter(): EnterTransition {
        val m = MaterialTheme.motionScheme
        return fadeIn(m.defaultEffectsSpec<Float>()) + expandHorizontally(m.defaultSpatialSpec<IntSize>())
    }

    @Composable
    fun expandHorizontalExit(): ExitTransition {
        val m = MaterialTheme.motionScheme
        return fadeOut(m.fastEffectsSpec<Float>()) + shrinkHorizontally(m.fastSpatialSpec<IntSize>())
    }

    /** Muncul dengan skala + expand (tombol Play di detail). */
    @Composable
    fun popExpandEnter(): EnterTransition {
        val m = MaterialTheme.motionScheme
        return fadeIn(m.defaultEffectsSpec<Float>()) +
            expandVertically(m.defaultSpatialSpec<IntSize>()) +
            scaleIn(m.defaultSpatialSpec<Float>(), initialScale = 0.96f)
    }

    @Composable
    fun popExpandExit(): ExitTransition {
        val m = MaterialTheme.motionScheme
        return fadeOut(m.fastEffectsSpec<Float>()) +
            shrinkVertically(m.fastSpatialSpec<IntSize>()) +
            scaleOut(m.fastSpatialSpec<Float>(), targetScale = 0.96f)
    }

    @Composable
    fun slideInEnd(): EnterTransition = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()) { it }

    @Composable
    fun slideOutEnd(): ExitTransition = slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()) { it }

    @Composable
    fun slideInTop(): EnterTransition {
        val m = MaterialTheme.motionScheme
        return slideInVertically(m.defaultSpatialSpec<IntOffset>()) { -it } + fadeIn(m.defaultEffectsSpec<Float>())
    }

    @Composable
    fun slideOutTop(): ExitTransition {
        val m = MaterialTheme.motionScheme
        return slideOutVertically(m.fastSpatialSpec<IntOffset>()) { -it } + fadeOut(m.fastEffectsSpec<Float>())
    }

    @Composable
    fun slideInUpSmall(): EnterTransition {
        val m = MaterialTheme.motionScheme
        return fadeIn(m.defaultEffectsSpec<Float>()) + slideInVertically(m.defaultSpatialSpec<IntOffset>()) { it / 12 }
    }

    @Composable
    fun slideOutUpSmall(): ExitTransition {
        val m = MaterialTheme.motionScheme
        return fadeOut(m.fastEffectsSpec<Float>()) + slideOutVertically(m.fastSpatialSpec<IntOffset>()) { it / 12 }
    }
}

/** `animateItem()` dengan spec Material Expressive. */
@Composable
fun LazyItemScope.animateItemExpressive(): Modifier {
    val m = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = m.defaultEffectsSpec<Float>(),
        placementSpec = m.defaultSpatialSpec<IntOffset>(),
        fadeOutSpec = m.fastEffectsSpec<Float>(),
    )
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
        enter = AppMotion.fadeEnter(),
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

    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            AppMotion.stateTransition(motion, this) { it.key == "ready" }
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
