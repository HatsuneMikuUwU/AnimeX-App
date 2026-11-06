@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.discussion

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.DiscussionRowPlaceholder
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.FloatingGlassButton
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.MiniPlayerBottomInset
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.UserAvatar
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.rememberStableHapticAction
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.theme.barGlass
import com.uwu.animex.ui.theme.bottomScrim
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.theme.resolveGlassStyle
import com.uwu.animex.ui.viewmodel.DiscussionScope
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import kotlin.math.roundToInt

private val InputBarHeight = 56.dp

private val InputBarFade = 24.dp

private val AvatarSize = 36.dp

private val ReplyAvatarSize = 28.dp

private val ThreadIndent = AvatarSize + 8.dp

private val SwipeTrigger = 64.dp

private val BubbleShape = RoundedCornerShape(20.dp)

@Stable
private class ThreadState {
    var expanded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var replies by mutableStateOf<List<UserApi.Discussion>>(emptyList())
}

private data class ReplyTarget(
    val parent: UserApi.Discussion,
    val username: String,
    val snippet: String,
    val mention: Boolean,
)

@Composable
fun DiscussionScreen(
    movieId: String,
    movieTitle: String?,
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
    scope: DiscussionScope = DiscussionScope.Movie,
    viewModel: DiscussionViewModel =
        viewModel(
            factory = DiscussionViewModel.factory(movieId, scope),
            key = "${scope.name}:$movieId",
        ),
) {
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val actionBusy by viewModel.actionBusy.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val loadMoreError by viewModel.loadMoreError.collectAsStateWithLifecycle()
    val session by UserSession.user.collectAsStateWithLifecycle()
    val loggedIn = session?.isAuthenticated == true
    var draft by rememberSaveable { mutableStateOf("") }
    var logoutDialog by remember { mutableStateOf(false) }
    var scrollOnUpdate by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    val threads = remember { HashMap<String, ThreadState>() }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val composerKey = remember(movieId, scope) { "${scope.name}:$movieId" }
    val barHeight = with(density) { barHeightPx.toDp() }
    DisposableEffect(composerKey) {
        onDispose { MiniPlayerBottomInset.clearDiscussionComposer(composerKey) }
    }
    val listState = rememberLazyListState()
    val shouldLoadOlder by remember { derivedStateOf { listState.firstVisibleItemIndex <= 2 } }
    val backdrop = rememberBlurBackdrop()
    val isEpisode = scope == DiscussionScope.Episode
    val headerTitle = if (isEpisode) "Komentar" else "Diskusi"
    val emptyTitle = if (isEpisode) "Belum ada komentar" else "Belum ada diskusi"
    val inputHint = if (isEpisode) "Tulis komentar..." else "Tulis diskusi..."
    val items =
        when (val s = state) {
            is UiState.Ready -> s.value
            else -> emptyList()
        }
    val chronologicalItems = remember(items) { items.asReversed() }

    fun threadOf(id: String): ThreadState = threads.getOrPut(id) { ThreadState() }

    fun loadThread(
        parent: UserApi.Discussion,
        thread: ThreadState,
    ) {
        thread.expanded = true
        thread.loading = true
        viewModel.loadReplies(parent) { loaded ->
            thread.replies = loaded
            thread.loading = false
        }
    }

    fun toggleThread(parent: UserApi.Discussion) {
        val id = parent.id?.takeIf(String::isNotBlank) ?: return
        val thread = threadOf(id)
        if (thread.expanded) thread.expanded = false else loadThread(parent, thread)
    }

    fun startReply(target: ReplyTarget) {
        replyTarget = target
        if (target.mention && draft.isBlank()) draft = "@${target.username} "
    }

    fun cancelReply() {
        val target = replyTarget ?: return
        if (draft.trim() == "@${target.username}") draft = ""
        replyTarget = null
    }

    BackHandler(enabled = replyTarget != null) { cancelReply() }

    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose {}
        } else {
            val previous = window.attributes.softInputMode
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose { window.setSoftInputMode(previous) }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { snackbar.show(snackScope, it) }
    }

    LaunchedEffect(replyTarget) {
        if (replyTarget != null) runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(chronologicalItems) {
        if (chronologicalItems.isNotEmpty() && (!initialScrollDone || scrollOnUpdate)) {
            listState.animateScrollToItem(chronologicalItems.lastIndex)
            initialScrollDone = true
            scrollOnUpdate = false
        }
    }

    LaunchedEffect(shouldLoadOlder, hasMore, loadingMore, loadMoreError, initialScrollDone) {
        if (initialScrollDone && shouldLoadOlder && hasMore && !loadingMore && !loadMoreError) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        containerColor = screenContainerColor(),
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                trailingIcon = if (loggedIn) Icons.AutoMirrored.Outlined.Logout else null,
                trailingDescription = "Keluar",
                trailingTint = MaterialTheme.colorScheme.error,
                onTrailingClick = { logoutDialog = true },
                title = {
                    Column {
                        Text(
                            headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!movieTitle.isNullOrBlank()) {
                            Text(
                                movieTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().imePadding()) {
            BlurContentBox(pad, backdrop, contentAlignment = Alignment.TopCenter) {
                ExpressivePullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.refresh() },
                    errors = viewModel.refreshError,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                ) {
                    UiStateContent(
                        state = state,
                        onRetry = { viewModel.refresh() },
                        loading = { DiscussionListPlaceholder() },
                    ) { _ ->
                        if (chronologicalItems.isEmpty()) {
                            AnimatedEmptyState(
                                icon = Icons.Outlined.Forum,
                                title = emptyTitle,
                                message = "Jadilah yang pertama!",
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding =
                                    PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 16.dp + LocalTopInset.current,
                                        bottom = barHeight + 8.dp,
                                    ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (loadMoreError) {
                                    item(key = "load-more-retry") {
                                        TextButton(
                                            onClick = hapticClick { viewModel.retryLoadMore() },
                                            modifier = Modifier.fillMaxWidth(),
                                            shapes = ButtonDefaults.shapes(),
                                        ) { Text("Gagal muat lagi. Coba lagi") }
                                    }
                                } else if (loadingMore) {
                                    items(
                                        count = 3,
                                        key = { "load-more-placeholder-$it" },
                                        contentType = { "discussionPlaceholder" },
                                    ) { index ->
                                        DiscussionRowPlaceholder(
                                            fraction = listOf(0.7f, 0.5f, 0.8f)[index % 3],
                                        )
                                    }
                                }
                                itemsIndexed(chronologicalItems, key = { index, d -> d.id ?: "item-$index" }) { _, d ->
                                    val thread =
                                        if (isEpisode) d.id?.takeIf(String::isNotBlank)?.let(::threadOf) else null
                                    DiscussionRow(
                                        discussion = d,
                                        currentUserId = session?.id,
                                        ownImage = session?.image,
                                        loggedIn = loggedIn,
                                        isEpisode = isEpisode,
                                        actionBusy = actionBusy || sending,
                                        thread = thread,
                                        onToggleReplies = { toggleThread(d) },
                                        onStartReply = ::startReply,
                                        onEdit = { item, text, onComplete ->
                                            viewModel.edit(item, text) {
                                                onComplete()
                                                snackbar.show(
                                                    snackScope,
                                                    if (isEpisode) "Komentar diperbarui" else "Diskusi diperbarui",
                                                )
                                            }
                                        },
                                        onDelete = { item, onComplete ->
                                            viewModel.delete(item) {
                                                onComplete()
                                                snackbar.show(
                                                    snackScope,
                                                    if (isEpisode) "Komentar dihapus" else "Diskusi dihapus",
                                                )
                                            }
                                        },
                                        onReport = { item, onComplete ->
                                            viewModel.report(item) {
                                                onComplete()
                                                snackbar.show(snackScope, "Laporan dikirim")
                                            }
                                        },
                                        onReact = { item, type -> viewModel.react(item, type) },
                                        modifier = animateItemExpressive(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barHeight),
            )
            DiscussionInputBar(
                draft = draft,
                onDraftChange = { draft = it },
                loggedIn = loggedIn,
                sending = sending,
                backdrop = backdrop,
                placeholder = if (replyTarget != null) "Tulis balasan..." else inputHint,
                replyTo = replyTarget,
                onCancelReply = ::cancelReply,
                focusRequester = focusRequester,
                onSend = { text ->
                    val target = replyTarget
                    if (target == null) {
                        viewModel.send(text) {
                            draft = ""
                            scrollOnUpdate = true
                        }
                    } else {
                        viewModel.reply(target.parent, text) {
                            draft = ""
                            replyTarget = null
                            snackbar.show(snackScope, "Balasan terkirim")
                            target.parent.id?.takeIf(String::isNotBlank)?.let { id ->
                                loadThread(target.parent, threadOf(id))
                            }
                        }
                    }
                },
                onNeedLogin = onNeedLogin,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged {
                            barHeightPx = it.height
                            MiniPlayerBottomInset.updateDiscussionComposer(
                                composerKey,
                                with(density) { it.height.toDp() },
                            )
                        },
            )
        }
    }

    if (logoutDialog) {
        AppDialog(
            icon = Icons.AutoMirrored.Outlined.Logout,
            title = "Keluar dari akun?",
            onDismiss = { logoutDialog = false },
            text = { Text("Kamu perlu login lagi untuk ikut diskusi.") },
            confirmButton = {
                DialogDestructiveButton("Keluar") {
                    logoutDialog = false
                    UserSession.clear()
                }
            },
            dismissButton = { DialogCancelButton { logoutDialog = false } },
        )
    }
}

@Composable
private fun DiscussionInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    loggedIn: Boolean,
    sending: Boolean,
    backdrop: LayerBackdrop?,
    onSend: (String) -> Unit,
    onNeedLogin: () -> Unit,
    replyTo: ReplyTarget?,
    onCancelReply: () -> Unit,
    focusRequester: FocusRequester,
    placeholder: String = "Tulis diskusi...",
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    val glassStyle = resolveGlassStyle(backdrop, appearance.barEffect)
    val brush = remember(cs.background) { bottomScrim(cs.background) }
    val shape = RoundedCornerShape(InputBarHeight / 2)
    val previewShape = CircleShape
    val canSend = loggedIn && !sending && draft.isNotBlank()

    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val extra = InputBarFade.toPx()
                drawRect(brush, topLeft = Offset(0f, -extra), size = Size(size.width, size.height + extra))
            }.navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            AnimatedContent(
                targetState = replyTo,
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "reply-preview",
            ) { target ->
                if (target != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .barGlass(glassStyle, backdrop, previewShape, cs.surfaceContainer)
                            .clip(previewShape)
                            .padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(32.dp).clip(CircleShape).background(cs.primary))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Membalas ${target.username}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                target.snippet.replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = hapticClick(block = onCancelReply)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Batal membalas", tint = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = InputBarHeight)
                        .barGlass(glassStyle, backdrop, shape, cs.surfaceContainer)
                        .clip(shape)
                        .then(if (loggedIn) Modifier else Modifier.hapticClick(onClick = onNeedLogin))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (loggedIn) {
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            enabled = !sending,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                            cursorBrush = SolidColor(cs.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            decorationBox = { inner ->
                                if (draft.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                inner()
                            },
                        )
                    } else {
                        Text(
                            "Login untuk ikut diskusi",
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                FloatingGlassButton(
                    contentDescription = if (loggedIn) "Kirim" else "Login",
                    onClick =
                        hapticClick {
                            if (!loggedIn) {
                                onNeedLogin()
                            } else if (canSend) {
                                onSend(draft.trim())
                            }
                        },
                    backdrop = backdrop,
                    size = InputBarHeight,
                ) {
                    when {
                        sending -> AppLoadingIndicator(Modifier.size(24.dp))
                        !loggedIn -> Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, tint = cs.onSurface)
                        else ->
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                tint = if (canSend) cs.primary else cs.onSurface.copy(alpha = 0.38f),
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToReply(
    enabled: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val triggerPx = with(LocalDensity.current) { SwipeTrigger.toPx() }
    val maxPx = triggerPx * 1.35f
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    val passed by remember(triggerPx) { derivedStateOf { offset <= -triggerPx } }
    val tick = rememberStableHapticAction(HapticFeedbackType.SegmentTick)
    val latestReply by rememberUpdatedState(onReply)

    Box(
        modifier
            .fillMaxWidth()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var crossed = false
                val settle: () -> Unit = {
                    scope.launch {
                        animate(
                            initialValue = offset,
                            targetValue = 0f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                        ) { value, _ -> offset = value }
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { crossed = false },
                    onDragEnd = {
                        val triggered = offset <= -triggerPx
                        settle()
                        if (triggered) latestReply()
                    },
                    onDragCancel = { settle() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val step = if (offset <= -triggerPx) dragAmount * 0.4f else dragAmount
                        offset = (offset + step).coerceIn(-maxPx, 0f)
                        val nowCrossed = offset <= -triggerPx
                        if (nowCrossed && !crossed) tick()
                        crossed = nowCrossed
                    },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(36.dp)
                .graphicsLayer {
                    val progress = (-offset / triggerPx).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = scaleX
                }.background(if (passed) cs.primary else cs.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Reply,
                contentDescription = null,
                tint = if (passed) cs.onPrimary else cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) { content() }
    }
}

@Composable
private fun ActionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val color = if (active) cs.primary else cs.onSurfaceVariant
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.hapticClick(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        if (text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
            )
        }
    }
}

@Composable
private fun ThreadToggle(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.hapticClick(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.primary,
        )
    }
}

@Composable
private fun ReplyItem(
    reply: UserApi.Discussion,
    canReply: Boolean,
    onReply: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val time = reply.time ?: reply.createdAt
    SwipeToReply(enabled = canReply, onReply = onReply) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            UserAvatar(reply.username, pokemon = reply.pokemon, size = ReplyAvatarSize)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            reply.username ?: "User",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (reply.body().isNotBlank()) {
                            Text(reply.body(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Row(Modifier.padding(start = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!time.isNullOrBlank()) {
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                        )
                    }
                    ActionLabel("Balas", enabled = canReply, onClick = onReply)
                }
            }
        }
    }
}

@Composable
private fun DiscussionRow(
    discussion: UserApi.Discussion,
    currentUserId: String?,
    ownImage: String?,
    loggedIn: Boolean,
    isEpisode: Boolean,
    actionBusy: Boolean,
    thread: ThreadState?,
    onToggleReplies: () -> Unit,
    onStartReply: (ReplyTarget) -> Unit,
    onEdit: (UserApi.Discussion, String, () -> Unit) -> Unit,
    onDelete: (UserApi.Discussion, () -> Unit) -> Unit,
    onReport: (UserApi.Discussion, () -> Unit) -> Unit,
    onReact: (UserApi.Discussion, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val isMine = !currentUserId.isNullOrBlank() && discussion.userId == currentUserId
    val hasId = !discussion.id.isNullOrBlank()
    var actionsExpanded by remember(discussion.id) { mutableStateOf(false) }
    var editDialog by remember(discussion.id) { mutableStateOf(false) }
    var deleteDialog by remember(discussion.id) { mutableStateOf(false) }
    var reportDialog by remember(discussion.id) { mutableStateOf(false) }
    var editText by remember(discussion.id, discussion.body()) { mutableStateOf(discussion.body()) }
    val canReply = isEpisode && loggedIn && hasId && !actionBusy
    val replyCount = discussion.replyCount?.toIntOrNull() ?: 0
    val expanded = thread?.expanded == true
    val showThreadLine =
        thread != null && thread.expanded && !thread.loading && thread.replies.isNotEmpty()
    val lineColor = cs.outlineVariant
    val liked = discussion.reaction.equals("Y", true)
    val disliked = discussion.reaction.equals("N", true)
    val reactEnabled = loggedIn && hasId
    val time = discussion.time ?: discussion.createdAt
    val meta =
        listOfNotNull(
            time?.takeIf { it.isNotBlank() },
            "diedit".takeIf { !discussion.timeUpdated.isNullOrBlank() },
        ).joinToString(" · ")
    val replyToComment = {
        onStartReply(ReplyTarget(discussion, discussion.username ?: "User", discussion.body(), false))
    }

    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (showThreadLine) {
                    val x = AvatarSize.toPx() / 2
                    drawLine(
                        color = lineColor,
                        start = Offset(x, AvatarSize.toPx() + 6.dp.toPx()),
                        end = Offset(x, size.height - 12.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SwipeToReply(enabled = canReply, onReply = replyToComment) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                UserAvatar(
                    discussion.username,
                    imageUrl = if (isMine) ownImage else null,
                    pokemon = discussion.pokemon,
                    size = AvatarSize,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Box {
                        Surface(
                            color = if (isMine) cs.primaryContainer else cs.surfaceContainer,
                            shape = BubbleShape,
                            modifier =
                                Modifier
                                    .clip(BubbleShape)
                                    .hapticClick(
                                        onLongClick = { if (loggedIn && hasId && !actionBusy) actionsExpanded = true },
                                        onClick = {},
                                    ),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        discussion.username ?: "User",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isMine) cs.onPrimaryContainer else cs.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (discussion.type.equals("A", true)) {
                                        Text("Admin", style = MaterialTheme.typography.labelSmall, color = cs.tertiary)
                                    }
                                    if (!discussion.pro.isNullOrBlank() && discussion.pro != "0") {
                                        Text("PRO", style = MaterialTheme.typography.labelSmall, color = cs.tertiary)
                                    }
                                    discussion.rank?.toIntOrNull()?.takeIf { it in 1..10 }?.let { rank ->
                                        Text("R$rank", style = MaterialTheme.typography.labelSmall, color = cs.secondary)
                                    }
                                }
                                if (discussion.body().isNotBlank()) {
                                    Text(discussion.body(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                            if (isMine) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    enabled = !actionBusy,
                                    onClick = {
                                        actionsExpanded = false
                                        editText = discussion.body()
                                        editDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Hapus") },
                                    enabled = !actionBusy,
                                    onClick = {
                                        actionsExpanded = false
                                        deleteDialog = true
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Laporkan") },
                                    enabled = !actionBusy,
                                    onClick = {
                                        actionsExpanded = false
                                        reportDialog = true
                                    },
                                )
                            }
                        }
                    }
                    Row(Modifier.padding(start = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                            )
                        }
                        if (isEpisode) {
                            ActionLabel("Balas", enabled = canReply, onClick = replyToComment)
                            ActionLabel(
                                text = discussion.likeCount ?: "0",
                                icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                active = liked,
                                enabled = reactEnabled,
                                onClick = { onReact(discussion, if (liked) "-" else "Y") },
                            )
                            ActionLabel(
                                text = discussion.dislikeCount ?: "0",
                                icon = if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                active = disliked,
                                enabled = reactEnabled,
                                onClick = { onReact(discussion, if (disliked) "-" else "N") },
                            )
                        }
                    }
                }
            }
        }
        if (isEpisode && (replyCount > 0 || expanded)) {
            Column(
                Modifier.fillMaxWidth().padding(start = ThreadIndent),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!expanded) {
                    ThreadToggle("Lihat $replyCount balasan", enabled = hasId, onClick = onToggleReplies)
                } else {
                    when {
                        thread == null || thread.loading ->
                            Text(
                                "Memuat balasan...",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        thread.replies.isEmpty() ->
                            Text(
                                "Belum ada balasan",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        else -> {
                            thread.replies.forEach { reply ->
                                ReplyItem(
                                    reply = reply,
                                    canReply = canReply,
                                    onReply = {
                                        onStartReply(
                                            ReplyTarget(
                                                parent = discussion,
                                                username = reply.username ?: "User",
                                                snippet = reply.body(),
                                                mention = true,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    ThreadToggle("Sembunyikan balasan", enabled = true, onClick = onToggleReplies)
                }
            }
        }
    }

    if (editDialog) {
        AppDialog(
            icon = Icons.Outlined.Edit,
            title = if (isEpisode) "Edit komentar" else "Edit diskusi",
            onDismiss = { if (!actionBusy) editDialog = false },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Teks") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                DialogConfirmButton("Simpan") {
                    if (!actionBusy && editText.isNotBlank() && editText.trim() != discussion.body().trim()) {
                        onEdit(discussion, editText.trim()) { editDialog = false }
                    }
                }
            },
            dismissButton = { DialogCancelButton { if (!actionBusy) editDialog = false } },
        )
    }

    if (deleteDialog) {
        AppDialog(
            icon = Icons.Outlined.Delete,
            title = if (isEpisode) "Hapus komentar?" else "Hapus diskusi?",
            onDismiss = { if (!actionBusy) deleteDialog = false },
            text = { Text("Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    if (!actionBusy) onDelete(discussion) { deleteDialog = false }
                }
            },
            dismissButton = { DialogCancelButton { if (!actionBusy) deleteDialog = false } },
        )
    }

    if (reportDialog) {
        AppDialog(
            icon = Icons.Outlined.Flag,
            title = if (isEpisode) "Laporkan komentar?" else "Laporkan diskusi?",
            onDismiss = { if (!actionBusy) reportDialog = false },
            text = { Text("Laporan akan dikirim ke moderator untuk ditinjau.") },
            confirmButton = {
                DialogConfirmButton("Laporkan") {
                    if (!actionBusy) onReport(discussion) { reportDialog = false }
                }
            },
            dismissButton = { DialogCancelButton { if (!actionBusy) reportDialog = false } },
        )
    }
}
