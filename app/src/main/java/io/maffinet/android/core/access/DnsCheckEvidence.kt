package io.maffinet.android.core.access

/** Evidence for one resolver and hostname; never whole-app reachability. */
data class DnsCheckEvidence(
    val resolver: String,
    val host: String,
    val dns: DnsProbeResolver.DiagnosticResult,
    val https: GenericHttpsProbe.Result? = null,
) {
    val hasProblem: Boolean get() = dns.outcome != DnsProbeResolver.Outcome.ANSWER ||
        dns.rejectedAddresses.isNotEmpty() || https?.let { !it.bodyComplete || it.statusCode !in 200..399 } == true

    fun summary(): String {
        val answer = when (dns.outcome) {
            DnsProbeResolver.Outcome.ANSWER -> if (dns.addresses.isEmpty()) "нет публичных IPv4" else "получены IPv4"
            DnsProbeResolver.Outcome.NXDOMAIN -> "NXDOMAIN: имя не существует; это не доказывает блокировку"
            DnsProbeResolver.Outcome.NODATA -> "нет A-записи; возможен IPv6-only домен"
            DnsProbeResolver.Outcome.RCODE_ERROR -> "ошибка DNS RCODE=${dns.rcode}"
            DnsProbeResolver.Outcome.MALFORMED -> "некорректный ответ DNS"
            DnsProbeResolver.Outcome.TIMEOUT -> "ответ не получен до таймаута"
            DnsProbeResolver.Outcome.TRANSPORT_ERROR -> "не удалось получить ответ DNS: ошибка соединения"
        }
        val rejected = if (dns.rejectedAddresses.isEmpty()) "" else
            "; непубличные адреса (возможная заглушка): ${dns.rejectedAddresses.joinToString()}; причина не установлена"
        val service = https?.let {
            when {
                !it.bodyComplete -> "; TLS/передача данных не подтверждены" +
                    (it.statusCode?.let { code -> " (HTTP $code)" } ?: "") +
                    (it.error?.takeIf { reason -> reason.isNotBlank() }?.let { reason -> "; причина: ${reason.take(240)}" } ?: "")
                it.statusCode in 200..399 -> "; сертификат и передача данных проверены (HTTP ${it.statusCode}, до 64 КиБ)"
                else -> "; получен HTTP ${it.statusCode}; отказ сервера не доказывает блокировку"
            }
        } ?: "; доступность HTTPS не проверена"
        return "$resolver → $host: DNS/${dns.transport}: $answer$rejected$service"
    }
}
