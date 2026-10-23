package com.uwu.animex.work

import android.content.Context
import androidx.work.WorkerParameters
import com.uwu.animex.notifications.subscription.SubscriptionNotificationWorker

/**
 * Cuma buat kerjaan lama yang mungkin masih ngantre di WorkManager dari versi sebelumnya
 * (nama kelasnya kesimpen di database WorkManager). Logikanya semua ada di [SubscriptionNotificationWorker].
 */
@Deprecated("Pakai SubscriptionNotificationWorker")
class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : SubscriptionNotificationWorker(context, params)
