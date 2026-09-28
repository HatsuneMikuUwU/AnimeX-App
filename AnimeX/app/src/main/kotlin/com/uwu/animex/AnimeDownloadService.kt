package com.uwu.animex

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
import com.uwu.animex.data.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Menjaga proses tetap hidup dan menampilkan notifikasi progres selama ada unduhan aktif. */
class AnimeDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Downloads.init(this)
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, Downloads.NOTIFICATION_ID, buildNotification(), type)
        if (loop?.isActive != true) {
            loop = scope.launch {
                val nm = getSystemService(NotificationManager::class.java)
                while (isActive) {
                    delay(1000)
                    val active = Downloads.items.values.filter {
                        it.status == Downloads.Status.QUEUED || it.status == Downloads.Status.DOWNLOADING
                    }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@AnimeDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                    nm.notify(Downloads.NOTIFICATION_ID, buildNotification())
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                Downloads.CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification(): Notification {
        val active = Downloads.items.values.filter {
            it.status == Downloads.Status.QUEUED || it.status == Downloads.Status.DOWNLOADING
        }
        val known = active.filter { it.percent >= 0f }
        val percent = if (known.isEmpty()) 0 else known.map { it.percent }.average().toInt()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Downloads.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Mengunduh episode")
            .setContentText(if (active.isEmpty()) "Menyelesaikan…" else "${active.size} unduhan · $percent%")
            .setProgress(100, percent, known.isEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }
}
