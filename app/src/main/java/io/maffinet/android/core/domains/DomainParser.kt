package io.maffinet.android.core.domains

import java.net.IDN
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
