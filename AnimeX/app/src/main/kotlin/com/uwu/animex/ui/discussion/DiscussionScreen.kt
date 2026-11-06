package com.uwu.animex.ui.discussion

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.UserAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(
    movieId: String,
    movieTitle: String?,
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
) {
    var items by remember(movieId) { mutableStateOf<List<UserApi.Discussion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val session by UserSession.user.collectAsStateWithLifecycle()
    val loggedIn = session?.isAuthenticated == true

    fun reload() {
        loading = true
        error = null
        scope.launch {
            try {
                items = UserApi.discussionList(movieId)
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(movieId) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Diskusi")
                        if (!movieTitle.isNullOrBlank()) {
                            Text(
                                movieTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
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
                        "Mode baca saja — login untuk ikut diskusi",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(if (loggedIn) "Tulis diskusi..." else "Login dulu untuk kirim diskusi")
                        },
                        enabled = loggedIn && !sending,
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick =
                            hapticClick {
                                if (!loggedIn) {
                                    onNeedLogin()
                                    return@hapticClick
                                }
                                val text = draft.trim()
                                if (text.isEmpty() || sending) return@hapticClick
                                sending = true
                                scope.launch {
                                    try {
                                        UserApi.discussionPost(movieId, text)
                                        draft = ""
                                        items = UserApi.discussionList(movieId)
                                        if (items.isNotEmpty()) {
                                            listState.animateScrollToItem(items.lastIndex)
                                        }
                                    } catch (e: Exception) {
                                        error = e.toUserMessage()
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                        enabled = if (loggedIn) !sending && draft.isNotBlank() else true,
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
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && items.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text("Tarik refresh atau coba lagi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items.isEmpty() -> {
                    Text(
                        "Belum ada diskusi. Jadilah yang pertama!",
                        Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id ?: it.hashCode().toString() }) { d ->
                            DiscussionRow(d)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionRow(d: UserApi.Discussion) {
    Row(Modifier.fillMaxWidth()) {
        UserAvatar(d.username, imageUrl = d.image, pokemon = d.pokemon, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.username ?: "User",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(d.body(), style = MaterialTheme.typography.bodyMedium)
            val time = d.time ?: d.createdAt
            if (!time.isNullOrBlank()) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
