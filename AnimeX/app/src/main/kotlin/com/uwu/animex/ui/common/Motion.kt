package com.uwu.animex.ui.common

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

object AppMotion {
    const val FADE_OUT_MS = 90
    const val FADE_IN_MS = 210
    const val SHARED_AXIS_MS = 300
    const val FADE_THROUGH_SCALE_MS = 300
    const val IMAGE_CROSSFADE_MS = 180

    const val SHARED_AXIS_SLIDE_DP = 30
    const val FADE_THROUGH_START_SCALE = 0.92f

    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private val fadeThroughEnter: EnterTransition =
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearOutSlowInEasing)) +
            scaleIn(
                tween(FADE_THROUGH_SCALE_MS, delayMillis = FADE_OUT_MS, easing = EmphasizedDecelerate),
                initialScale = FADE_THROUGH_START_SCALE,
            )
    private val fadeThroughExit: ExitTransition =
        fadeOut(tween(FADE_OUT_MS, easing = FastOutLinearInEasing))

    @Suppress("UNUSED_PARAMETER")
    fun fadeThrough(m: MotionScheme): ContentTransform =
        ContentTransform(fadeThroughEnter, fadeThroughExit, sizeTransform = SizeTransform(clip = false))

    fun crossfade(m: MotionScheme): ContentTransform =
        ContentTransform(
            fadeIn(m.fastEffectsSpec<Float>()),
            fadeOut(m.fastEffectsSpec<Float>()),
            sizeTransform = SizeTransform(clip = false),
        )

    fun sharedAxisXEnter(
        forward: Boolean,
        slidePx: Int,
    ): EnterTransition =
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(tween(SHARED_AXIS_MS, easing = FastOutSlowInEasing)) {
                if (forward) slidePx else -slidePx
            }

    fun sharedAxisXExit(
        forward: Boolean,
        slidePx: Int,
    ): ExitTransition =
        fadeOut(tween(FADE_OUT_MS, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(tween(SHARED_AXIS_MS, easing = FastOutSlowInEasing)) {
                if (forward) -slidePx else slidePx
            }

    fun sharedAxisX(
        forward: Boolean,
        slidePx: Int,
    ): ContentTransform =
        ContentTransform(
            sharedAxisXEnter(forward, slidePx),
            sharedAxisXExit(forward, slidePx),
            sizeTransform = SizeTransform(clip = false),
        )

    @Composable
    fun fadeEnter(): EnterTransition = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())

    @Composable
    fun fadeExit(): ExitTransition = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    @Composable
    fun popEnter(): EnterTransition {
        val m = MaterialTheme.motionScheme
        return fadeIn(m.defaultEffectsSpec<Float>()) +
            scaleIn(m.defaultSpatialSpec<Float>(), initialScale = FADE_THROUGH_START_SCALE)
    }

    @Composable
    fun popExit(): ExitTransition {
        val m = MaterialTheme.motionScheme
        return fadeOut(m.fastEffectsSpec<Float>()) +
            scaleOut(m.fastSpatialSpec<Float>(), targetScale = FADE_THROUGH_START_SCALE)
    }

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

    @Composable
    fun slideInEnd(): EnterTransition = slideInHorizontally(tween(SHARED_AXIS_MS, easing = EmphasizedDecelerate)) { it }

    @Composable
    fun slideOutEnd(): ExitTransition = slideOutHorizontally(tween(FADE_IN_MS, easing = EmphasizedAccelerate)) { it }
}

@Composable
fun LazyItemScope.animateItemExpressive(): Modifier {
    val m = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = m.defaultEffectsSpec<Float>(),
        placementSpec = m.defaultSpatialSpec<IntOffset>(),
        fadeOutSpec = m.fastEffectsSpec<Float>(),
    )
}

private const val SKELETON_MIN_VISIBLE_MS = 220L

@Composable
fun <P> rememberHeldPhase(
    target: P,
    loadingPhase: P,
): P {
    var shown by remember { mutableStateOf(target) }
    var loadingSince by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(target) {
        if (target == loadingPhase) {
            if (shown != loadingPhase) loadingSince = SystemClock.uptimeMillis()
        } else if (shown == loadingPhase) {
            delay(SKELETON_MIN_VISIBLE_MS - (SystemClock.uptimeMillis() - loadingSince))
        }
        shown = target
    }
    return shown
}

private enum class UiPhase { Loading, Error, Ready }

@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { GridPlaceholder() },
    error: @Composable (String) -> Unit = { msg -> ErrorState(msg, onRetry) },
    content: @Composable (T) -> Unit,
) {
    val target =
        when (state) {
            is UiState.Error -> UiPhase.Error
            is UiState.Ready -> UiPhase.Ready
            else -> UiPhase.Loading
        }
    val phase = rememberHeldPhase(target, UiPhase.Loading)

    var lastReady by remember { mutableStateOf<UiState.Ready<T>?>(null) }
    var lastError by remember { mutableStateOf("") }
    if (state is UiState.Ready<T>) lastReady = state
    if (state is UiState.Error) lastError = state.msg

    val motion = MaterialTheme.motionScheme
    val transitionState = remember { MutableTransitionState(phase) }
    transitionState.targetState = phase

    rememberTransition(transitionState, label = "ui-state").AnimatedContent(
        transitionSpec = { AppMotion.fadeThrough(motion) },
        modifier = modifier.fillMaxSize(),
    ) { p ->
        when (p) {
            UiPhase.Loading -> {
                Box(
                    Modifier.fillMaxSize().clearAndSetSemantics {
                        contentDescription = "Memuat konten"
                        liveRegion = LiveRegionMode.Polite
                    },
                ) { loading() }
            }
            UiPhase.Error -> Box(Modifier.fillMaxSize()) { error(lastError) }
            UiPhase.Ready -> lastReady?.let { content(it.value) }
        }
    }
}
