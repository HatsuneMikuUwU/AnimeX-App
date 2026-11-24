@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.uwu.animex.data.api.SkipStamp
import com.uwu.animex.data.api.SkipType
import com.uwu.animex.data.local.PlayerButton
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberStableHapticAction
import com.uwu.animex.ui.player.PipHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.foundation.layout.BoxScope

@Composable
internal fun BoxScope.PlayerGestureHud(
    visible: Boolean,
    compact: Boolean,
    reduceMotion: Boolean,
    locked: Boolean,
    lockIconVisible: Boolean,
    activeStamp: SkipStamp?,
    shouldAutoSkipActive: Boolean,
    votedStamps: Map<String, String>,
    pos: Long,
    seekAcc: Int,
    swipeSeek: Long?,
    side: Int,
    volume: Int,
    brightness: Float,
    boostFailed: Boolean,
    swapVolBright: Boolean,
    swapIndicator: Boolean,
    holding: Boolean,
    hint: String?,
    audio: AudioManager,
    player: ExoPlayer,
    onLockedChange: (Boolean) -> Unit,
    onVoteStamp: ((SkipStamp, String) -> Unit)?,
    onDismissStamp: (SkipStamp) -> Unit,
) {
    val skipTargetBottom = if (visible) (if (compact) 52.dp else 96.dp) else 20.dp
    val skipBottomPad by animateDpAsState(
        targetValue = skipTargetBottom,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 180),
        label = "skipPillBottom",
    )
    val showSkipPill = activeStamp != null && !locked && !shouldAutoSkipActive
    if (showSkipPill) {
        val stamp = activeStamp!!
        val stampVote = stamp.skipId?.let { votedStamps[it] }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .then(if (compact) Modifier else Modifier.safeDrawingPadding())
                .padding(end = 16.dp, bottom = skipBottomPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (stamp.skipId != null && onVoteStamp != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.ThumbUp,
                        contentDescription = "Upvote skip",
                        tint = if (stampVote == true) MaterialTheme.colorScheme.primary else Color.White,
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .clickable(
                                    onClick = hapticClick {
                                        val id = stamp.skipId
                                        if (id != null && stampVote != true) {
                                            votedStamps = votedStamps + (id to true)
                                            onVoteStamp(stamp, true)
                                        }
                                    },
                                ).padding(6.dp)
                                .size(16.dp),
                    )
                    Icon(
                        Icons.Outlined.ThumbDown,
                        contentDescription = "Downvote skip",
                        tint = if (stampVote == false) MaterialTheme.colorScheme.error else Color.White,
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .clickable(
                                    onClick = hapticClick {
                                        val id = stamp.skipId
                                        if (id != null && stampVote != false) {
                                            votedStamps = votedStamps + (id to false)
                                            onVoteStamp(stamp, false)
                                            onDismissStamp(stamp)
                                        }
                                    },
                                ).padding(6.dp)
                                .size(16.dp),
                    )
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC000000))
                    .clickable(
                        onClick = hapticClick {
                            player.seekTo(stamp.endMs)
                            pos = stamp.endMs
                            onDismissStamp(stamp)
                        },
                    ).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stamp.type.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (seekAcc != 0) {
        val forward = seekAcc > 0
        Column(
            Modifier
                .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = if (compact) 36.dp else 56.dp)
                .background(Color(0x66000000), CircleShape)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (forward) Icons.Outlined.Forward10 else Icons.Outlined.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 28.dp else 32.dp),
            )
            Text("${abs(seekAcc)} dtk", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }

    swipeSeek?.let { target ->
        val delta = (target - pos) / 1000
        Text(
            text = formatTime(target) + "  (" + (if (delta >= 0) "+" else "") + delta + " dtk)",
            color = Color.White,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    if (side != 0) {
        val controllingBrightness = if (swapVolBright) side == 2 else side == 1
        val level = if (controllingBrightness) brightness else volume
        val shownSide = if (swapIndicator) 3 - side else side
        Column(
            Modifier
                .align(if (shownSide == 1) Alignment.CenterStart else Alignment.CenterEnd)
                .safeDrawingPadding()
                .padding(horizontal = 32.dp)
                .background(Color(0x66000000), RoundedCornerShape(24.dp))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (controllingBrightness) Icons.Outlined.BrightnessMedium else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = null,
                tint = Color.White,
            )
            Box(
                Modifier
                    .width(6.dp)
                    .height(if (compact) 70.dp else 140.dp)
                    .clip(CircleShape)
                    .background(Color(0x40FFFFFF)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(Modifier.fillMaxHeight(level.coerceAtMost(1f)).width(6.dp).background(Color.White))
                if (!controllingBrightness && level > 1f) {
                    Box(
                        Modifier
                            .fillMaxHeight((level - 1f).coerceIn(0f, 1f))
                            .width(6.dp)
                            .background(if (boostFailed) Color(0xFFE53935) else Color(0xFFFF9800)),
                    )
                }
            }
            Text(
                when {
                    PlayerPrefs.displayVolPer.value || (!controllingBrightness && level > 1f) ->
                        "${(level * 100).roundToInt()}%"
                    controllingBrightness -> "${(level * 255).roundToInt()}"
                    else -> "${(
                        level * (
                            runCatching {
                                audio.getStreamMaxVolume(
                                    android.media.AudioManager.STREAM_MUSIC,
                                )
                            }.getOrDefault(15)
                        )
                    ).roundToInt()}"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    val topHint =
        when {
            holding -> "${speedLabel(HOLD_SPEED)} Mempercepat"
            else -> hint
        }
    if (topHint != null) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .then(if (compact) Modifier else Modifier.safeDrawingPadding())
                .padding(top = if (compact) 10.dp else 16.dp)
                .background(Color(0xB3000000), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (holding) {
                Icon(
                    Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(topHint, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }

    AnimatedVisibility(
        visible = locked && lockIconVisible,
        modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp),
    ) {
        OverlayButton({ onLockedChange(false) }) {
            Icon(Icons.Outlined.Lock, contentDescription = "Buka kuncinya", tint = Color.White)
        }
    }
}
