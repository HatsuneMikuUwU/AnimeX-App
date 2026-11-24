@androidx.annotation.OptIn(UnstableApi::class)
enum class PlayerResize(
    val label: String,
    val exoMode: Int,
) {
    Fit("Pas layar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Rentangkan", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Zoom("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ;

    fun next(): PlayerResize = entries[(ordinal + 1) % entries.size]
}

private const val SEEK_STEP_MS = 10_000L

private const val HOLD_SPEED = 2f

private const val SWIPE_SEEK_FULL_WIDTH_MS = 90_000f

private const val SKIP_OP_UNTIL_PERCENT = 50

private const val STAMP_SHOW_MS = 6_000L

internal fun speedLabel(speed: Float): String = speed.toString().removeSuffix(".0") + "x"

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

private fun clockNow(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

@Composable
internal fun YouTubeAutoplayButton(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    FilledIconButton(
        onClick = hapticClick(block = onToggle),
        modifier = Modifier.size(40.dp),
        shapes = IconButtonDefaults.shapes(),
        colors = overlayButtonColors(),
    ) {
        Icon(
            if (checked) Icons.Outlined.PlayCircleOutline else Icons.Outlined.PauseCircleOutline,
            contentDescription = if (checked) "Autoplay aktif" else "Autoplay mati",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun PlayerChrome(
    player: Player,
    title: String,
    subtitle: String,
    locked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    resize: PlayerResize,
    onResize: (PlayerResize) -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    sleepDeadlineMs: Long,
    sleepAfterEpisode: Boolean,
    onSleepTimer: (deadlineMs: Long, afterEpisode: Boolean) -> Unit,
    onSleepExpired: () -> Unit,
    hasNext: Boolean,
    onNext: () -> Unit,
    hasPrev: Boolean = false,
    onPrev: () -> Unit = {},
    hasSources: Boolean,
    onSources: () -> Unit,
    hasEpisodes: Boolean,
    onEpisodes: () -> Unit,
    onBack: () -> Unit,
    epId: String,
    onOpenSpeed: () -> Unit = {},
    onOpenSleep: () -> Unit = {},
    compact: Boolean = false,
    minimized: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onPullDownDelta: ((Float) -> Unit)? = null,
    onPullDownEnd: ((Boolean) -> Unit)? = null,
    onComment: (() -> Unit)? = null,
    streamUrl: String = "",
    onMessage: (String) -> Unit = {},
    onProgressSaved: (crossedDone: Boolean) -> Unit = {},
    loadStamps: suspend (durationMs: Long) -> List<SkipStamp> = { emptyList() },
    onVoteStamp: ((SkipStamp, Boolean) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val activity = remember(ctx) { ctx.findActivity() }
    val audio = remember(ctx) { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val externalLauncher =
        rememberExternalLauncher { data ->
            ExternalPlayer.record(epId, data)?.let { r ->
                player.seekTo(if (r.completed) r.durationMs else r.positionMs)
                onProgressSaved(r.crossedDone)
            }
        }

    val latestPullDelta by rememberUpdatedState(onPullDownDelta)
    val latestPullEnd by rememberUpdatedState(onPullDownEnd)
    val latestToggleFullscreen by rememberUpdatedState(onToggleFullscreen)

    val hiddenButtons by PlayerPrefs.hiddenButtons.collectAsStateWithLifecycle()
    val gesturesEnabled by PlayerPrefs.gesturesEnabled.collectAsStateWithLifecycle()
    val autoplayEnabled by PlayerPrefs.autoplayEnabled.collectAsStateWithLifecycle()
    val inPip by PipHelper.inPip.collectAsStateWithLifecycle()
    val invertDuration by PlayerPrefs.invertDuration.collectAsStateWithLifecycle()
    val reduceMotion by PlayerPrefs.reduceMotion.collectAsStateWithLifecycle()
    val swapVolBright by PlayerPrefs.swapVolumeBrightness.collectAsStateWithLifecycle()
    val swapIndicator by PlayerPrefs.swapIndicator.collectAsStateWithLifecycle()
    val skipIntroMs by PlayerPrefs.skipIntroMs.collectAsStateWithLifecycle()
    val aniSkipEnabled by PlayerPrefs.aniSkipEnabled.collectAsStateWithLifecycle()
    val autoSkipOp by PlayerPrefs.autoSkipOp.collectAsStateWithLifecycle()
    val autoSkipEd by PlayerPrefs.autoSkipEd.collectAsStateWithLifecycle()
    val autoSkipRecap by PlayerPrefs.autoSkipRecap.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    val pillScroll = rememberScrollState()
    var sleepNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    var playWhenReady by remember { mutableStateOf(player.playWhenReady) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var ended by remember { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var pos by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0)) }
    var buffered by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    var scrubPos by remember { mutableStateOf<Long?>(null) }
    var swipeSeek by remember { mutableStateOf<Long?>(null) }
    var holding by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val doubleTapHaptic = rememberStableHapticAction(HapticFeedbackType.GestureThresholdActivate)
    val holdHaptic = rememberStableHapticAction(HapticFeedbackType.LongPress)
    val dragTickHaptic = rememberStableHapticAction(HapticFeedbackType.SegmentFrequentTick)
    val dragEndHaptic = rememberStableHapticAction(HapticFeedbackType.GestureEnd)

    var hint by remember { mutableStateOf<String?>(null) }
    var hintKey by remember { mutableIntStateOf(0) }

    var seekAcc by remember { mutableIntStateOf(0) }
    var seekAccKey by remember { mutableIntStateOf(0) }

    var side by remember { mutableIntStateOf(0) }
    var sideKey by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    val volumeBoostEnabled by PlayerPrefs.volumeBoost.collectAsStateWithLifecycle()
    var loudness by remember(player) { mutableStateOf<LoudnessEnhancer?>(null) }
    var boostExtra by remember(player) { mutableFloatStateOf(0f) }
    var boostFailed by remember(player) { mutableStateOf(false) }
    var volumeLocked by remember { mutableStateOf(false) }

    var lockTapKey by remember { mutableIntStateOf(0) }
    var lockIconVisible by remember { mutableStateOf(true) }
    var clock by remember { mutableStateOf(clockNow()) }

    var stamps by remember(epId) { mutableStateOf<List<SkipStamp>>(emptyList()) }
    var stampsRequested by remember(epId) { mutableStateOf(false) }
    var stampsLoaded by remember(epId) { mutableStateOf(false) }
    var dismissedStamp by remember(epId) { mutableStateOf<SkipStamp?>(null) }
    var autoSkippedStamps by remember(epId) { mutableStateOf<Set<SkipStamp>>(emptySet()) }
    var votedStamps by remember(epId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    fun showHint(text: String) {
        hint = text
        hintKey++
    }

    LaunchedEffect(minimized) {
        if (minimized) visible = false
    }

    LaunchedEffect(sleepDeadlineMs) {
        if (sleepDeadlineMs <= 0L) return@LaunchedEffect
        while (true) {
            sleepNow = SystemClock.elapsedRealtime()
            val left = sleepDeadlineMs - sleepNow
            if (left <= 0L) break
            delay(minOf(left, SLEEP_TICK_MS))
        }
        player.pause()
        onSleepTimer(0L, false)
        onSleepExpired()
        showHint("Timer tidur abis, video dijeda")
    }

    fun seekBy(delta: Long) {
        val d = player.duration
        val max = if (d > 0) d else Long.MAX_VALUE
        val target = (player.currentPosition + delta).coerceIn(0L, max)
        player.seekTo(target)
        pos = target
    }

    fun togglePlay() {
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
    }

    fun currentBrightness(): Float {
        val set = activity?.window?.attributes?.screenBrightness ?: -1f
        if (set >= 0f) return set
        return runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    fun applyBrightness(value: Float) {
        val w = activity?.window ?: return
        val v = value.coerceIn(0.02f, 1f)
        w.attributes = w.attributes.apply { screenBrightness = v }
        if (PlayerPrefs.rememberLevels.value) {
            PlayerPrefs.setBrightnessValue(v)
        }
    }

    fun currentVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun applyBoost(extra: Float) {
        boostExtra = extra
        if (extra > 0f) {
            val gain = (extra * 1000f).toInt()
            val existing = loudness
            if (existing != null) {
                runCatching { existing.setTargetGain(gain) }
            } else {
                val sessionId = (player as? ExoPlayer)?.audioSessionId
                if (sessionId != null && sessionId != AudioManager.ERROR && sessionId != 0) {
                    runCatching {
                        loudness = LoudnessEnhancer(sessionId).apply {
                            setTargetGain(gain)
                            enabled = true
                        }
                    }.onFailure { boostFailed = true }
                }
            }
        } else {
            loudness?.release()
            loudness = null
        }
    }

    fun applyVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val level = (value.coerceAtMost(1f) * max).roundToInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        applyBoost(if (volumeBoostEnabled) (value - 1f).coerceIn(0f, 1f) else 0f)
        if (PlayerPrefs.rememberLevels.value) {
            PlayerPrefs.setVolumeValue(level)
        }
    }

    DisposableEffect(player) {
        onDispose {
            loudness?.release()
            loudness = null
        }
    }
    LaunchedEffect(volumeBoostEnabled) {
        if (!volumeBoostEnabled && boostExtra > 0f) {
            applyBoost(0f)
            volume = volume.coerceAtMost(1f)
        }
    }

    LaunchedEffect(Unit) {
        if (PlayerPrefs.rememberLevels.value && PlayerPrefs.brightnessValue.value >= 0f) {
            val v = PlayerPrefs.brightnessValue.value
            brightness = v
            applyBrightness(v)
        }
        if (PlayerPrefs.rememberLevels.value && PlayerPrefs.volumeValue.value >= 0) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val v = PlayerPrefs.volumeValue.value.toFloat() / max
            volume = v
            applyVolume(v)
        }
    }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying2: Boolean) {
                    isPlaying = isPlaying2
                }

                override fun onPlayWhenReadyChanged(
                    value: Boolean,
                    reason: Int,
                ) {
                    playWhenReady = value
                }

                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    ended = state == Player.STATE_ENDED
                    dur = player.duration.coerceAtLeast(0)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, visible, minimized) {
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0)
            buffered = player.bufferedPosition.coerceAtLeast(0)
            dur = player.duration.coerceAtLeast(0)
            delay(
                when {
                    visible && !minimized -> 250
                    minimized -> 500
                    compact -> 500
                    else -> 1_000
                },
            )
        }
    }

    LaunchedEffect(isPlaying) { PipHelper.isPlaying = isPlaying }

    LaunchedEffect(ended) { if (ended && !minimized) visible = true }
    LaunchedEffect(playWhenReady) { if (!playWhenReady && !minimized) visible = true }

    LaunchedEffect(epId, dur, aniSkipEnabled) {
        if (!aniSkipEnabled) {
            stampsRequested = false
            stamps = emptyList()
            stampsLoaded = true
            return@LaunchedEffect
        }
        if (dur > 0 && !stampsRequested) {
            stampsRequested = true
            stamps = runCatching { loadStamps(dur) }.getOrDefault(emptyList())
            stampsLoaded = true
        }
    }

    val activeStamp =
        if (aniSkipEnabled) {
            stamps.firstOrNull { pos >= it.startMs && pos < it.endMs - 1_000 && it != dismissedStamp }
        } else {
            null
        }

    val shouldAutoSkipActive =
        activeStamp != null &&
            !locked &&
            activeStamp !in autoSkippedStamps &&
            when (activeStamp.type) {
                SkipType.Opening, SkipType.MixedOpening -> autoSkipOp
                SkipType.Ending, SkipType.MixedEnding -> autoSkipEd
                SkipType.Recap -> autoSkipRecap
            }

    LaunchedEffect(activeStamp, shouldAutoSkipActive) {
        val stamp = activeStamp ?: return@LaunchedEffect
        if (shouldAutoSkipActive) {
            autoSkippedStamps = autoSkippedStamps + stamp
            dismissedStamp = stamp
            player.seekTo(stamp.endMs)
            pos = stamp.endMs
            showHint("${stamp.type.label} otomatis")
            return@LaunchedEffect
        }
        delay(STAMP_SHOW_MS)
        dismissedStamp = stamp
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = clockNow()
            delay(15_000)
        }
    }

    LaunchedEffect(player, speed, holding) {
        player.setPlaybackSpeed(if (holding) HOLD_SPEED else speed)
    }

    LaunchedEffect(visible, poke, isPlaying, scrubPos != null, locked, minimized, PlayerPrefs.controlsTimeoutMs.value) {
        if (visible && !minimized && isPlaying && scrubPos == null && !locked) {
            val timeout = PlayerPrefs.controlsTimeoutMs.value
                .toLong()
                .coerceAtLeast(500L)
            delay(timeout)
            visible = false
        }
    }

    LaunchedEffect(hintKey) {
        if (hint != null) {
            delay(1_000)
            hint = null
        }
    }

    LaunchedEffect(seekAccKey) {
        if (seekAcc != 0) {
            delay(700)
            seekAcc = 0
        }
    }

    LaunchedEffect(sideKey, dragging) {
        if (side != 0 && !dragging) {
            delay(800)
            side = 0
        }
    }

    LaunchedEffect(locked, lockTapKey) {
        if (!locked) return@LaunchedEffect
        lockIconVisible = true
        delay(5_000)
        lockIconVisible = false
    }

    LaunchedEffect(locked) {
        if (locked) visible = false
    }

    if (minimized) {
        if (buffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        return
    }

    val openingStamp = stamps.firstOrNull { it.type == SkipType.Opening || it.type == SkipType.MixedOpening }
    val opVisible = stampsLoaded && dur > 0 && pos * 100L / dur < SKIP_OP_UNTIL_PERCENT
    val skipOp: () -> Unit = {
        val end = openingStamp?.endMs
        if (end != null && pos < end - 1_000) {
            player.seekTo(end)
            pos = end
        } else {
            seekBy(skipIntroMs.toLong())
        }
        poke++
    }

    val pullUpFullscreenThresholdPx = with(density) { 64.dp.toPx() }
    val pullDownMinimizeThresholdPx = with(density) { 88.dp.toPx() }

    val gestures: Modifier =
        if (locked) {
            Modifier.pointerInput(Unit) { detectTapGestures { lockTapKey++ } }
        } else {
            Modifier
                .pointerInput(player, gesturesEnabled, swapVolBright) {
                    detectTapGestures(
                        onTap = {
                            visible = !visible
                            poke++
                        },
                        onDoubleTap =
                            if (!gesturesEnabled) {
                                null
                            } else {
                                { o ->
                                    doubleTapHaptic()
                                    val w = size.width
                                    when {
                                        o.x < w * 0.4f -> {
                                            seekBy(-SEEK_STEP_MS)
                                            seekAcc = if (seekAcc <= 0) seekAcc - 10 else -10
                                            seekAccKey++
                                        }
                                        o.x > w * 0.6f -> {
                                            seekBy(SEEK_STEP_MS)
                                            seekAcc = if (seekAcc >= 0) seekAcc + 10 else 10
                                            seekAccKey++
                                        }
                                        else -> togglePlay()
                                    }
                                }
                            },
                        onLongPress =
                            if (gesturesEnabled) {
                                {
                                    holdHaptic()
                                    holding = true
                                }
                            } else {
                                null
                            },
                        onPress = {
                            tryAwaitRelease()
                            if (holding) holding = false
                        },
                    )
                }.pointerInput(player, gesturesEnabled, swapVolBright, compact) {
                    var mode = 0
                    var startX = 0f
                    var accX = 0f
                    var accY = 0f
                    var startPos = 0L
                    val finish = {
                        if (mode == 1) {
                            dragEndHaptic()
                            swipeSeek?.let { player.seekTo(it) }
                            swipeSeek = null
                        } else if (mode == 4) {
                            val shouldMin = accY > pullDownMinimizeThresholdPx
                            if (shouldMin) dragEndHaptic()
                            latestPullEnd?.invoke(shouldMin)
                        } else if (mode == 5) {
                            if (accY < -pullUpFullscreenThresholdPx) {
                                dragEndHaptic()
                                latestToggleFullscreen()
                            }
                        } else if (mode != 0) {
                            dragEndHaptic()
                        }
                        mode = 0
                        dragging = false
                        sideKey++
                    }
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = 0
                            startX = o.x
                            accX = 0f
                            accY = 0f
                            startPos = player.currentPosition.coerceAtLeast(0)
                            dragging = true
                        },
                        onDragEnd = { finish() },
                        onDragCancel = {
                            if (mode == 4) latestPullEnd?.invoke(false)
                            mode = 0
                            swipeSeek = null
                            dragging = false
                            sideKey++
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            accX += amount.x
                            accY += amount.y
                            if (mode == 0) {
                                if (abs(accX) > 22f || abs(accY) > 22f) {
                                    if (abs(accX) > abs(accY)) {
                                        if (gesturesEnabled) {
                                            mode = 1
                                            doubleTapHaptic()
                                        }
                                    } else if (compact) {
                                        mode = if (accY > 0f) 4 else 5
                                        if (mode == 4) latestPullDelta?.invoke(accY)
                                    } else if (gesturesEnabled) {
                                        mode = if (startX < size.width / 2f) {
                                            if (swapVolBright) 3 else 2
                                        } else {
                                            if (swapVolBright) 2 else 3
                                        }
                                        doubleTapHaptic()
                                        if (mode == 2) brightness = currentBrightness()
                                        if (mode == 3) {
                                            val sys = currentVolume()
                                            volume = if (sys >= 1f && boostExtra > 0f) 1f + boostExtra else sys
                                            volumeLocked = volume < 1f
                                        }
                                    }
                                }
                            } else {
                                when (mode) {
                                    1 -> {
                                        val d = player.duration.coerceAtLeast(0)
                                        if (d > 0) {
                                            val delta = (accX / size.width * SWIPE_SEEK_FULL_WIDTH_MS).toLong()
                                            val next = (startPos + delta).coerceIn(0L, d)
                                            if (swipeSeek?.let { it / 5000L } != next / 5000L) dragTickHaptic()
                                            swipeSeek = next
                                        }
                                    }
                                    2 -> {
                                        val prevB = brightness
                                        brightness = (brightness - amount.y / size.height * 1.2f).coerceIn(0f, 1f)
                                        if (brightness != prevB &&
                                            (brightness == 0f || brightness == 1f)
                                        ) {
                                            dragTickHaptic()
                                        }
                                        applyBrightness(brightness)
                                        side = if (swapVolBright) 2 else 1
                                        sideKey++
                                    }
                                    3 -> {
                                        val cap = if (volumeBoostEnabled && !volumeLocked) 2f else 1f
                                        val prevV = volume
                                        volume = (volume - amount.y / size.height * 1.2f).coerceIn(0f, cap)
                                        if (volume != prevV &&
                                            (volume == 0f || volume == cap || volume == 1f)
                                        ) {
                                            dragTickHaptic()
                                        }
                                        applyVolume(volume)
                                        side = if (swapVolBright) 1 else 2
                                        sideKey++
                                    }
                                    4 -> {
                                        latestPullDelta?.invoke(accY.coerceAtLeast(0f))
                                    }
                                    5 -> {
                                        if (accY < -pullUpFullscreenThresholdPx && hint == null) {
                                            showHint("Lepas buat layar penuh")
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
        }

    val controlsShown = visible && !locked && !inPip

    Box(Modifier.fillMaxSize().then(gestures)) {
        if (buffering && !controlsShown) {
            LoadingIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(if (compact || inPip) 36.dp else 48.dp),
            )
        }

        if (compact && !controlsShown && !inPip && dur > 0) {
            val playedFrac = ((scrubPos ?: pos).toFloat() / dur).coerceIn(0f, 1f)
            val bufferedFrac = (buffered.toFloat() / dur).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color(0x40FFFFFF)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bufferedFrac).background(Color(0x66FFFFFF)))
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(playedFrac)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }


        PlayerControlsOverlay(
            controlsShown = controlsShown,
            reduceMotion = reduceMotion,
            compact = compact,
            title = title,
            subtitle = subtitle,
            clock = clockNow(),
            hasEpisodes = hasEpisodes,
            hasSources = hasSources,
            hasPrev = hasPrev,
            hasNext = hasNext,
            ended = ended,
            buffering = buffering,
            playWhenReady = playWhenReady,
            pos = pos,
            dur = dur,
            buffered = buffered,
            scrubPos = scrubPos,
            invertDuration = invertDuration,
            autoplayEnabled = autoplayEnabled,
            speed = speed,
            resize = resize,
            stamps = stamps,
            opVisible = opVisible,
            hiddenButtons = hiddenButtons,
            sleepDeadlineMs = sleepDeadlineMs,
            sleepNow = sleepNow,
            sleepAfterEpisode = sleepAfterEpisode,
            pillScroll = pillScroll,
            player = player,
            onBack = onBack,
            onLockedChange = onLockedChange,
            onToggleFullscreen = onToggleFullscreen,
            onComment = onComment,
            onEpisodes = onEpisodes,
            onSources = onSources,
            onPrev = onPrev,
            onNext = onNext,
            onOpenSpeed = onOpenSpeed,
            onOpenSleep = onOpenSleep,
            onResize = onResize,
            onLaunchExternal = { ExternalPlayer.open(ctx, externalLauncher, epId, streamUrl, title, onMessage) },
            togglePlay = togglePlay,
            seekBy = seekBy,
            skipOp = skipOp,
            onPoke = { poke++ },
            onScrubPosChange = { scrubPos = it },
            showHint = showHint,
        )

        PlayerGestureHud(
            visible = visible,
            compact = compact,
            reduceMotion = reduceMotion,
            locked = locked,
            lockIconVisible = lockIconVisible,
            activeStamp = activeStamp,
            shouldAutoSkipActive = shouldAutoSkipActive,
            votedStamps = votedStamps,
            pos = pos,
            seekAcc = seekAcc,
            swipeSeek = swipeSeek,
            side = side,
            volume = volume,
            brightness = brightness,
            boostFailed = boostFailed,
            swapVolBright = swapVolBright,
            swapIndicator = swapIndicator,
            holding = holding,
            hint = hint,
            audio = audio,
            player = player,
            onLockedChange = onLockedChange,
            onVoteStamp = onVoteStamp,
            onDismissStamp = { dismissedStamp = it },
        )
    }
}
