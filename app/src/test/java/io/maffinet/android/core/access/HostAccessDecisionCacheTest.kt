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
        cache.putPositive(key("other.org"), null)
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
}
