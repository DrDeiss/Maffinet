package io.maffinet.android.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.maffinet.android.core.debug.AppDebugManager as Log
import java.util.concurrent.TimeUnit
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.data.settings.MaffinetSettingsRepository

class WatchdogWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val appCtx = applicationContext
        val prefs = appCtx.getSharedPreferences(appCtx.packageName + "_preferences", Context.MODE_PRIVATE)
        if (!MaffinetSettingsRepository(appCtx).anyModeRequested() || prefs.getBoolean("econom_mode", false)) return Result.success()
        try { if (!ConnectionCoordinator.recover(appCtx)) return Result.retry() }
        catch (error: Exception) {
            Log.e("WatchdogWorker", "Mode recovery could not start", error)
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "io.maffinet.android.work.WATCHDOG"

        fun schedulePeriodicWork(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WatchdogWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
