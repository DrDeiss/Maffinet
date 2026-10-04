package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test

class HostAccessDecisionCacheTest {
    private val network = AccessNetworkIdentity(10, "policy-a")
    private fun key(host: String = "service.unlisted.org", identity: AccessNetworkIdentity = network) = HostAccessKey(identity, host, 443)

    @Test fun positiveEvidenceExpiresAtTenMinutesAndDirectProofHasNoRoute() {
        var now = 0L
        val cache = HostAccessDecisionCache({ now })
        cache.putPositive(key(), "1.1.1.1")
        cache.putDirect(key("other.org"), "8.8.8.8")
        now = HostAccessPolicy.POSITIVE_TTL_MS - 1
        assertEquals("1.1.1.1", (cache.get(key()) as HostAccessDecision.Positive).ipv4)
        assertNull((cache.get(key("other.org")) as HostAccessDecision.Positive).ipv4)
        now++
        assertNull(cache.get(key()))
        assertTrue(cache.positives(network).isEmpty())
    }

    @Test fun failuresBackOffForOneMinuteAndCannotCrossPhysicalNetworkOrPolicy() {
        var now = 0L
        val cache = HostAccessDecisionCache({ now })
        cache.putNegative(key())
        now = HostAccessPolicy.NEGATIVE_BACKOFF_MS - 1
        assertTrue(cache.get(key()) is HostAccessDecision.Negative)
        assertNull(cache.get(key(identity = network.copy(networkHandle = 11))))
        assertNull(cache.get(key(identity = network.copy(policyFingerprint = "policy-b"))))
        now++
        assertNull(cache.get(key()))
        cache.putPositive(key(), "1.1.1.1")
        cache.retainNetwork(network.copy(networkHandle = 11))
        assertEquals(0, cache.size())
    }

    @Test fun boundedCacheEvictsTheLeastRecentlyUsedDecision() {
        val cache = HostAccessDecisionCache({ 0 }, maxEntries = 2)
        cache.putPositive(key("first.org"), "1.1.1.1")
        cache.putNegative(key("second.org"))
        assertNotNull(cache.get(key("first.org")))
        cache.putPositive(key("third.org"), "8.8.8.8")
        assertNull(cache.get(key("second.org")))
        assertNotNull(cache.get(key("first.org")))
        assertNotNull(cache.get(key("third.org")))
        assertEquals(2, cache.size())
    }

    @Test(expected = IllegalArgumentException::class) fun privateDnsAnswersCannotBeCachedAsRoutes() {
        HostAccessDecisionCache().putPositive(key(), "192.168.1.1")
    }

    @Test fun directSuccessDoesNotSuppressABrokenAddressOfTheSameHost() {
        val cache = HostAccessDecisionCache({ 0 })
        cache.putDirect(key(), "1.1.1.1")
        assertTrue(cache.getForObservation(key(), "1.1.1.1") is HostAccessDecision.Positive)
        assertNull(cache.getForObservation(key(), "8.8.8.8"))

        // The second address can now recover through DNS instead of being skipped.
        val result = HostAccessRecovery.recover(HostObservation(key().host, 443, "8.8.8.8"), listOf("9.9.9.9"),
            resolve = { _, _, _ -> listOf("1.1.1.1") },
            probe = { _, _, ip, _ -> AccessProbeEvidence(200, ip == "1.1.1.1") }, isCurrent = { true })
        assertEquals(HostAccessRecoveryResult.Route("1.1.1.1"), result)
        cache.putPositive(key(), (result as HostAccessRecoveryResult.Route).ipv4)
        assertEquals("1.1.1.1", (cache.getForObservation(key(), "8.8.8.8") as HostAccessDecision.Positive).ipv4)
    }

    @Test fun ANewDirectAddressDoesNotRenewOlderProofAndSleepCountsTowardExpiry() {
        var elapsed = 0L
        val cache = HostAccessDecisionCache({ elapsed })
        cache.putDirect(key(), "1.1.1.1")
        elapsed = 120_000
        cache.putDirect(key(), "8.8.8.8")
        elapsed = HostAccessPolicy.POSITIVE_TTL_MS
        assertNull(cache.getForObservation(key(), "1.1.1.1"))
        assertNotNull(cache.getForObservation(key(), "8.8.8.8"))
        // The injected clock advances during sleep; no observation is needed.
        elapsed += 120_000
        assertNull(cache.getForObservation(key(), "8.8.8.8"))
        assertEquals(0, cache.size())
    }

    @Test fun directProofsAreBoundedAndAnIpv6ProofCannotSuppressAnIpv4Failure() {
        var now = 0L
        val cache = HostAccessDecisionCache({ now })
        cache.putDirect(key(), "2606:4700:4700::1111")
        assertNull(cache.getForObservation(key(), "1.1.1.1"))
        for (last in 1..HostAccessPolicy.MAX_CANDIDATE_IPS) {
            now++
            cache.putDirect(key(), "11.0.0.$last")
        }
        assertNull(cache.getForObservation(key(), "2606:4700:4700::1111"))
        val decision = cache.get(key()) as HostAccessDecision.Positive
        assertEquals(HostAccessPolicy.MAX_CANDIDATE_IPS, decision.directProofs.size)
        assertNotNull(cache.getForObservation(key(), "11.0.0.1"))
    }
}
