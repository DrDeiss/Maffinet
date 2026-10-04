package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test

class DnsCheckEvidenceTest {
    private fun evidence(
        outcome: DnsProbeResolver.Outcome = DnsProbeResolver.Outcome.ANSWER,
        https: GenericHttpsProbe.Result? = null,
        rejected: List<String> = emptyList(),
    ) = DnsCheckEvidence("1.1.1.1", "example.com", DnsProbeResolver.DiagnosticResult(
        outcome, addresses = if (outcome == DnsProbeResolver.Outcome.ANSWER) listOf("93.184.215.14") else emptyList(),
        rejectedAddresses = rejected,
    ), https)

    @Test fun dnsAnswerAloneDoesNotClaimHttpsSuccess() {
        val summary = evidence().summary()
        assertTrue(summary.contains("получены IPv4"))
        assertTrue(summary.contains("доступность HTTPS не проверена"))
        assertFalse(summary.contains("сертификат и передача данных проверены"))
    }

    @Test fun nonexistentNameIsReportedWithoutAttributingBlocking() {
        val value = evidence(DnsProbeResolver.Outcome.NXDOMAIN)
        assertTrue(value.hasProblem)
        assertTrue(value.summary().contains("NXDOMAIN: имя не существует"))
        assertTrue(value.summary().contains("это не доказывает блокировку"))
    }

    @Test fun forbiddenHttpResponseIsAServiceFailureWithoutBlockingAttribution() {
        val value = evidence(https = GenericHttpsProbe.Result(403, true))
        assertTrue(value.hasProblem)
        assertTrue(value.summary().contains("HTTP 403"))
        assertTrue(value.summary().contains("отказ сервера не доказывает блокировку"))
        assertFalse(value.summary().contains("сертификат и передача данных проверены"))
    }

    @Test fun successfulStatusWithPartialBodyDoesNotClaimDataTransferSuccess() {
        val value = evidence(https = GenericHttpsProbe.Result(200, false, "Incomplete response"))
        assertTrue(value.hasProblem)
        assertTrue(value.summary().contains("TLS/передача данных не подтверждены"))
        assertTrue(value.summary().contains("HTTP 200"))
        assertFalse(value.summary().contains("сертификат и передача данных проверены"))
    }

    @Test fun internalHttpsPolicyErrorIsVisibleWithoutBlockingAttribution() {
        val value = evidence(https = GenericHttpsProbe.Result(null, false, "Invalid public HTTPS host"))
        assertTrue(value.hasProblem)
        assertTrue(value.summary().contains("причина: Invalid public HTTPS host"))
        assertFalse(value.summary().contains("блокировку"))
    }

    @Test fun validatedTlsAndBodyAreSuccessOnlyForThisResolverAndHost() {
        val value = evidence(https = GenericHttpsProbe.Result(200, true))
        assertFalse(value.hasProblem)
        assertTrue(value.summary().startsWith("1.1.1.1 → example.com:"))
        assertTrue(value.summary().contains("сертификат и передача данных проверены (HTTP 200, до 64 КиБ)"))
    }

    @Test fun rejectedAddressesRemainAProblemEvenIfPublicAnswerServesHttps() {
        val value = evidence(https = GenericHttpsProbe.Result(200, true), rejected = listOf("127.0.0.1"))
        assertTrue(value.hasProblem)
        assertTrue(value.summary().contains("непубличные адреса (возможная заглушка): 127.0.0.1"))
        assertTrue(value.summary().contains("причина не установлена"))
    }
}
