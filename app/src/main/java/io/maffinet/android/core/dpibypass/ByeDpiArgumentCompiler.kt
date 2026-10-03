package io.maffinet.android.core.dpibypass

import io.maffinet.android.core.domains.DomainList
import io.maffinet.android.core.domains.DomainParser
import io.maffinet.android.core.domains.LegacyStrategyAliases

data class ByeDpiFilterConfiguration(
    val lists: List<DomainList>,
    val activeDomains: List<String>,
    val hostFilterOverride: Boolean = false,
)

/** Compiles options structurally: host filtering belongs to each native -A group. */
object ByeDpiArgumentCompiler {
    private data class Option(val code: Char, val value: String? = null) {
        fun arguments(): List<String> = if (value == null) listOf("-$code") else listOf("-$code", value)
    }

    private val longOptions = mapOf(
        "no-domain" to 'N', "no-ipv6" to 'X', "no-udp" to 'U', "help" to 'h', "version" to 'v',
        "ip" to 'i', "port" to 'p', "transparent" to 'E', "conn-ip" to 'I', "buf-size" to 'b',
        "max-conn" to 'c', "debug" to 'x', "tfo" to 'F', "auto" to 'A', "auto-mode" to 'L',
        "cache-ttl" to 'u', "timeout" to 'T', "copy" to 'B', "cache-file" to 'y', "proto" to 'K',
        "hosts" to 'H', "pf" to 'V', "round" to 'R', "split" to 's', "disorder" to 'd',
        "oob" to 'o', "disoob" to 'q', "fake" to 'f', "md5sig" to 'S', "fake-sni" to 'n',
        "ttl" to 't', "fake-data" to 'l', "fake-offset" to 'O', "fake-tls-mod" to 'Q',
        "oob-data" to 'e', "mod-http" to 'M', "tlsrec" to 'r', "tlsminor" to 'm',
        "udp-fake" to 'a', "def-ttl" to 'g', "wait-send" to 'Z', "await-int" to 'W',
        "drop-sack" to 'Y', "protect-path" to 'P', "ipset" to 'j', "connect-to" to 'C',
        "comment" to '#', "cache-merge" to '/',
    )
    private val noValueOptions = setOf('N', 'X', 'U', 'h', 'v', 'E', 'F', 'S', 'Z', 'Y')

    fun compile(
        command: String,
        configuration: ByeDpiFilterConfiguration,
        ip: String = "127.0.0.1",
        port: String = "1080",
        forceListener: Boolean = false,
    ): Array<String> = compileTokens(CommandLineTokenizer.split(command), configuration, ip, port, forceListener)

    fun compileTokens(
        arguments: List<String>,
        configuration: ByeDpiFilterConfiguration,
        ip: String = "127.0.0.1",
        port: String = "1080",
        forceListener: Boolean = false,
    ): Array<String> {
        val tokens = if (arguments.firstOrNull() == "ciadpi") arguments.drop(1) else arguments
        var options = parseOptions(tokens).filter { it.code !in setOf('h', 'v') }
            .flatMap { expand(it, configuration) }
        if (forceListener) options = options.filter { it.code != 'i' && it.code != 'p' }
        val prefix = mutableListOf<Option>()
        if (options.none { it.code == 'i' }) prefix += Option('i', ip)
        if (options.none { it.code == 'p' }) prefix += Option('p', port)
        (prefix + options).filter { it.code == 'i' }.forEach {
            require(!it.value.isNullOrBlank() && it.value.none(Char::isWhitespace)) { "Invalid proxy address" }
        }
        (prefix + options).filter { it.code == 'p' }.forEach {
            require(it.value?.toIntOrNull() in 1..65535) { "Proxy port must be between 1 and 65535" }
        }

        val compiled = if (configuration.hostFilterOverride) {
            prefix + options
        } else {
            val domains = configuration.activeDomains.map(DomainParser::normalize).distinct()
            require(domains.isNotEmpty()) { "Enable a service or add user domains before connecting" }
            require(options.none { it.code == 'H' }) {
                "Custom hosts commands require Advanced → Override service domain filtering"
            }
            require(options.none { it.code == 'B' }) {
                "Native group copying requires the advanced host filtering override"
            }
            selectivelyFilter(prefix + options, domains)
        }
        return (listOf("ciadpi") + compiled.flatMap(Option::arguments)).toTypedArray()
    }

