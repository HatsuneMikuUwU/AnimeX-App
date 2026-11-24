@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipEpisode
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.Translator
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.background.AnimatedBackground
import com.uwu.animex.ui.background.LocalAnimatedBackgroundActive
import com.uwu.animex.ui.background.rememberActiveBackground
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppDropdownMenu
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.BatteryOptimizationDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.DiscussionRowPlaceholder
import com.uwu.animex.ui.common.EpisodeRowPlaceholder
import com.uwu.animex.ui.common.LocalOpenStatusSheet
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.SmallWavyProgress
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.UserAvatar
import com.uwu.animex.ui.common.WaitingCardPlaceholder
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.coverOrPoster
import com.uwu.animex.ui.common.defaultPlaceholder
import com.uwu.animex.ui.common.fmtNum
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.rememberHideStats
import com.uwu.animex.ui.common.rememberStatusLongClick
import com.uwu.animex.ui.common.rememberSyncedWatched
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.detail.shareAnime
import com.uwu.animex.ui.detail.shareEpisode
import com.uwu.animex.ui.discussion.DiscussionRow
import com.uwu.animex.ui.discussion.ReplyTarget
import com.uwu.animex.ui.discussion.ThreadState
import com.uwu.animex.ui.navigation.NotificationRouter
import com.uwu.animex.ui.onboarding.needsNotificationSetup
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import com.uwu.animex.ui.viewmodel.PlayerEpisodeList
import com.uwu.animex.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt



