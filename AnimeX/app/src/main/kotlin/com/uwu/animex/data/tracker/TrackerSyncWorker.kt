package com.uwu.animex.data.tracker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.sync.AccountManager
import java.util.concurrent.TimeUnit

object TrackerSyncScheduler {
    private const val UNIQUE_NAME = "tracker_sync"

    fun schedule(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<TrackerSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        WorkManager
            .getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

class TrackerSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        AccountManager.init(app)
        TrackerOutbox.attach(app)

        var retry = false
        for (source in TrackerSource.entries) {
            val report = TrackerOutbox.flush(source)
            if (report.retry) retry = true

            if (report.dropped > 0) {
                when (source) {
                    TrackerSource.MAL -> {
                        MalLibrary.init(app)
                        MalLibrary.refresh(force = true)
                    }
                    TrackerSource.ANILIST -> {
                        AniListLibrary.init(app)
                        AniListLibrary.refresh(force = true)
                    }
                }
            }
        }
        return if (retry) Result.retry() else Result.success()
    }
}
