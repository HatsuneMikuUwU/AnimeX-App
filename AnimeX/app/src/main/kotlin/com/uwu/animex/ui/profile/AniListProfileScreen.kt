package com.uwu.animex.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.uwu.animex.data.anilist.AniList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniListProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    val loggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val user by AniList.user.collectAsStateWithLifecycle()
    val busy by AniList.busy.collectAsStateWithLifecycle()
    val autoSync by AniList.autoSync.collectAsStateWithLifecycle()
    val message by AniList.message.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            AniList.clearMessage()
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Logout dari AniList?") },
            text = { Text("Sinkronisasi ke AniList akan berhenti sampai kamu login lagi. Akun MAL tetap tersambung.") },
            confirmButton = {
                TextButton(onClick = { AniList.logout(); confirmLogout = false }) { Text("Logout") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Batal") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AniList", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (loggedIn) Arrangement.Top else Arrangement.Center,
        ) {
            if (loggedIn) {
                Spacer(Modifier.height(36.dp))
                if (!user?.avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = user?.avatar,
                        contentDescription = "Avatar AniList",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(112.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(20.dp))
                Text(user?.name ?: "Akun AniList", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text("Sinkron otomatis", fontWeight = FontWeight.SemiBold)
                        Text("Progres episode ikut dikirim ke AniList", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoSync, onCheckedChange = AniList::updateAutoSync)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { user?.name?.let { uriHandler.openUri(AniList.PROFILE_URL + it) } },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Text("  Buka profil AniList")
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Text("  Logout")
                }
            } else {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(104.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Sambungkan AniList", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Progres menonton, status, dan rating bisa disinkronkan ke AniList. Jika akun MAL juga tersambung, satu perubahan akan dikirim ke keduanya.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { AniList.startLogin(context) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null)
                    Text(if (busy) "  Menghubungkan…" else "  Login pakai AniList")
                }
            }
        }
    }
}
