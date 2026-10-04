package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test

class HostAccessPolicyTest {
    @Test fun publicNamesNormalizeWithoutAdmittingLocalNamesOrUrls() {
        assertEquals("www.linkedin.com", HostAccessPolicy.normalizePublicHost("WWW.LinkedIn.COM."))
        assertEquals("xn--e1afmkfd.xn--p1ai", HostAccessPolicy.normalizePublicHost("пример.рф"))
        listOf("localhost", "printer.local", "host.home", "host.lan", "host.internal", "host.onion",
            "example.com", "api.example.net", "https://linkedin.com", "1.1.1.1", "*.linkedin.com",
            "user@linkedin.com", "host.com:443").forEach { assertNull(it, HostAccessPolicy.normalizePublicHost(it)) }
    }

    @Test fun privateReservedAndNonNumericAddressesCannotReceiveRoutes() {
        listOf("0.1.2.3", "10.0.0.1", "127.0.0.1", "100.64.0.1", "169.254.1.1", "172.31.255.255",
            "192.168.0.1", "192.0.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1",
            "224.0.0.1", "255.255.255.255", "1.1.1.256", "01.1.1.1", "resolver.net").forEach {
            assertFalse(it, HostAccessPolicy.isPublicIpv4(it))
        }
        listOf("1.1.1.1", "130.211.32.14", "193.233.112.67").forEach { assertTrue(it, HostAccessPolicy.isPublicIpv4(it)) }
    }

    @Test fun observationsNeedPublicOriginalAddressIncludingGlobalIpv6() {
        assertNotNull(HostAccessPolicy.observation("unlisted.org", 443, "1.1.1.1"))
        assertNotNull(HostAccessPolicy.observation("unlisted.org", 8443, "2606:4700:4700::1111"))
        listOf("::", "::1", "fe80::1", "fc00::1", "ff02::1", "2001:db8::1", "100::1",
            "::ffff:7f00:1", "2606:4700::1%eth0", "localhost").forEach {
            assertNull(it, HostAccessPolicy.observation("unlisted.org", 443, it))
        }
        assertNull(HostAccessPolicy.observation("unlisted.org", 443, "192.168.1.1"))
        assertNull(HostAccessPolicy.observation("unlisted.org", 0, "1.1.1.1"))
    }
}
