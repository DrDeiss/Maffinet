package io.maffinet.android.core.dpibypass

class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    fun startProxy(preferences: ByeDpiProxyPreferences, shouldStart: () -> Boolean = { !Thread.currentThread().isInterrupted }): Int {
        if (!shouldStart()) return 0
        return jniStartProxy(prepareArgs(preferences))
    }

    fun stopProxy(): Int {
        return jniStopProxy()
    }

    private fun prepareArgs(preferences: ByeDpiProxyPreferences): Array<String> =
        when (preferences) {
            is ByeDpiProxyCmdPreferences -> preferences.args
            is ByeDpiProxyUIPreferences -> preferences.uiargs
            is ByeDpiAutomaticPreferences -> preferences.args
        }

    fun setAccessEpoch(epoch: Long) = jniSetAccessEpoch(epoch)
    fun updateHostRoute(epoch: Long, host: String, port: Int, ipv4: String?, ttlSeconds: Int): Boolean =
        jniUpdateHostRoute(epoch, host, port, ipv4, ttlSeconds)

    private external fun jniStartProxy(args: Array<String>): Int
    private external fun jniStopProxy(): Int
    private external fun jniSetAccessEpoch(epoch: Long)
    private external fun jniUpdateHostRoute(epoch: Long, host: String, port: Int, ipv4: String?, ttlSeconds: Int): Boolean
    external fun jniForceClose(): Int
}
