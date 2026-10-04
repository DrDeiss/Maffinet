package io.maffinet.android.core.access

import io.maffinet.android.core.domains.DomainParser
import java.net.InetAddress

object HostAccessPolicy {
    const val VERSION = "auto-access-v1"
    const val MAX_PENDING_HOSTS = 16
    const val MAX_CACHE_ENTRIES = 256
    const val MAX_CANDIDATE_IPS = 8
    const val HOST_BUDGET_MS = 25_000L
    const val POSITIVE_TTL_MS = 10 * 60 * 1_000L
    const val NEGATIVE_BACKOFF_MS = 60 * 1_000L

    private val privateSuffixes = setOf("local", "localhost", "lan", "home", "internal", "invalid", "test", "example", "onion", "arpa")

    /** Accept DNS-shaped names only; an observation must separately have a public original IP. */
    fun normalizePublicHost(raw: String): String? {
        val host = runCatching { DomainParser.normalize(raw) }.getOrNull() ?: return null
        val labels = host.split('.')
        if (labels.size < 2 || labels.last() in privateSuffixes) return null
        if (!labels.last().matches(Regex("[a-z]{2,63}|xn--[a-z0-9-]{2,59}"))) return null
        if (host in setOf("example.com", "example.net", "example.org") ||
            listOf("example.com", "example.net", "example.org").any { host.endsWith(".$it") }) return null
        return host
    }

    fun isPublicIpv4(value: String): Boolean {
        val octets = value.split('.').takeIf { it.size == 4 }?.map { part ->
            if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' } ||
                (part.length > 1 && part.startsWith('0'))) return false
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        } ?: return false
        val (a, b, c) = octets
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false
        if (a == 100 && b in 64..127 || a == 169 && b == 254 || a == 172 && b in 16..31) return false
        if (a == 192 && (b == 168 || b == 0 && c in setOf(0, 2))) return false
        if (a == 198 && (b in 18..19 || b == 51 && c == 100)) return false
        if (a == 203 && b == 0 && c == 113) return false
        return true
    }

    fun isPublicOriginalIp(value: String): Boolean {
        if (isPublicIpv4(value)) return true
        // A colon and this character restriction make this a literal IPv6 parse, never DNS.
        if (':' !in value || value.any { it !in "0123456789abcdefABCDEF:" }) return false
        val address = runCatching { InetAddress.getByName(value) }.getOrNull() ?: return false
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size != 16) return false
        // Only global-unicast space. This also excludes mapped IPv4 and discard-only ranges.
        if ((bytes[0].toInt() and 0xe0) != 0x20) return false
        if (bytes.take(4).map { it.toInt() and 255 } == listOf(0x20, 0x01, 0x0d, 0xb8)) return false
        return true
    }

    fun observation(rawHost: String, port: Int, originalIp: String): HostObservation? {
        val host = normalizePublicHost(rawHost) ?: return null
        if (port !in 1..65535 || !isPublicOriginalIp(originalIp)) return null
        return HostObservation(host, port, originalIp)
    }
}

data class HostObservation(val host: String, val port: Int, val originalIp: String)
data class AccessNetworkIdentity(val networkHandle: Long, val policyFingerprint: String)
data class HostAccessKey(val network: AccessNetworkIdentity, val host: String, val port: Int)
