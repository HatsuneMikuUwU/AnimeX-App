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

@Composable
internal fun BoxScope.PlayerModalsHost(
    fullscreen: Boolean,
    expanded: Boolean,
    curMovieId: String?,
    curEpId: String,
    curIndex: String?,
    activeTitle: String,
    servers: List<Server>,
    speed: Float,
    sleepDeadlineMs: Long,
    sleepNow: Long,
    sleepAfterEpisode: Boolean,
    showEpisodes: Boolean,
    showComments: Boolean,
    showEndSuggestions: Boolean,
    showSpeed: Boolean,
    showSleep: Boolean,
    showDialog: Boolean,
    similar: List<SimilarMovie>,
    similarLoading: Boolean,
    commentsViewModel: DiscussionViewModel,
    snackbar: SnackbarHostState,
    resolveSel: (List<Server>) -> Int,
    switchTo: (Episode) -> Unit,
    pickServer: (Int) -> Unit,
    onOpenAnime: (String) -> Unit,
    showMessage: (String) -> Unit,
    onDismissEpisodes: () -> Unit,
    onDismissComments: () -> Unit,
    onDismissEndSuggestions: () -> Unit,
    onDismissSpeed: () -> Unit,
    onDismissSleep: () -> Unit,
    onDismissDialog: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimerChange: (deadlineMs: Long, afterEpisode: Boolean) -> Unit,
) {
    AnimatedVisibility(
        visible = showEpisodes && curMovieId != null && fullscreen,
        enter = AppMotion.slideInEnd(),
        exit = AppMotion.slideOutEnd(),
    ) {
        val mId = curMovieId
        if (mId != null) {
            EpisodePanel(
                movieId = mId,
                currentEpId = curEpId,
                currentIndex = curIndex,
                viewModel = viewModel,
                onPick = {
                    onDismissEpisodes()
                    switchTo(it, false)
                },
                onDismiss = { onDismissEpisodes() },
            )
        }
    }

    AnimatedVisibility(
        visible = showComments && fullscreen,
        enter = AppMotion.slideInEnd(),
        exit = AppMotion.slideOutEnd(),
    ) {
        CommentsPanel(
            viewModel = commentsViewModel,
            onDismiss = { onDismissComments() },
        )
    }

    if (showEndSuggestions && expanded) {
        EndSuggestions(
            animeTitle = activeTitle.substringBeforeLast(" - Ep ", activeTitle),
            movies = similar,
            loading = similarLoading,
            onOpen = onOpenAnime,
            onDismiss = { onDismissEndSuggestions() },
            modifier = Modifier.fillMaxSize(),
        )
    }

    SpeedPanel(
        visible = showSpeed && fullscreen,
        onSpeedChange(s)peed,
        onDismiss = { onDismissSpeed() },
    ) { s ->
        onDismissSpeed()
        onSpeedChange(s)
    }

    SleepTimerPanel(
        visible = showSleep && fullscreen,
        deadlineMs = sleepDeadlineMs,
        nowMs = sleepNow,
        afterEpisode = sleepAfterEpisode,
        onDismiss = { onDismissSleep() },
    ) { deadlineMs, afterEpisode, message ->
        onDismissSleep()
        onSleepTimerChange(deadlineMs, afterEpisode)
        showMessage(message)
    }

    AnimatedVisibility(
        visible = showDialog && servers.isNotEmpty() && fullscreen,
        enter = AppMotion.slideInEnd(),
        exit = AppMotion.slideOutEnd(),
    ) {
        if (servers.isNotEmpty()) {
            PlayerOptionPanel(
                title = "Kualitas",
                options = servers.map { it.label() },
                selected = resolveSel(servers),
                onDismiss = { onDismissDialog() },
                onSelect = pickServer,
            )
        }
    }

    if (!fullscreen && expanded) {
        if (showSpeed) {
            SpeedSheet(
                onSpeedChange(s)peed,
                onDismiss = { onDismissSpeed() },
            ) { s ->
                onDismissSpeed()
                onSpeedChange(s)
            }
        }
        if (showSleep) {
            SleepTimerSheet(
                deadlineMs = sleepDeadlineMs,
                nowMs = sleepNow,
                afterEpisode = sleepAfterEpisode,
                onDismiss = { onDismissSleep() },
            ) { choice ->
                onDismissSleep()
                sleepDeadlineMs = choice.deadlineMs
                sleepAfterEpisode = choice.afterEpisode
                showMessage(choice.message)
            }
        }
        if (showDialog && servers.isNotEmpty()) {
            QualitySheet(
                servers = servers,
                selected = resolveSel(servers),
                onDismiss = { onDismissDialog() },
                onSelect = pickServer,
            )
        }
    }

    if (expanded) {
        SnackbarHost(
            hostState = snackbar,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = if (fullscreen) 72.dp else 0.dp),
        )
    }
}
