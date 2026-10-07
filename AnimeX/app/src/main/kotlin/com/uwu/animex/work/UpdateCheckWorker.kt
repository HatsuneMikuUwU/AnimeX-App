package com.uwu.animex.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uwu.animex.data.update.AppUpdate

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppUpdate.init(applicationContext)
        AppUpdate.check()
        return Result.success()
    }
}
