package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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

object AppMotion {
    fun enterContent(m: MotionScheme): EnterTransition =
        fadeIn(m.defaultEffectsSpec<Float>()) +
            scaleIn(m.defaultSpatialSpec<Float>(), initialScale = 0.92f)

    fun fadeThrough(m: MotionScheme): ContentTransform = enterContent(m) togetherWith fadeOut(m.fastEffectsSpec<Float>())

    fun crossfade(m: MotionScheme): ContentTransform =
        fadeIn(m.fastEffectsSpec<Float>()) togetherWith fadeOut(m.fastEffectsSpec<Float>())

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

    @Composable
    fun slideInEnd(): EnterTransition =
        slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()) {
            it
        }

    @Composable
    fun slideOutEnd(): ExitTransition =
        slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()) { it }
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

private const val PLACEHOLDER_DELAY_MS = 250L

@Composable
fun rememberDelayedVisible(
    active: Boolean,
    delayMs: Long = PLACEHOLDER_DELAY_MS,
): Boolean {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(delayMs)
            show = true
        } else {
            show = false
        }
    }
    return active && show
}

@Composable
fun DelayedPlaceholder(
    delayMs: Long = PLACEHOLDER_DELAY_MS,
    content: @Composable () -> Unit,
) {
    val show = rememberDelayedVisible(active = true, delayMs = delayMs)
    AnimatedVisibility(
        visible = show,
        enter = AppMotion.fadeEnter(),
        exit = ExitTransition.None,
    ) { content() }
}

/**
 * Kalau true, [AnimatedPhase] memutar animasi masuk (fade + scale) saat pertama kali tampil,
 * termasuk ketika datanya sudah ada di cache. MainScreen mematikannya untuk isi tab karena
 * tab-nya sendiri sudah beranimasi, supaya animasinya tidak dobel (penyebab blink).
 */
val LocalAnimateEnter = compositionLocalOf { true }

/**
 * Satu-satunya cara ganti fase konten (loading / error / kosong / isi) di semua layar.
 * - Fase awal langsung tampil dengan animasi masuk yang sama, tanpa frame kosong "Loading" dulu.
 * - Perpindahan fase selanjutnya pakai fadeThrough Material Expressive.
 */
@Composable
fun <T> AnimatedPhase(
    targetState: T,
    modifier: Modifier = Modifier,
    animateEnter: Boolean = LocalAnimateEnter.current,
    contentKey: (T) -> Any? = { it },
    label: String = "phase",
    content: @Composable (T) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val entered = remember { MutableTransitionState(!animateEnter).apply { this.targetState = true } }
    AnimatedVisibility(
        visibleState = entered,
        enter = AppMotion.enterContent(motion),
        exit = ExitTransition.None,
        modifier = modifier.fillMaxSize(),
    ) {
        AnimatedContent(
            targetState = targetState,
            contentKey = contentKey,
            transitionSpec = { AppMotion.fadeThrough(motion) },
            label = label,
            modifier = Modifier.fillMaxSize(),
        ) { p -> content(p) }
    }
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
        when (state) {
            is UiState.Error -> Phase("error", state)
            is UiState.Ready -> Phase("ready", state)
            else -> Phase("loading", UiState.Loading)
        }

    AnimatedPhase(
        targetState = phase,
        modifier = modifier,
        contentKey = { it.key },
        label = "ui-state",
    ) { p ->
        when (val s = p.state) {
            is UiState.Error -> Box(Modifier.fillMaxSize()) { error(s.msg) }
            is UiState.Ready -> content(s.value)
            else ->
                Box(
                    Modifier.fillMaxSize().clearAndSetSemantics {
                        contentDescription = "Memuat konten"
                        liveRegion = LiveRegionMode.Polite
                    },
                ) { DelayedPlaceholder { loading() } }
        }
    }
}
