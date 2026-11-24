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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope

@Composable
internal fun BoxScope.PlayerControlsOverlay(
    controlsShown: Boolean,
    reduceMotion: Boolean,
    compact: Boolean,
    title: String,
    subtitle: String,
    clock: String,
    hasEpisodes: Boolean,
    hasSources: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    ended: Boolean,
    buffering: Boolean,
    playWhenReady: Boolean,
    pos: Long,
    dur: Long,
    buffered: Long,
    scrubPos: Long?,
    invertDuration: Boolean,
    autoplayEnabled: Boolean,
    speed: Float,
    resize: PlayerResize,
    stamps: List<SkipStamp>,
    opVisible: Boolean,
    hiddenButtons: Set<PlayerButton>,
    sleepDeadlineMs: Long,
    sleepNow: Long,
    sleepAfterEpisode: Boolean,
    pillScroll: ScrollState,
    player: ExoPlayer,
    onBack: () -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onComment: () -> Unit,
    onEpisodes: () -> Unit,
    onSources: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSleep: () -> Unit,
    onResize: () -> Unit,
    onLaunchExternal: () -> Unit,
    togglePlay: () -> Unit,
    seekBy: (Long) -> Unit,
    skipOp: () -> Unit,
    onPoke: () -> Unit,
    onScrubPosChange: (Long?) -> Unit,
    showHint: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = controlsShown,
        enter = if (reduceMotion) {
            fadeIn(animationSpec = androidx.compose.animation.core.snap())
        } else {
            AppMotion.fadeEnter()
        },
        exit = if (reduceMotion) {
            fadeOut(animationSpec = androidx.compose.animation.core.snap())
        } else {
            AppMotion.fadeExit()
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (compact) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0x88000000),
                                Color(0x4D000000),
                                Color(0x88000000),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color(0x66000000), Color(0x66000000)))
                    },
                ),
        ) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .then(if (compact) Modifier else Modifier.safeDrawingPadding())
                    .padding(horizontal = if (compact) 12.dp else 4.dp, vertical = if (compact) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (compact) {
                    FilledIconButton(
                        onClick = hapticClick(block = onBack),
                        modifier = Modifier.size(40.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = overlayButtonColors(),
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Kecilkan ke mini player",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (opVisible) {
                        PillButton(Icons.Outlined.FastForward, "Lewati OP") { skipOp() }
                        Spacer(Modifier.width(8.dp))
                    }
                    YouTubeAutoplayButton(
                        checked = autoplayEnabled,
                        onToggle = {
                            val next = !autoplayEnabled
                            PlayerPrefs.setAutoplayEnabled(next)
                            showHint(if (next) "Autoplay aktif" else "Autoplay nonaktif")
                            onPoke()
                        },
                    )
                } else {
                    OverlayButton(onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Balik",
                            tint = Color.White,
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                color = Color(0xB3FFFFFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(clock, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    if (onComment != null) {
                        Spacer(Modifier.width(8.dp))
                        OverlayButton(onComment) {
                            Icon(Icons.Outlined.Forum, contentDescription = "Komentar episode", tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }

            val sideBtn = if (compact) 42.dp else 56.dp
            val sideIcon = if (compact) 26.dp else 32.dp
            val mainBtn = if (compact) 56.dp else 76.dp
            val mainIcon = if (compact) 34.dp else 44.dp
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!compact) {
                    FilledIconButton(
                        onClick = hapticClick {
                            onPrev()
                            onPoke()
                        },
                        enabled = hasPrev,
                        modifier = Modifier.size(sideBtn),
                        shapes = IconButtonDefaults.shapes(),
                        colors = overlayButtonColors(),
                    ) {
                        Icon(
                            Icons.Outlined.SkipPrevious,
                            contentDescription = "Episode sebelumnya",
                            tint = Color.White.copy(alpha = if (hasPrev) 1f else 0.35f),
                            modifier = Modifier.size(sideIcon),
                        )
                    }
                }

                FilledIconButton(
                    onClick = hapticClick {
                        seekBy(-SEEK_STEP_MS)
                        onPoke()
                    },
                    modifier = Modifier.size(sideBtn),
                    shapes = IconButtonDefaults.shapes(),
                    colors = overlayButtonColors(),
                ) {
                    Icon(
                        Icons.Outlined.Replay10,
                        contentDescription = "Mundur 10 detik",
                        tint = Color.White,
                        modifier = Modifier.size(sideIcon),
                    )
                }

                FilledIconButton(
                    onClick = hapticClick {
                        togglePlay()
                        onPoke()
                    },
                    modifier = Modifier.size(mainBtn),
                    shapes = IconButtonDefaults.shapes(),
                    colors = overlayButtonColors(),
                ) {
                    if (buffering) {
                        LoadingIndicator(
                            color = Color.White,
                            modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                        )
                    } else {
                        Icon(
                            imageVector =
                                when {
                                    ended -> Icons.Outlined.Replay
                                    playWhenReady -> Icons.Outlined.Pause
                                    else -> Icons.Outlined.PlayArrow
                                },
                            contentDescription = "Putar atau jeda",
                            tint = Color.White,
                            modifier = Modifier.size(mainIcon),
                        )
                    }
                }

                FilledIconButton(
                    onClick = hapticClick {
                        seekBy(SEEK_STEP_MS)
                        onPoke()
                    },
                    modifier = Modifier.size(sideBtn),
                    shapes = IconButtonDefaults.shapes(),
                    colors = overlayButtonColors(),
                ) {
                    Icon(
                        Icons.Outlined.Forward10,
                        contentDescription = "Maju 10 detik",
                        tint = Color.White,
                        modifier = Modifier.size(sideIcon),
                    )
                }

                if (!compact) {
                    FilledIconButton(
                        onClick = hapticClick {
                            onNext()
                            onPoke()
                        },
                        enabled = hasNext,
                        modifier = Modifier.size(sideBtn),
                        shapes = IconButtonDefaults.shapes(),
                        colors = overlayButtonColors(),
                    ) {
                        Icon(
                            Icons.Outlined.SkipNext,
                            contentDescription = "Episode selanjutnya",
                            tint = Color.White.copy(alpha = if (hasNext) 1f else 0.35f),
                            modifier = Modifier.size(sideIcon),
                        )
                    }
                }
            }

            if (compact) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val currentStr =
                            if (invertDuration) {
                                "-" + formatTime((dur - (scrubPos ?: pos)).coerceAtLeast(0))
                            } else {
                                formatTime(scrubPos ?: pos)
                            }
                        val totalStr = formatTime(dur)
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                                    append(currentStr)
                                }
                                if (dur > 0) {
                                    withStyle(SpanStyle(color = Color(0xB3FFFFFF))) {
                                        append(" / $totalStr")
                                    }
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0x66000000))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            onClick = hapticClick {
                                onToggleFullscreen()
                                onPoke()
                            },
                            modifier = Modifier.size(40.dp),
                            shapes = IconButtonDefaults.shapes(),
                            colors = overlayButtonColors(),
                        ) {
                            Icon(
                                Icons.Outlined.Fullscreen,
                                contentDescription = "Layar penuh",
                                tint = Color.White,
                            )
                        }
                    }
                    SeekBar(
                        position = pos,
                        buffered = buffered,
                        duration = dur,
                        onScrub = { onScrubPosChange(it) },
                        onSeek = { t ->
                            player.seekTo(t)
                            pos = t
                            onPoke()
                        },
                        edgeToEdge = true,
                        stamps = stamps,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (invertDuration) {
                                "-" + formatTime((dur - (scrubPos ?: pos)).coerceAtLeast(0))
                            } else {
                                formatTime(scrubPos ?: pos)
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        SeekBar(
                            position = pos,
                            buffered = buffered,
                            duration = dur,
                            onScrub = { onScrubPosChange(it) },
                            onSeek = { t ->
                                player.seekTo(t)
                                pos = t
                                onPoke()
                            },
                            stamps = stamps,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(
                            if (invertDuration) {
                                formatTime(scrubPos ?: pos)
                            } else {
                                "-" + formatTime((dur - (scrubPos ?: pos)).coerceAtLeast(0))
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(pillScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (PlayerButton.LOCK !in hiddenButtons) {
                            PillButton(Icons.Outlined.Lock, "Kunci") { onLockedChange(true) }
                        }
                        if (streamUrl.isNotBlank() && PlayerButton.EXTERNAL !in hiddenButtons) {
                            PillButton(Icons.AutoMirrored.Outlined.OpenInNew, "Eksternal") {
                                player.pause()
                                onLaunchExternal()
                                onPoke()
                            }
                        }
                        if (PlayerButton.ROTATE !in hiddenButtons) {
                            PillButton(Icons.Outlined.FullscreenExit, "Kecilkan") {
                                onToggleFullscreen()
                            }
                        }
                        if (PlayerButton.RESIZE !in hiddenButtons) {
                            PillButton(Icons.Outlined.AspectRatio, resize.label) {
                                val n = resize.next()
                                onResize(n)
                                showHint(n.label)
                                onPoke()
                            }
                        }
                        if (PlayerButton.SPEED !in hiddenButtons) {
                            PillButton(Icons.Outlined.Speed, speedLabel(speed)) {
                                visible = false
                                onOpenSpeed()
                            }
                        }
                        if (PlayerButton.SLEEP !in hiddenButtons) {
                            PillButton(
                                Icons.Outlined.Bedtime,
                                sleepButtonLabel(sleepDeadlineMs, sleepNow, sleepAfterEpisode),
                            ) {
                                visible = false
                                onOpenSleep()
                            }
                        }
                        if (hasSources && PlayerButton.QUALITY !in hiddenButtons) {
                            PillButton(Icons.Outlined.HighQuality, "Kualitas") {
                                visible = false
                                onSources()
                            }
                        }
                        if (hasEpisodes && PlayerButton.EPISODES !in hiddenButtons) {
                            PillButton(Icons.Outlined.VideoLibrary, "Episode") {
                                visible = false
                                onEpisodes()
                            }
                        }
                        if (opVisible) {
                            PillButton(Icons.Outlined.FastForward, "Lewati OP") { skipOp() }
                        }
                    }
                }
            }
        }
    }
}
