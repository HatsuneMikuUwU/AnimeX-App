@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.DiscussionRowPlaceholder
import com.uwu.animex.ui.common.EpisodeRowPlaceholder
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.UserAvatar
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.rememberSyncedWatched
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.discussion.DiscussionRow
import com.uwu.animex.ui.discussion.ReplyTarget
import com.uwu.animex.ui.detail.EpisodeRow
import com.uwu.animex.ui.discussion.ThreadState
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import com.uwu.animex.ui.viewmodel.PlayerViewModel

internal data class WatchChip(
    val icon: ImageVector,
    val label: String,
    val highlighted: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun PlayerWatchPage(
    animeTitle: String,
    episodeLabel: String,
    movieId: String?,
    currentEpId: String,
    currentIndex: String?,
    playerViewModel: PlayerViewModel,
    commentsViewModel: DiscussionViewModel,
    commentsOpen: Boolean,
    onOpenComments: () -> Unit,
    onCloseComments: () -> Unit,
    chips: List<WatchChip>,
    onPickEpisode: (Episode) -> Unit,
    onNeedLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        WatchInfoList(
            animeTitle = animeTitle,
            episodeLabel = episodeLabel,
            movieId = movieId,
            currentEpId = currentEpId,
            currentIndex = currentIndex,
            playerViewModel = playerViewModel,
            commentsViewModel = commentsViewModel,
            chips = chips,
            onOpenComments = onOpenComments,
            onPickEpisode = onPickEpisode,
        )
        // Panel komentar ditaruh di area bawah player (bukan ModalBottomSheet), jadi video tetap
        // kelihatan & gak ketutup scrim, dan tinggi panel cuma sampai bawah kotak player.
        AnimatedVisibility(
            visible = commentsOpen,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
            enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        ) {
            var dragY by remember { mutableFloatStateOf(0f) }
            val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }
            val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 8.dp,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, dragY.roundToInt().coerceAtLeast(0)) }
                        // Window player pakai SOFT_INPUT_ADJUST_RESIZE, jadi area ini sudah mengecil sendiri
                        // pas keyboard muncul. imePadding() di sini bakal kehitung dobel (panel jadi ketarik
                        // ke atas player), jadi cukup padding nav bar waktu keyboard tertutup.
                        .padding(bottom = if (imeOpen) 0.dp else navBottom),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        val close = dragY > dismissPx
                                        dragY = 0f
                                        if (close) onCloseComments()
                                    },
                                    onDragCancel = { dragY = 0f },
                                ) { _, delta -> dragY += delta }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                    }
                    EpisodeCommentsPanel(
                        viewModel = commentsViewModel,
                        onClose = onCloseComments,
                        onNeedLogin = onNeedLogin,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchInfoList(
    animeTitle: String,
    episodeLabel: String,
    movieId: String?,
    currentEpId: String,
    currentIndex: String?,
    playerViewModel: PlayerViewModel,
    commentsViewModel: DiscussionViewModel,
    chips: List<WatchChip>,
    onOpenComments: () -> Unit,
    onPickEpisode: (Episode) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val anchorIndex = remember(movieId) { currentIndex }
    LaunchedEffect(movieId) {
        if (movieId != null) {
            playerViewModel.openEpisodeList(movieId, anchorIndex)
            playerViewModel.loadAniZip(movieId)
        }
    }
    val episodeList by playerViewModel.episodeList.collectAsStateWithLifecycle()
    val aniZip by playerViewModel.aniZip.collectAsStateWithLifecycle()
    val commentsState by commentsViewModel.state.collectAsStateWithLifecycle()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()

    val malWatched = rememberSyncedWatched(movieId).watched

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    val canLoadMore = episodeList.hasMore && !episodeList.loadingMore && !episodeList.moreError
    LaunchedEffect(nearEnd, canLoadMore, episodeList.episodes.size) {
        if (nearEnd && canLoadMore) playerViewModel.loadMoreEpisodes()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBottom + 16.dp),
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    animeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val epNumber = currentIndex?.trim()?.toIntOrNull()
                val zipTitle = epNumber?.let { aniZip?.episodes?.get(it)?.title }?.takeIf { it.isNotBlank() }
                val meta =
                    if (zipTitle != null) {
                        "$epNumber. $zipTitle"
                    } else {
                        episodeLabel
                    }
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (chips.isNotEmpty()) {
            item(key = "chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chips) { chip -> WatchChipButton(chip) }
                }
            }
        }

        item(key = "comments-preview") {
            CommentsPreviewCard(
                state = commentsState,
                onClick = onOpenComments,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (movieId != null) {
            when {
                episodeList.loading ->
                    item(key = "episodes-loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            AppLoadingIndicator()
                        }
                    }
                episodeList.error != null ->
                    item(key = "episodes-error") {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Yah, daftar episode gagal dimuat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                            )
                            TextButton(
                                onClick =
                                    hapticClick {
                                        playerViewModel.openEpisodeList(movieId, anchorIndex, force = true)
                                    },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Coba lagi") }
                        }
                    }
                else -> {
                    items(
                        episodeList.episodes,
                        key = { it.id ?: "${it.index}-${it.title}" },
                        contentType = { "episode" },
                    ) { ep ->
                        EpisodeRow(
                            ep = ep,
                            fallbackImage = null,
                            download = null,
                            malWatched = malWatched,
                            meta = ep.index?.trim()?.toIntOrNull()?.let { aniZip?.episodes?.get(it) },
                            current = ep.id == currentEpId,
                            onClick = { onPickEpisode(ep) },
                        )
                    }
                    if (episodeList.loadingMore) {
                        items(
                            count = 3,
                            key = { "episode-placeholder-$it" },
                            contentType = { "episodePlaceholder" },
                        ) { EpisodeRowPlaceholder() }
                    } else if (episodeList.moreError) {
                        item(key = "episodes-more-error") {
                            TextButton(
                                onClick = hapticClick { playerViewModel.loadMoreEpisodes() },
                                modifier = Modifier.fillMaxWidth(),
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Gagal muat lagi. Coba lagi") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchChipButton(chip: WatchChip) {
    val cs = MaterialTheme.colorScheme
    val container = if (chip.highlighted) cs.primaryContainer else cs.surfaceContainerHigh
    val content = if (chip.highlighted) cs.onPrimaryContainer else cs.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(onClick = hapticClick(block = chip.onClick))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(chip.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(chip.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

@Composable
private fun CommentsPreviewCard(
    state: UiState<List<UserApi.Discussion>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val items =
        when (state) {
            is UiState.Ready -> state.value
            else -> emptyList()
        }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceContainer)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Komentar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.UnfoldMore,
                contentDescription = "Buka komentar",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        when {
            state is UiState.Loading ->
                Text("Memuat komentar…", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            state is UiState.Error ->
                Text(
                    "Gagal memuat komentar. Ketuk buat coba lagi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            items.isEmpty() ->
                Text(
                    "Belum ada komentar. Jadilah yang pertama!",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            else -> {
                val top = items.first()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(top.username, pokemon = top.pokemon, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        top.body(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCommentsPanel(
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
                                ownImage = session?.image,
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
            avatarUrl = session?.image,
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
private fun CommentInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    loggedIn: Boolean,
    sending: Boolean,
    username: String?,
    avatarUrl: String?,
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
            .imePadding()
            .background(cs.surfaceContainer)
            .navigationBarsPadding(),
    ) {
        if (replyTo != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(28.dp).clip(CircleShape).background(cs.primary))
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
                imageUrl = avatarUrl,
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
                    !loggedIn -> Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = "Login", tint = cs.onSurface)
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
