@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.data.animein.AnimeinAuth
import com.uwu.animex.data.animein.AnimeinChatMessage
import com.uwu.animex.data.animein.AnimeinSession
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedNavIcon
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.profile.LoginPrompt
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch

private data class AnimeinTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val animeinTabs =
    listOf(
        AnimeinTab("Profil", Icons.Outlined.BarChart, Icons.Filled.BarChart),
        AnimeinTab("Chat", Icons.Outlined.Chat, Icons.Filled.Chat),
        AnimeinTab("Riwayat", Icons.Outlined.History, Icons.Filled.History),
    )

@Composable
fun AnimeinProfileScreen(
    onBack: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val loggedIn by AnimeinSession.loggedIn.collectAsStateWithLifecycle()
    val user by AnimeinSession.user.collectAsStateWithLifecycle()
    val message by AnimeinSession.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmLogout by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            AnimeinSession.clearMessage()
        }
    }
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            runCatching { AnimeinAuth.refreshProfile() }
        }
    }

    if (confirmLogout) {
        AppDialog(
            icon = Icons.AutoMirrored.Outlined.Logout,
            title = "Logout dari Animein?",
            onDismiss = { confirmLogout = false },
            text = {
                Text("Sesi akun Animein di perangkat ini akan dihapus. Chat dan data profil butuh login lagi.")
            },
            confirmButton = {
                DialogDestructiveButton(
                    label = "Logout",
                    onClick = {
                        confirmLogout = false
                        AnimeinAuth.logout()
                    },
                )
            },
            dismissButton = {
                DialogCancelButton(label = "Batal", onClick = { confirmLogout = false })
            },
        )
    }

    if (!loggedIn) {
        val loginBackdrop = rememberBlurBackdrop()
        Scaffold(
            topBar = {
                FloatingTopBar(
                    backdrop = loginBackdrop,
                    onBack = onBack,
                    title = { Text("Animein", fontWeight = FontWeight.Bold) },
                )
            },
            containerColor = screenContainerColor(),
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                LoginPrompt(
                    title = "Belum login Animein",
                    description = "Login pakai akun Animein untuk sinkron profil, history server, dan chat komunitas.",
                    buttonText = "Login Animein",
                    busy = false,
                    onLogin = onOpenLogin,
                )
            }
        }
        return
    }

    val backdrop = rememberBlurBackdrop()
    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        user?.name ?: user?.username ?: "Profil Animein",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { confirmLogout = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Logout")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = screenContainerColor(),
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            BlurContentBox(
                PaddingValues(top = pad.calculateTopPadding(), bottom = floatingTabBarSpace() + 8.dp),
                backdrop,
            ) {
                ExpressivePullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            runCatching {
                                when (tab) {
                                    1 -> { /* chat di-refresh di dalam ChatTab */ }
                                    else -> AnimeinAuth.refreshProfile()
                                }
                            }
                            refreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (tab) {
                        0 -> AnimeinProfileInfoTab(user = user)
                        1 -> AnimeinChatTab()
                        else -> AnimeinHistoryPlaceholder()
                    }
                }
            }

            // Floating tab bar termasuk tab Chat
            FloatingTabBarOverlay(
                items = animeinTabs,
                selectedIndex = { tab.coerceIn(0, animeinTabs.lastIndex) },
                onSelected = { tab = it },
                backdrop = backdrop,
                label = { it.label },
                icon = { item, i ->
                    AnimatedNavIcon(tab == i, item.icon, item.label, selectedIcon = item.selectedIcon)
                },
            )
        }
    }
}

@Composable
private fun AnimeinProfileInfoTab(user: com.uwu.animex.data.animein.AnimeinUser?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val avatar = user?.avatar
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            tonalElevation = 2.dp,
        ) {
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            user?.name ?: user?.username ?: "Pengguna",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (!user?.username.isNullOrBlank() && user?.name != user?.username) {
            Text(
                "@${user?.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!user?.email.isNullOrBlank()) {
            Text(user!!.email!!, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatChip("Level", user?.level ?: "—")
            StatChip("Coin", user?.coin ?: "—")
            StatChip("ID", user?.id ?: "—")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Akun tersambung ke API yang sama dengan Animein. " +
                "Request berikutnya otomatis membawa id_user & key_client.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.padding(4.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnimeinHistoryPlaceholder() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Riwayat server", fontWeight = FontWeight.SemiBold)
        Text(
            "History nonton dari server Animein bisa ditambahkan di sini (endpoint userHistoryAnime).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnimeinChatTab() {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<AnimeinChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val myId = AnimeinSession.idUser

    suspend fun reload() {
        loading = true
        messages = runCatching { AnimeinAuth.chatData() }.getOrDefault(emptyList())
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppLoadingIndicator()
                    }
                }
                messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Belum ada chat. Kirim pesan pertama!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = true,
                    ) {
                        items(messages.asReversed(), key = { it.id }) { msg ->
                            ChatBubble(msg, isMine = msg.userId != null && msg.userId == myId)
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp) {
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
                    placeholder = { Text("Tulis pesan…") },
                    singleLine = true,
                    enabled = !sending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                if (draft.isNotBlank() && !sending) {
                                    scope.launch {
                                        sending = true
                                        val ok = AnimeinAuth.sendChat(draft.trim())
                                        if (ok) {
                                            draft = ""
                                            reload()
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
                                    val ok = AnimeinAuth.sendChat(draft.trim())
                                    if (ok) {
                                        draft = ""
                                        reload()
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

@Composable
private fun ChatBubble(
    msg: AnimeinChatMessage,
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
            modifier = Modifier.fillMaxWidth(0.78f),
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
