package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
 * Spek animasi bawaan Material dari [MaterialTheme.motionScheme]
 * (Standar / Ekspresif mengikuti pengaturan tema).
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
    fun fadeIn(): EnterTransition = fadeIn(effectsDefault)

    fun fadeOut(): ExitTransition = fadeOut(effectsFast)

    fun fadeTogether() = fadeIn() togetherWith fadeOut()
}

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
        enter = motion.fadeIn(),
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
        transitionSpec = { motion.fadeTogether() },
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
