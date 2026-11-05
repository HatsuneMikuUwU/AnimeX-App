package com.uwu.animex.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.animein.AnimeInUser
import com.uwu.animex.data.api.Api
import com.uwu.animex.ui.profile.ProfileScaffold
import com.uwu.animex.ui.profile.ProfileTab
import com.uwu.animex.ui.profile.ProfileTabState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val animeInProfileTabs = listOf(
    ProfileTab("Profil", Icons.Outlined.AccountCircle, Icons.Outlined.AccountCircle),
    ProfileTab("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Outlined.ChatBubbleOutline),
)

@Composable
fun AnimeInProfileScreen(
    user: AnimeInUser,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<Api.AnimeInChatMessage>>(emptyList()) }
    var draft by rememberSaveable { mutableStateOf("") }
    var chatError by remember { mutableStateOf<String?>(null) }
    var chatLoading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    suspend fun refreshChat() {
        if (chatLoading) return
        chatLoading = true
        try {
            val page = AnimeInAccount.loadChat(messages.firstOrNull()?.id)
            val freshById = (page.messages + page.updates).associateBy { it.id }
            if (messages.isEmpty()) {
                messages = (page.messages + page.updates).distinctBy { it.id }
            } else {
                val knownIds = messages.mapTo(mutableSetOf()) { it.id }
                val additions = page.messages.filterNot { it.id in knownIds }
                val updated = messages.map { freshById[it.id] ?: it }
                messages = (additions + updated).distinctBy { it.id }
            }
            chatError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            chatError = e.message?.takeIf { it.isNotBlank() } ?: "Chat gagal dimuat. Coba lagi."
        } finally {
            chatLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            refreshChat()
            while (true) {
                delay(25_000)
                refreshChat()
            }
        }
    }

    ProfileScaffold(
        title = "ANIMEIN",
        onBack = onBack,
        snackbar = snackbar,
        tabs = ProfileTabState(selectedTab) { selectedTab = it },
        tabItems = animeInProfileTabs,
        refreshing = chatLoading,
        onRefresh = if (selectedTab == 1) ({ scope.launch { refreshChat() } }) else null,
    ) {
        if (selectedTab == 0) {
            AnimeInProfileDetails(user = user, onLogout = onLogout)
        } else {
            AnimeInChatTab(
                messages = messages,
                draft = draft,
                onDraftChange = { draft = it },
                loading = chatLoading,
                sending = sending,
                error = chatError,
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && !sending) {
                        scope.launch {
                            sending = true
                            chatError = null
                            try {
                                AnimeInAccount.sendChat(text)
                                draft = ""
                                refreshChat()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                chatError = e.message?.takeIf { it.isNotBlank() } ?: "Pesan gagal dikirim."
                            } finally {
                                sending = false
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AnimeInProfileDetails(
    user: AnimeInUser,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Profil ANIMEIN", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Username", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("ID akun: ${user.id}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Akun AnimeIN sudah terhubung ke AnimeX.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}

@Composable
private fun AnimeInChatTab(
    messages: List<Api.AnimeInChatMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    loading: Boolean,
    sending: Boolean,
    error: String?,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Chat komunitas ANIMEIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (loading && messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (error ==
                        null
                    ) {
                        "Belum ada pesan. Mulai obrolan komunitas."
                    } else {
                        "Tarik untuk mencoba memuat chat lagi."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    message.username.ifBlank { "Pengguna ANIMEIN" },
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(message.time, style = MaterialTheme.typography.labelSmall)
                            }
                            if (message.text.isNotBlank()) {
                                Text(message.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (message.imageUrl.isNotBlank()) {
                                Text(
                                    "Pesan berisi gambar",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                label = { Text("Tulis pesan") },
                maxLines = 3,
                enabled = !sending,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            )
            Button(onClick = onSend, enabled = draft.isNotBlank() && !sending) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(2.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Kirim")
                }
            }
        }
    }
}
