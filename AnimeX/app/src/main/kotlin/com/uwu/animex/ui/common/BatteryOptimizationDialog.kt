package com.uwu.animex.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.uwu.animex.core.notification.BatteryOptimization

@Composable
fun BatteryOptimizationDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AppDialog(
        icon = Icons.Outlined.BatteryChargingFull,
        title = "Biar notif episode gak telat",
        onDismiss = onDismiss,
        confirmButton = {
            DialogConfirmButton(label = "Izinkan") {
                BatteryOptimization.requestIgnore(ctx)
                onDismiss()
            }
        },
        dismissButton = {
            DialogCancelButton {
                BatteryOptimization.markDismissed(ctx)
                onDismiss()
            }
        },
        text = {
            Text(
                "Hemat baterai bisa nahan AnimeX ngecek episode baru di latar belakang. " +
                    "Matiin pembatasan baterai buat AnimeX supaya notif episode baru tetap masuk.",
            )
        },
    )
}
