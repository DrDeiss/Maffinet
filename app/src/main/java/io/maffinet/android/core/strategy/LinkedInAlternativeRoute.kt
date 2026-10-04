package io.maffinet.android.core.strategy

import java.net.Inet4Address
import java.net.InetAddress

/** An optional route for one HTTPS host; application selection and normal groups stay intact. */
object LinkedInAlternativeRoute {
    const val TARGET_HOST = "www.linkedin.com"
    const val ENDPOINT_HOST = "gcp-lb.www.linkedin.com"
    const val POLICY_VERSION = "linkedin-gcp-v3-group-route"

    fun prepareArguments(
        normalArguments: Array<String>,
        enabled: Boolean,
        shouldStart: () -> Boolean = { true },
        lookup: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
        onResolved: (String) -> Unit = {},
    ): Array<String>? {
        if (!shouldStart()) return null
        if (!enabled) return normalArguments
        require(normalArguments.firstOrNull() == "ciadpi") { "Invalid ByeDPI arguments" }
        val address = try {
            lookup(ENDPOINT_HOST).filterIsInstance<Inet4Address>().firstOrNull()
                ?: error("DNS не вернул IPv4")
        } catch (error: Exception) {
            if (!shouldStart()) return null
            throw IllegalStateException("Не удалось определить IPv4 альтернативного маршрута LinkedIn ($ENDPOINT_HOST)", error)
        }
        // DNS can finish after STOP. Never initialize JNI for a cancelled startup.
        if (!shouldStart()) return null
        val numericAddress = address.hostAddress ?: error("Нет IPv4 альтернативного маршрута LinkedIn")
        onResolved(numericAddress)
        return (listOf("ciadpi", "-H", ":$TARGET_HOST", "-K", "t", "-V", "443",
            "--group-redirect=tcp://$numericAddress:443", "-d", "1", "-s", "1+s", "-r", "1+s",
            "--group-pacing=20", "-R", "1", "-A", "n") + normalArguments.drop(1)).toTypedArray()
    }
}
