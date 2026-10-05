@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.profile

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalStats
import com.uwu.animex.data.mal.MalUser
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.RotatingCookieFrame
import com.uwu.animex.ui.common.label
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

@Composable
fun MalAvatar(modifier: Modifier = Modifier) {
    val user by Mal.user.collectAsStateWithLifecycle()
    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
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
fun ProfileScreen(onBack: () -> Unit = {}, onOpenAbout: () -> Unit = {}) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val message by Mal.message.collectAsStateWithLifecycle()

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
            title = "Logout dari MAL?",
            text = {
                Text(
                    "Sinkronisasi ke MyAnimeList bakal berhenti sampai kamu login lagi. " +
                        "Status tontonan yang kesimpen di HP ini juga bakal dihapus.",
                )
            },
            confirmButton = {
                DialogDestructiveButton("Logout") {
                    Mal.logout()
                    confirmLogout = false
                }
            },
            dismissButton = { DialogCancelButton { confirmLogout = false } },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                expandedHeight = 160.dp,
                title = { Text("Profil", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAbout, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Info, contentDescription = "Tentang AnimeX")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = cs.background,
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
    val cs = MaterialTheme.colorScheme
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(144.dp).clip(shape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                tint = cs.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Sambungin MyAnimeList",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Progres nonton dan status anime bakal otomatis nyambung ke daftar MAL kamu.",
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        val busy by Mal.busy.collectAsStateWithLifecycle()
        if (busy) {
            AppLoadingIndicator()
        } else {
            Button(
                onClick = onLogin,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Login pakai MAL", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfileContent(onLogout: () -> Unit) {
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val userState by Mal.user.collectAsStateWithLifecycle()
    val user = userState
    val stats = user?.anime_statistics

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ProfileHero(user)
        Spacer(Modifier.height(28.dp))
        HighlightGrid(stats)
        Spacer(Modifier.height(12.dp))
        DistributionCard(stats)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { user?.name?.let { uri.openUri(Mal.PROFILE_URL + it) } },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Intip profil di MAL", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = onLogout,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = cs.errorContainer,
                    contentColor = cs.onErrorContainer,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Logout", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfileHero(user: MalUser?) {
    val cs = MaterialTheme.colorScheme
    val pic = user?.picture
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RotatingCookieFrame {
            if (!pic.isNullOrBlank()) {
                AsyncImage(
                    model = pic,
                    contentDescription = "Foto profil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = cs.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            user?.name ?: "Sabar bentar ya…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            user?.location?.takeIf { it.isNotBlank() }?.let { InfoPill(Icons.Filled.LocationOn, it) }
            user?.birthday?.let { InfoPill(Icons.Filled.Cake, prettyDate(it, "yyyy-MM-dd", "MMM d, yyyy")) }
            val joined = user?.joined_at?.let { prettyDate(it, "yyyy-MM-dd'T'HH:mm:ssXXX", "MMM d, yyyy") }
            InfoPill(Icons.Filled.Schedule, if (joined != null) "Gabung sejak $joined" else "Sabar bentar ya…")
        }
    }
}

private fun prettyDate(raw: String, from: String, to: String): String = runCatching {
    val d = SimpleDateFormat(from, Locale.US).parse(raw)
    SimpleDateFormat(to, Locale.getDefault()).format(d!!)
}.getOrDefault(raw)

@Composable
private fun InfoPill(icon: ImageVector, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
    }
}

@Composable
private fun HighlightGrid(s: MalStats?) {
    val cs = MaterialTheme.colorScheme
    val big = 36.dp
    val small = 12.dp
    val leaning = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = big, bottomStart = small)
    val mirrored = RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = small, bottomStart = big)
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                icon = Icons.Filled.Star,
                value = s?.mean_score?.let { "%.2f".format(Locale.US, it) } ?: "0",
                label = "Rata-rata skor",
                container = cs.primaryContainer,
                content = cs.onPrimaryContainer,
                shape = leaning,
                modifier = Modifier.weight(1f),
            )
            HighlightTile(
                icon = Icons.Filled.PlayCircleOutline,
                value = (s?.num_episodes ?: 0).toString(),
                label = "Episode",
                container = cs.secondaryContainer,
                content = cs.onSecondaryContainer,
                shape = mirrored,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                icon = Icons.Filled.Event,
                value = s?.num_days?.let { "%.2f".format(Locale.US, it) } ?: "0",
                label = "Hari nonton",
                container = cs.tertiaryContainer,
                content = cs.onTertiaryContainer,
                shape = mirrored,
                modifier = Modifier.weight(1f),
            )
            HighlightTile(
                icon = Icons.Filled.Repeat,
                value = (s?.num_times_rewatched ?: 0).toString(),
                label = "Nonton ulang",
                container = cs.surfaceContainerHigh,
                content = cs.onSurface,
                shape = leaning,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HighlightTile(
    icon: ImageVector,
    value: String,
    label: String,
    container: Color,
    content: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(shape)
            .background(container)
            .padding(20.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(content),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = container)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.8f))
    }
}

