package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test

class RouteHintRegistryTest {
    @Test fun hintsAreExactPublicDnsNamesWithNoWildcardOrAddressMapping() {
        val hint = RouteHintRegistry.forHost("WWW.LinkedIn.COM.", 443).single()
        assertEquals("gcp-lb.www.linkedin.com", hint.endpointHost)
        assertNotNull(HostAccessPolicy.normalizePublicHost(hint.endpointHost))
        assertTrue(RouteHintRegistry.forHost("api.linkedin.com", 443).isEmpty())
        assertTrue(RouteHintRegistry.forHost("www.linkedin.com", 80).isEmpty())
    }

    @Test fun hostsOutsideTheRegistryRemainEligibleForAutomaticRecovery() {
        assertTrue(RouteHintRegistry.forHost("service.unlisted.org", 443).isEmpty())
        assertNotNull(HostAccessPolicy.observation("service.unlisted.org", 443, "1.1.1.1"))
    }
}
