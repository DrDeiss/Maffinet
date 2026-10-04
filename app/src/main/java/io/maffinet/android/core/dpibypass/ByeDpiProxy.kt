package io.maffinet.android.core.dpibypass

import android.os.Looper
import io.maffinet.android.core.debug.AppDebugManager as Log
import io.maffinet.android.core.strategy.LinkedInAlternativeRoute

class ByeDpiProxy {
    companion object {
        @Volatile var lastResolvedLinkedInAddress: String? = null
            private set

        init {
            System.loadLibrary("byedpi")
        }
    }

    fun startProxy(preferences: ByeDpiProxyPreferences, shouldStart: () -> Boolean = { !Thread.currentThread().isInterrupted }): Int {
        lastResolvedLinkedInAddress = null
        if (preferences.linkedInAlternativeRouteEnabled) {
            check(Thread.currentThread() != Looper.getMainLooper().thread) { "LinkedIn DNS must run on the proxy worker" }
        }
        val args = LinkedInAlternativeRoute.prepareArguments(prepareArgs(preferences),
            preferences.linkedInAlternativeRouteEnabled, shouldStart, onResolved = { address ->
                lastResolvedLinkedInAddress = address
                Log.i("ByeDpiProxy", "LinkedIn alternative route: ${LinkedInAlternativeRoute.ENDPOINT_HOST} → $address")
            }) ?: return 0
        if (!shouldStart()) return 0
        return jniStartProxy(args)
    }

    fun stopProxy(): Int {
        return jniStopProxy()
    }

    private fun prepareArgs(preferences: ByeDpiProxyPreferences): Array<String> =
        when (preferences) {
            is ByeDpiProxyCmdPreferences -> preferences.args
            is ByeDpiProxyUIPreferences -> preferences.uiargs
        }

    private external fun jniStartProxy(args: Array<String>): Int
    private external fun jniStopProxy(): Int
    external fun jniForceClose(): Int
}
