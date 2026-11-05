@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.animein

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.data.animein.AnimeinAuth
import com.uwu.animex.data.animein.AnimeinDiscussionMessage
import com.uwu.animex.data.animein.AnimeinSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch

/**
 * Diskusi per anime — setara fitur movie discussion di Animein.
 * Endpoint: data/movie/discussion/list + data/movie/discussion/do
 */
@Composable
fun AnimeinDiscussionScreen(
    movieId: String,
    title: String = "",
    onBack: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val loggedIn by AnimeinSession.loggedIn.collectAsStateWithLifecycle()
    val myId = AnimeinSession.idUser

    var messages by remember { mutableStateOf<List<AnimeinDiscussionMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun reload() {
        error = null
        val list =
            runCatching { AnimeinAuth.discussionList(movieId) }
                .onFailure { error = it.message ?: "Gagal memuat diskusi" }
                .getOrDefault(emptyList())
        messages = list
        loading = false
        refreshing = false
    }

    LaunchedEffect(movieId) {
        loading = true
        reload()
    }

    val backdrop = rememberBlurBackdrop()
    val headerTitle =
        if (title.isNotBlank()) {
            "Diskusi · $title"
        } else {
            "Diskusi"
        }

    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        headerTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = screenContainerColor(),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ExpressivePullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        reload()
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    loading && messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppLoadingIndicator()
                        }
                    }
                    error != null && messages.isEmpty() -> {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Outlined.Forum, null, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(error ?: "Error", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { scope.launch { loading = true; reload() } }) {
                                Text("Coba lagi")
                            }
                        }
                    }
                    messages.isEmpty() -> {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Outlined.Forum, null, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Belum ada diskusi", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Jadi yang pertama bahas anime ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                DiscussionBubble(
                                    msg = msg,
                                    isMine = msg.userId != null && msg.userId == myId,
                                )
                            }
                        }
                    }
                }
            }

            // Composer
            Surface(tonalElevation = 2.dp) {
                if (!loggedIn) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Login Animein untuk ikut diskusi",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = onOpenLogin) {
                            Icon(Icons.AutoMirrored.Outlined.Login, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Login")
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Tulis diskusi…") },
                            singleLine = false,
                            maxLines = 4,
                            enabled = !sending,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions =
                                KeyboardActions(
                                    onSend = {
                                        if (draft.isNotBlank() && !sending) {
                                            scope.launch {
                                                sending = true
                                                val ok = AnimeinAuth.discussionSend(movieId, draft.trim())
                                                if (ok) {
                                                    draft = ""
                                                    reload()
                                                    snackbar.showSnackbar("Terkirim")
                                                } else {
                                                    snackbar.showSnackbar("Gagal mengirim")
                                                }
                                                sending = false
                                            }
                                        }
                                    },
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick =
                                hapticClick {
                                    if (draft.isNotBlank() && !sending) {
                                        scope.launch {
                                            sending = true
                                            val ok = AnimeinAuth.discussionSend(movieId, draft.trim())
                                            if (ok) {
                                                draft = ""
                                                reload()
                                                snackbar.showSnackbar("Terkirim")
                                            } else {
                                                snackbar.showSnackbar("Gagal mengirim")
                                            }
                                            sending = false
                                        }
                                    }
                                },
                            enabled = draft.isNotBlank() && !sending,
                        ) {
                            if (sending) {
                                AppLoadingIndicator()
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Kirim")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionBubble(
    msg: AnimeinDiscussionMessage,
    isMine: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        if (!isMine) {
            val avatar = msg.avatar
            Surface(Modifier.size(36.dp), shape = CircleShape, tonalElevation = 1.dp) {
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AccountCircle, null, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape =
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp,
                ),
            color = if (isMine) cs.primaryContainer else cs.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isMine && !msg.username.isNullOrBlank()) {
                    Text(
                        msg.username,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                if (!msg.time.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        msg.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
