package io.maffinet.android.service

import android.content.Context
import android.net.VpnService
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.maffinet.android.core.dpibypass.ServiceManager
import io.maffinet.android.core.dpibypass.ByeDpiVpnService
import io.maffinet.android.core.debug.AppDebugManager as Log
import io.maffinet.android.core.tgproxy.TgProxyController
import java.util.concurrent.TimeUnit

class WatchdogWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val appCtx = applicationContext
        val prefs = appCtx.getSharedPreferences(appCtx.packageName + "_preferences", Context.MODE_PRIVATE)
        val serviceEnabled = prefs.getBoolean("service_enabled", false)

        if (!serviceEnabled) return Result.success()

        val wantsVpn = prefs.getBoolean("wants_youtube_bypass", true)
        val wantsTelegram = prefs.getBoolean("telegram_proxy_enabled_by_user", true)

        val serviceDead = !ByeDpiVpnService.isVpnActive && !ByeDpiVpnService.hasProxyResources
        val tgDead = !TgProxyController.isPortOpen(
            TgProxyController.DEFAULT_BIND_IP,
            TgProxyController.getPort(appCtx),
            500
        )

        if (wantsVpn && serviceDead && ServiceManager.canStartVpn()) {
            if (VpnService.prepare(appCtx) == null) {
                try { ServiceManager.ensureStarted(appCtx) }
                catch (error: Exception) {
                    Log.e("WatchdogWorker", "VPN recovery could not start", error)
                    return Result.retry()
                }
            }
        }

        if (wantsTelegram && tgDead) {
            if (wantsVpn) {
                TgProxyController.startAsync(appCtx, {}, {})
            } else {
                val intent = android.content.Intent(appCtx, io.maffinet.android.core.tgproxy.TgProxyService::class.java).apply {
                    action = io.maffinet.android.data.START_ACTION
                    putExtra("open_tg", false)
                }
                androidx.core.content.ContextCompat.startForegroundService(appCtx, intent)
            }
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