private class StatSlice(val label: String, val value: Int, val bg: Color, val fg: Color)

@Composable
private fun DistributionCard(s: MalStats?) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val slices = listOf(
        StatSlice(
            "Lagi Nonton", s?.num_items_watching ?: 0,
            if (dark) Color(0xFF45E267) else Color(0xFF006E26),
            if (dark) Color(0xFF003910) else Color.White,
        ),
        StatSlice(
            "Tamat", s?.num_items_completed ?: 0,
            if (dark) Color(0xFFA9C7FF) else Color(0xFF005DB7),
            if (dark) Color(0xFF003063) else Color.White,
        ),
        StatSlice(
            "Ditunda Dulu", s?.num_items_on_hold ?: 0,
            if (dark) Color(0xFFEAC300) else Color(0xFF705D00),
            if (dark) Color(0xFF3B2F00) else Color.White,
        ),
        StatSlice(
            "Gak Dilanjut", s?.num_items_dropped ?: 0,
            if (dark) Color(0xFFFFB4AA) else Color(0xFFBE0D13),
            if (dark) Color(0xFF690004) else Color.White,
        ),
        StatSlice("Mau Ditonton", s?.num_items_plan_to_watch ?: 0, scheme.outline, scheme.onSurfaceVariant),
    )
    val total = slices.sumOf { it.value }

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val sweep by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "donutSweep",
    )

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainer)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Statistik nonton kamu",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        DonutChart(slices, sweep) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    total.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Total anime",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slices.forEach { sl ->
                val percent = if (total > 0) "%.0f".format(Locale.US, sl.value * 100f / total) else "0"
                LegendPill(sl, percent)
            }
        }
    }
}

@Composable
private fun LegendPill(slice: StatSlice, percent: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(slice.bg))
        Spacer(Modifier.width(8.dp))
        Text(slice.label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
        Spacer(Modifier.width(6.dp))
        Text(
            slice.value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Text("$percent%", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

private const val CHART_DEGREES = 340f
private const val CHART_START_ANGLE = 100f

@Composable
private fun DonutChart(slices: List<StatSlice>, progress: Float, center: @Composable () -> Unit) {
    val total = slices.sumOf { it.value }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    BoxWithConstraints(Modifier.size(196.dp).padding(20.dp), contentAlignment = Alignment.Center) {
        val canvasSize = min(constraints.maxWidth, constraints.maxHeight)
        val canvasSizeDp = with(LocalDensity.current) { canvasSize.toDp() }
        val sliceWidth = with(LocalDensity.current) { 18.dp.toPx() }
        Canvas(Modifier.size(canvasSizeDp)) {
            drawArc(
                color = track,
                startAngle = CHART_START_ANGLE,
                sweepAngle = CHART_DEGREES,
                useCenter = false,
                size = Size(canvasSize.toFloat(), canvasSize.toFloat()),
                style = Stroke(width = sliceWidth, cap = StrokeCap.Round),
            )
            if (total > 0) {
                var start = CHART_START_ANGLE
                slices.filter { it.value > 0 }.forEach { sl ->
                    val angle = CHART_DEGREES * sl.value / total
                    drawArc(
                        color = sl.bg,
                        startAngle = start,
                        sweepAngle = angle * progress,
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
