@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.animein.AnimeIn
import com.uwu.animex.data.animein.AnimeInSession
import com.uwu.animex.data.animein.Discussion
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** "Diskusi" tab of the detail screen: ANIMEIN movie discussions (read for everyone, write after login). */
@Composable
fun DiscussionTab(
    movieId: String,
    listState: LazyListState,
    snackbar: SnackbarHostState,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val user by AnimeInSession.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var items by remember(movieId) { mutableStateOf<List<Discussion>>(emptyList()) }
    var loading by remember(movieId) { mutableStateOf(true) }
    var error by remember(movieId) { mutableStateOf<String?>(null) }
    var page by remember(movieId) { mutableIntStateOf(1) }
    var hasMore by remember(movieId) { mutableStateOf(false) }
    var loadingMore by remember(movieId) { mutableStateOf(false) }
    var tick by remember(movieId) { mutableIntStateOf(0) }

    var composing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Discussion?>(null) }
    var sending by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Discussion?>(null) }

    LaunchedEffect(movieId, tick, user?.id) {
        loading = true
        error = null
        try {
            val first = AnimeIn.discussions(movieId, 1)
            items = first.items
            hasMore = first.hasMore
            page = 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                val next = AnimeIn.discussions(movieId, page + 1)
                val seen = items.mapTo(HashSet()) { it.id }
                val fresh = next.items.filter { it.id !in seen }
                items = items + fresh
                page += 1
                hasMore = next.hasMore && fresh.isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                hasMore = false
            } finally {
                loadingMore = false
            }
        }
    }

    fun submit(text: String) {
        if (sending) return
        sending = true
        val target = editing
        scope.launch {
            try {
                if (target != null) AnimeIn.edit(target.id, text) else AnimeIn.post(movieId, text)
                composing = false
                editing = null
                snackbar.show(scope, if (target != null) "Diskusimu sudah diperbarui" else "Diskusimu sudah terkirim")
                tick++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(scope, e.toUserMessage())
            } finally {
                sending = false
            }
        }
    }

    fun act(
        success: String,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                block()
                snackbar.show(scope, success)
                tick++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(scope, e.toUserMessage())
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                top = LocalTopInset.current + 8.dp,
                bottom = LocalBottomInset.current + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "composer") {
            val cs = MaterialTheme.colorScheme
            if (user != null) {
                Surface(
                    onClick = {
                        editing = null
                        composing = true
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = cs.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Forum, contentDescription = null, tint = cs.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Tulis diskusi…", color = cs.onSurfaceVariant)
                    }
                }
            } else {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.secondaryContainer) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Login ANIMEIN dulu buat ikut ngobrol di sini.",
                            color = cs.onSecondaryContainer,
                        )
                        FilledTonalButton(onClick = onOpenLogin, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Login ANIMEIN")
                        }
                    }
                }
            }
        }

        when {
            loading && items.isEmpty() ->
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
                }
            error != null && items.isEmpty() ->
                item(key = "error") {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(error.orEmpty(), textAlign = TextAlign.Center)
                        FilledTonalButton(onClick = { tick++ }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Coba lagi")
                        }
                    }
                }
            items.isEmpty() ->
                item(key = "empty") {
                    Text(
                        "Belum ada diskusi. Jadi yang pertama yuk!",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else -> {
                items(items, key = { it.id }, contentType = { "discussion" }) { d ->
                    val own = user?.id != null && d.userId == user?.id
                    DiscussionRow(
                        d = d,
                        own = own,
                        canReport = user != null && !own,
                        onEdit = {
                            editing = d
                            composing = true
                        },
                        onDelete = { deleteTarget = d },
                        onReport = { act("Laporanmu sudah dikirim") { AnimeIn.report(d.id) } },
                    )
                }
                if (hasMore) {
                    item(key = "more") {
                        LaunchedEffect(page) { loadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            AppLoadingIndicator(Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }

    if (composing) {
        DiscussionComposeSheet(
            initial = editing?.text.orEmpty(),
            isEdit = editing != null,
            busy = sending,
            onDismiss = {
                composing = false
                editing = null
            },
            onSubmit = ::submit,
        )
    }

    deleteTarget?.let { target ->
        AppDialog(
            icon = Icons.Outlined.DeleteOutline,
            title = "Hapus diskusi ini?",
            onDismiss = { deleteTarget = null },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    deleteTarget = null
                    act("Diskusi sudah dihapus") { AnimeIn.delete(target.id) }
                }
            },
            dismissButton = { DialogCancelButton { deleteTarget = null } },
        )
    }
}

@Composable
private fun DiscussionRow(
    d: Discussion,
    own: Boolean,
    canReport: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                if (!d.avatar.isNullOrBlank()) {
                    AsyncImage(model = d.avatar, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!d.time.isNullOrBlank()) {
                        Text(
                            "  •  ${d.time}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.size(4.dp))
                Text(d.text, style = MaterialTheme.typography.bodyMedium)
                if (own || canReport) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (own) {
                            IconButton(onClick = onEdit, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(onClick = onReport, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Flag, contentDescription = "Laporkan", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionComposeSheet(
    initial: String,
    isEdit: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
    ) {
        WindowBlurEffect()
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(bottom = 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Gak usah deh") }
                Button(
                    onClick = { onSubmit(text.trim()) },
                    enabled = !busy && text.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (busy) {
                        AppLoadingIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (isEdit) "Simpan" else "Kirim")
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Diskusimu") },
                minLines = 4,
                maxLines = 10,
                enabled = !busy,
            )
        }
    }
}
