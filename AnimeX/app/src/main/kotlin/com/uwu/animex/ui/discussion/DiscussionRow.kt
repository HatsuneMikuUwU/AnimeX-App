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

@Composable
private fun SwipeToReply(
    enabled: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val triggerPx = with(LocalDensity.current) { DiscussionSwipeTrigger.toPx() }
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
    readOnly: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val color = if (active) cs.primary else cs.onSurfaceVariant
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled && !readOnly) Modifier.hapticClick(onClick = onClick) else Modifier)
            .alpha(if (enabled || readOnly) 1f else 0.38f)
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
    highlighted: Boolean = false,
    readOnly: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val time = reply.time ?: reply.createdAt
    val bubbleColor by animateColorAsState(
        targetValue = if (highlighted) cs.tertiaryContainer else cs.surfaceContainer,
        animationSpec = tween(durationMillis = 450),
        label = "reply-highlight",
    )
    SwipeToReply(enabled = canReply, onReply = onReply) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            UserAvatar(reply.username, size = DiscussionReplyAvatarSize)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Surface(color = bubbleColor, shape = RoundedCornerShape(18.dp)) {
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
                    if (!readOnly) ActionLabel("Balas", enabled = canReply, onClick = onReply)
                }
            }
        }
    }
}

@Composable
internal fun DiscussionRow(
    discussion: UserApi.Discussion,
    currentUserId: String?,
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
    highlightedId: String? = null,
    readOnly: Boolean = false,
    socialActions: Boolean = isEpisode,
) {
    val cs = MaterialTheme.colorScheme
    val isMine = !currentUserId.isNullOrBlank() && discussion.userId == currentUserId
    val hasId = !discussion.id.isNullOrBlank()
    var actionsExpanded by remember(discussion.id) { mutableStateOf(false) }
    var editDialog by remember(discussion.id) { mutableStateOf(false) }
    var deleteDialog by remember(discussion.id) { mutableStateOf(false) }
    var reportDialog by remember(discussion.id) { mutableStateOf(false) }
    var editText by remember(discussion.id, discussion.body()) { mutableStateOf(discussion.body()) }
    val canOpenActions = !readOnly && loggedIn && hasId && !actionBusy
    val canReply = !readOnly && socialActions && loggedIn && hasId && !actionBusy
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
                    val x = DiscussionAvatarSize.toPx() / 2
                    drawLine(
                        color = lineColor,
                        start = Offset(x, DiscussionAvatarSize.toPx() + 6.dp.toPx()),
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
                    size = DiscussionAvatarSize,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    val isHighlighted = !highlightedId.isNullOrBlank() && discussion.id == highlightedId
                    val bubbleColor by animateColorAsState(
                        targetValue =
                            when {
                                isHighlighted -> cs.tertiaryContainer
                                isMine -> cs.primaryContainer
                                else -> cs.surfaceContainer
                            },
                        animationSpec = tween(durationMillis = 450),
                        label = "discussion-highlight",
                    )
                    Box {
                        Surface(
                            color = bubbleColor,
                            shape = DiscussionBubbleShape,
                            modifier =
                                Modifier
                                    .clip(DiscussionBubbleShape)
                                    .hapticClick(
                                        onLongClick = { if (canOpenActions) actionsExpanded = true },
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
                                        Text(
                                            "R$rank",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.secondary,
                                        )
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
                        if (socialActions) {
                            if (!readOnly) ActionLabel("Balas", enabled = canReply, onClick = replyToComment)
                            ActionLabel(
                                text = discussion.likeCount ?: "0",
                                icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                active = liked,
                                enabled = reactEnabled,
                                readOnly = readOnly,
                                onClick = { onReact(discussion, if (liked) "-" else "Y") },
                            )
                            ActionLabel(
                                text = discussion.dislikeCount ?: "0",
                                icon = if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                active = disliked,
                                enabled = reactEnabled,
                                readOnly = readOnly,
                                onClick = { onReact(discussion, if (disliked) "-" else "N") },
                            )
                        }
                    }
                }
            }
        }
        if (socialActions && (replyCount > 0 || expanded)) {
            Column(
                Modifier.fillMaxWidth().padding(start = DiscussionThreadIndent),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!expanded) {
                    ThreadToggle("Lihat $replyCount balasan", enabled = hasId, onClick = onToggleReplies)
                } else {
                    when {
                        thread.loading ->
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
                                    highlighted = !highlightedId.isNullOrBlank() && reply.id == highlightedId,
                                    readOnly = readOnly,
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
