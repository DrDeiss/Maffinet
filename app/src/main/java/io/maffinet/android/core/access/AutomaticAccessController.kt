package io.maffinet.android.core.access

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import io.maffinet.android.core.debug.AppDebugManager as Log
import io.maffinet.android.core.dns.DnsCatalog
import io.maffinet.android.core.dns.DnsConfiguration
import io.maffinet.android.core.dpibypass.ByeDpiProxy
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Learns from public TLS hosts actually observed in the selected applications.
 * Initial observations trigger a bounded public GET: encrypted response stalls
 * cannot be diagnosed by native handshake detection. Application payloads and
 * credentials are never replayed. Network monitoring continues while work is idle.
 */
object AutomaticAccessController {
    private const val TAG = "AutomaticAccess"
    private const val MONITOR_INTERVAL_MS = 500L
    private val lock = Any()
    private val lifecycle = AccessSessionLifecycle()
    private val cache = HostAccessDecisionCache(clockMs = { monotonicMs() })
    private val workerDispatcher by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "Maffinet-AutomaticAccess").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }
    private val mutableStatus = MutableStateFlow(AutomaticAccessStatus())
    val status: StateFlow<AutomaticAccessStatus> = mutableStatus.asStateFlow()
    val currentEpoch: Long get() = lifecycle.currentEpoch
    private var connection: ConnectionRun? = null
    private var active: Session? = null

    private data class NetworkSnapshot(
        val network: Network,
        val identity: AccessNetworkIdentity,
        val systemResolvers: List<String>,
        val resolvers: List<String>,
        val dns: DnsConfiguration,
    )

    /** Lives until proxy STOP. Losing a physical network only replaces its Session. */
    private class ConnectionRun(
        val owner: Long,
        val connectivity: ConnectivityManager,
        val preferences: SharedPreferences,
        val proxy: ByeDpiProxy,
        val listenerIp: String,
        val listenerPort: Int,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // TLS factory initialization is outside the native callback lock.
        val probe = GenericHttpsProbe()
    }

    private class Session(val epoch: Long, val run: ConnectionRun, val snapshot: NetworkSnapshot) {
        val scope = CoroutineScope(SupervisorJob() + workerDispatcher)
        val queue = HostAccessWorkQueue()
        val signal = Channel<Unit>(Channel.CONFLATED)
        val resolver = DnsProbeResolver(
            bindUdp = { snapshot.network.bindSocket(it) },
            bindTcp = { snapshot.network.bindSocket(it) },
        )
        var nextNetworkCheckMs = 0L // Only the single recovery worker accesses this.
        var dnsChecks: List<DnsCheckEvidence> = emptyList()
        var checkingDns = false
    }

    /** Reserve before JNI startup. Old workers/monitors cannot own this proxy epoch. */
    fun prepareStart(): Long = synchronized(lock) {
        clearOwnedNativeRoutesLocked()
        cancelConnectionLocked()
        lifecycle.reserve().also { mutableStatus.value = AutomaticAccessStatus() }
    }

    /** Starts only the reserved epoch, after SOCKS is ready and before TUN startup. */
    fun start(context: Context, proxy: ByeDpiProxy, listenerIp: String, listenerPort: Int): Long {
        require(listenerPort in 1..65535)
        val epoch = lifecycle.reservedEpoch() ?: return currentEpoch
        val app = context.applicationContext
        val preferences = app.getSharedPreferences(app.packageName + "_preferences", Context.MODE_PRIVATE)
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val run = ConnectionRun(epoch, connectivity, preferences, proxy, listenerIp, listenerPort)
        // The private diagnostic marker is unavailable to remote SOCKS clients.
        val supportedListener = listenerIp in setOf("127.0.0.1", "::1")
        val initial = if (supportedListener) snapshot(connectivity, preferences) else null
        synchronized(lock) {
            if (!lifecycle.claim(epoch)) { run.scope.cancel(); return epoch }
            cancelConnectionLocked()
            if (!supportedListener) {
                run.scope.cancel()
                proxy.setAccessEpoch(0)
                mutableStatus.value = AutomaticAccessStatus(AutomaticAccessPhase.UNRESOLVED)
                Log.w(TAG, "Automatic checks need a loopback SOCKS listener")
                return epoch
            }
            connection = run
            installSessionLocked(run, initial, epoch)
            run.scope.launch { monitor(run) }
        }
        return epoch
    }

    /** Synchronous invalidation precedes native cleanup. Evidence retains its network scope. */
    fun stop() = synchronized(lock) {
        clearOwnedNativeRoutesLocked()
        lifecycle.stop()
        cancelConnectionLocked()
        mutableStatus.value = AutomaticAccessStatus()
    }

    /** Native callback: normalization, bounded enqueue and a nonblocking wake-up only. */
    @JvmStatic
    fun onNativeHostObserved(epoch: Long, host: String, originalIp: String, port: Int) {
        if (epoch != currentEpoch || port != 443) return
        val observation = HostAccessPolicy.observation(host, port, originalIp) ?: return
        synchronized(lock) {
            val session = active?.takeIf { it.epoch == epoch && it.scope.isActive } ?: return
            val key = HostAccessKey(session.snapshot.identity, observation.host, observation.port)
            val decision = cache.getForObservation(key, observation.originalIp)
            if (decision is HostAccessDecision.Negative || decision is HostAccessDecision.Positive && decision.ipv4 == null) return
            // Cached routes are reinserted if native's smaller table evicted them.
            if (!session.queue.offer(observation)) return
            session.signal.trySend(Unit)
            mutableStatus.value = mutableStatus.value.copy(queuedHosts = session.queue.size())
        }
    }

    /** Independent of the probe queue: also sees same-handle DNS/LinkProperties changes. */
    private suspend fun monitor(run: ConnectionRun) {
        while (run.scope.isActive) {
            delay(MONITOR_INTERVAL_MS)
            val fresh = try { snapshot(run.connectivity, run.preferences) }
            catch (error: Exception) { Log.w(TAG, "Physical network snapshot unavailable", error); null }
            synchronized(lock) {
                if (!isRunActive(run)) return
                val session = active
                if ((fresh == null && session == null) ||
                    (fresh != null && session?.scope?.isActive == true && session.snapshot.identity == fresh.identity)) {
                    return@synchronized
                }
                val epoch = lifecycle.nextEpoch(run.owner) ?: return
                installSessionLocked(run, fresh, epoch)
            }
        }
    }

    /** Caller holds lock after a fresh snapshot read; these are bounded memory/JNI writes. */
    private fun installSessionLocked(run: ConnectionRun, snapshot: NetworkSnapshot?, epoch: Long) {
        cancelSessionLocked()
        run.proxy.setAccessEpoch(if (snapshot == null) 0 else epoch)
        if (snapshot == null) {
            mutableStatus.value = AutomaticAccessStatus(AutomaticAccessPhase.UNRESOLVED)
            return // The connection monitor stays alive and retries without a VPN restart.
        }
        cache.retainNetwork(snapshot.identity)
        val session = Session(epoch, run, snapshot)
        active = session
        // Same-network validated hints are restored before returning to TUN startup.
        for ((key, decision) in cache.positives(snapshot.identity)) {
            if (!isSessionActive(session)) break
            val remaining = (decision.expiresAtMs - monotonicMs()) / 1_000
            if (decision.ipv4 != null && remaining > 0) {
                run.proxy.updateHostRoute(epoch, key.host, key.port, decision.ipv4, remaining.toInt())
            }
        }
        publishLocked(session, AutomaticAccessPhase.OBSERVING)
        session.scope.launch { runWorker(session) }
    }

    private suspend fun runWorker(session: Session) {
        try {
            checkConfiguredDns(session)
            for (ignored in session.signal) {
                while (true) {
                    val observation = session.queue.poll() ?: break
                    try { process(session, observation) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        checkCurrent(session)
                        synchronized(lock) {
                            if (isSessionActive(session)) {
                                cache.putNegative(HostAccessKey(session.snapshot.identity, observation.host, observation.port))
                                publishLocked(session, AutomaticAccessPhase.UNRESOLVED, observation.host)
                            }
                        }
                        Log.w(TAG, "Public host check failed for ${observation.host}", error)
                    } finally {
                        session.queue.complete(observation)
                        synchronized(lock) {
                            if (active === session) mutableStatus.value = mutableStatus.value.copy(queuedHosts = session.queue.size())
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            invalidateSession(session)
        } catch (error: Exception) {
            Log.w(TAG, "Automatic worker stopped", error)
            invalidateSession(session)
        }
    }

    /** A small control check also runs without TLS observations, after every configuration change.
     * It never installs a route and does not prove that a selected application uses this DNS. */
    private fun checkConfiguredDns(session: Session) {
        val configured = session.snapshot.dns.savedAddresses
        val assigned = session.snapshot.dns.vpnAssignedAddresses.orEmpty()
        val resolvers = (configured + assigned).distinct().ifEmpty { session.snapshot.systemResolvers.take(2) }
        synchronized(lock) {
            if (!isSessionActive(session)) return
            session.checkingDns = true
            publishLocked(session, AutomaticAccessPhase.OBSERVING)
        }
        for (resolver in resolvers.take(4)) {
            checkCurrent(session)
            val dns = session.resolver.diagnose(DnsControlProbe.HOST, resolver,
                checkCancelled = { checkSessionActive(session) })
            checkCurrent(session)
            val https = dns.addresses.firstOrNull()?.let { ip ->
                session.run.probe.probe(DnsControlProbe.HOST, 443, ip,
                    localSocksPort = session.run.listenerPort, timeoutMs = 3_000,
                    checkCancelled = { checkSessionActive(session) }, localSocksIp = session.run.listenerIp)
            }
            checkCurrent(session)
            synchronized(lock) {
                if (!isSessionActive(session)) return
                session.dnsChecks = session.dnsChecks + DnsCheckEvidence(resolver, DnsControlProbe.HOST, dns, https)
                publishLocked(session, AutomaticAccessPhase.OBSERVING)
            }
        }
        synchronized(lock) {
            if (isSessionActive(session)) {
                session.checkingDns = false
                publishLocked(session, AutomaticAccessPhase.OBSERVING)
            }
        }
    }

    /** Old worker cancellation can never clear a replacement session/native epoch. */
    private fun invalidateSession(session: Session) = synchronized(lock) {
        if (active === session && isRunActive(session.run) &&
            lifecycle.isCurrent(session.run.owner, session.epoch)) {
            lifecycle.nextEpoch(session.run.owner)
            session.run.proxy.setAccessEpoch(0)
            cancelSessionLocked()
            mutableStatus.value = AutomaticAccessStatus(AutomaticAccessPhase.UNRESOLVED)
            // The owning monitor installs a fresh session even if the same network returns.
        }
    }

    private fun process(session: Session, observation: HostObservation) {
        checkCurrent(session)
        val key = HostAccessKey(session.snapshot.identity, observation.host, observation.port)
        when (val cached = cache.getForObservation(key, observation.originalIp)) {
            is HostAccessDecision.Positive -> {
                if (cached.ipv4 != null && updateRoute(session, key, cached.ipv4, cached.expiresAtMs)) {
                    synchronized(lock) { if (active === session) publishLocked(session, AutomaticAccessPhase.ROUTE_APPLIED, observation.host) }
                }
                return
            }
            is HostAccessDecision.Negative -> return
            null -> Unit
        }
        synchronized(lock) { if (active === session) publishLocked(session, AutomaticAccessPhase.CHECKING, observation.host) }
        val result = HostAccessRecovery.recover(
            observation = observation,
            resolvers = session.snapshot.resolvers,
            resolve = { host, resolver, remaining ->
                val sources = resolver?.let(::listOf) ?: session.snapshot.systemResolvers.take(2)
                val deadline = monotonicMs() + remaining.coerceAtMost(2_000)
                val answers = linkedSetOf<String>()
                for (dns in sources) {
                    checkCurrent(session)
                    val available = deadline - monotonicMs()
                    if (available <= 0) break
                    val evidence = session.resolver.diagnose(host, dns, timeoutMs = available,
                        checkCancelled = { checkSessionActive(session) })
                    checkCurrent(session)
                    answers += evidence.addresses
                    if (dns in session.snapshot.dns.savedAddresses || dns in session.snapshot.dns.vpnAssignedAddresses.orEmpty()) {
                        synchronized(lock) {
                            if (isSessionActive(session)) {
                                session.dnsChecks = (session.dnsChecks.filterNot { it.resolver == dns } +
                                    DnsCheckEvidence(dns, host, evidence)).takeLast(4)
                                publishLocked(session, AutomaticAccessPhase.CHECKING, observation.host)
                            }
                        }
                    }
                    if (answers.isNotEmpty()) break
                }
                answers.toList()
            },
            probe = { host, port, ip, remaining ->
                val evidence = session.run.probe.probe(host, port, ip,
                    localSocksPort = session.run.listenerPort, timeoutMs = remaining.coerceAtMost(5_000),
                    checkCancelled = { checkSessionActive(session) }, localSocksIp = session.run.listenerIp)
                checkCurrent(session)
                synchronized(lock) {
                    if (isSessionActive(session)) {
                        session.dnsChecks = session.dnsChecks.map { check ->
                            if (check.host == host && ip in check.dns.addresses) check.copy(https = evidence) else check
                        }
                        publishLocked(session, AutomaticAccessPhase.CHECKING, host)
                    }
                }
                AccessProbeEvidence(evidence.statusCode, evidence.bodyComplete)
            },
            isCurrent = { isCurrent(session) },
            clockMs = { monotonicMs() },
        )
        checkCurrent(session)
        when (result) {
            HostAccessRecoveryResult.Direct -> synchronized(lock) {
                if (!isSessionActive(session)) return@synchronized
                if (session.run.proxy.updateHostRoute(session.epoch, observation.host, observation.port, null, 0)) {
                    cache.putDirect(key, observation.originalIp)
                    publishLocked(session, AutomaticAccessPhase.OBSERVING)
                }
            }
            is HostAccessRecoveryResult.Route -> {
                val expiry = monotonicMs() + HostAccessPolicy.POSITIVE_TTL_MS
                if (updateRoute(session, key, result.ipv4, expiry) && isCurrent(session)) synchronized(lock) {
                    if (isSessionActive(session)) {
                        cache.putPositive(key, result.ipv4)
                        publishLocked(session, AutomaticAccessPhase.ROUTE_APPLIED, observation.host)
                        Log.i(TAG, "Validated new-connection route ${observation.host}:${observation.port} -> ${result.ipv4}")
                    }
                }
            }
            HostAccessRecoveryResult.Unavailable -> synchronized(lock) {
                if (isSessionActive(session)) {
                    cache.putNegative(key)
                    publishLocked(session, AutomaticAccessPhase.UNRESOLVED, observation.host)
                }
            }
            HostAccessRecoveryResult.Superseded -> Unit
        }
    }

    private fun updateRoute(session: Session, key: HostAccessKey, ipv4: String, expiresAtMs: Long): Boolean {
        if (!isCurrent(session)) return false
        return synchronized(lock) {
            if (!isSessionActive(session)) return false
            val remaining = (expiresAtMs - monotonicMs()).coerceAtLeast(0)
            if (remaining < 1_000) return false
            session.run.proxy.updateHostRoute(session.epoch, key.host, key.port, ipv4, (remaining / 1_000).toInt())
        }
    }

    private fun checkCurrent(session: Session) {
        if (!isCurrent(session)) throw CancellationException("Automatic Access session changed")
    }

    private fun checkSessionActive(session: Session) {
        if (!isSessionActive(session)) throw CancellationException("Automatic Access session stopped")
        val now = monotonicMs()
        if (now >= session.nextNetworkCheckMs) {
            session.nextNetworkCheckMs = now + 250
            if (snapshot(session.run.connectivity, session.run.preferences)?.identity != session.snapshot.identity) {
                throw CancellationException("Automatic Access network or policy changed")
            }
        }
    }

    private fun isRunActive(run: ConnectionRun): Boolean =
        connection === run && run.scope.isActive && lifecycle.isOwner(run.owner)

    // Socket parsers use cheap checks; Binder reads stay outside the callback lock.
    private fun isSessionActive(session: Session): Boolean {
        if (!lifecycle.isCurrent(session.run.owner, session.epoch) || !session.scope.isActive) return false
        synchronized(lock) { return active === session && isRunActive(session.run) }
    }

    private fun isCurrent(session: Session): Boolean = isSessionActive(session) &&
        snapshot(session.run.connectivity, session.run.preferences)?.identity == session.snapshot.identity

    private fun snapshot(connectivity: ConnectivityManager, preferences: SharedPreferences): NetworkSnapshot? {
        if (!preferences.getBoolean(MaffinetSettingsRepository.AUTOMATIC_ACCESS, true)) return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        val properties = connectivity.getLinkProperties(network) ?: return null
        val systemResolvers = properties.dnsServers.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
            .filter(DnsCatalog::isUnicastIpv4)
        val dns = DnsConfigurationMonitor.capture(preferences, properties, network)
        val smartResolvers = listOf("geohide", "xbox", "comss").mapNotNull { id ->
            DnsCatalog.presets.firstOrNull { it.id == id }?.ipv4?.firstOrNull()
        }
        // Try a distinct provider before a selected provider's second address.
        val resolvers = (dns.savedAddresses.take(1) + smartResolvers + dns.savedAddresses.drop(1).take(1)).distinct().take(5)
        val policy = listOf(HostAccessPolicy.VERSION, RouteHintRegistry.VERSION,
            AutomaticAccessArguments.POLICY_VERSION, properties.interfaceName.orEmpty(),
            properties.linkAddresses.joinToString(","), properties.routes.joinToString(",")) + dns.fingerprint
        val digest = MessageDigest.getInstance("SHA-256")
        policy.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8)); digest.update(bytes)
        }
        val identity = AccessNetworkIdentity(network.networkHandle, digest.digest().joinToString("") { "%02x".format(it) })
        return NetworkSnapshot(network, identity, systemResolvers, resolvers, dns)
    }

    private fun publishLocked(session: Session, phase: AutomaticAccessPhase, host: String? = null) {
        mutableStatus.value = AutomaticAccessStatus(phase, host, session.queue.size(),
            cache.positives(session.snapshot.identity).count { it.second.ipv4 != null },
            session.snapshot.dns, session.dnsChecks, session.checkingDns)
    }

    private fun cancelSessionLocked() {
        active?.let { it.scope.cancel(); it.signal.close(); it.queue.clear() }
        active = null
    }

    private fun cancelConnectionLocked() {
        connection?.scope?.cancel()
        connection = null
        cancelSessionLocked()
    }

    private fun clearOwnedNativeRoutesLocked() {
        connection?.takeIf { lifecycle.isOwner(it.owner) }?.proxy?.setAccessEpoch(0)
    }

    private fun monotonicMs(): Long = SystemClock.elapsedRealtime()
}
