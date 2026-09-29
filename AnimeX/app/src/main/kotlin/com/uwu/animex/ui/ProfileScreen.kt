@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalStats
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

@Composable
fun MalAvatar(modifier: Modifier = Modifier) {
    val user by Mal.user.collectAsState()
    val loggedIn by Mal.loggedIn.collectAsState()
    val pic = user?.picture
    if (loggedIn && !pic.isNullOrBlank()) {
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

    val loggedIn by Mal.loggedIn.collectAsState()
    val message by Mal.message.collectAsState()

    LaunchedEffect(loggedIn) {
        if (loggedIn) runCatching { Mal.refreshUser() }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            Mal.clearMessage()
        }
    }

    if (confirmLogout) {
        AppDialog(
            icon = Icons.AutoMirrored.Filled.Logout,
            onDismiss = { confirmLogout = false },
            title = "Keluar dari MAL?",
            text = {
                Text(
                    "Sinkronisasi ke MyAnimeList akan berhenti sampai kamu login lagi. " +
                        "Status tontonan yang tersimpan di perangkat ini juga akan dihapus.",
                )
            },
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
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!loggedIn) {
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
        val busy by Mal.busy.collectAsState()
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onLogin) { Text("Login dengan MAL") }
        }
    }
}

@Composable
private fun ProfileContent(onLogout: () -> Unit) {
    val uri = LocalUriHandler.current
    val userState by Mal.user.collectAsState()
    val user = userState
    val autoSync by Mal.autoSync.collectAsState()
    val stats = user?.anime_statistics

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!user?.picture.isNullOrBlank()) {
                AsyncImage(
                    model = user.picture,
                    contentDescription = "Foto profil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(16.dp).size(100.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).size(100.dp),
                )
            }
            Column {
                Text(
                    user?.name ?: "Memuat…",
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                user?.location?.takeIf { it.isNotBlank() }?.let { InfoLine(Icons.Filled.LocationOn, it) }
                user?.birthday?.let { InfoLine(Icons.Filled.Cake, prettyDate(it, "yyyy-MM-dd", "MMM d, yyyy")) }
                InfoLine(
                    Icons.Filled.Schedule,
                    user?.joined_at?.let { prettyDate(it, "yyyy-MM-dd'T'HH:mm:ssXXX", "MMM d, yyyy HH:mm") } ?: "Memuat…",
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        StatsBlock(stats)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { Mal.updateAutoSync(!autoSync) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sinkron otomatis", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Update progress ke MAL saat episode selesai ditonton",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoSync,
                onCheckedChange = { Mal.updateAutoSync(it) },
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        TextButton(
            onClick = { user?.name?.let { uri.openUri(Mal.PROFILE_URL + it) } },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text("Lihat profil di MAL", color = MaterialTheme.colorScheme.primary)
        }
        TextButton(
            onClick = onLogout,
            modifier = Modifier.padding(bottom = 16.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text("Keluar", color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun prettyDate(raw: String, from: String, to: String): String = runCatching {
    val d = SimpleDateFormat(from, Locale.US).parse(raw)
    SimpleDateFormat(to, Locale.getDefault()).format(d!!)
}.getOrDefault(raw)

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = text,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            modifier = Modifier.padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private class StatSlice(val label: String, val value: Int, val bg: Color, val fg: Color)

@Composable
private fun StatsBlock(s: MalStats?) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val slices = listOf(
        StatSlice(
            "Menonton", s?.num_items_watching ?: 0,
            if (dark) Color(0xFF45E267) else Color(0xFF006E26),
            if (dark) Color(0xFF003910) else Color.White,
        ),
        StatSlice(
            "Selesai", s?.num_items_completed ?: 0,
            if (dark) Color(0xFFA9C7FF) else Color(0xFF005DB7),
            if (dark) Color(0xFF003063) else Color.White,
        ),
        StatSlice(
            "Ditunda", s?.num_items_on_hold ?: 0,
            if (dark) Color(0xFFEAC300) else Color(0xFF705D00),
            if (dark) Color(0xFF3B2F00) else Color.White,
        ),
        StatSlice(
            "Dihentikan", s?.num_items_dropped ?: 0,
            if (dark) Color(0xFFFFB4AA) else Color(0xFFBE0D13),
            if (dark) Color(0xFF690004) else Color.White,
        ),
        StatSlice("Ingin Ditonton", s?.num_items_plan_to_watch ?: 0, scheme.surfaceVariant, scheme.onSurfaceVariant),
    )
    val total = slices.sumOf { it.value }
    val scope = rememberCoroutineScope()

    Text(
        "Statistik Anime",
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DonutChart(slices) {
            Text(
                "Total: $total",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
        Column {
            slices.forEach { sl ->
                val percent = if (total > 0) "%.1f".format(Locale.US, sl.value * 100f / total) else "0"
                StatChip(sl, "$percent%", scope)
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        TextIconVertical(Icons.Filled.Event, s?.num_days?.let { "%.2f".format(Locale.US, it) } ?: "0", "Hari")
        TextIconVertical(Icons.Filled.PlayCircleOutline, (s?.num_episodes ?: 0).toString(), "Episode")
        TextIconVertical(Icons.Filled.Star, s?.mean_score?.let { "%.2f".format(Locale.US, it) } ?: "0", "Skor rata-rata")
        TextIconVertical(Icons.Filled.Repeat, (s?.num_times_rewatched ?: 0).toString(), "Ditonton ulang")
    }
}

private const val CHART_DEGREES = 340f
private const val CHART_START_ANGLE = 100f

@Composable
private fun DonutChart(slices: List<StatSlice>, center: @Composable () -> Unit) {
    val total = slices.sumOf { it.value }
    BoxWithConstraints(Modifier.size(164.dp).padding(16.dp), contentAlignment = Alignment.Center) {
        val canvasSize = min(constraints.maxWidth, constraints.maxHeight)
        val canvasSizeDp = with(LocalDensity.current) { canvasSize.toDp() }
        val sliceWidth = with(LocalDensity.current) { 16.dp.toPx() }
        Canvas(Modifier.size(canvasSizeDp)) {
            if (total > 0) {
                var start = CHART_START_ANGLE
                slices.filter { it.value > 0 }.forEach { sl ->
                    val angle = CHART_DEGREES * sl.value / total
                    drawArc(
                        color = sl.bg,
                        startAngle = start,
                        sweepAngle = angle,
                        useCenter = false,
                        size = Size(canvasSize.toFloat(), canvasSize.toFloat()),
                        style = Stroke(width = sliceWidth, cap = StrokeCap.Round),
                    )
                    start += angle
                }
            }
        }
        center()
    }
}

@Composable
private fun StatChip(slice: StatSlice, tooltip: String, scope: CoroutineScope) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = tooltipState,
    ) {
        ElevatedAssistChip(
            onClick = { scope.launch { tooltipState.show() } },
            label = { Text(slice.label) },
            modifier = Modifier.padding(end = 8.dp),
            leadingIcon = { Text(slice.value.toString(), color = slice.fg) },
            colors = AssistChipDefaults.elevatedAssistChipColors(
                containerColor = slice.bg,
                labelColor = slice.fg,
                leadingIconContentColor = slice.fg,
            ),
        )
    }
}

@Composable
private fun TextIconVertical(icon: ImageVector, text: String, tooltip: String) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = tooltipState,
    ) {
        Column(
            Modifier.clickable { scope.launch { tooltipState.show() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = tooltip,
                modifier = Modifier.padding(4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
