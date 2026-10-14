package com.uwu.animex.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.uwu.animex.core.notification.BatteryOptimization

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    title: String = "Biar notif episode gak telat",
    message: String =
        "Hemat baterai bisa nahan AnimeX ngecek episode baru di latar belakang. " +
            "Matiin pembatasan baterai buat AnimeX supaya notif episode baru tetap masuk.",
) {
    val ctx = LocalContext.current
    AppDialog(
        icon = Icons.Outlined.BatteryChargingFull,
        title = title,
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
        text = { Text(message) },
    )
}
