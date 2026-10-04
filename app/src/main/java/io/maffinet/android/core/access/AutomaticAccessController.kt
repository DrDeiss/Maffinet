package io.maffinet.android.core.access

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.maffinet.android.core.debug.AppDebugManager as Log
import io.maffinet.android.core.dns.DnsCatalog
import io.maffinet.android.core.dpibypass.ByeDpiProxy
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Learns from public TLS hosts actually observed in the selected applications.
 * Initial observations trigger a bounded public GET, because the native engine
 * can see handshake failures but cannot diagnose a stalled encrypted response.
 * No original application request, credentials or TLS session is replayed.
 */
object AutomaticAccessController {
    private const val TAG = "AutomaticAccess"
    private val lock = Any()
    private val epochs = AtomicLong()
    private val cache = HostAccessDecisionCache()
    private val workerDispatcher by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "Maffinet-AutomaticAccess").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }
    private val mutableStatus = MutableStateFlow(AutomaticAccessStatus())
    val status: StateFlow<AutomaticAccessStatus> = mutableStatus.asStateFlow()
    val currentEpoch: Long get() = epochs.get()
    private var active: Session? = null
    private var preparedEpoch: Long? = null

    private data class NetworkSnapshot(
        val network: Network,
        val identity: AccessNetworkIdentity,
        val systemResolvers: List<String>,
        val resolvers: List<String>,
    )

    private class Session(
        val epoch: Long,
        val connectivity: ConnectivityManager,
        val preferences: SharedPreferences,
        val snapshot: NetworkSnapshot,
        val proxy: ByeDpiProxy,
        val listenerIp: String,
        val listenerPort: Int,
    ) {
        val scope = CoroutineScope(SupervisorJob() + workerDispatcher)
        val queue = HostAccessWorkQueue()
        val signal = Channel<Unit>(Channel.CONFLATED)
        val resolver = DnsProbeResolver(
            bindUdp = { snapshot.network.bindSocket(it) },
            bindTcp = { snapshot.network.bindSocket(it) },
        )
        val probe = GenericHttpsProbe()
        var nextNetworkCheckMs = 0L // Read/write only on the single recovery worker.
    }

    /** Reserve before JNI startup; old workers can never write into this new epoch. */
    fun prepareStart(): Long = synchronized(lock) {
        cancelSessionLocked()
        epochs.incrementAndGet().also {
            preparedEpoch = it
            mutableStatus.value = AutomaticAccessStatus()
        }
    }

    /** Starts only the epoch already reserved by prepareStart, after SOCKS is ready. */
    fun start(context: Context, proxy: ByeDpiProxy, listenerIp: String, listenerPort: Int): Long {
        require(listenerPort in 1..65535)
        val epoch = synchronized(lock) { preparedEpoch } ?: return currentEpoch
        val app = context.applicationContext
        val preferences = app.getSharedPreferences(app.packageName + "_preferences", Context.MODE_PRIVATE)
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkSnapshot = snapshot(connectivity, preferences)
        // The diagnostic marker is intentionally unavailable to remote SOCKS clients.
        val loopback = listenerIp in setOf("127.0.0.1", "::1")
        val session = if (networkSnapshot != null && loopback) {
            Session(epoch, connectivity, preferences, networkSnapshot, proxy, listenerIp, listenerPort)
        } else null
        // TLS factory initialization and Binder reads happen outside the callback lock.
        val networkStillCurrent = session != null && snapshot(connectivity, preferences)?.identity == session.snapshot.identity
        synchronized(lock) {
            if (epoch != currentEpoch || preparedEpoch != epoch) {
                session?.scope?.cancel()
                return epoch
            }
            preparedEpoch = null
            cancelSessionLocked()
            if (session == null || !networkStillCurrent) {
                session?.scope?.cancel()
                mutableStatus.value = AutomaticAccessStatus(AutomaticAccessPhase.UNRESOLVED)
                Log.w(TAG, "Automatic checks unavailable: no physical network or loopback listener")
                return epoch
            }
            cache.retainNetwork(session.snapshot.identity)
            active = session
            // Pure, bounded JNI memory writes complete before returning to TUN
            // startup; no DNS or public HTTP warm-up is performed for unused hosts.
            for ((key, decision) in cache.positives(session.snapshot.identity)) {
                if (!isSessionActive(session)) break
                val remaining = (decision.expiresAtMs - monotonicMs()) / 1_000
                if (decision.ipv4 != null && remaining > 0) {
                    session.proxy.updateHostRoute(epoch, key.host, key.port, decision.ipv4, remaining.toInt())
                }
            }
            publishLocked(session, AutomaticAccessPhase.OBSERVING)
            session.scope.launch { runWorker(session) }
        }
        return epoch
    }

    /** Invalidate synchronously before closing native resources; cache retains its network scope. */
    fun stop() = synchronized(lock) {
        epochs.incrementAndGet()
        preparedEpoch = null
        cancelSessionLocked()
        mutableStatus.value = AutomaticAccessStatus()
    }

    /** Native callback: only normalization, bounded enqueue and a nonblocking wake-up. */
    @JvmStatic
    fun onNativeHostObserved(epoch: Long, host: String, originalIp: String, port: Int) {
        if (epoch != currentEpoch || port != 443) return
        val observation = HostAccessPolicy.observation(host, port, originalIp) ?: return
        synchronized(lock) {
            val session = active?.takeIf { it.epoch == epoch && it.scope.isActive } ?: return
            val key = HostAccessKey(session.snapshot.identity, observation.host, observation.port)
            // Direct proof/backoff stay cheap. A cached route is reinserted by the
            // worker if native's smaller bounded table evicted it; no probe is needed.
            val decision = cache.get(key)
            if (decision is HostAccessDecision.Negative || decision is HostAccessDecision.Positive && decision.ipv4 == null) return
            if (!session.queue.offer(observation)) return
            session.signal.trySend(Unit)
            mutableStatus.value = mutableStatus.value.copy(queuedHosts = session.queue.size())
        }
    }

    private suspend fun runWorker(session: Session) {
        try {
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
                    }
                    finally {
                        session.queue.complete(observation)
                        synchronized(lock) {
                            if (active === session) mutableStatus.value = mutableStatus.value.copy(queuedHosts = session.queue.size())
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // Session invalidation is expected on STOP, policy/network changes and restart.
            synchronized(lock) {
                if (active === session && session.epoch == currentEpoch) {
                    // A changed network/policy must also invalidate already learned
                    // native routes. Ordinary STOP has already removed active here.
                    session.proxy.setAccessEpoch(0)
                    cancelSessionLocked()
                    mutableStatus.value = AutomaticAccessStatus(AutomaticAccessPhase.UNRESOLVED)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Automatic worker stopped", error)
            synchronized(lock) {
                if (active === session) publishLocked(session, AutomaticAccessPhase.UNRESOLVED)
            }
        }
    }

    private fun process(session: Session, observation: HostObservation) {
        checkCurrent(session)
        val key = HostAccessKey(session.snapshot.identity, observation.host, observation.port)
        when (val cached = cache.get(key)) {
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
                    try {
                        answers += session.resolver.lookup(host, dns, timeoutMs = available, checkCancelled = { checkSessionActive(session) })
                    } catch (_: IOException) {
                        // Resolver timeouts/bad replies are failed candidates, not a dead worker.
                    }
                    if (answers.isNotEmpty()) break
                }
                answers.toList()
            },
            probe = { host, port, ip, remaining ->
                val result = session.probe.probe(host, port, ip,
                    localSocksPort = session.listenerPort, timeoutMs = remaining.coerceAtMost(5_000),
                    checkCancelled = { checkSessionActive(session) }, localSocksIp = session.listenerIp)
                AccessProbeEvidence(result.statusCode, result.bodyComplete)
            },
            isCurrent = { isCurrent(session) },
        )
        checkCurrent(session)
        when (result) {
            HostAccessRecoveryResult.Direct -> synchronized(lock) {
                if (!isSessionActive(session)) return@synchronized
                if (session.proxy.updateHostRoute(session.epoch, observation.host, observation.port, null, 0)) {
                    cache.putPositive(key, null)
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
            session.proxy.updateHostRoute(session.epoch, key.host, key.port, ipv4, (remaining / 1_000).toInt())
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
            if (snapshot(session.connectivity, session.preferences)?.identity != session.snapshot.identity) {
                throw CancellationException("Automatic Access network or policy changed")
            }
        }
    }

    // Socket parsers call this frequently: no Binder/network queries per received byte.
    private fun isSessionActive(session: Session): Boolean {
        if (session.epoch != currentEpoch || !session.scope.isActive) return false
        synchronized(lock) { if (active !== session) return false }
        return true
    }

    private fun isCurrent(session: Session): Boolean {
        if (!isSessionActive(session)) return false
        return snapshot(session.connectivity, session.preferences)?.identity == session.snapshot.identity
    }

    private fun snapshot(connectivity: ConnectivityManager, preferences: SharedPreferences): NetworkSnapshot? {
        if (!preferences.getBoolean(MaffinetSettingsRepository.AUTOMATIC_ACCESS, true)) return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        // Maffinet's own UID is excluded from the application VPN. Never bind DNS
        // to a tunnel that could recursively feed these probes back into itself.
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        val properties = connectivity.getLinkProperties(network) ?: return null
        val systemResolvers = properties.dnsServers.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
            .filter(DnsCatalog::isUnicastIpv4)
        val selected = DnsCatalog.resolve(preferences.getString("custom_dns_preset", DnsCatalog.SYSTEM_ID))
        val smartResolvers = listOf("geohide", "xbox", "comss").mapNotNull { id ->
            DnsCatalog.presets.firstOrNull { it.id == id }?.ipv4?.firstOrNull()
        }
        val resolvers = (selected.ipv4.take(2) + smartResolvers).distinct().take(5)
        val policy = listOf(HostAccessPolicy.VERSION, RouteHintRegistry.VERSION,
            AutomaticAccessArguments.POLICY_VERSION, selected.id, selected.ipv4.joinToString(","),
            systemResolvers.joinToString(","), properties.interfaceName.orEmpty(),
            properties.linkAddresses.joinToString(","),
            if (Build.VERSION.SDK_INT >= 28) "${properties.isPrivateDnsActive}:${properties.privateDnsServerName}" else "")
        val digest = MessageDigest.getInstance("SHA-256")
        policy.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8)); digest.update(bytes)
        }
        val identity = AccessNetworkIdentity(network.networkHandle, digest.digest().joinToString("") { "%02x".format(it) })
        return NetworkSnapshot(network, identity, systemResolvers, resolvers)
    }

    private fun publishLocked(session: Session, phase: AutomaticAccessPhase, host: String? = null) {
        mutableStatus.value = AutomaticAccessStatus(phase, host, session.queue.size(),
            cache.positives(session.snapshot.identity).count { it.second.ipv4 != null })
    }

    private fun cancelSessionLocked() {
        active?.let { session ->
            session.scope.cancel()
            session.signal.close()
            session.queue.clear()
        }
        active = null
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000
}
