@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Auth
import com.uwu.animex.data.History

@Composable
fun SettingsScreen(
    onOpenLogin: () -> Unit,
) {
    val ctx = LocalContext.current
    var showLogout by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Auth.isLoggedIn) {
        if (Auth.isLoggedIn) runCatching { Auth.refreshProfile() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Pengaturan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        // Akun
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (Auth.isLoggedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val avatar = Auth.user?.image ?: Auth.user?.avatar
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).padding(8.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            Auth.username ?: Auth.user?.name ?: "Pengguna",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val sub = Auth.email
                        if (!sub.isNullOrBlank()) {
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Auth.user?.coin?.let {
                            Text("Coin: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Keluar") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                    modifier = Modifier.clickable { showLogout = true },
                )
            } else {
                ListItem(
                    headlineContent = { Text("Masuk / Daftar") },
                    supportingContent = { Text("Login seperti di AnimeIn untuk sinkron favorit & profil") },
                    leadingContent = { Icon(Icons.Filled.Person, null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                    modifier = Modifier.clickable(onClick = onOpenLogin),
                )
            }
        }

        // Data
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = { Text("Hapus riwayat tonton") },
                supportingContent = { Text("${History.items.size} judul tersimpan lokal") },
                leadingContent = { Icon(Icons.Filled.Delete, null) },
                modifier = Modifier.clickable { showClearHistory = true },
            )
        }

        // Tentang
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = { Text("Tentang AnimeX") },
                supportingContent = { Text("Klien tidak resmi untuk API AnimeIn") },
                leadingContent = { Icon(Icons.Filled.Info, null) },
                modifier = Modifier.clickable { aboutOpen = true },
            )
            ListItem(
                headlineContent = { Text("Versi") },
                supportingContent = {
                    val ver = try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                    } catch (_: Exception) {
                        "?"
                    }
                    Text(ver ?: "?")
                },
                leadingContent = { Icon(Icons.Filled.Settings, null) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Keluar?") },
            text = { Text("Sesi login akan dihapus dari perangkat ini.") },
            confirmButton = {
                TextButton(onClick = {
                    Auth.logout()
                    showLogout = false
                }) { Text("Keluar") }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Batal") }
            },
        )
    }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text("Hapus riwayat?") },
            text = { Text("Semua riwayat tonton lokal akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    History.clear()
                    showClearHistory = false
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistory = false }) { Text("Batal") }
            },
        )
    }

    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("AnimeX") },
            text = {
                Text(
                    "Aplikasi open-source untuk menonton anime via API AnimeIn. " +
                        "Login memakai endpoint auth yang sama dengan aplikasi resmi " +
                        "(auth/login, auth/register). Bukan afiliasi resmi AnimeIn.",
                )
            },
            confirmButton = {
                TextButton(onClick = { aboutOpen = false }) { Text("OK") }
            },
        )
    }
}
