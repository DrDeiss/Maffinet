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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import io.maffinet.android.core.strategy.TargetConnectivityResult
import io.maffinet.android.core.strategy.ServiceConnectivityResult
import io.maffinet.android.core.strategy.StrategyEvaluation
import io.maffinet.android.core.strategy.StrategyScorer
import io.maffinet.android.core.domains.LegacyStrategyAliases
import io.maffinet.android.data.strategy.ProbeTargetRepository
import io.maffinet.android.data.strategy.StrategyProbeSnapshot
import io.maffinet.android.core.strategy.HttpProbeBodyValidator
import kotlinx.coroutines.CancellationException

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
        snapshot: StrategyProbeSnapshot = ProbeTargetRepository(context).snapshot(),
    ): String? = withContext(Dispatchers.IO) {
        require(snapshot.urls.isNotEmpty()) { "Добавьте хотя бы один проверочный адрес" }
        // Every candidate uses the same host/target snapshot, independently of application routing.
        val filters = snapshot.filters
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
                    strategy, filters, "127.0.0.1", testPort.toString(), forceListener = true), snapshot.linkedInAlternativeRouteEnabled)
                val exitCode = AtomicInteger(Int.MIN_VALUE)
                val stopping = AtomicBoolean(false)
                val startupError = AtomicReference<String?>(null)
                val nativeThread = Thread({
                    try { exitCode.set(proxy.startProxy(configuration) { !stopping.get() }) }
                    catch (error: Throwable) {
                        startupError.set(error.message ?: error.javaClass.simpleName)
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
                    snapshot.urls.mapIndexed { targetIndex, url ->
                        currentCoroutineContext().ensureActive()
                        ServiceConnectivityResult("probe_$targetIndex", URL(url).host, listOf(
                            if (ready) probe(url, testPort) else TargetConnectivityResult(
                                url, false, 0, error = startupError.get() ?: "Локальный proxy не запустился (код ${exitCode.get()})"
                            )
                        ))
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        stopping.set(true)
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
                onProgress(index, strategy, "${evaluation.passedServices}/${evaluation.totalServices} адресов · $latency")
            }
            val best = StrategyScorer.bestComplete(evaluations, snapshot.urls)?.command
            currentCoroutineContext().ensureActive()
            check(snapshot.fingerprint == ProbeTargetRepository(context).snapshot().fingerprint) {
                "Hosts или проверочные адреса изменились. Повторите проверку для текущих настроек."
            }
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

    private suspend fun probe(url: String, port: Int): TargetConnectivityResult {
        val started = System.nanoTime()
        val coroutineContext = currentCoroutineContext()
        var connection: HttpURLConnection? = null
        var status: Int? = null
        return try {
            val socks = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            val request = URL(url).openConnection(socks) as HttpURLConnection
            connection = request
            request.connectTimeout = 2_500
            request.readTimeout = 2_500
            request.instanceFollowRedirects = false
            request.setRequestProperty("Connection", "close")
            // Keep Content-Length comparable with the bytes read, without transparent gzip.
            request.setRequestProperty("Accept-Encoding", "identity")
            val responseCode = request.responseCode
            status = responseCode
            coroutineContext.ensureActive()
            if (responseCode in 200..399) {
                HttpProbeBodyValidator.validate(responseCode, request.contentLengthLong,
                    openBody = { request.inputStream }, checkCancelled = { coroutineContext.ensureActive() })
            }
            TargetConnectivityResult(url, responseCode in 200..399,
                (System.nanoTime() - started) / 1_000_000, responseCode,
                if (responseCode in 200..399) null else "HTTP $responseCode")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A cancelled blocking read may first wake with its socket timeout.
            coroutineContext.ensureActive()
            TargetConnectivityResult(url, false, (System.nanoTime() - started) / 1_000_000,
                httpStatus = status,
                error = error.message ?: error.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