    private fun selectivelyFilter(options: List<Option>, domains: List<String>): List<Option> {
        val result = mutableListOf<Option>()
        val group = mutableListOf<Option>()
        fun appendGroup() {
            // udp_hook in this pinned engine ignores hosts. Constrain every desync group
            // to TCP; UDP is forwarded unchanged through the final no-desync fallback.
            val protocols = group.filter { it.code == 'K' }.flatMap { it.value.orEmpty().split(',') }
                .map { it.firstOrNull() }.toSet()
            result += Option('H', ":" + domains.joinToString(" "))
            if ('t' !in protocols && 'h' !in protocols) result += Option('K', "t,h")
            // UDP-only groups retain -Ku: adding TCP makes them unreachable for either
            // transport, rather than reinterpreting UDP options as a TCP strategy.
            result += group
            group.clear()
        }
        options.forEach { option ->
            if (option.code == 'A') {
                appendGroup()
                result += option
            } else group += option
        }
        appendGroup()
        result += Option('A', "n") // Unfiltered/no-desync group for other hosts and UDP.
        return result
    }

    private fun expand(option: Option, configuration: ByeDpiFilterConfiguration): List<Option> {
        val original = option.value ?: return listOf(option)
        val hasDomains = original.contains("{domains}") || original.contains("{list:")
        var value = LegacyStrategyAliases.expand(original)
        fun selectedDomains(name: String): List<String> {
            val list = configuration.lists.firstOrNull { it.id.equals(name, true) || it.name.equals(name, true) }
                ?: throw IllegalArgumentException("Unknown domain list: $name")
            val domains = list.domains.map(DomainParser::normalize).distinct()
            require(domains.isNotEmpty()) { "Domain list '$name' is empty" }
            return domains
        }
        val separator = if (option.code == 'n') "," else " "
        value = Regex("\\{list:([^}]+)\\}").replace(value) { match ->
            selectedDomains(match.groupValues[1].trim()).joinToString(separator)
        }
        if (value.contains("{domains}")) {
            require(configuration.activeDomains.isNotEmpty()) { "The active domain list is empty" }
            value = value.replace("{domains}", configuration.activeDomains.map(DomainParser::normalize).distinct().joinToString(separator))
        }
        require(!value.contains(Regex("\\{(?:list:|domains|sni)"))) { "Invalid domain placeholder" }
        if (hasDomains) {
            require(option.code == 'H' || option.code == 'n') { "Domain placeholders require --hosts or --fake-sni" }
            if (option.code == 'H') value = ":" + value.removePrefix(":")
            if (option.code == 'n') {
                // Unlike the old {sni} alias, use actual repeated -n flags for new lists.
                return value.split(',').map { Option('n', DomainParser.normalize(it)) }
            }
        }
        if (option.code == 'H' && value.startsWith(':')) {
            val parsed = DomainParser.parse(value.drop(1))
            require(parsed.isValid && parsed.domains.isNotEmpty()) { "Hosts filter must contain valid domains" }
            value = ":" + parsed.domains.joinToString(" ")
        }
        return listOf(option.copy(value = value))
    }

    private fun parseOptions(tokens: List<String>): List<Option> {
        val result = mutableListOf<Option>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index++]
            val code: Char
            var value: String?
            if (token.startsWith("--")) {
                val parts = token.drop(2).split('=', limit = 2)
                code = longOptions[parts[0]] ?: throw IllegalArgumentException("Unknown or abbreviated option: $token")
                value = parts.getOrNull(1)
            } else {
                require(token.length >= 2 && token[0] == '-' && token[1] in longOptions.values) { "Invalid option: $token" }
                code = token[1]
                value = token.drop(2).takeIf(String::isNotEmpty)
            }
            if (code in noValueOptions) {
                require(value == null) { "Combined flag options are not supported: $token" }
            } else if (value == null) {
                require(index < tokens.size) { "Missing value for $token" }
                value = tokens[index++]
            }
            result += Option(code, value)
        }
        return result
    }
}

/** Split first, then expand placeholders, keeping a host-list value as one native argument. */
object CommandLineTokenizer {
    fun split(command: CharSequence): List<String> {
        val tokens = mutableListOf<String>()
        var quote: Char? = null
        var current = StringBuilder()
        var hasToken = false
        var index = 0
        while (index < command.length) {
            val char = command[index++]
            when {
                quote != null && char == '\\' && index < command.length && command[index] == quote -> {
                    current.append(command[index++])
                    hasToken = true
                }
                quote != null && char == quote -> quote = null
                quote == null && (char == '\'' || char == '"') -> { quote = char; hasToken = true }
                quote == null && char.isWhitespace() -> {
                    if (hasToken) { tokens += current.toString(); current = StringBuilder(); hasToken = false }
                }
                else -> { current.append(char); hasToken = true }
            }
        }
        require(quote == null) { "Unclosed quote in strategy command" }
        if (hasToken) tokens += current.toString()
        return tokens
    }
}
