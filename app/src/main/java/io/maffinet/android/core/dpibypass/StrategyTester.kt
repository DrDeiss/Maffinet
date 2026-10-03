package io.maffinet.android.core.dpibypass

import android.content.Context
import io.maffinet.android.core.debug.AppDebugManager as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.core.strategy.TargetConnectivityResult
import io.maffinet.android.core.strategy.ServiceConnectivityResult
import io.maffinet.android.core.strategy.StrategyEvaluation
import io.maffinet.android.core.strategy.StrategyScorer
import io.maffinet.android.core.domains.LegacyStrategyAliases
import io.maffinet.android.data.domains.DomainListRepository

class StrategyTester(private val context: Context) {
    companion object {
        @Deprecated("Use LegacyStrategyAliases.SNI for imported strategies only")
        const val YOUTUBE_SNI_DOMAINS = LegacyStrategyAliases.SNI

        val defaultStrategies: List<String> = io.maffinet.android.core.strategy.DefaultStrategyCatalog.commands
    }

    suspend fun runTests(
        onProgress: (Int, String, String) -> Unit,
        onEvaluation: (StrategyEvaluation) -> Unit = {},
        excludedCommands: Set<String> = emptySet(),
        onBestReady: suspend (String?) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val selected = MaffinetSettingsRepository(context).enabledServiceIds()
        val profiles = ServiceCatalog.enabledProfiles(selected)
        require(profiles.isNotEmpty()) { "Выберите хотя бы один сервис для проверки" }
        val lists = DomainListRepository(context).getLists()
        val filters = ByeDpiFilterConfiguration(lists, lists.first { it.id == "general" }.domains,
            MaffinetSettingsRepository(context).hostFilterOverride())
        // A run uses one selection snapshot even if Services is edited while it runs.
        val session = ServiceManager.beginStrategyTest(context)
        val testPort = 1082
        val evaluations = mutableListOf<StrategyEvaluation>()
        var nativeClean = true
        try {
            ServiceManager.stopForStrategyTest(context)
            check(ServiceManager.awaitVpnStopped()) { "Не удалось остановить VPN перед проверкой" }
            check(!isPortOpen(testPort)) { "Порт проверки занят; закройте другой локальный proxy" }
            for ((index, strategy) in defaultStrategies.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (strategy in excludedCommands) continue
                val proxy = ByeDpiProxy()
                val configuration = ByeDpiProxyCmdPreferences(ByeDpiArgumentCompiler.compile(
                    strategy, filters, "127.0.0.1", testPort.toString(), forceListener = true))
                val exitCode = AtomicInteger(Int.MIN_VALUE)
                val nativeThread = Thread({
                    try { exitCode.set(proxy.startProxy(configuration)) }
                    catch (error: Throwable) {
                        Log.e("StrategyTester", "Native candidate failed", error)
                        exitCode.set(-1)
                    }
                }, "maffinet-strategy-$index").apply { isDaemon = true }
                nativeThread.start()
                nativeClean = false
                val results = try {
                    var ready = false
                    for (attempt in 0 until 30) {
                        currentCoroutineContext().ensureActive()
                        if (!nativeThread.isAlive) break
                        ready = isPortOpen(testPort) && nativeThread.isAlive
                        if (ready) break
                        delay(50)
                    }
                    profiles.map { profile ->
                        currentCoroutineContext().ensureActive()
                        ServiceConnectivityResult(profile.id, profile.name, profile.testUrls.map { url ->
                            currentCoroutineContext().ensureActive()
                            if (ready) probe(url, testPort) else TargetConnectivityResult(
                                url, false, 0, error = "Локальный proxy не запустился (код ${exitCode.get()})"
                            )
                        })
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        try { proxy.stopProxy() }
                        catch (error: Throwable) { Log.e("StrategyTester", "Candidate stop failed", error) }
                        nativeThread.join(2_000)
                        if (nativeThread.isAlive) {
                            try { proxy.jniForceClose() }
                            catch (error: Throwable) { Log.e("StrategyTester", "Candidate force-close failed", error) }
                            nativeThread.join(1_000)
                        }
                        nativeClean = !nativeThread.isAlive
                        check(nativeClean) { "Native proxy не завершился; проверка остановлена" }
                    }
                }
                val evaluation = StrategyEvaluation(index, strategy, results)
                evaluations.add(evaluation)
                onEvaluation(evaluation)
                val latency = evaluation.averageLatencyMs?.let { "$it мс" } ?: "нет соединения"
                onProgress(index, strategy, "${evaluation.passedServices}/${evaluation.totalServices} сервисов · $latency")
            }
            val best = StrategyScorer.best(evaluations)?.command
            currentCoroutineContext().ensureActive()
            onBestReady(best) // Apply the chosen command before restoring the VPN.
            best
        } finally {
            withContext(NonCancellable) {
                ServiceManager.finishStrategyTest(context, session, nativeClean)
            }
        }
    }

    private fun isPortOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 100) }
        true
    } catch (_: Exception) { false }

    private fun probe(url: String, port: Int): TargetConnectivityResult {
        val started = System.nanoTime()
        var connection: HttpURLConnection? = null
        return try {
            val socks = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            connection = URL(url).openConnection(socks) as HttpURLConnection
            connection.connectTimeout = 2_500
            connection.readTimeout = 2_500
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Connection", "close")
            val status = connection.responseCode
            TargetConnectivityResult(url, status in 200..399,
                (System.nanoTime() - started) / 1_000_000, status,
                if (status in 200..399) null else "HTTP $status")
        } catch (error: Exception) {
            TargetConnectivityResult(url, false, (System.nanoTime() - started) / 1_000_000,
                error = error.message ?: error.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
