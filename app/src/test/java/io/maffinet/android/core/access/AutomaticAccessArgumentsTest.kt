package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test

class AutomaticAccessArgumentsTest {
    @Test fun unknownHostsAreNotRestrictedByGeneralAndUdpKeepsForwarding() {
        val args = AutomaticAccessArguments.create("127.0.0.1", 1080)
        assertTrue(args.contains("--auto-access"))
        assertFalse(args.any { it.startsWith("-H") || it.startsWith("-U") || it.startsWith("-Ku") })
        assertEquals("-An", args.last())
        assertEquals(6, args.count { it.startsWith("-A") })
        assertEquals(5, args.count { it == "-Kt" })
        assertEquals(1, args.count { it == "-Kh" })
    }

    @Test fun onlyTlsHandshakeGroupsHaveRetryDetection() {
        val args = AutomaticAccessArguments.create("127.0.0.1", 1080)
        val http = args.indexOf("-Kh")
        assertEquals(4, args.take(http).count { it == "-At,r,s,c" })
        assertFalse(args.drop(http).any { it.startsWith("-A") && it != "-An" })
        assertFalse(args.any { it.startsWith("-C") || it.startsWith("--group-redirect") })
    }

    @Test fun diagnosticUseCanDisableObservationWithoutChangingTheChain() {
        val active = AutomaticAccessArguments.create("127.0.0.1", 1080)
        val diagnostics = AutomaticAccessArguments.create("127.0.0.1", 1080, false)
        assertArrayEquals(active.filterNot { it == "--auto-access" }.toTypedArray(), diagnostics)
        assertThrows(IllegalArgumentException::class.java) { AutomaticAccessArguments.create("127.0.0.1", 0) }
    }
}
