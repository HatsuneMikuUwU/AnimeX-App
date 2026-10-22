@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.mal.hasStatsData
import com.uwu.animex.data.mal.toListStats
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedNavIcon
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.RotatingCookieFrame
import com.uwu.animex.ui.common.fabBottomInset
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.detail.DetailActionButton
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

@Composable
fun AccountAvatar(modifier: Modifier = Modifier) {
    val malUser by Mal.user.collectAsStateWithLifecycle()
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniUser by AniList.user.collectAsStateWithLifecycle()
    val aniLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val syncPref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val pic =
        when (SyncSourcePref.effective(syncPref, malLoggedIn, aniLoggedIn)) {
            SyncSource.MAL -> malUser?.picture
            SyncSource.ANILIST -> aniUser?.avatar
            SyncSource.LOCAL -> null
        }
    if (!pic.isNullOrBlank()) {
        AsyncImage(
            model = pic,
            contentDescription = "Profil",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(32.dp).clip(CircleShape),
        )
    } else {
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = if (malLoggedIn || aniLoggedIn) "Profil" else "Login",
            modifier = modifier.size(32.dp),
        )
    }
}

@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }

    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val message by Mal.message.collectAsStateWithLifecycle()

    LaunchedEffect(loggedIn) {
        if (loggedIn) runCatching { Mal.refreshUser() }
    }
    LaunchedEffect(loggedIn) {
        if (loggedIn) runCatching { MalLibrary.refresh(force = MalLibrary.lacksStatsData()) }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            Mal.clearMessage()
        }
    }

    if (confirmLogout) {
        AppDialog(
            icon = Icons.AutoMirrored.Outlined.Logout,
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

    val busy by Mal.busy.collectAsStateWithLifecycle()
    val userState by Mal.user.collectAsStateWithLifecycle()
    val libraryItems by MalLibrary.items.collectAsStateWithLifecycle()
    val libraryRefreshing by MalLibrary.refreshing.collectAsStateWithLifecycle()
    val libraryError by MalLibrary.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val listStats = remember(libraryItems) { libraryItems.toListStats() }
    val libraryReady =
        libraryItems.hasStatsData() ||
            (libraryItems.isEmpty() && MalLibrary.loaded && libraryError == null)
    val libraryFailed = !libraryReady && !libraryRefreshing && libraryError != null
    val libraryLoading = !libraryReady && !libraryFailed
    ProfileScaffold(
        title = "MAL",
        onBack = onBack,
        snackbar = snackbar,
        actions =
            if (loggedIn) {
                ({
                    ProfileActions(
                        openLabel = "Intip profil di MAL",
                        onOpen = { userState?.name?.let { uri.openUri(Mal.PROFILE_URL + it) } },
                        onLogout = { confirmLogout = true },
                    )
                })
            } else {
                null
            },
        tabs = if (loggedIn) ProfileTabState(tab) { tab = it } else null,
    ) {
        if (!loggedIn) {
            LoginPrompt(
                title = "Sambungin MyAnimeList",
                description = "Progres nonton dan status anime bakal otomatis nyambung ke daftar MAL kamu.",
                buttonText = "Login pakai MAL",
                busy = busy,
                onLogin = { Mal.startLogin(ctx) },
            )
        } else {
            val user = userState
            val historyState by Mal.history.collectAsStateWithLifecycle()
            ProfileContent(
                info =
                    ProfileInfo(
                        name = user?.name,
                        picture = user?.picture,
                        location = user?.location,
                        birthday = user?.birthday?.let { prettyDate(it, "yyyy-MM-dd", "MMM d, yyyy") },
                        joined = user?.joined_at?.let { prettyDate(it, "yyyy-MM-dd'T'HH:mm:ssXXX", "MMM d, yyyy") },
                    ),
                tab = tab,
                historyContent = {
                    ProfileHistorySection(
                        state = historyState,
                        onLoad = { scope.launch { Mal.refreshHistory() } },
                        onOpenDetail = onOpenDetail,
                        movieIdFor = { Mal.movieIdFor(it) },
                        link = { movieId, malId -> Mal.link(movieId, malId) },
                        snackbar = snackbar,
                        failedMessage = "Riwayat MAL gagal dimuat",
                    )
                },
            ) {
                MalStatsSection(
                    malStats = user?.anime_statistics,
                    s = listStats,
                    libraryLoading = libraryLoading,
                    libraryFailed = libraryFailed,
                    onRetry = {
                        scope.launch {
                            runCatching { Mal.refreshUser() }
                            runCatching { MalLibrary.refresh(force = true) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun ProfileScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    actions: (@Composable RowScope.() -> Unit)? = null,
    tabs: ProfileTabState? = null,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = screenContainerColor(),
    ) { pad ->
        val contentPad =
            if (tabs != null) {
                PaddingValues(top = pad.calculateTopPadding(), bottom = floatingTabBarSpace() + 8.dp)
            } else {
                pad
            }
        Box(Modifier.fillMaxSize()) {
            BlurContentBox(contentPad, backdrop) { content() }
            if (tabs != null) {
                FloatingTabBarOverlay(
                    items = profileTabs,
                    selectedIndex = { tabs.selected },
                    onSelected = tabs.onSelect,
                    backdrop = backdrop,
                    label = { it.label },
                    icon = { item, i ->
                        AnimatedNavIcon(tabs.selected == i, item.icon, item.label, selectedIcon = item.selectedIcon)
                    },
                )
            }
        }
    }
}

@Composable
internal fun LoginPrompt(
    title: String,
    description: String,
    buttonText: String,
    busy: Boolean,
    onLogin: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Column(
        Modifier.fillMaxSize().padding(top = LocalTopInset.current, bottom = LocalBottomInset.current).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(144.dp).clip(shape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                tint = cs.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        if (busy) {
            AppLoadingIndicator()
        } else {
            Button(
                onClick = hapticClick(block = onLogin),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Login,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal class ProfileInfo(
    val name: String?,
    val picture: String?,
    val location: String? = null,
    val birthday: String? = null,
    val joined: String? = null,
)

@Composable
internal fun ProfileActions(
    openLabel: String,
    onOpen: () -> Unit,
    onLogout: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    DetailActionButton(enabled = true, onClick = hapticClick(block = onOpen)) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = openLabel, tint = cs.onSurface)
    }
    DetailActionButton(enabled = true, onClick = hapticClick(block = onLogout)) {
        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Logout", tint = cs.error)
    }
}

@Composable
internal fun ProfileContent(
    info: ProfileInfo,
    tab: Int = 0,
    historyContent: (@Composable () -> Unit)? = null,
    statsContent: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp + LocalTopInset.current))
        ProfileHero(info)
        Spacer(Modifier.height(28.dp))
        if (historyContent != null && tab == 1) historyContent() else statsContent()

        Spacer(Modifier.height(16.dp + fabBottomInset()))
    }
}

internal data class ProfileTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

internal class ProfileTabState(
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

private val profileTabs =
    listOf(
        ProfileTab("Statistik", Icons.Outlined.BarChart, Icons.Filled.BarChart),
        ProfileTab("Riwayat", Icons.Outlined.History, Icons.Filled.History),
    )

@Composable
private fun ProfileHero(user: ProfileInfo) {
    val cs = MaterialTheme.colorScheme
    val pic = user.picture
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
                    Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = cs.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            user.name ?: "Sabar bentar ya…",
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
            user.location?.takeIf { it.isNotBlank() }?.let { InfoPill(Icons.Outlined.LocationOn, it) }
            user.birthday?.let { InfoPill(Icons.Outlined.Cake, it) }
            InfoPill(
                Icons.Outlined.Schedule,
                if (user.joined != null) "Gabung sejak ${user.joined}" else "Sabar bentar ya…",
            )
        }
    }
}

internal fun prettyDate(
    raw: String,
    from: String,
    to: String,
): String =
    runCatching {
        val d = SimpleDateFormat(from, Locale.US).parse(raw)
        SimpleDateFormat(to, Locale.getDefault()).format(requireNotNull(d))
    }.getOrDefault(raw)

@Composable
private fun InfoPill(
    icon: ImageVector,
    text: String,
) {
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
internal fun HighlightTile(
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

internal class StatSlice(
    val label: String,
    val value: Int,
    val bg: Color,
    val fg: Color,
)

@Composable
internal fun DonutCard(
    title: String,
    centerLabel: String,
    slices: List<StatSlice>,
) {
    val scheme = MaterialTheme.colorScheme
    val total = slices.sumOf { it.value }

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
            title,
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        DonutChart(slices) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    total.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    centerLabel,
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
private fun LegendPill(
    slice: StatSlice,
    percent: String,
) {
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
private fun DonutChart(
    slices: List<StatSlice>,
    center: @Composable () -> Unit,
) {
    val total = slices.sumOf { it.value }
    val visible = remember(slices) { slices.filter { it.value > 0 } }
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
                visible.forEach { sl ->
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
