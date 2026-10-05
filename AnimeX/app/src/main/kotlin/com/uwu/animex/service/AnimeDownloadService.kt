package com.uwu.animex.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.uwu.animex.MainActivity
import com.uwu.animex.R
import com.uwu.animex.data.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AnimeDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Downloads.init(this)
        createChannel()
        if (intent?.action == ACTION_PAUSE_ALL) {
            Downloads.pauseAll()
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) {
            loop = scope.launch {
                val nm = getSystemService(NotificationManager::class.java)
                while (isActive) {
                    delay(1000)
                    if (activeItems().isEmpty()) {
                        stopForegroundAndSelf()
                        break
                    }
                    nm.notify(Downloads.NOTIFICATION_ID, buildNotification())
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val paused = activeItems().size
        Downloads.pauseAll()
        if (paused > 0) notifyTimeout()
        stopForegroundAndSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, Downloads.NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun stopForegroundAndSelf() {
        loop?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun activeItems() = Downloads.items.value.values.filter {
        it.status == Downloads.Status.QUEUED || it.status == Downloads.Status.DOWNLOADING
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                Downloads.CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(): Notification {
        val active = activeItems()
        val known = active.filter { it.percent >= 0f }
        val percent = if (known.isEmpty()) 0 else known.map { it.percent }.average().toInt()
        val pauseAll = PendingIntent.getService(
            this,
            1,
            Intent(this, AnimeDownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Downloads.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Lagi ngunduh episode nih")
            .setContentText(if (active.isEmpty()) "Dikit lagi kelar…" else "${active.size} unduhan · $percent%")
            .setProgress(100, percent, known.isEmpty())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(android.R.drawable.ic_media_pause, "Pause semua", pauseAll)
            .build()
    }

    private fun notifyTimeout() {
        val n = NotificationCompat.Builder(this, Downloads.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Unduhan di-pause")
            .setContentText("Waktu layanan unduhan dari Android udah habis. Buka aplikasinya buat lanjut.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Waktu layanan unduhan dari Android udah habis. Buka aplikasinya buat lanjut."),
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(Downloads.NOTIFICATION_ID + 1, n)
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.uwu.animex.action.PAUSE_ALL"
    }
}
