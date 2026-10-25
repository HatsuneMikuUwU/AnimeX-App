@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.notifications.AlarmManagerScheduler
import com.uwu.animex.ui.common.hapticClick

fun needsNotificationSetup(context: android.content.Context): Boolean =
    !AppNotifier.canPost(context) ||
        !AlarmManagerScheduler.canScheduleExact(context) ||
        BatteryOptimization.isRestricted(context)

private data class SetupItem(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val granted: Boolean,
    val onGrant: () -> Unit,
)

@Composable
fun NotificationSetupScreen(onDone: () -> Unit) {
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

    val items =
        listOf(
            SetupItem(
                icon = Icons.Outlined.Notifications,
                title = "Izin notifikasi",
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
                title = "Alarm & pengingat",
                body = "Biar notif muncul pas banget saat episodenya rilis, bukan telat.",
                granted = alarmOk,
                onGrant = { AlarmManagerScheduler.openExactAlarmSettings(ctx) },
            ),
            SetupItem(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "Pembatasan baterai",
                body = "Biar AnimeX nggak dimatiin sistem pas lagi di background.",
                granted = batteryOk,
                onGrant = { BatteryOptimization.requestIgnore(ctx) },
            ),
        )
    val allGranted = items.all { it.granted }

    BackHandler(onBack = onDone)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!allGranted) {
                    TextButton(onClick = hapticClick { onDone() }, shapes = ButtonDefaults.shapes()) {
                        Text("Nanti aja", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SetupHero(allGranted = allGranted)

                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (allGranted) "Beres, siap dikabarin!" else "Biar notif episodenya tepat waktu",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        if (allGranted) {
                            "Semua izin sudah aktif. Kamu bakal dikasih tau begitu episode baru rilis."
                        } else {
                            "Aktifkan izin di bawah supaya kamu nggak ketinggalan episode baru."
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEach { SetupCard(it) }
                }
                Spacer(Modifier.height(16.dp))
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Button(
                    onClick = hapticClick { onDone() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        if (allGranted) "Siap" else "Lanjut tanpa izin",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupHero(allGranted: Boolean) {
    val cs = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "setup_spin")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "setup_hero_spin",
    )
    val heroShape: Shape = MaterialShapes.Flower.toShape()
    val accentShape: Shape = MaterialShapes.Sunny.toShape()

    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(196.dp)
                .graphicsLayer { rotationZ = spin }
                .clip(heroShape)
                .background(cs.primaryContainer),
        )
        AnimatedContent(
            targetState = allGranted,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "setup_hero_icon",
        ) { ok ->
            Icon(
                imageVector = if (ok) Icons.Outlined.Check else Icons.Outlined.Notifications,
                contentDescription = null,
                tint = cs.onPrimaryContainer,
                modifier = Modifier.size(80.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 4.dp)
                .size(64.dp)
                .graphicsLayer { rotationZ = -spin * 1.5f }
                .clip(accentShape)
                .background(cs.tertiaryContainer),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 8.dp, y = (-8).dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(cs.onTertiaryContainer.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun SetupCard(item: SetupItem) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (item.granted) cs.secondaryContainer else cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(if (item.granted) cs.secondary else cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (item.granted) Icons.Outlined.Check else item.icon,
                    contentDescription = null,
                    tint = if (item.granted) cs.onSecondary else cs.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.granted) cs.onSecondaryContainer else cs.onSurface,
                )
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.granted) cs.onSecondaryContainer else cs.onSurfaceVariant,
                )
            }
            if (!item.granted) {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = hapticClick { item.onGrant() },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("Izinkan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
