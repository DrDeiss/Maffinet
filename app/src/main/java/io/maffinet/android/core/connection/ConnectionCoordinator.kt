package io.maffinet.android.core.connection

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import io.maffinet.android.core.dpibypass.ByeDpiVpnService
import io.maffinet.android.core.dpibypass.ServiceManager
import io.maffinet.android.core.services.ApplicationRouting
import io.maffinet.android.core.tgproxy.TgProxyController
import io.maffinet.android.core.tgproxy.TgProxyService
import io.maffinet.android.data.Mode
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.service.WatchdogReceiver
import io.maffinet.android.service.WatchdogWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.maffinet.android.core.debug.AppDebugManager as Log

/** Every foreground/background entry point dispatches the two modes independently. */
object ConnectionCoordinator {
    private val _startErrors = MutableStateFlow<List<String>>(emptyList())
    val startErrors = _startErrors.asStateFlow()

    fun clearStartErrors() { _startErrors.value = emptyList() }

    data class StartResult(
        val hasSelectedModes: Boolean,
        val startedApplications: Boolean,
        val startedTelegram: Boolean,
        val errors: List<String>,
    )

    fun startSelected(context: Context, openTelegram: Boolean = false): StartResult {
        val settings = MaffinetSettingsRepository(context)
        val modes = settings.selectedModes()
        if (!modes.any) {
            val errors = listOf("Включите «Приложения» или «Telegram»")
            _startErrors.value = errors
            return StartResult(false, false, false, errors)
        }
        val errors = mutableListOf<String>()
        var vpn = false
        var telegram = false
        if (modes.applications) {
            try {
                check(VpnService.prepare(context) == null) { "Разрешите VPN для режима «Приложения»" }
                installedApplicationSelection(context, settings)
                ServiceManager.start(context, Mode.VPN)
                vpn = true
            } catch (error: Exception) { errors += error.message ?: "Не удалось запустить VPN" }
        }
        if (modes.telegram) {
            try {
                TgProxyService.requestStart(context, openTelegram)
                telegram = true
            } catch (error: Exception) { errors += error.message ?: "Не удалось запустить Telegram-прокси" }
        }
        refreshWatchdog(context)
        _startErrors.value = errors.toList()
        return StartResult(true, vpn, telegram, errors)
    }

    fun stopAll(context: Context) {
        clearStartErrors()
        // Clear both desired states before either asynchronous STOP is dispatched.
        MaffinetSettingsRepository(context).setRequested(false, false)
        try { ServiceManager.stop(context) }
        catch (error: Exception) { Log.e("ConnectionCoordinator", "VPN STOP dispatch failed", error) }
        try { TgProxyService.requestStop(context) }
        catch (error: Exception) { Log.e("ConnectionCoordinator", "Telegram STOP dispatch failed", error) }
        refreshWatchdog(context)
    }

    /** Boot starts current choices only when autostart is enabled. Recovery uses saved requests. */
    fun recover(context: Context, boot: Boolean = false): Boolean {
        val settings = MaffinetSettingsRepository(context)
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        if (boot) {
            if (prefs.getBoolean("autostart", false)) return startSelected(context).errors.isEmpty()
            // A saved desired connection belongs to the previous boot. WorkManager
            // persists its periodic watchdog across reboot, so skipping just this
            // broadcast would let a later normal recovery revive that old session.
            settings.setRequested(false, false)
            refreshWatchdog(context)
            return true
        }
        var recovered = true
        if (settings.applicationsRequested() && settings.applicationsEnabled() && VpnService.prepare(context) == null) {
            try { ServiceManager.ensureStarted(context) }
            catch (error: Exception) { recovered = false; Log.e("ConnectionCoordinator", "VPN recovery dispatch failed", error) }
        }
        if (settings.telegramRequested() && settings.telegramEnabled()) {
            try { TgProxyService.ensureStarted(context) }
            catch (error: Exception) { recovered = false; Log.e("ConnectionCoordinator", "Telegram recovery dispatch failed", error) }
        }
        refreshWatchdog(context)
        return recovered
    }

    fun installedApplicationSelection(context: Context, settings: MaffinetSettingsRepository = MaffinetSettingsRepository(context)): Set<String> {
        val selected = ApplicationRouting.selectedPackages(settings.manualApplications(), context.packageName)
        val installed = selected.filterTo(linkedSetOf()) {
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(it, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) { false }
        }
        return ApplicationRouting.installedPackages(selected, installed, context.packageName)
    }

    fun isConfigurationLocked(context: Context): Boolean =
        MaffinetSettingsRepository(context).anyModeRequested() || isConfigurationLocked()

    fun isConfigurationLocked(): Boolean = ByeDpiVpnService.hasProxyResources || TgProxyController.hasResources ||
        ByeDpiVpnService.currentStatus.value in activeStates || TgProxyController.status.value in activeStates ||
            ServiceManager.isStrategyTestInProgress

    val activeStates = setOf(ModeConnectionState.Starting, ModeConnectionState.Running, ModeConnectionState.Stopping)

    fun refreshWatchdog(context: Context) {
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        if (MaffinetSettingsRepository(context).anyModeRequested() && !prefs.getBoolean("econom_mode", false)) {
            WatchdogWorker.schedulePeriodicWork(context.applicationContext)
            WatchdogReceiver.scheduleWatchdogAlarm(context.applicationContext)
        } else {
            WatchdogWorker.cancelPeriodicWork(context.applicationContext)
            WatchdogReceiver.cancelWatchdogAlarm(context.applicationContext)
        }
    }
}
