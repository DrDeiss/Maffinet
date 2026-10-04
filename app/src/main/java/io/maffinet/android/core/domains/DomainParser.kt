package io.maffinet.android.core.domains

import java.net.IDN
import java.net.InetAddress
import java.util.Locale

object DomainParser {
    /** Accept plain host names, whitespace/comma separated lists, and # comments. */
    fun parse(text: String): DomainParseResult {
        val domains = linkedSetOf<String>()
        val errors = mutableListOf<DomainParseError>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, line ->
            line.substringBefore('#').trim().split(Regex("[\\s,]+"))
                .filter(String::isNotBlank).forEach { input ->
                    try {
                        domains += normalize(input)
                    } catch (error: IllegalArgumentException) {
                        errors += DomainParseError(index + 1, input, error.message ?: "Invalid domain")
                    }
                }
        }
        return DomainParseResult(domains.toList(), errors)
    }

    /** Import plain lists or hosts files as a DPI domain filter, without applying IP mappings. */
    fun parseImport(text: String): DomainParseResult {
        val domains = linkedSetOf<String>()
        val errors = mutableListOf<DomainParseError>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, line ->
            val tokens = line.substringBefore('#').trim().split(Regex("[\\s,]+"))
                .filter(String::isNotBlank)
            if (tokens.isEmpty()) return@forEachIndexed
            val address = ipAddress(tokens.first())
            val hostsEntry = address != null
            // Block/ad entries and local-network hosts are not DPI bypass destinations.
            if (address != null && (address.isAnyLocalAddress || address.isLoopbackAddress ||
                    address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress ||
                    (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc) ||
                    address.address.all { (it.toInt() and 0xff) == 255 })) return@forEachIndexed
            if (hostsEntry && tokens.size == 1) {
                errors += DomainParseError(index + 1, tokens.first(), "An IP address needs a domain name")
            }
            (if (hostsEntry) tokens.drop(1) else tokens).forEach { input ->
                if (isLocalHostname(input, hostsEntry)) return@forEach
                try {
                    domains += normalize(input)
                } catch (error: IllegalArgumentException) {
                    errors += DomainParseError(index + 1, input, error.message ?: "Invalid domain")
                }
            }
        }
        return DomainParseResult(domains.toList(), errors)
    }

    private fun isLocalHostname(input: String, hostsEntry: Boolean): Boolean {
        val host = runCatching {
            IDN.toASCII(input.removeSuffix("."), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        }.getOrNull() ?: return false
        if (host.length > 253 || !host.split('.').all {
                it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
            }) return false
        return host == "localhost" || host == "localdomain" ||
            host.endsWith(".localhost") || host.endsWith(".localdomain") || host.endsWith(".local") ||
            (hostsEntry && '.' !in host)
    }

    private fun ipAddress(input: String): InetAddress? {
        val ipv4 = input.split('.')
        if (ipv4.size == 4 && ipv4.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
                    part.toInt() in 0..255
            }) return InetAddress.getByName(input)
        // Only literal IPv6 characters reach InetAddress; this never performs a hostname lookup.
        if (':' !in input || !input.matches(Regex("[0-9a-fA-F:.]+"))) return null
        return runCatching { InetAddress.getByName(input) }.getOrNull()
    }

    fun normalize(input: String): String {
        val host = input.trim().removeSuffix(".")
        require(host.isNotEmpty() && host.none { it in ":/\\*?@[]{}'\"" || it.isWhitespace() }) {
            "Use a domain name without a URL, port, wildcard or path"
        }
        val ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        require(ascii.length <= 253) { "Domain is too long" }
        val labels = ascii.split('.')
        require(labels.size >= 2 && labels.all {
            it.length in 1..63 && it.first() != '-' && it.last() != '-' &&
                it.all { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' }
        } && labels.last().any { it in 'a'..'z' }) { "Invalid domain name" }
        return ascii
    }

    fun merge(lists: Iterable<DomainList>): List<String> = lists.asSequence()
        .filter { it.isActive && !it.isDeleted }
        .flatMap { it.domains.asSequence() }
        .map(::normalize)
        .distinct()
        .toList()
}
