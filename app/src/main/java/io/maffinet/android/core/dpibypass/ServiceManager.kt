package io.maffinet.android.core.dpibypass

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.maffinet.android.core.debug.AppDebugManager as Log
import io.maffinet.android.data.Mode
import io.maffinet.android.data.START_ACTION
import io.maffinet.android.data.STOP_ACTION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Coordinates the process-wide ByeDPI singleton without changing its transport. */
object ServiceManager {
    const val EXTRA_KEEP_DESIRED_STATE = "io.maffinet.android.keep_desired_state"
    const val EXTRA_START_GENERATION = "io.maffinet.android.start_generation"
    const val EXTRA_STOP_GENERATION = "io.maffinet.android.stop_generation"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var desiredGeneration = 0L
    @Volatile private var testing = false
    @Volatile private var nativeQuarantined = false
    @Volatile private var paused = false
    private var startRequested = false
    private var resumeAfterTesting = false

    val isStrategyTestInProgress: Boolean get() = testing
    val isNativeProxyQuarantined: Boolean get() = nativeQuarantined
    fun canStartVpn(): Boolean = !testing && !nativeQuarantined && !paused

    @Synchronized fun isStartRequestCurrent(intent: Intent?): Boolean =
        intent?.hasExtra(EXTRA_START_GENERATION) != true ||
            intent?.getLongExtra(EXTRA_START_GENERATION, -1L) == desiredGeneration

    /** Explicit connection request; during a scan it is deferred until native cleanup. */
    @Synchronized fun start(context: Context, mode: Mode) {
        val applicationContext = context.applicationContext
        applicationContext.getPreferences().edit().putBoolean("service_enabled", true).apply()
        desiredGeneration++
        paused = false
        if (testing) {
            resumeAfterTesting = true
            return
        }
        if (nativeQuarantined) {
            Log.e("ServiceManager", "Native proxy did not stop; reopen Maffinet before reconnecting")
            return
        }
        dispatchStart(applicationContext, desiredGeneration)
    }

    /** Recovery is conditional and never reverses a newer user STOP. */
    @Synchronized fun ensureStarted(context: Context) {
        if (!context.getPreferences().getBoolean("service_enabled", false) || !canStartVpn() ||
            ByeDpiVpnService.isVpnActive || ByeDpiVpnService.hasProxyResources) return
        dispatchStart(context.applicationContext, desiredGeneration)
    }

    @Synchronized fun stop(context: Context) {
        onUserStop(context)
        dispatchStop(context.applicationContext, preserveDesiredState = false)
    }

    @Synchronized fun isStopRequestCurrent(intent: Intent?): Boolean =
        (intent?.getBooleanExtra(EXTRA_KEEP_DESIRED_STATE, false) == true && testing) ||
            intent?.hasExtra(EXTRA_STOP_GENERATION) != true ||
            intent?.getLongExtra(EXTRA_STOP_GENERATION, -1L) == desiredGeneration

    /** A manager STOP already applied its desired state; direct notification STOP has not. */
    @Synchronized fun handleUserStopIntent(context: Context, intent: Intent?): Boolean {
        if (!isStopRequestCurrent(intent)) return false
        if (intent?.hasExtra(EXTRA_STOP_GENERATION) != true) onUserStop(context)
        return true
    }

    @Synchronized fun onUserStop(context: Context) {
        desiredGeneration++
        resumeAfterTesting = false
        startRequested = false
        paused = false
        context.getPreferences().edit().putBoolean("service_enabled", false).apply()
        StrategyTestManager.cancelTesting()
    }

    @Synchronized fun onVpnPaused() {
        desiredGeneration++
        paused = true
        resumeAfterTesting = false
        startRequested = false
        StrategyTestManager.cancelTesting()
    }

    @Synchronized fun onVpnResumed() {
        desiredGeneration++
        paused = false
        startRequested = true
        if (testing) resumeAfterTesting = true
    }

    /** Restart stops resources while preserving the requested connection state. */
    @Synchronized fun restart(context: Context, mode: Mode) {
        if (!context.getPreferences().getBoolean("service_enabled", false) || paused || nativeQuarantined) return
        if (testing) {
            resumeAfterTesting = true
            return
        }
        if (!ByeDpiVpnService.isVpnActive && !ByeDpiVpnService.hasProxyResources) return
        val applicationContext = context.applicationContext
        val generation = ++desiredGeneration
        dispatchStop(applicationContext, preserveDesiredState = true)
        scope.launch {
            val stopped = awaitVpnStopped()
            synchronized(this@ServiceManager) {
                if (stopped && generation == desiredGeneration && canStartVpn() &&
                    applicationContext.getPreferences().getBoolean("service_enabled", false)) {
                    dispatchStart(applicationContext, generation)
                }
            }
        }
    }

    class StrategyTestSession internal constructor()
    private var activeSession: StrategyTestSession? = null

    @Synchronized fun beginStrategyTest(context: Context): StrategyTestSession {
        check(!testing && !nativeQuarantined) { "Another native proxy operation is still running" }
        val session = StrategyTestSession()
        activeSession = session
        resumeAfterTesting = !paused && context.getPreferences().getBoolean("service_enabled", false) &&
            (startRequested || ByeDpiVpnService.isVpnActive || ByeDpiVpnService.hasProxyResources)
        testing = true // Start/reconnect/watchdog gates close before temporary STOP.
        return session
    }

    @Synchronized fun stopForStrategyTest(context: Context) = dispatchStop(context.applicationContext, preserveDesiredState = true)

    suspend fun awaitVpnStopped(): Boolean = withTimeoutOrNull(5_000) {
        while (ByeDpiVpnService.isVpnActive || ByeDpiVpnService.hasProxyResources) delay(50)
        // Allow queued service commands to observe the already-closed scan gate.
        delay(100)
        !ByeDpiVpnService.isVpnActive && !ByeDpiVpnService.hasProxyResources
    } == true

    @Synchronized fun finishStrategyTest(context: Context, session: StrategyTestSession, nativeClean: Boolean) {
        if (activeSession !== session) return
        if (!nativeClean) nativeQuarantined = true
        val resume = resumeAfterTesting && nativeClean && !paused && !ByeDpiVpnService.hasProxyResources &&
            context.getPreferences().getBoolean("service_enabled", false)
        activeSession = null
        resumeAfterTesting = false
        testing = false
        // The desired preference was never overwritten by scan STOP. A user STOP wins.
        if (resume) dispatchStart(context.applicationContext, desiredGeneration)
        if (context.getPreferences().getBoolean("service_enabled", false) &&
            !context.getPreferences().getBoolean("econom_mode", false)) {
            io.maffinet.android.service.WatchdogWorker.schedulePeriodicWork(context.applicationContext)
        }
    }

    private fun dispatchStart(context: Context, generation: Long) {
        startRequested = true
        val intent = Intent(context, ByeDpiVpnService::class.java).apply {
            action = START_ACTION
            putExtra(EXTRA_START_GENERATION, generation)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun dispatchStop(context: Context, preserveDesiredState: Boolean) {
        val intent = Intent(context, ByeDpiVpnService::class.java).apply {
            action = STOP_ACTION
            putExtra(EXTRA_KEEP_DESIRED_STATE, preserveDesiredState)
            putExtra(EXTRA_STOP_GENERATION, desiredGeneration)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
