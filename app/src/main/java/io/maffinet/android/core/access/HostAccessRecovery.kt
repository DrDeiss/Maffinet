package io.maffinet.android.core.access

import io.maffinet.android.core.dns.DnsCatalog

data class AccessProbeEvidence(val statusCode: Int?, val bodyComplete: Boolean) {
    val acceptsRoute: Boolean get() = bodyComplete && statusCode != null && statusCode in 200..399

    // A complete client-error response proves transport availability. GeoIP denials
    // still merit another endpoint; account credentials are never part of this probe.
    val directReachable: Boolean get() = bodyComplete && statusCode != null &&
        statusCode in 200..499 && statusCode !in setOf(403, 451)
}

sealed interface HostAccessRecoveryResult {
    data object Direct : HostAccessRecoveryResult
    data class Route(val ipv4: String) : HostAccessRecoveryResult
    data object Unavailable : HostAccessRecoveryResult
    data object Superseded : HostAccessRecoveryResult
}

/** Bounded, lazy recovery shared by the runtime controller and deterministic tests. */
object HostAccessRecovery {
    fun recover(
        observation: HostObservation,
        resolvers: List<String>,
        resolve: (host: String, resolver: String?, remainingMs: Long) -> List<String>,
        probe: (host: String, port: Int, ip: String, remainingMs: Long) -> AccessProbeEvidence,
        isCurrent: () -> Boolean,
        clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
        budgetMs: Long = HostAccessPolicy.HOST_BUDGET_MS,
    ): HostAccessRecoveryResult {
        require(budgetMs > 0)
        val deadline = clockMs() + budgetMs
        fun remaining() = (deadline - clockMs()).coerceAtLeast(0)
        fun available() = isCurrent() && remaining() > 0
        fun interrupted() = if (isCurrent()) HostAccessRecoveryResult.Unavailable else HostAccessRecoveryResult.Superseded

        if (!available()) return interrupted()
        val direct = probe(observation.host, observation.port, observation.originalIp, remaining())
        if (!isCurrent()) return HostAccessRecoveryResult.Superseded
        if (direct.directReachable) return HostAccessRecoveryResult.Direct

        // A deliberately configured local resolver is allowed; returned route
        // destinations still have to be public unicast addresses.
        val publicResolvers = resolvers.filter(DnsCatalog::isUnicastIpv4).distinct().take(5)
        val sources = linkedSetOf<Pair<String, String?>>()
        // Verified endpoint hints are cheap data; they never bypass TLS verification.
        RouteHintRegistry.forHost(observation.host, observation.port).forEach { hint ->
            sources += hint.endpointHost to null
            publicResolvers.firstOrNull()?.let { sources += hint.endpointHost to it }
        }
        sources += observation.host to null
        publicResolvers.forEach { sources += observation.host to it }

        val seen = hashSetOf(observation.originalIp)
        val extraAnswers = mutableListOf<String>()
        var attempted = 0
        fun tryCandidate(ip: String): HostAccessRecoveryResult.Route? {
            if (!HostAccessPolicy.isPublicIpv4(ip) || !seen.add(ip)) return null
            if (!available() || attempted >= HostAccessPolicy.MAX_CANDIDATE_IPS) return null
            attempted++
            val result = probe(observation.host, observation.port, ip, remaining())
            return if (isCurrent() && result.acceptsRoute) HostAccessRecoveryResult.Route(ip) else null
        }

        // Try one answer per source first so a large system/CDN answer cannot
        // exhaust the entire budget before a Smart DNS provider is considered.
        for ((host, resolver) in sources) {
            if (!available()) return interrupted()
            val answers = resolve(host, resolver, remaining()).filter(HostAccessPolicy::isPublicIpv4).distinct().take(2)
            if (!isCurrent()) return HostAccessRecoveryResult.Superseded
            answers.firstOrNull()?.let { tryCandidate(it)?.let { result -> return result } }
            extraAnswers += answers.drop(1)
            if (!isCurrent()) return HostAccessRecoveryResult.Superseded
            if (attempted >= HostAccessPolicy.MAX_CANDIDATE_IPS) return HostAccessRecoveryResult.Unavailable
        }
        for (ip in extraAnswers) {
            if (!available()) return interrupted()
            tryCandidate(ip)?.let { return it }
            if (!isCurrent()) return HostAccessRecoveryResult.Superseded
            if (attempted >= HostAccessPolicy.MAX_CANDIDATE_IPS) break
        }
        return HostAccessRecoveryResult.Unavailable
    }
}
