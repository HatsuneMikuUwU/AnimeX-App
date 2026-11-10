package com.uwu.animex.ui.cuplix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppModalBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuplixCommentSheet(
    clipId: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded, skipHiddenState = false)
    val comments = remember { mutableStateListOf<UserApi.Discussion>() }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val myUserId = UserSession.userId()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            val result = runSuspendCatching { UserApi.cuplixCommentList(clipId) }
            comments.clear()
            result.onSuccess { comments.addAll(it) }
                .onFailure { error = it.message ?: "Gagal memuat komentar" }
            loading = false
        }
    }

    LaunchedEffect(clipId) { reload() }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                "Komentar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Box(
                Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
            ) {
                when {
                    loading -> AppLoadingIndicator(Modifier.align(Alignment.Center))
                    error != null && comments.isEmpty() ->
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { reload() }) { Text("Coba lagi") }
                        }
                    comments.isEmpty() ->
                        Text(
                            "Belum ada komentar",
                            Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(comments, key = { it.id ?: it.hashCode().toString() }) { item ->
                                CommentRow(
                                    item = item,
                                    isMine = !item.userId.isNullOrBlank() && item.userId == myUserId,
                                    onEdit = {
                                        editingId = item.id
                                        draft = item.body()
                                    },
                                    onDelete = {
                                        val id = item.id ?: return@CommentRow
                                        scope.launch {
                                            runSuspendCatching { UserApi.cuplixCommentDelete(id) }
                                                .onSuccess { comments.removeAll { c -> c.id == id } }
                                        }
                                    },
                                    onReport = {
                                        val id = item.id ?: return@CommentRow
                                        scope.launch {
                                            runSuspendCatching { UserApi.cuplixCommentReport(id) }
                                                .onSuccess { comments.removeAll { c -> c.id == id } }
                                        }
                                    },
                                )
                            }
                        }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (editingId != null) "Edit komentar…" else "Tulis komentar…")
                    },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    enabled = !sending && UserSession.isLoggedIn(),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = draft.isNotBlank() && !sending && UserSession.isLoggedIn(),
                    onClick = {
                        val text = draft.trim()
                        if (text.isEmpty()) return@IconButton
                        scope.launch {
                            sending = true
                            val editId = editingId
                            val result =
                                if (editId != null) {
                                    runSuspendCatching {
                                        UserApi.cuplixCommentEdit(clipId, editId, text)
                                    }
                                } else {
                                    runSuspendCatching {
                                        UserApi.cuplixCommentPost(clipId, text)
                                    }
                                }
                            sending = false
                            result
                                .onSuccess {
                                    draft = ""
                                    editingId = null
                                    reload()
                                }.onFailure {
                                    error = it.message ?: "Gagal mengirim komentar"
                                }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = "Kirim")
                }
            }
            if (!UserSession.isLoggedIn()) {
                Text(
                    "Login untuk berkomentar",
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CommentRow(
    item: UserApi.Discussion,
    isMine: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier = Modifier.fillMaxWidth()) {
        Column(Modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.username?.takeIf { it.isNotBlank() } ?: "User",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                val whenText = item.timeUpdated?.takeIf { it.isNotBlank() }
                    ?: item.time?.takeIf { it.isNotBlank() }
                    ?: item.createdAt
                if (!whenText.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        whenText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(item.body(), fontSize = 14.sp)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Opsi")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (isMine) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Laporkan") },
                        onClick = {
                            menuOpen = false
                            onReport()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                    )
                }
            }
        }
    }
}
