@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.notifications.AlarmManagerScheduler
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.icon
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private data class OnboardPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val heroShape: Shape,
    val accentShape: Shape,
)

private data class CtaState(
    val label: String,
    val icon: ImageVector,
)

private enum class Palette { Primary, Secondary, Tertiary }

private fun paletteFor(page: Int) =
    when (page % 3) {
        0 -> Palette.Primary
        1 -> Palette.Tertiary
        else -> Palette.Secondary
    }

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    permissionsOnly: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val setup = rememberSetupState()

    val introPages =
        if (permissionsOnly) {
            emptyList()
        } else {
            listOf(
                OnboardPage(
                    icon = Icons.Outlined.PlayArrow,
                    title = "Halo, selamat datang di AnimeX!",
                    body = "Nonton anime jadi gampang nih. Ngebut, bersih, dan nggak ada iklan.",
                    heroShape = MaterialShapes.Cookie9Sided.toShape(),
                    accentShape = MaterialShapes.Sunny.toShape(),
                ),
                OnboardPage(
                    icon = Icons.Outlined.Explore,
                    title = "Cari apa aja, ketemu",
                    body = "Ketik judul favoritmu, atau kulik lewat kategori, studio, tahun, dan tipe.",
                    heroShape = MaterialShapes.Clover4Leaf.toShape(),
                    accentShape = MaterialShapes.Cookie6Sided.toShape(),
                ),
                OnboardPage(
                    icon = Icons.Outlined.Download,
                    title = "Simpen dulu, nonton nanti",
                    body = "Episode ke-download di background, jadi bisa ditonton kapan aja tanpa kuota.",
                    heroShape = MaterialShapes.SoftBurst.toShape(),
                    accentShape = MaterialShapes.Pill.toShape(),
                ),
                OnboardPage(
                    icon = Icons.Outlined.Notifications,
                    title = "Anti ketinggalan episode",
                    body = "Sambungin ke MyAnimeList, terus dapet notif begitu episode baru rilis.",
                    heroShape = MaterialShapes.Flower.toShape(),
                    accentShape = MaterialShapes.Sunny.toShape(),
                ),
            )
        }

    val permissionShapes =
        listOf(
            MaterialShapes.Flower.toShape() to MaterialShapes.Sunny.toShape(),
            MaterialShapes.Clover4Leaf.toShape() to MaterialShapes.Pill.toShape(),
            MaterialShapes.Cookie9Sided.toShape() to MaterialShapes.Cookie6Sided.toShape(),
        )
    val permissionPages =
        setup.items.mapIndexed { index, item ->
            OnboardPage(
                icon = if (item.granted) Icons.Outlined.Check else item.icon,
                title = item.title,
                body = item.body,
                heroShape = permissionShapes[index].first,
                accentShape = permissionShapes[index].second,
            )
        }

    val pages = introPages + permissionPages
    val introCount = introPages.size
    val pageCount = pages.size
    val pagerState =
        rememberPagerState(
            initialPage = if (permissionsOnly) setup.items.indexOfFirst { !it.granted }.coerceAtLeast(0) else 0,
            pageCount = { pageCount },
        )
    val isLast = pagerState.currentPage == pageCount - 1
    val current = setup.items.getOrNull(pagerState.currentPage - introCount)

    val cta =
        when {
            current != null && !current.granted -> CtaState(current.cta, current.icon)
            !isLast -> CtaState("Lanjut", Icons.AutoMirrored.Outlined.ArrowForward)
            setup.allGranted -> CtaState(if (permissionsOnly) "Siap" else "Yuk nonton", Icons.Outlined.Check)
            else -> CtaState("Lengkapi izin", Icons.AutoMirrored.Outlined.ArrowBack)
        }

    BackHandler(enabled = permissionsOnly || pagerState.currentPage > 0) {
        if (permissionsOnly && setup.allGranted) {
            onFinish()
        } else if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = pagerState.currentPage < introCount,
                    enter = AppMotion.fadeEnter(),
                    exit = AppMotion.fadeExit(),
                ) {
                    TextButton(
                        onClick = hapticClick { scope.launch { pagerState.animateScrollToPage(introCount) } },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text("Skip", fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                val distance =
                    ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 1f)
                OnboardingPageContent(
                    page = pages[index],
                    palette = paletteFor(index),
                    distance = distance,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (pageCount > 1) PageIndicator(count = pageCount, current = pagerState.currentPage)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = AppMotion.expandHorizontalEnter(),
                        exit = AppMotion.expandHorizontalExit(),
                    ) {
                        Row {
                            FilledTonalIconButton(
                                onClick = hapticClick {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                                modifier = Modifier.size(56.dp),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Kembali",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                    }

                    Button(
                        onClick = hapticClick {
                            when {
                                current != null && !current.granted -> current.onGrant()
                                !isLast ->
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                setup.allGranted -> onFinish()
                                else ->
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            introCount + setup.items.indexOfFirst { !it.granted },
                                        )
                                    }
                            }
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        val motion = MaterialTheme.motionScheme
                        AnimatedContent(
                            targetState = cta,
                            transitionSpec = { AppMotion.crossfade(motion) },
                            label = "onboarding_cta",
                        ) { state ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    state.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(
                                    state.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHero(
    icon: ImageVector,
    heroShape: Shape,
    accentShape: Shape,
    palette: Palette,
    distance: Float,
    size: Dp,
) {
    val cs = MaterialTheme.colorScheme
    val heroContainer: Color
    val onHero: Color
    val accentContainer: Color
    val onAccent: Color
    when (palette) {
        Palette.Primary -> {
            heroContainer = cs.primaryContainer
            onHero = cs.onPrimaryContainer
            accentContainer = cs.tertiaryContainer
            onAccent = cs.onTertiaryContainer
        }
        Palette.Secondary -> {
            heroContainer = cs.secondaryContainer
            onHero = cs.onSecondaryContainer
            accentContainer = cs.primaryContainer
            onAccent = cs.onPrimaryContainer
        }
        Palette.Tertiary -> {
            heroContainer = cs.tertiaryContainer
            onHero = cs.onTertiaryContainer
            accentContainer = cs.secondaryContainer
            onAccent = cs.onSecondaryContainer
        }
    }

    val infinite = rememberInfiniteTransition(label = "onboarding_spin")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "hero_spin",
    )
    val k = size.value / 280f
    val motion = MaterialTheme.motionScheme

    Box(
        modifier =
            Modifier
                .size(size)
                .graphicsLayer {
                    alpha = 1f - distance
                    val s = 1f - 0.2f * distance
                    scaleX = s
                    scaleY = s
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size((248 * k).dp)
                    .graphicsLayer { rotationZ = spin }
                    .clip(heroShape)
                    .background(heroContainer),
        )
        AnimatedContent(
            targetState = icon,
            transitionSpec = { AppMotion.crossfade(motion) },
            label = "hero_icon",
        ) { current ->
            Icon(
                imageVector = current,
                contentDescription = null,
                tint = onHero,
                modifier = Modifier.size((96 * k).dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size((76 * k).dp)
                    .graphicsLayer { rotationZ = -spin * 1.5f }
                    .clip(accentShape)
                    .background(accentContainer),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size((28 * k).dp)
                    .clip(CircleShape)
                    .background(onAccent.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardPage,
    palette: Palette,
    distance: Float,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingHero(
            icon = page.icon,
            heroShape = page.heroShape,
            accentShape = page.accentShape,
            palette = palette,
            distance = distance,
            size = 280.dp,
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { alpha = 1f - distance },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = 1f - distance },
        )
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width: Dp by animateDpAsState(
                targetValue = if (selected) 36.dp else 12.dp,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "dot_w",
            )
            val color by animateColorAsState(
                targetValue =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "dot_c",
            )
            Box(
                Modifier
                    .height(12.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

fun needsNotificationSetup(context: Context): Boolean =
    !AppNotifier.canPost(context) ||
        !AlarmManagerScheduler.canScheduleExact(context) ||
        BatteryOptimization.isRestricted(context)

private data class SetupItem(
    val icon: ImageVector,
    val title: String,
    val cta: String,
    val body: String,
    val granted: Boolean,
    val onGrant: () -> Unit,
)

private class SetupState(val items: List<SetupItem>) {
    val allGranted: Boolean = items.all { it.granted }
}

@Composable
private fun rememberSetupState(): SetupState {
    val ctx = LocalContext.current

    var notifOk by remember { mutableStateOf(AppNotifier.canPost(ctx)) }
    var alarmOk by remember { mutableStateOf(AlarmManagerScheduler.canScheduleExact(ctx)) }
    var batteryOk by remember { mutableStateOf(!BatteryOptimization.isRestricted(ctx)) }

    fun refresh() {
        notifOk = AppNotifier.canPost(ctx)
        alarmOk = AlarmManagerScheduler.canScheduleExact(ctx)
        batteryOk = !BatteryOptimization.isRestricted(ctx)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            refresh()
            if (!granted) AppNotifier.openSystemSettings(ctx)
        }

    return SetupState(
        listOf(
            SetupItem(
                icon = Icons.Outlined.Notifications,
                title = "Izinin notifikasi",
                cta = "Izinkan notifikasi",
                body = "Biar kabar episode baru bisa muncul di layar HP kamu.",
                granted = notifOk,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        AppNotifier.openSystemSettings(ctx)
                    }
                },
            ),
            SetupItem(
                icon = Icons.Outlined.Alarm,
                title = "Alarm biar tepat waktu",
                cta = "Izinkan alarm",
                body = "Biar notif muncul pas banget saat episodenya rilis, bukan telat.",
                granted = alarmOk,
                onGrant = { AlarmManagerScheduler.openExactAlarmSettings(ctx) },
            ),
            SetupItem(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "Jangan sampai dimatiin sistem",
                cta = "Izinkan di background",
                body = "Matiin pembatasan baterai biar AnimeX nggak dimatiin sistem pas lagi di background.",
                granted = batteryOk,
                onGrant = { BatteryOptimization.requestIgnore(ctx) },
            ),
        ),
    )
}