@Composable
internal fun CommentsPreviewCard(
    state: UiState<List<UserApi.Discussion>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val session by UserSession.user.collectAsStateWithLifecycle()
    val items =
        when (state) {
            is UiState.Ready -> state.value
            else -> emptyList()
        }
    val shape = RoundedCornerShape(14.dp)
    val motion = MaterialTheme.motionScheme
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(motion.defaultSpatialSpec())
            .clip(shape)
            .background(cs.surfaceContainer)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Komentar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = "Buka komentar",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        val phase =
            when {
                state is UiState.Loading -> 0
                state is UiState.Error -> 1
                items.isEmpty() -> 2
                else -> 3
            }
        AnimatedContent(
            targetState = phase,
            transitionSpec = { AppMotion.crossfade(motion) },
            label = "comments-preview",
        ) { p ->
            when (p) {
                0 ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).defaultPlaceholder())
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Memuat komentar...",
                            modifier = Modifier.weight(1f).defaultPlaceholder(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                1 ->
                    Text(
                        "Gagal memuat komentar. Ketuk buat coba lagi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                2 ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(session?.username, size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(cs.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "Tambahkan komentar...",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                else -> {
                    val top = items.firstOrNull()
                    if (top != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(top.username, size = 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                buildAnnotatedString {
                                    top.username?.takeIf { it.isNotBlank() }?.let { u ->
                                        withStyle(
                                            SpanStyle(
                                                color = cs.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                        ) {
                                            append("@$u  ")
                                        }
                                    }
                                    append(top.body())
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EpisodeCommentsPanel(
    viewModel: DiscussionViewModel,
    onClose: () -> Unit,
    onNeedLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val actionBusy by viewModel.actionBusy.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val loadMoreError by viewModel.loadMoreError.collectAsStateWithLifecycle()
    val session by UserSession.user.collectAsStateWithLifecycle()
    val loggedIn = session?.isAuthenticated == true
    var draft by rememberSaveable { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    val threads = remember { HashMap<String, ThreadState>() }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val items =
        when (val s = state) {
            is UiState.Ready -> s.value
            else -> emptyList()
        }

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

    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { snackbar.show(snackScope, it) }
    }
    LaunchedEffect(replyTarget) {
        if (replyTarget != null) runCatching { focusRequester.requestFocus() }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore, loadingMore, loadMoreError, items.size) {
        if (nearEnd && hasMore && !loadingMore && !loadMoreError && items.isNotEmpty()) {
            viewModel.loadMore()
        }
    }

    Column(modifier.background(cs.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Komentar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = hapticClick(block = onClose)) {
                Icon(Icons.Outlined.Close, contentDescription = "Tutup komentar")
            }
        }
        HorizontalDivider(color = cs.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            UiStateContent(
                state = state,
                onRetry = { viewModel.refresh() },
                loading = { DiscussionListPlaceholder() },
            ) { _ ->
                if (items.isEmpty()) {
                    AnimatedEmptyState(
                        icon = Icons.Outlined.Forum,
                        title = "Belum ada komentar",
                        message = "Jadilah yang pertama!",
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(items, key = { index, d -> d.id ?: "item-$index" }) { _, d ->
                            val thread = d.id?.takeIf(String::isNotBlank)?.let(::threadOf)
                            DiscussionRow(
                                discussion = d,
                                currentUserId = session?.id,
                                loggedIn = loggedIn,
                                isEpisode = true,
                                actionBusy = actionBusy || sending,
                                thread = thread,
                                onToggleReplies = { toggleThread(d) },
                                onStartReply = ::startReply,
                                onEdit = { item, text, onComplete ->
                                    viewModel.edit(item, text) {
                                        onComplete()
                                        snackbar.show(snackScope, "Komentar diperbarui")
                                    }
                                },
                                onDelete = { item, onComplete ->
                                    viewModel.delete(item) {
                                        onComplete()
                                        snackbar.show(snackScope, "Komentar dihapus")
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
                                count = 2,
                                key = { "load-more-placeholder-$it" },
                            ) { index ->
                                DiscussionRowPlaceholder(fraction = listOf(0.7f, 0.5f)[index % 2])
                            }
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
        CommentInputBar(
            draft = draft,
            onDraftChange = { draft = it },
            loggedIn = loggedIn,
            sending = sending,
            username = session?.username,
            placeholder = if (replyTarget != null) "Tulis balasan..." else "Tulis komentar...",
            replyTo = replyTarget,
            onCancelReply = ::cancelReply,
            focusRequester = focusRequester,
            onNeedLogin = onNeedLogin,
            onSend = { text ->
                val target = replyTarget
                if (target == null) {
                    viewModel.send(text) {
                        draft = ""
                        runCatching { listState.requestScrollToItem(0) }
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
        )
    }
}

@Composable
internal fun CommentInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    loggedIn: Boolean,
    sending: Boolean,
    username: String?,
    placeholder: String,
    replyTo: ReplyTarget?,
    onCancelReply: () -> Unit,
    focusRequester: FocusRequester,
    onNeedLogin: () -> Unit,
    onSend: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val canSend = loggedIn && !sending && draft.isNotBlank()
    if (!loggedIn) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainer)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Mau ikut komentar?",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = hapticClick(block = onNeedLogin), shapes = ButtonDefaults.shapes()) {
                Text("Login")
            }
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        if (replyTo != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Membalas ${replyTo.username}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        replyTo.snippet.replace('\n', ' '),
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserAvatar(
                username,
                size = 36.dp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cs.surfaceContainerHighest)
                    .then(if (loggedIn) Modifier else Modifier.hapticClick(onClick = onNeedLogin))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        "Login dulu buat komentar",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick =
                    hapticClick {
                        if (!loggedIn) {
                            onNeedLogin()
                        } else if (canSend) {
                            onSend(draft.trim())
                        }
                    },
                modifier = Modifier.size(44.dp),
            ) {
                when {
                    sending -> AppLoadingIndicator(Modifier.size(24.dp))
                    !loggedIn -> Icon(
                        Icons.AutoMirrored.Outlined.Login,
                        contentDescription = "Login",
                        tint = cs.onSurface,
                    )
                    else ->
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Kirim",
                            tint = if (canSend) cs.primary else cs.onSurface.copy(alpha = 0.38f),
                        )
                }
            }
        }
    }
}
