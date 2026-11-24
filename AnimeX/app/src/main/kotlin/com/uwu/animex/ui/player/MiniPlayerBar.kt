@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.AniSkip
import com.uwu.animex.data.api.AniSkipActionResult
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.MalIdLookup
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.data.api.SimilarRepo
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerOrientation
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.model.toEpisodeIndex
import com.uwu.animex.service.PlaybackSessions
import androidx.compose.ui.graphics.lerp as lerpColor
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.player.PipHelper
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.viewmodel.DiscussionScope
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import com.uwu.animex.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope

val MiniPlayerBarHeight = 68.dp
val MiniPlayerDockedSpace = 76.dp

internal val FloatingMiniWidth = 212.dp
internal val FloatingMiniVideoHeight = 119.dp
internal val FloatingMiniBarHeight = 37.dp
internal val FloatingMiniTotalHeight = FloatingMiniVideoHeight + FloatingMiniBarHeight

@Composable
internal fun BoxScope.MiniPlayerBar(
    fullscreen: Boolean,
    expandFrac: Float,
    miniFloating: Boolean,
    activeTitle: String,
    curIndex: String?,
    miniPlayWhenReady: Boolean,
    miniEnded: Boolean,
    miniBuffering: Boolean,
    miniPos: Long,
    miniBuffered: Long,
    miniDur: Long,
    showNext: Boolean,
    cs: ColorScheme,
    density: Density,
    animScope: CoroutineScope,
    expandAnim: Animatable<Float, *>,
    floatXAnim: Animatable<Float, *>,
    floatYAnim: Animatable<Float, *>,
    dismissDragXState: MutableFloatState,
    dismissDragYState: MutableFloatState,
    collapseDistPx: Float,
    screenWPx: Float,
    floatWPx: Float,
    marginPx: Float,
    minFloatY: Float,
    maxFloatY: Float,
    toggleMiniPlayback: () -> Unit,
    goNext: () -> Unit,
    closeCompletely: () -> Unit,
) {
    var dismissDragX by dismissDragXState
    var dismissDragY by dismissDragYState
    if (!fullscreen && expandFrac < 0.45f) {
        val miniAlpha = ((0.45f - expandFrac) / 0.45f).coerceIn(0f, 1f)
        val epHeading =
            MiniPlayerController.currentEpisodeTitle.ifBlank {
                if (!curIndex.isNullOrBlank()) "Episode $curIndex" else activeTitle
            }
        val animeSub =
            MiniPlayerController.currentAnimeTitle.ifBlank {
                activeTitle.substringBeforeLast(" - Ep ", activeTitle)
            }

        if (!miniFloating) {
            val dismissYThreshold = with(density) { 52.dp.toPx() }
            val dismissXThreshold = with(density) { 96.dp.toPx() }
            val expandYThreshold = with(density) { 64.dp.toPx() }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = miniAlpha }
                    .pointerInput(collapseDistPx) {
                        var mode = 0
                        var accX = 0f
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = {
                                mode = 0
                                accX = 0f
                                accY = 0f
                            },
                            onDragEnd = {
                                when (mode) {
                                    1 -> {
                                        if (-accY > expandYThreshold || expandAnim.value > 0.25f) {
                                            onExpand?.invoke()
                                            animScope.launch {
                                                expandAnim.animateTo(
                                                    1f,
                                                    spring(
                                                        dampingRatio = 0.88f,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                                )
                                            }
                                        } else {
                                            animScope.launch { expandAnim.animateTo(0f) }
                                        }
                                    }
                                    2 -> {
                                        val shouldDismiss = dismissDragY > dismissYThreshold
                                        dismissDragY = 0f
                                        if (shouldDismiss) closeCompletely()
                                    }
                                    3 -> {
                                        val shouldDismiss = abs(dismissDragX) > dismissXThreshold
                                        dismissDragX = 0f
                                        if (shouldDismiss) closeCompletely()
                                    }
                                }
                                mode = 0
                            },
                            onDragCancel = {
                                dismissDragX = 0f
                                dismissDragY = 0f
                                if (mode == 1) animScope.launch { expandAnim.animateTo(0f) }
                                mode = 0
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                accX += amount.x
                                accY += amount.y
                                if (mode == 0 && (abs(accX) > 12f || abs(accY) > 12f)) {
                                    mode =
                                        if (abs(accX) > abs(accY)) {
                                            3
                                        } else if (accY < 0f) {
                                            1
                                        } else {
                                            2
                                        }
                                }
                                when (mode) {
                                    1 -> {
                                        val f = (-accY / collapseDistPx).coerceIn(0f, 1f)
                                        animScope.launch { expandAnim.snapTo(f) }
                                    }
                                    2 -> dismissDragY = accY.coerceAtLeast(0f)
                                    3 -> dismissDragX = accX
                                }
                            },
                        )
                    }.clickable(
                        onClick = hapticClick { onExpand?.invoke() },
                    ),
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 118.dp, end = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            epHeading,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            animeSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(
                        onClick = hapticClick { PlayerPrefs.setMiniPlayerFloating(true) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Tab,
                            contentDescription = "Mode mengambang",
                            tint = cs.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = hapticClick { toggleMiniPlayback() },
                        modifier = Modifier.size(38.dp),
                    ) {
                        if (miniBuffering) {
                            AppLoadingIndicator(Modifier.size(18.dp))
                        } else {
                            Icon(
                                when {
                                    miniEnded -> Icons.Outlined.Replay
                                    miniPlayWhenReady -> Icons.Outlined.Pause
                                    else -> Icons.Outlined.PlayArrow
                                },
                                contentDescription = "Putar atau jeda",
                                tint = cs.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    if (showNext) {
                        IconButton(
                            onClick = hapticClick { goNext() },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.Outlined.SkipNext,
                                contentDescription = "Episode berikutnya",
                                tint = cs.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = hapticClick { closeCompletely() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Tutup mini player",
                            tint = cs.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (miniDur > 0L) {
                    val pFrac = (miniPos.toFloat() / miniDur).coerceIn(0f, 1f)
                    val bFrac = (miniBuffered.toFloat() / miniDur).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(cs.onSurface.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bFrac)
                                .background(cs.onSurface.copy(alpha = 0.24f)),
                        )
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(pFrac)
                                .background(cs.primary),
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = miniAlpha }
                    .pointerInput(screenWPx, minFloatY, maxFloatY) {
                        detectDragGestures(
                            onDragEnd = {
                                val rightX = (screenWPx - floatWPx - marginPx).coerceAtLeast(marginPx)
                                val snapX =
                                    if (floatXAnim.value + floatWPx / 2f < screenWPx / 2f) {
                                        marginPx
                                    } else {
                                        rightX
                                    }
                                animScope.launch {
                                    floatXAnim.animateTo(
                                        snapX,
                                        spring(
                                            dampingRatio = 0.8f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            val rightX = (screenWPx - floatWPx - marginPx).coerceAtLeast(marginPx)
                            val nextX = (floatXAnim.value + amount.x).coerceIn(marginPx, rightX)
                            val nextY = (floatYAnim.value + amount.y).coerceIn(minFloatY, maxFloatY)
                            animScope.launch {
                                floatXAnim.snapTo(nextX)
                                floatYAnim.snapTo(nextY)
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(FloatingMiniVideoHeight)
                        .clickable(onClick = hapticClick { onExpand?.invoke() }),
                )

                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(FloatingMiniBarHeight)
                        .background(cs.surfaceContainerHighest)
                        .padding(start = 10.dp, end = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (!curIndex.isNullOrBlank()) "Ep $curIndex" else epHeading,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clickable(onClick = hapticClick { onExpand?.invoke() }),
                    )

                    IconButton(
                        onClick = hapticClick { PlayerPrefs.setMiniPlayerFloating(false) },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ViewAgenda,
                            contentDescription = "Mode bar bawah",
                            tint = cs.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    IconButton(
                        onClick = hapticClick { toggleMiniPlayback() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            when {
                                miniEnded -> Icons.Outlined.Replay
                                miniPlayWhenReady -> Icons.Outlined.Pause
                                else -> Icons.Outlined.PlayArrow
                            },
                            contentDescription = "Putar atau jeda",
                            tint = cs.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = hapticClick { closeCompletely() },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = cs.onSurface,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }

                if (miniDur > 0L) {
                    val pFrac = (miniPos.toFloat() / miniDur).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(cs.onSurface.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(pFrac)
                                .background(cs.primary),
                        )
                    }
                }
            }
        }
    }
}
