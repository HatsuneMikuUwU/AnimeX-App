package com.uwu.animex.ui.discussion

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.DiscussionListPlaceholder
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.FloatingGlassButton
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.UserAvatar
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.barGlass
import com.uwu.animex.ui.theme.bottomScrim
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.theme.resolveGlassStyle
import com.uwu.animex.ui.viewmodel.DiscussionScope
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import top.yukonga.miuix.kmp.blur.LayerBackdrop

private val InputBarHeight = 56.dp

private val InputBarFade = 24.dp

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
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val actionBusy by viewModel.actionBusy.collectAsStateWithLifecycle()
    val session by UserSession.user.collectAsStateWithLifecycle()
    val loggedIn = session?.isAuthenticated == true
    var draft by rememberSaveable { mutableStateOf("") }
    var scrollOnUpdate by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barHeight = with(LocalDensity.current) { barHeightPx.toDp() }
    val listState = rememberLazyListState()
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

    LaunchedEffect(viewModel) {
        viewModel.sendError.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(chronologicalItems) {
        if (chronologicalItems.isNotEmpty() && (!initialScrollDone || scrollOnUpdate)) {
            listState.animateScrollToItem(chronologicalItems.lastIndex)
            initialScrollDone = true
            scrollOnUpdate = false
        }
    }

    Scaffold(
        containerColor = screenContainerColor(),
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
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
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(chronologicalItems, key = { index, d -> d.id ?: "item-$index" }) { _, d ->
                                    DiscussionRow(
                                        discussion = d,
                                        currentUserId = session?.id,
                                        ownImage = session?.image,
                                        loggedIn = loggedIn,
                                        isEpisode = isEpisode,
                                        actionBusy = actionBusy || sending,
                                        onEdit = { item, text, onComplete ->
                                            viewModel.edit(item, text) {
                                                onComplete()
                                                Toast.makeText(
                                                    context,
                                                    if (isEpisode) "Komentar diperbarui" else "Diskusi diperbarui",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                        onDelete = { item, onComplete ->
                                            viewModel.delete(item) {
                                                onComplete()
                                                Toast.makeText(
                                                    context,
                                                    if (isEpisode) "Komentar dihapus" else "Diskusi dihapus",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                        onReport = { item, onComplete ->
                                            viewModel.report(item) {
                                                onComplete()
                                                Toast.makeText(context, "Laporan dikirim", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = animateItemExpressive(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            DiscussionInputBar(
                draft = draft,
                onDraftChange = { draft = it },
                loggedIn = loggedIn,
                sending = sending,
                backdrop = backdrop,
                placeholder = inputHint,
                onSend = { text ->
                    viewModel.send(text) {
                        draft = ""
                        scrollOnUpdate = true
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
    placeholder: String = "Tulis diskusi...",
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    val glassStyle = resolveGlassStyle(backdrop, appearance.barEffect)
    val brush = remember(cs.background) { bottomScrim(cs.background) }
    val shape = RoundedCornerShape(InputBarHeight / 2)
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
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun DiscussionRow(
    discussion: UserApi.Discussion,
    currentUserId: String?,
    ownImage: String?,
    loggedIn: Boolean,
    isEpisode: Boolean,
    actionBusy: Boolean,
    onEdit: (UserApi.Discussion, String, () -> Unit) -> Unit,
    onDelete: (UserApi.Discussion, () -> Unit) -> Unit,
    onReport: (UserApi.Discussion, () -> Unit) -> Unit,
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
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine) {
            UserAvatar(discussion.username, pokemon = discussion.pokemon, size = 36.dp)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isMine) cs.primaryContainer else cs.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .hapticClick(
                        onLongClick = { if (loggedIn && hasId && !actionBusy) actionsExpanded = true },
                        onClick = {},
                    ),
        ) {
            Box {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            discussion.username ?: "User",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMine) cs.onPrimaryContainer else cs.primary,
                            modifier = Modifier.weight(1f),
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val time = discussion.time ?: discussion.createdAt
                        if (!time.isNullOrBlank()) {
                            Text(time, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        if (!discussion.timeUpdated.isNullOrBlank()) {
                            Text("· diedit", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
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
        }
        if (isMine) {
            Spacer(Modifier.width(8.dp))
            UserAvatar(discussion.username, imageUrl = ownImage, pokemon = discussion.pokemon, size = 36.dp)
        }
    }

    if (editDialog) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) editDialog = false },
            title = { Text(if (isEpisode) "Edit komentar" else "Edit diskusi") },
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
                TextButton(
                    enabled = !actionBusy && editText.isNotBlank() && editText.trim() != discussion.body().trim(),
                    onClick = {
                        onEdit(discussion, editText.trim()) { editDialog = false }
                    },
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { editDialog = false }) { Text("Batal") }
            },
        )
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) deleteDialog = false },
            title = { Text(if (isEpisode) "Hapus komentar?" else "Hapus diskusi?") },
            text = { Text("Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                TextButton(
                    enabled = !actionBusy,
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.error),
                    onClick = { onDelete(discussion) { deleteDialog = false } },
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { deleteDialog = false }) { Text("Batal") }
            },
        )
    }

    if (reportDialog) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) reportDialog = false },
            title = { Text(if (isEpisode) "Laporkan komentar?" else "Laporkan diskusi?") },
            text = { Text("Laporan akan dikirim ke moderator untuk ditinjau.") },
            confirmButton = {
                TextButton(
                    enabled = !actionBusy,
                    onClick = { onReport(discussion) { reportDialog = false } },
                ) { Text("Laporkan") }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { reportDialog = false }) { Text("Batal") }
            },
        )
    }
}
