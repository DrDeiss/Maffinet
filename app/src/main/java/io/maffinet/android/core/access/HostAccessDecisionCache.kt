package io.maffinet.android.core.access

sealed interface HostAccessDecision {
    val expiresAtMs: Long
    data class Positive(val ipv4: String?, override val expiresAtMs: Long) : HostAccessDecision
    data class Negative(override val expiresAtMs: Long) : HostAccessDecision
}

/** Process-local evidence. Neither another physical network nor another policy can reuse it. */
class HostAccessDecisionCache(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxEntries: Int = HostAccessPolicy.MAX_CACHE_ENTRIES,
) {
    init { require(maxEntries > 0) }
    private val entries = LinkedHashMap<HostAccessKey, HostAccessDecision>(16, 0.75f, true)

    @Synchronized fun get(key: HostAccessKey): HostAccessDecision? {
        val decision = entries[key] ?: return null
        if (decision.expiresAtMs <= clockMs()) { entries.remove(key); return null }
        return decision
    }

    @Synchronized fun putPositive(key: HostAccessKey, ipv4: String?): HostAccessDecision.Positive {
        require(ipv4 == null || HostAccessPolicy.isPublicIpv4(ipv4)) { "A route needs a public IPv4" }
        return HostAccessDecision.Positive(ipv4, clockMs() + HostAccessPolicy.POSITIVE_TTL_MS).also { put(key, it) }
    }

    @Synchronized fun putNegative(key: HostAccessKey): HostAccessDecision.Negative =
        HostAccessDecision.Negative(clockMs() + HostAccessPolicy.NEGATIVE_BACKOFF_MS).also { put(key, it) }

    @Synchronized fun retainNetwork(network: AccessNetworkIdentity) {
        entries.keys.removeAll { it.network != network }
    }

    @Synchronized fun positives(network: AccessNetworkIdentity): List<Pair<HostAccessKey, HostAccessDecision.Positive>> {
        val now = clockMs()
        entries.entries.removeAll { it.value.expiresAtMs <= now }
        return entries.mapNotNull { (key, value) ->
            if (key.network == network && value is HostAccessDecision.Positive) key to value else null
        }
    }

    @Synchronized fun size(): Int = entries.size

    private fun put(key: HostAccessKey, value: HostAccessDecision) {
        entries[key] = value
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }
}
