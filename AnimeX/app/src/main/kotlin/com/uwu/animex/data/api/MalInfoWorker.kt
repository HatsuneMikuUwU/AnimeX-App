package com.uwu.animex.data.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uwu.animex.MainActivity
import com.uwu.animex.R
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.Appearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MalInfoWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    // Dipanggil WorkManager untuk expedited work (wajib di Android < 12).
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext)

    override suspend fun doWork(): Result {
        Appearance.init(applicationContext)
        if (!Appearance.settings.value.malData) return Result.success()
        MalInfoRepo.init(applicationContext)

        // Naik ke foreground service: lepas dari batas ~10 menit worker biasa dan
        // proses nggak gampang di-kill pas app ditutup. Di Android 12+ ini cuma boleh
        // dari work expedited / app yang lagi kelihatan, jadi kalau ditolak ya lanjut biasa.
        var foreground = updateForeground(0, MalInfoStore.infos.value.size)

        return try {
            MalInfoBatch.prefetchCatalog(applicationContext) { page, saved ->
                if (foreground) foreground = updateForeground(page, saved)
            }
            Result.success()
        } catch (e: CancellationException) {
            // Dihentikan sistem (timeout / constraint hilang). WorkManager jadwalin ulang,
            // dan prefetchCatalog lanjut dari checkpoint.
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun updateForeground(
        page: Int,
        saved: Int,
    ): Boolean =
        try {
            setForeground(foregroundInfo(applicationContext, page, saved))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    companion object {
        private const val ONCE = "mal_info_prefetch_once"
        private const val PERIODIC = "mal_info_prefetch_periodic"
        private const val BACKOFF_MINUTES = 15L

        private const val CHANNEL_ID = "mal_info_sync"
        private const val NOTIFICATION_ID = 7201

        // Expedited work nggak boleh pakai constraint baterai, jadi cuma jaringan.
        private val onceConstraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        private val periodicConstraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        fun watch(context: Context) {
            val app = context.applicationContext
            Appearance.init(app)
            AppScope.io.launch {
                Appearance.settings
                    .map { it.malData }
                    .distinctUntilChanged()
                    .collect { on -> if (on) schedule(app) else cancel(app) }
            }
        }

        private fun schedule(context: Context) {
            runCatching {
                val wm = WorkManager.getInstance(context)
                wm.enqueueUniqueWork(
                    ONCE,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<MalInfoWorker>()
                        .setConstraints(onceConstraints)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                        .build(),
                )
                wm.enqueueUniquePeriodicWork(
                    PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<MalInfoWorker>(1, TimeUnit.DAYS)
                        .setConstraints(periodicConstraints)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                        .build(),
                )
            }
        }

        private fun cancel(context: Context) {
            runCatching {
                val wm = WorkManager.getInstance(context)
                wm.cancelUniqueWork(ONCE)
                wm.cancelUniqueWork(PERIODIC)
            }
            MalInfoBatch.clear()
        }

        private fun foregroundInfo(
            context: Context,
            page: Int = 0,
            saved: Int = 0,
        ): ForegroundInfo {
            val notification = buildNotification(context, page, saved)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }

        private fun buildNotification(
            context: Context,
            page: Int,
            saved: Int,
        ): Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.mal_sync_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
            val open =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle("Lagi sinkron info MAL")
                .setContentText(
                    if (page <= 0) "Ngumpulin skor & info anime di latar belakang" else "Halaman $page · $saved anime tersimpan",
                )
                .setProgress(0, 0, true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }
}
