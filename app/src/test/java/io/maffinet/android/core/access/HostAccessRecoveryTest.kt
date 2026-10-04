package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test

class HostAccessRecoveryTest {
    private val unknown = HostObservation("service.unlisted.org", 443, "1.1.1.1")
    private val good = AccessProbeEvidence(200, true)
    private val stalled = AccessProbeEvidence(200, false)

    @Test fun unknownHostRecoversFromSmartDnsOnlyAfterBodyAndTlsProof() {
        val queries = mutableListOf<Pair<String, String?>>()
        val attempts = mutableListOf<Triple<String, Int, String>>()
        val result = HostAccessRecovery.recover(unknown, listOf("193.233.112.67"),
            resolve = { host, dns, _ ->
                queries += host to dns
                if (dns == null) listOf("1.1.1.1", "8.8.8.8") else listOf("192.168.1.1", "9.9.9.9")
            }, probe = { host, port, ip, _ ->
                attempts += Triple(host, port, ip)
                if (ip == "9.9.9.9") good else stalled
            }, isCurrent = { true })
        assertTrue(RouteHintRegistry.forHost(unknown.host, unknown.port).isEmpty())
        assertEquals(HostAccessRecoveryResult.Route("9.9.9.9"), result)
        assertEquals(listOf(unknown.host to null, unknown.host to "193.233.112.67"), queries)
        assertEquals(listOf(Triple(unknown.host, 443, "1.1.1.1"), Triple(unknown.host, 443, "9.9.9.9")), attempts)
    }

    @Test fun availableDirectAndClientErrorBodiesDoNotTriggerResolverTraffic() {
        for (status in listOf(200, 204, 302, 401, 404, 429)) {
            val result = HostAccessRecovery.recover(unknown, emptyList(),
                resolve = { _, _, _ -> fail("direct $status must not query DNS"); emptyList() },
                probe = { _, _, _, _ -> AccessProbeEvidence(status, true) }, isCurrent = { true })
            assertEquals(HostAccessRecoveryResult.Direct, result)
        }
    }

    @Test fun geoDenialRequiresAnImprovementAndCannotInstallAnotherDenial() {
        for (status in listOf(403, 451)) {
            var count = 0
            val result = HostAccessRecovery.recover(unknown, emptyList(),
                resolve = { _, _, _ -> listOf("8.8.8.8", "9.9.9.9") },
                probe = { _, _, _, _ -> AccessProbeEvidence(if (++count < 3) status else 302, true) },
                isCurrent = { true })
            assertEquals(HostAccessRecoveryResult.Route("9.9.9.9"), result)
            assertEquals(3, count)
        }
    }

    @Test fun epochChangeDuringAProbeDiscardsEvenAPositiveCandidate() {
        var current = true
        var count = 0
        val result = HostAccessRecovery.recover(unknown, emptyList(),
            resolve = { _, _, _ -> listOf("8.8.8.8") }, probe = { _, _, _, _ ->
                if (++count == 1) stalled else good.also { current = false }
            }, isCurrent = { current })
        assertEquals(HostAccessRecoveryResult.Superseded, result)
        assertEquals(2, count)
    }

    @Test fun deadlineAndEightCandidateBoundStopFurtherProbes() {
        var now = 0L
        var count = 0
        val timed = HostAccessRecovery.recover(unknown, listOf("8.8.8.8"),
            resolve = { _, _, _ -> listOf("9.9.9.9") }, probe = { _, _, _, remaining ->
                assertTrue(remaining > 0)
                count++; now += 15_000; stalled
            }, isCurrent = { true }, clockMs = { now })
        assertEquals(HostAccessRecoveryResult.Unavailable, timed)
        assertEquals(2, count)
        count = 0
        var query = 0
        val bounded = HostAccessRecovery.recover(unknown, listOf("8.8.8.8", "9.9.9.9", "193.233.112.67", "111.88.96.54", "83.220.169.155"),
            resolve = { _, _, _ -> query++; listOf("11.0.0.$query", "12.0.0.$query") },
            probe = { _, _, _, _ -> count++; stalled }, isCurrent = { true })
        assertEquals(HostAccessRecoveryResult.Unavailable, bounded)
        assertEquals(1 + HostAccessPolicy.MAX_CANDIDATE_IPS, count)
    }

    @Test fun knownHintUsesOriginalHostnameForTlsAndRemainsPortScoped() {
        val queried = mutableListOf<String>()
        val result = HostAccessRecovery.recover(HostObservation("www.linkedin.com", 443, "1.1.1.1"), emptyList(),
            resolve = { host, _, _ -> queried += host; listOf("130.211.32.14") },
            probe = { host, port, ip, _ ->
                assertEquals("www.linkedin.com", host)
                assertEquals(443, port)
                if (ip == "1.1.1.1") stalled else good
            }, isCurrent = { true })
        assertEquals(listOf("gcp-lb.www.linkedin.com"), queried)
        assertEquals(HostAccessRecoveryResult.Route("130.211.32.14"), result)
    }
}
