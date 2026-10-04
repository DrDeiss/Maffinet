package io.maffinet.android.core.access

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class DnsControlProbeTest {
    @Test fun controlHostPassesProductionHostAndProbeValidation() {
        assertEquals(DnsControlProbe.HOST, HostAccessPolicy.normalizePublicHost(DnsControlProbe.HOST))
        val stopBeforeNetwork = CancellationException("Validated; stop before creating a socket")
        val thrown = assertThrows(CancellationException::class.java) {
            GenericHttpsProbe().probe(DnsControlProbe.HOST, 443, "8.8.8.8",
                checkCancelled = { throw stopBeforeNetwork })
        }
        assertSame(stopBeforeNetwork, thrown)
    }

    @Test fun documentationDomainStillFailsBeforeNetwork() {
        val result = GenericHttpsProbe().probe("example.com", 443, "8.8.8.8",
            checkCancelled = { fail("Rejected host must not reach network work") })
        assertEquals("Invalid public HTTPS host", result.error)
        assertFalse(result.bodyComplete)
    }
}
