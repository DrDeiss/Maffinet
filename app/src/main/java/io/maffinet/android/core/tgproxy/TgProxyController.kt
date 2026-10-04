package io.maffinet.android.core.tgproxy

import android.content.Context
import io.maffinet.android.core.debug.AppDebugManager as Log
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import io.maffinet.android.core.connection.ModeConnectionState
import kotlinx.coroutines.sync.withLock

object TgProxyController {
    const val DEFAULT_PORT = 1443
    const val DEFAULT_BIND_IP = "127.0.0.1"

    private fun generateSecret(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getSharedPrefs(context: Context) =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    fun getOrGenerateSecret(context: Context): String {
        val prefs = getSharedPrefs(context)
        var secret = prefs.getString("secret_key", "") ?: ""
        if (secret.isEmpty()) {
            secret = generateSecret()
            prefs.edit().putString("secret_key", secret).apply()
        }
        return secret
    }

    fun regenerateSecret(context: Context): String {
        val secret = generateSecret()
        getSharedPrefs(context).edit().putString("secret_key", secret).apply()
        return secret
    }

    fun isAutostartEnabled(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean("autostart", false)
    }

    fun setAutostartEnabled(context: Context, enabled: Boolean) {
        getSharedPrefs(context).edit().putBoolean("autostart", enabled).apply()
    }

    fun getDcIps(context: Context): String {
        return getSharedPrefs(context).getString("tgproxy_dc_ips", "") ?: ""
    }

    fun setDcIps(context: Context, value: String) {
        getSharedPrefs(context).edit().putString("tgproxy_dc_ips", value).apply()
    }

    fun getPort(context: Context): Int {
        return getSharedPrefs(context).getInt("tgproxy_port", DEFAULT_PORT)
    }

    fun setPort(context: Context, port: Int) {
        getSharedPrefs(context).edit().putInt("tgproxy_port", port).apply()
    }

    fun getPoolSize(context: Context): Int {
        return getSharedPrefs(context).getInt("tgproxy_pool_size", 4)
    }

    fun setPoolSize(context: Context, size: Int) {
        getSharedPrefs(context).edit().putInt("tgproxy_pool_size", size).apply()
    }

    fun isCfEnabled(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean("tgproxy_cf_enabled", true)
    }

    fun setCfEnabled(context: Context, enabled: Boolean) {
        getSharedPrefs(context).edit().putBoolean("tgproxy_cf_enabled", enabled).apply()
    }

    fun isCfPriority(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean("tgproxy_cf_priority", true)
    }

    fun setCfPriority(context: Context, priority: Boolean) {
        getSharedPrefs(context).edit().putBoolean("tgproxy_cf_priority", priority).apply()
    }

    fun getTgProxyUrl(bindIp: String, port: Int, secret: String): String {
        return "tg://proxy?server=$bindIp&port=$port&secret=dd$secret"
    }

    fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun measureLatency(host: String, port: Int): Int {
        val startTime = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 500)
                (System.currentTimeMillis() - startTime).toInt()
            }
        } catch (e: Exception) {
            -1
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val operations = Mutex()
    private val state = MutableStateFlow(ModeConnectionState.Stopped)
    private val lifecycle = ProxyLifecycleState { state.value = it }
    val status: StateFlow<ModeConnectionState> = state.asStateFlow()
    @Volatile private var nativeStarted = false
    @Volatile private var boundPort = DEFAULT_PORT
    val hasResources: Boolean get() = nativeStarted

    internal fun markStartRequested() { lifecycle.beginStart() }

    fun startAsync(context: Context, onSuccess: () -> Unit, onError: () -> Unit) {
        val token = lifecycle.beginStart()
        val app = context.applicationContext
        scope.launch {
            operations.withLock {
                if (!lifecycle.isCurrent(token)) return@withLock
                try {
                    check(stopNative()) { "Previous Telegram proxy did not stop" }
                    boundPort = getPort(app)
                    check(!isPortOpen(DEFAULT_BIND_IP, boundPort, 100)) { "Telegram proxy port is already occupied" }
                    NativeProxy.setPoolSize(getPoolSize(app))
                    NativeProxy.setCfProxyCacheDir(app.cacheDir.absolutePath)
                    NativeProxy.setCfProxyConfig(isCfEnabled(app), isCfPriority(app), "")
                    // Rust StartProxy returns after binding, then owns asynchronous Tokio
                    // tasks. A Java worker exiting is therefore not a proxy failure.
                    nativeStarted = true
                    val code = NativeProxy.startProxy(DEFAULT_BIND_IP, boundPort, getDcIps(app), getOrGenerateSecret(app), 1)
                    check(code == 0) { "Telegram proxy failed to bind (code $code)" }
                    if (!lifecycle.isCurrent(token)) {
                        stopNative()
                        return@withLock
                    }
                    check(isPortOpen(DEFAULT_BIND_IP, boundPort, 500)) { "Telegram proxy listener did not become ready" }
                    if (!lifecycle.publishIfCurrent(token, ModeConnectionState.Running)) {
                        stopNative()
                        return@withLock
                    }
                    onSuccess()
                    monitorListener(token, onError)
                } catch (error: Throwable) {
                    Log.e("TgProxyController", "Telegram proxy startup failed", error)
                    stopNative()
                    if (lifecycle.publishIfCurrent(token, ModeConnectionState.Failed)) {
                        onError()
                    }
                }
            }
        }
    }

    private fun monitorListener(token: Long, onError: () -> Unit) {
        scope.launch {
            while (lifecycle.isCurrent(token) && nativeStarted) {
                delay(1_000)
                if (!lifecycle.isCurrent(token)) return@launch
                if (!isPortOpen(DEFAULT_BIND_IP, boundPort, 500)) {
                    operations.withLock {
                        if (!lifecycle.isCurrent(token)) return@withLock
                        stopNative()
                        if (lifecycle.publishIfCurrent(token, ModeConnectionState.Failed)) onError()
                    }
                    return@launch
                }
            }
        }
    }

    fun stop(preserveFailure: Boolean = false, onStopped: () -> Unit = {}) {
        // Allocate ownership and publish Stopping together: an older caller must not
        // overwrite a newer STOP's already-completed state after being preempted.
        val request = lifecycle.beginStop(preserveFailure, nativeStarted)
        val token = request.generation
        scope.launch {
            operations.withLock {
                if (!lifecycle.isCurrent(token)) return@withLock
                val clean = stopNative()
                val terminal = if (clean && !request.keepFailure) ModeConnectionState.Stopped else ModeConnectionState.Failed
                if (lifecycle.publishIfCurrent(token, terminal)) {
                    onStopped()
                }
            }
        }
    }

    private fun stopNative(): Boolean {
        if (!nativeStarted) return true
        try { NativeProxy.stopProxy() }
        catch (error: Throwable) { Log.w("TgProxyController", "Telegram StopProxy failed", error) }
        repeat(30) {
            if (!isPortOpen(DEFAULT_BIND_IP, boundPort, 100)) {
                nativeStarted = false
                return true
            }
            Thread.sleep(50)
        }
        return false // Keep ownership and the settings lock if native cleanup failed.
    }
}
