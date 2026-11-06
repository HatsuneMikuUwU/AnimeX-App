package com.uwu.animex.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.UserAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
) {
    val context = LocalContext.current
    val session by UserSession.user.collectAsStateWithLifecycle()
    var messages by remember { mutableStateOf<List<UserApi.ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingOlder by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var replyTo by remember { mutableStateOf<UserApi.ChatMessage?>(null) }
    var editTarget by remember { mutableStateOf<UserApi.ChatMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<UserApi.ChatMessage?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val loggedIn = session?.isAuthenticated == true

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            imageUri = uri
        }

    suspend fun fetchLatest() {
        val old = messages
        val incoming = UserApi.chatList(highestId = old.firstOrNull()?.id)
        messages = mergeLatestMessages(old, incoming)
    }

    fun reload() {
        loading = messages.isEmpty()
        error = null
        scope.launch {
            try {
                fetchLatest()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                loading = false
            }
        }
    }

    fun loadOlder() {
        val cursor = messages.lastOrNull()?.id ?: return
        if (loadingOlder || !hasMore) return
        loadingOlder = true
        scope.launch {
            try {
                val older = UserApi.chatList(lowestId = cursor)
                val knownIds = messages.mapNotNull { it.id }.toSet()
                val additions = older.filter { it.id == null || it.id !in knownIds }
                if (additions.isEmpty()) {
                    hasMore = false
                } else {
                    messages = messages + additions
                }
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                loadingOlder = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        error = null
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus pesan?") },
            text = { Text("Pesan ini akan dihapus dari chat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = deleteTarget
                        deleteTarget = null
                        val id = target?.id ?: return@TextButton
                        scope.launch {
                            try {
                                UserApi.chatDelete(id)
                                messages = messages.filterNot { it.id == id }
                            } catch (e: Exception) {
                                error = e.toUserMessage()
                            }
                        }
                    },
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = hapticClick(block = onBack)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = hapticClick { reload() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                if (!loggedIn) {
                    Text(
                        "Mode baca saja — login untuk ikut chat",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (replyTo != null || editTarget != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (editTarget != null) "Mengedit pesan" else "Membalas ${replyTo?.username ?: "User"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                editTarget?.body()?.ifBlank { "Pesan dengan gambar" }
                                    ?: replyTo?.body()?.ifBlank { "Pesan dengan gambar" }.orEmpty(),
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                replyTo = null
                                editTarget = null
                                draft = ""
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Batal reply/edit")
                        }
                    }
                }
                imageUri?.let { uri ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Gambar yang akan dikirim",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text("Gambar siap dikirim", Modifier.weight(1f).padding(start = 10.dp))
                        IconButton(onClick = { imageUri = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Hapus gambar")
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = loggedIn && !sending && editTarget == null,
                        onClick = {
                            if (!loggedIn) onNeedLogin()
                            else imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Pilih gambar")
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(if (loggedIn) "Tulis pesan..." else "Login dulu untuk kirim pesan")
                        },
                        enabled = loggedIn && !sending,
                        singleLine = false,
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (!loggedIn) {
                                onNeedLogin()
                                return@FilledIconButton
                            }
                            val text = draft.trim()
                            val edit = editTarget
                            if (sending || (text.isEmpty() && imageUri == null) || (edit != null && text.isEmpty())) return@FilledIconButton
                            val selectedImage = imageUri
                            val selectedReply = replyTo
                            sending = true
                            scope.launch {
                                try {
                                    if (edit != null) {
                                        UserApi.chatEdit(edit.id.orEmpty(), text)
                                        draft = ""
                                        editTarget = null
                                        fetchLatest()
                                    } else {
                                        val upload = selectedImage?.let { readChatImage(context, it) }
                                        val sent =
                                            UserApi.chatSend(
                                                text,
                                                replyToId = selectedReply?.id,
                                                image = upload,
                                            )
                                        draft = ""
                                        imageUri = null
                                        replyTo = null

                                        val latest =
                                            try {
                                                UserApi.chatList()
                                            } catch (refreshError: Exception) {
                                                error = "Pesan terkirim, tetapi daftar chat gagal diperbarui."
                                                emptyList()
                                            }
                                        messages = mergeLatestMessages(messages, latest)

                                        val sentUserId = sent.userId ?: session?.id
                                        val alreadyVisible =
                                            if (!sent.id.isNullOrBlank()) {
                                                latest.any { it.id == sent.id }
                                            } else {
                                                latest.take(5).any { item ->
                                                    item.userId == sentUserId &&
                                                        (text.isNotBlank() && item.body().trim() == text ||
                                                            text.isBlank() && selectedImage != null && !item.imageUrl.isNullOrBlank())
                                                }
                                            }
                                        if (!alreadyVisible) {
                                            val localMessage =
                                                sent.copy(
                                                    id = sent.id?.takeIf { it.isNotBlank() } ?: "local-${System.nanoTime()}",
                                                    userId = sentUserId,
                                                    username = sent.username ?: session?.username,
                                                    text = sent.body().takeIf { it.isNotBlank() } ?: text,
                                                    time = sent.time ?: "Baru saja",
                                                    replyUsername = sent.replyUsername ?: selectedReply?.username,
                                                    replyText = sent.replyText ?: selectedReply?.body(),
                                                    replyImageUrl = sent.replyImageUrl ?: selectedReply?.imageUrl,
                                                    replyChatId = sent.replyChatId ?: selectedReply?.id,
                                                    localEcho = true,
                                                    localImageUri = selectedImage?.toString(),
                                                )
                                            messages = listOf(localMessage) + messages
                                        }
                                    }
                                    if (messages.isNotEmpty()) listState.animateScrollToItem(0)
                                } catch (e: Exception) {
                                    error = e.toUserMessage()
                                } finally {
                                    sending = false
                                }
                            }
                        },
                        enabled = !sending && (if (loggedIn) draft.isNotBlank() || imageUri != null else true),
                    ) {
                        if (sending) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (!loggedIn) {
                            Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = "Login")
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Kirim")
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                loading && messages.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && messages.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text("Tarik refresh atau coba lagi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                messages.isEmpty() -> {
                    Text(
                        "Belum ada pesan. Mulai chat!",
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(messages, key = { it.id ?: it.hashCode().toString() }) { msg ->
                            val myId = session?.id
                            val isMine = myId != null && msg.userId == myId
                            ChatBubble(
                                msg = msg,
                                isMine = isMine,
                                ownImage = session?.image,
                                onReply = { replyTo = it; editTarget = null },
                                onEdit = {
                                    editTarget = it
                                    replyTo = null
                                    draft = it.body()
                                    imageUri = null
                                },
                                onDelete = { deleteTarget = it },
                                onReport = { target ->
                                    scope.launch {
                                        try {
                                            UserApi.chatReport(target.id.orEmpty())
                                            messages = messages.filterNot { it.id == target.id }
                                        } catch (e: Exception) {
                                            error = e.toUserMessage()
                                        }
                                    }
                                },
                                onOpenReply = { replyId ->
                                    val index = messages.indexOfFirst { it.id == replyId }
                                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                                },
                            )
                        }
                        if (hasMore) {
                            item(key = "load-older") {
                                TextButton(
                                    onClick = { loadOlder() },
                                    enabled = !loadingOlder,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (loadingOlder) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (loadingOlder) "Memuat..." else "Muat pesan lama")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mergeLatestMessages(
    existing: List<UserApi.ChatMessage>,
    incoming: List<UserApi.ChatMessage>,
): List<UserApi.ChatMessage> {
    val byId = incoming.mapNotNull { item -> item.id?.let { it to item } }.toMap()
    val existingIds = existing.mapNotNull { it.id }.toSet()
    val newMessages = incoming.filter { it.id == null || it.id !in existingIds }
    val pendingLocal =
        existing.filter { it.localEcho }.filterNot { local ->
            incoming.take(5).any { server ->
                if (!local.id.isNullOrBlank() && !local.id.startsWith("local-")) {
                    server.id == local.id
                } else {
                    server.userId == local.userId &&
                        (local.body().isNotBlank() && server.body().trim() == local.body().trim() ||
                            local.body().isBlank() && local.localImageUri != null && !server.imageUrl.isNullOrBlank())
                }
            }
        }
    val confirmedExisting = existing.filterNot { it.localEcho }
    val updatedExisting = confirmedExisting.map { old -> old.id?.let(byId::get) ?: old }
    return newMessages + pendingLocal + updatedExisting
}

private suspend fun readChatImage(context: Context, uri: Uri): UserApi.ChatImageUpload =
    withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Gambar tidak dapat dibaca")
        if (bytes.size > 20 * 1024 * 1024) throw IOException("Ukuran gambar maksimal 20 MB")
        val fileName =
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.takeIf { it.isNotBlank() } ?: "chat-image"
        UserApi.ChatImageUpload(
            bytes = bytes,
            fileName = fileName,
            mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    msg: UserApi.ChatMessage,
    isMine: Boolean,
    ownImage: String?,
    onReply: (UserApi.ChatMessage) -> Unit,
    onEdit: (UserApi.ChatMessage) -> Unit,
    onDelete: (UserApi.ChatMessage) -> Unit,
    onReport: (UserApi.ChatMessage) -> Unit,
    onOpenReply: (String) -> Unit,
) {
    var menuExpanded by remember(msg.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine) {
            UserAvatar(msg.username, pokemon = msg.pokemon, size = 36.dp)
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f, fill = false)) {
            Surface(
                color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true }),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            msg.username ?: "User",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (msg.type.equals("A", true)) {
                            Text("Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (!msg.pro.isNullOrBlank() && msg.pro != "0") {
                            Text("PRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        msg.rank?.toIntOrNull()?.takeIf { it in 1..10 }?.let { rank ->
                            Text("R$rank", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (!msg.replyChatId.isNullOrBlank() || !msg.replyUsername.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { msg.replyChatId?.let(onOpenReply) },
                                onLongClick = { msg.replyChatId?.let(onOpenReply) },
                            ),
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    msg.replyUsername ?: "Balasan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (!msg.replyText.isNullOrBlank()) {
                                    Text(msg.replyText, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                                }
                                msg.replyImageUrl?.takeIf { it.isNotBlank() }?.let { image ->
                                    AsyncImage(
                                        model = Api.absUrl(image),
                                        contentDescription = "Gambar pesan yang dibalas",
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                    if (msg.body().isNotBlank()) {
                        Text(msg.body(), style = MaterialTheme.typography.bodyMedium)
                    }
                    (msg.localImageUri ?: msg.imageUrl)?.takeIf { it.isNotBlank() }?.let { image ->
                        AsyncImage(
                            model = if (msg.localImageUri != null) image else Api.absUrl(image),
                            contentDescription = "Gambar chat",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    msg.movie?.let { SharedMovieCard(it) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val time = msg.timeHour?.takeIf { it.isNotBlank() } ?: msg.time.orEmpty()
                        if (time.isNotBlank()) {
                            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!msg.timeUpdated.isNullOrBlank()) {
                            Text("· diedit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Balas") },
                    leadingIcon = { Icon(Icons.Outlined.Reply, contentDescription = null) },
                    onClick = { menuExpanded = false; onReply(msg) },
                )
                if (isMine) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        enabled = !msg.id.isNullOrBlank() && !msg.localEcho,
                        onClick = { menuExpanded = false; onEdit(msg) },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        enabled = !msg.id.isNullOrBlank() && !msg.localEcho,
                        onClick = { menuExpanded = false; onDelete(msg) },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Laporkan") },
                        leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                        enabled = !msg.id.isNullOrBlank() && !msg.localEcho,
                        onClick = { menuExpanded = false; onReport(msg) },
                    )
                }
            }
        }
        if (isMine) {
            Spacer(Modifier.width(8.dp))
            UserAvatar(msg.username, imageUrl = ownImage, pokemon = msg.pokemon, size = 36.dp)
        }
    }
}

@Composable
private fun SharedMovieCard(movie: Movie) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Api.absUrl(movie.image_cover),
                contentDescription = movie.title,
                modifier = Modifier.size(width = 56.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(movie.title ?: "Anime", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                movie.synopsis?.takeIf { it.isNotBlank() }?.let {
                    Text(it, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
