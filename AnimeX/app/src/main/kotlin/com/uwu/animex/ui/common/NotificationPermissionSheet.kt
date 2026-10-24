package com.uwu.animex.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.notifications.AlarmManagerScheduler
import com.uwu.animex.ui.util.WindowBlurEffect

fun needsEpisodeAlertPermissions(context: android.content.Context): Boolean =
    !AlarmManagerScheduler.canScheduleExact(context) || BatteryOptimization.isRestricted(context)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPermissionSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var alarmOk by remember { mutableStateOf(AlarmManagerScheduler.canScheduleExact(ctx)) }
    var batteryOk by remember { mutableStateOf(!BatteryOptimization.isRestricted(ctx)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    alarmOk = AlarmManagerScheduler.canScheduleExact(ctx)
                    batteryOk = !BatteryOptimization.isRestricted(ctx)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetOptionItem(
                index = 0,
                count = 2,
                icon = Icons.Outlined.Alarm,
                label = "Izin alarm & pengingat",
                selected = alarmOk,
                onClick = { if (!alarmOk) AlarmManagerScheduler.openExactAlarmSettings(ctx) },
            )
            SheetOptionItem(
                index = 1,
                count = 2,
                icon = Icons.Outlined.BatteryChargingFull,
                label = "Pembatasan baterai",
                selected = batteryOk,
                onClick = { if (!batteryOk) BatteryOptimization.requestIgnore(ctx) },
            )
        }
    }
}
