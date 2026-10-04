package io.maffinet.android.core.access

data class RouteHint(val host: String, val port: Int, val endpointHost: String)

/** Hints supply DNS names, never fixed IPs or an exemption from normal TLS verification. */
object RouteHintRegistry {
    const val VERSION = "public-route-hints-v1"
    private val hints = listOf(RouteHint("www.linkedin.com", 443, "gcp-lb.www.linkedin.com"))

    fun forHost(host: String, port: Int): List<RouteHint> {
        val normalized = HostAccessPolicy.normalizePublicHost(host) ?: return emptyList()
        return hints.filter { it.host == normalized && it.port == port }
    }
}
