package com.uwu.animex.data.api

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.Appearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Prefetch info MAL untuk seluruh katalog ANIMEIN di background (WorkManager). */
class MalInfoWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Appearance.init(applicationContext)
        if (!Appearance.settings.value.malData) return Result.success()
        MalInfoRepo.init(applicationContext)
        return try {
            MalInfoBatch.prefetchCatalog()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val ONCE = "mal_info_prefetch_once"
        private const val PERIODIC = "mal_info_prefetch_periodic"
        private const val BACKOFF_MINUTES = 15L

        private val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        /** Jalankan prefetch tiap app dibuka dan tiap switch "Data MyAnimeList" dinyalakan. */
        fun watch(context: Context) {
            val app = context.applicationContext
            AppScope.io.launch {
                Appearance.init(app)
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
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                        .build(),
                )
                wm.enqueueUniquePeriodicWork(
                    PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<MalInfoWorker>(1, TimeUnit.DAYS)
                        .setConstraints(constraints)
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
    }
}
