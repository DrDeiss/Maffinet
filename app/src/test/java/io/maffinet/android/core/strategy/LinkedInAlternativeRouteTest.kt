package io.maffinet.android.core.strategy

import io.maffinet.android.core.domains.DomainList
import io.maffinet.android.core.dpibypass.ByeDpiArgumentCompiler
import io.maffinet.android.core.dpibypass.ByeDpiFilterConfiguration
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class LinkedInAlternativeRouteTest {
    private val address = InetAddress.getByAddress(byteArrayOf(203.toByte(), 0, 113, 7))
    private val normal = ByeDpiArgumentCompiler.compile("-o1 -a1 -r-5+se -As -s2",
        ByeDpiFilterConfiguration(listOf(DomainList("general", "General", listOf("example.com", "linkedin.com"))),
            listOf("example.com", "linkedin.com")), "127.0.0.1", "1082", forceListener = true)

    @Test fun disabledProfilePreservesArgumentsAndNeverResolvesDns() {
        assertSame(normal, LinkedInAlternativeRoute.prepareArguments(normal, false,
            lookup = { error("Disabled profile must not resolve DNS") }))
    }

    @Test fun routeIsLimitedToLinkedInTls443AndNormalGroupsArePreservedExactly() {
        val original = normal.clone()
        val args = LinkedInAlternativeRoute.prepareArguments(normal, true, lookup = { host ->
            assertEquals("gcp-lb.www.linkedin.com", host)
            arrayOf(address)
        })!!
        val separator = args.indexOf("-A")
        assertEquals(listOf("ciadpi", "-H", ":www.linkedin.com", "-K", "t", "-V", "443",
            "--group-redirect=tcp://203.0.113.7:443", "-d", "1", "-s", "1+s", "-r", "1+s", "--group-pacing=20", "-R", "1"),
            args.take(separator))
        assertEquals("n", args[separator + 1])
        assertEquals(normal.drop(1), args.drop(separator + 2))
        assertArrayEquals(original, normal)
    }

    @Test fun advancedOverridesAndListenerOptionsAreNotRecompiled() {
        val advanced = ByeDpiArgumentCompiler.compile("-H :custom.org -Kt -s5 -An -i0.0.0.0 -p12345",
            ByeDpiFilterConfiguration(emptyList(), emptyList(), hostFilterOverride = true))
        val args = LinkedInAlternativeRoute.prepareArguments(advanced, true, lookup = { arrayOf(address) })!!
        assertEquals(advanced.drop(1), args.takeLast(advanced.size - 1))
    }

    @Test fun profileKeepsPacingAndRedirectScopedAndPreservesUserGlobalFlags() {
        val userGlobal = ByeDpiArgumentCompiler.compile("-s1 -Z -W7 -Ctcp://198.51.100.1:443",
            ByeDpiFilterConfiguration(emptyList(), listOf("example.com")))
        val args = LinkedInAlternativeRoute.prepareArguments(userGlobal, true, lookup = { arrayOf(address) })!!
        val addedGroup = args.take(args.indexOf("-A"))
        assertTrue(addedGroup.contains("--group-pacing=20"))
        assertTrue(addedGroup.contains("--group-redirect=tcp://203.0.113.7:443"))
        assertFalse(addedGroup.contains("-Z"))
        assertFalse(addedGroup.contains("-W"))
        assertFalse(addedGroup.contains("-C"))
        assertEquals(userGlobal.drop(1), args.takeLast(userGlobal.size - 1))
        assertEquals("7", args[args.indexOf("-W") + 1])
        assertEquals("tcp://198.51.100.1:443", args[args.indexOf("-C") + 1])
    }

    @Test fun ipv4IsSelectedWithoutChangingTlsServerHostname() {
        val ipv6 = InetAddress.getByAddress(ByteArray(16).apply { this[15] = 1 })
        var resolved: String? = null
        val args = LinkedInAlternativeRoute.prepareArguments(normal, true,
            lookup = { arrayOf(ipv6, address) }, onResolved = { resolved = it })!!
        assertEquals("203.0.113.7", resolved)
        assertTrue(args.contains("--group-redirect=tcp://203.0.113.7:443"))
        assertEquals(1, args.count { it == ":www.linkedin.com" })
        assertFalse(args.any { it.contains("gcp-lb") })
    }

    @Test fun failedDnsAndIpv6OnlyDnsFailWithClearEndpointError() {
        val error = assertThrows(IllegalStateException::class.java) {
            LinkedInAlternativeRoute.prepareArguments(normal, true,
                lookup = { throw UnknownHostException("Network DNS failed") })
        }
        assertTrue(error.message.orEmpty().contains("gcp-lb.www.linkedin.com"))
        assertTrue(error.cause is UnknownHostException)
        assertThrows(IllegalStateException::class.java) {
            LinkedInAlternativeRoute.prepareArguments(normal, true,
                lookup = { arrayOf(InetAddress.getByAddress(ByteArray(16))) })
        }
    }

    @Test fun dnsReturningAfterStopCannotPrepareNativeStart() {
        var startRequested = true
        var reported = false
        val args = LinkedInAlternativeRoute.prepareArguments(normal, true,
            shouldStart = { startRequested }, lookup = {
                startRequested = false
                arrayOf(address)
            }, onResolved = { reported = true })
        assertNull(args)
        assertFalse(reported)
    }

    @Test fun stoppedStartupDoesNotResolveDnsEvenWithProfileEnabled() {
        assertNull(LinkedInAlternativeRoute.prepareArguments(normal, true,
            shouldStart = { false }, lookup = { error("Stopped startup") }))
    }
}
