package com.uwu.animex.ui.cuplix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.discussion.DiscussionRow
import com.uwu.animex.ui.player.CommentInputBar
import com.uwu.animex.ui.viewmodel.DiscussionScope
import com.uwu.animex.ui.viewmodel.DiscussionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuplixCommentSheet(
    clipId: String,
    onDismiss: () -> Unit,
    onNeedLogin: () -> Unit,
    viewModel: DiscussionViewModel =
        viewModel(
            factory = DiscussionViewModel.factory(clipId, DiscussionScope.Cuplix),
            key = "${DiscussionScope.Cuplix.name}:$clipId",
        ),
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val actionBusy by viewModel.actionBusy.collectAsStateWithLifecycle()
    val session by UserSession.user.collectAsStateWithLifecycle()
    val loggedIn = session?.isAuthenticated == true
    var draft by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val items =
        when (val s = state) {
            is UiState.Ready -> s.value
            else -> emptyList()
        }

    LaunchedEffect(viewModel) {
        if (viewModel.state.value is UiState.Ready) viewModel.refresh(force = false)
    }

    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { snackbar.show(snackScope, it) }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 560.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Komentar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = hapticClick(block = onDismiss)) {
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
                                DiscussionRow(
                                    discussion = d,
                                    currentUserId = session?.id,
                                    loggedIn = loggedIn,
                                    isEpisode = true,
                                    socialActions = false,
                                    actionBusy = actionBusy || sending,
                                    thread = null,
                                    onToggleReplies = {},
                                    onStartReply = {},
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
                                    onReact = { _, _ -> },
                                    modifier = animateItemExpressive(),
                                )
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
                placeholder = "Tulis komentar...",
                replyTo = null,
                onCancelReply = {},
                focusRequester = focusRequester,
                onNeedLogin = onNeedLogin,
                onSend = { text ->
                    viewModel.send(text) {
                        draft = ""
                        runCatching { listState.requestScrollToItem(0) }
                    }
                },
            )
        }
    }
}
