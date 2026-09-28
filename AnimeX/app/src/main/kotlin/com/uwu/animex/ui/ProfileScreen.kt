@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalStats
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MalAvatar(modifier: Modifier = Modifier) {
    val pic = Mal.user?.picture
    if (Mal.loggedIn && !pic.isNullOrBlank()) {
        AsyncImage(
            model = pic,
            contentDescription = "Profil",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(32.dp).clip(CircleShape),
        )
    } else {
        Icon(Icons.Filled.AccountCircle, contentDescription = "Login MAL", modifier = modifier.size(32.dp))
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Mal.loggedIn) {
        if (Mal.loggedIn) runCatching { Mal.refreshUser() }
    }
    LaunchedEffect(Mal.message) {
        Mal.message?.let {
            snackbar.showSnackbar(it)
            Mal.message = null
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Keluar dari MAL?") },
            text = { Text("Sinkronisasi ke MyAnimeList akan berhenti sampai kamu login lagi.") },
            confirmButton = {
                TextButton(onClick = {
                    Mal.logout()
                    confirmLogout = false
                }) { Text("Keluar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Batal") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!Mal.loggedIn) {
                LoginPrompt(onLogin = { Mal.startLogin(ctx) })
            } else {
                ProfileContent(onLogout = { confirmLogout = true })
            }
        }
    }
}

@Composable
private fun LoginPrompt(onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Hubungkan MyAnimeList", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Progress tontonan dan status anime akan otomatis tersinkron ke daftar MAL kamu.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (Mal.busy) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onLogin) { Text("Login dengan MAL") }
        }
    }
}

@Composable
private fun ProfileContent(onLogout: () -> Unit) {
    val uri = LocalUriHandler.current
    val user = Mal.user
    val stats = user?.anime_statistics

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!user?.picture.isNullOrBlank()) {
                AsyncImage(
                    model = user?.picture,
                    contentDescription = "Foto profil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(100.dp).clip(CircleShape),
                )
            } else {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(100.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    user?.name ?: "Memuat…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                user?.location?.takeIf { it.isNotBlank() }?.let { InfoLine(Icons.Filled.LocationOn, it) }
                user?.birthday?.let { InfoLine(Icons.Filled.Cake, prettyDate(it, "yyyy-MM-dd", "MMM d, yyyy")) }
                user?.joined_at?.let { InfoLine(Icons.Filled.Schedule, prettyDate(it, "yyyy-MM-dd'T'HH:mm:ssXXX", "MMM d, yyyy HH:mm")) }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text(
            "Statistik Anime",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        StatsBlock(stats)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sinkron otomatis")
                Text(
                    "Update progress ke MAL saat episode selesai ditonton",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = Mal.autoSync, onCheckedChange = { Mal.updateAutoSync(it) })
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { user?.name?.let { uri.openUri(Mal.PROFILE_URL + it) } }) {
                Text("Lihat profil di MAL")
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = onLogout) { Text("Keluar", color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun prettyDate(raw: String, from: String, to: String): String = runCatching {
    val d = SimpleDateFormat(from, Locale.US).parse(raw)
    SimpleDateFormat(to, Locale.getDefault()).format(d!!)
}.getOrDefault(raw)

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(text, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class StatSlice(val label: String, val value: Int, val bg: Color, val fg: Color)

@Composable
private fun StatsBlock(s: MalStats?) {
    val slices = listOf(
        StatSlice("Menonton", s?.num_items_watching ?: 0, Color(0xFF84E040), Color(0xFF0F2A00)),
        StatSlice("Selesai", s?.num_items_completed ?: 0, Color(0xFFA0CDFF), Color(0xFF00325A)),
        StatSlice("Ditunda", s?.num_items_on_hold ?: 0, Color(0xFFCCCC22), Color(0xFF2F2F00)),
        StatSlice("Dihentikan", s?.num_items_dropped ?: 0, Color(0xFFFFB59B), Color(0xFF5A1D00)),
        StatSlice("Ingin Ditonton", s?.num_items_plan_to_watch ?: 0, Color(0xFF2B2E22), Color(0xFFE3E4D3)),
    )
    val total = slices.sumOf { it.value }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                if (total == 0) {
                    drawArc(
                        Color.Gray.copy(alpha = 0.3f), 0f, 360f, false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke),
                    )
                } else {
                    val gap = if (slices.count { it.value > 0 } > 1) 6f else 0f
                    var start = -90f
                    slices.filter { it.value > 0 }.forEach { sl ->
                        val sweep = 360f * sl.value / total
                        drawArc(
                            sl.bg, start + gap / 2, (sweep - gap).coerceAtLeast(1f), false,
                            Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        start += sweep
                    }
                }
            }
            Text("Total: $total", style = MaterialTheme.typography.labelMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEach { sl ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(sl.bg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("${sl.value}  ${sl.label}", color = sl.fg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        StatItem(Icons.Filled.Event, s?.num_days?.let { "%.2f".format(Locale.US, it) } ?: "0")
        StatItem(Icons.Filled.PlayCircleOutline, (s?.num_episodes ?: 0).toString())
        StatItem(Icons.Filled.Star, s?.mean_score?.let { "%.2f".format(Locale.US, it) } ?: "0")
        StatItem(Icons.Filled.Repeat, (s?.num_times_rewatched ?: 0).toString())
    }
}

@Composable
private fun StatItem(icon: ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
