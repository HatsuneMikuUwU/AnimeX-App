@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.discussion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import kotlin.math.roundToInt

internal val DiscussionInputBarHeight = 56.dp
internal val DiscussionInputBarFade = 24.dp
internal val DiscussionAvatarSize = 36.dp
internal val DiscussionReplyAvatarSize = 28.dp
internal val DiscussionThreadIndent = DiscussionAvatarSize + 8.dp
internal val DiscussionSwipeTrigger = 64.dp
internal val DiscussionBubbleShape = RoundedCornerShape(20.dp)


@Stable
internal class ThreadState {
    var expanded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var replies by mutableStateOf<List<UserApi.Discussion>>(emptyList())
}

internal data class ReplyTarget(
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
    targetCommentId: String? = null,
    targetParentId: String? = null,
    viewModel: DiscussionViewModel =
        viewModel(
            factory = DiscussionViewModel.factory(movieId, scope, movieTitle),
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
    var deepLinkHandled by remember(targetCommentId, targetParentId) { mutableStateOf(false) }
    var deepLinkLoadAttempts by remember(targetCommentId, targetParentId) { mutableIntStateOf(0) }
    var highlightedCommentId by remember(targetCommentId) { mutableStateOf<String?>(null) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    val threads = remember { HashMap<String, ThreadState>() }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val barHeight = with(density) { barHeightPx.toDp() }
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
        onLoaded: (() -> Unit)? = null,
    ) {
        thread.expanded = true
        thread.loading = true
        viewModel.loadReplies(parent) { loaded ->
            thread.replies = loaded
            thread.loading = false
            onLoaded?.invoke()
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

    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { snackbar.show(snackScope, it) }
    }

    LaunchedEffect(replyTarget) {
        if (replyTarget != null) runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            viewModel.markViewed(items)
        }
    }

    LaunchedEffect(highlightedCommentId) {
        if (highlightedCommentId != null) {
            delay(3_000L)
            highlightedCommentId = null
        }
    }

    val deepLinkAnchorId = targetParentId?.takeIf(String::isNotBlank) ?: targetCommentId?.takeIf(String::isNotBlank)
    val deepLinkPending = deepLinkAnchorId != null && !deepLinkHandled

    LaunchedEffect(chronologicalItems, deepLinkAnchorId, deepLinkHandled) {
        if (chronologicalItems.isEmpty()) return@LaunchedEffect
        if (deepLinkAnchorId != null && !deepLinkHandled) {
            val matchIndex = chronologicalItems.indexOfFirst { it.id == deepLinkAnchorId }
            if (matchIndex >= 0) {
                val targetItem = chronologicalItems[matchIndex]
                val headerOffset = if (loadMoreError) 1 else if (loadingMore) 3 else 0
                val scrollIndex = (matchIndex + headerOffset).coerceAtLeast(0)
                listState.scrollToItem(scrollIndex)
                if (!targetParentId.isNullOrBlank() && isEpisode) {
                    val thread = threadOf(deepLinkAnchorId)
                    loadThread(targetItem, thread) {
                        highlightedCommentId = targetCommentId?.takeIf(String::isNotBlank) ?: deepLinkAnchorId
                        snackScope.launch {
                            val updatedIndex = chronologicalItems.indexOfFirst { it.id == deepLinkAnchorId }
                            if (updatedIndex >= 0) {
                                listState.animateScrollToItem(updatedIndex)
                            }
                        }
                    }
                } else {
                    highlightedCommentId = targetCommentId?.takeIf(String::isNotBlank) ?: deepLinkAnchorId
                }
                deepLinkHandled = true
                initialScrollDone = true
                scrollOnUpdate = false
            } else if (hasMore && !loadingMore && !loadMoreError && deepLinkLoadAttempts < 5) {
                deepLinkLoadAttempts += 1
                viewModel.loadMore()
            } else if (!loadingMore) {
                deepLinkHandled = true
                if (!initialScrollDone) {
                    listState.scrollToItem(chronologicalItems.lastIndex)
                    initialScrollDone = true
                }
            }
            return@LaunchedEffect
        }
        if (!initialScrollDone || scrollOnUpdate) {
            listState.animateScrollToItem(chronologicalItems.lastIndex)
            initialScrollDone = true
            scrollOnUpdate = false
        }
    }

    LaunchedEffect(shouldLoadOlder, hasMore, loadingMore, loadMoreError, initialScrollDone, deepLinkPending) {
        if (initialScrollDone && !deepLinkPending && shouldLoadOlder && hasMore && !loadingMore && !loadMoreError) {
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
                                        highlightedId = highlightedCommentId,
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
                        .onSizeChanged { barHeightPx = it.height },
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

