package io.maffinet.android.core.strategy

import io.maffinet.android.core.domains.DomainList
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

data class ProbeTargetParseResult(val urls: List<String>, val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty() && urls.isNotEmpty()
}

/** Explicit HTTP/TLS targets. Service switches and installed applications never supply targets. */
object ProbeTargetParser {
    val defaults = listOf("https://www.youtube.com", "https://www.instagram.com", "https://www.linkedin.com")
    const val MAX_TARGETS = 20

    fun parse(text: String): ProbeTargetParseResult {
        val urls = linkedSetOf<String>()
        val errors = mutableListOf<String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val value = raw.trim()
            if (value.isEmpty() || value.startsWith("#")) return@forEachIndexed
            val normalized = try {
                val uri = URI(value)
                require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https"))
                require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null)
                require(uri.port == -1 || uri.port in 1..65535)
                require(uri.host.length <= 253 && uri.host.none { it.isWhitespace() })
                val host = uri.host.lowercase(Locale.ROOT)
                val authority = host + if (uri.port == -1) "" else ":${uri.port}"
                URI("${uri.scheme.lowercase(Locale.ROOT)}://$authority${uri.rawPath.orEmpty()}" +
                    (uri.rawQuery?.let { "?$it" } ?: "")).toASCIIString()
            } catch (_: Exception) { null }
            if (normalized == null) errors.add("Строка ${index + 1}: нужен адрес http:// или https:// без логина и #fragment")
            else urls.add(normalized)
        }
        if (urls.isEmpty()) errors.add("Добавьте хотя бы один проверочный адрес")
        if (urls.size > MAX_TARGETS) errors.add("Допустимо не более $MAX_TARGETS адресов")
        return ProbeTargetParseResult(urls.toList(), errors)
    }
}

/** Hash the inputs actually used by the candidate compiler/probes, rather than application routing. */
object ProbeConfigurationFingerprint {
    fun create(
        urls: List<String>, lists: List<DomainList>, activeDomains: List<String>,
        hostFilterOverride: Boolean, advancedHostsMode: String = "disable", advancedHosts: String = "",
        candidates: List<String> = DefaultStrategyCatalog.commands,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        // StrategyTester compiles these manual candidates in either application
        // mode. Automatic Access has its own independent policy/session cache.
        add("maffinet-http-probes-v4-manual-body64k")
        urls.forEach(::add)
        add("lists")
        lists.sortedBy { it.id }.forEach { list ->
            add(list.id); add(list.name); add(list.isActive.toString())
            list.domains.sorted().forEach(::add)
            add("end-list")
        }
        add("active")
        activeDomains.sorted().forEach(::add)
        add(hostFilterOverride.toString())
        add(advancedHostsMode); add(advancedHosts)
        add("candidates")
        candidates.forEach(::add)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
