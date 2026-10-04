package io.maffinet.android.core.dns

import org.junit.Assert.*
import org.junit.Test

class DnsConfigurationTest {
    private fun snapshot(
        selection: String = "custom:1.1.1.1",
        label: String = "Пользовательский",
        saved: List<String> = listOf("1.1.1.1"),
        vpn: List<String>? = listOf("1.1.1.1"),
        physical: List<String> = listOf("192.168.1.1"),
        active: Boolean? = false,
        hostname: String? = null,
        networkIdentity: String? = null,
    ) = DnsConfiguration(selection, label, saved, vpn, physical, active, hostname, networkIdentity)

    @Test fun identityIncludesEveryObservedAndSavedField() {
        val base = snapshot()
        assertEquals(base, snapshot())
        assertEquals(base.hashCode(), snapshot().hashCode())
        listOf(
            snapshot(selection = "another"), snapshot(label = "Другой"),
            snapshot(saved = emptyList()), snapshot(vpn = emptyList()), snapshot(vpn = null),
            snapshot(physical = listOf("192.168.2.1")), snapshot(active = true),
            snapshot(active = null), snapshot(hostname = "dns.example"), snapshot(hostname = ""),
            snapshot(networkIdentity = "network-1"),
        ).forEach { assertNotEquals(base.fingerprint, it.fingerprint) }
    }

    @Test fun identicalDnsOnDifferentPhysicalNetworksDoesNotShareIdentity() {
        val first = snapshot(networkIdentity = "network-1:eth0:192.168.1.5/24")
        val second = snapshot(networkIdentity = "network-2:eth0:192.168.1.5/24")
        assertNotEquals(first, second)
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertNotEquals(snapshot(), snapshot(networkIdentity = ""))
    }

    @Test fun absentVpnAndInheritedNetworkDnsHaveDistinctMeaning() {
        val absent = snapshot(vpn = null)
        val inherited = snapshot(vpn = emptyList())
        assertFalse(absent.hasSelectionAssignmentMismatch)
        assertTrue(inherited.hasSelectionAssignmentMismatch)
        assertFalse(snapshot(saved = emptyList(), vpn = emptyList()).hasSelectionAssignmentMismatch)
        assertTrue(absent.summaryLines().any { it.contains("VPN отсутствует") })
        assertTrue(inherited.summaryLines().any { it.contains("наследование") })
        assertNotEquals(absent, inherited)
    }

    @Test fun mismatchComparesAddressSetsWithoutFormattingOrOrderNoise() {
        assertFalse(snapshot(saved = listOf("ABCD::1", "1.1.1.1", "1.1.1.1"),
            vpn = listOf(" 1.1.1.1 ", "abcd::1")).hasSelectionAssignmentMismatch)
        val different = snapshot(vpn = listOf("8.8.8.8"))
        assertTrue(different.hasSelectionAssignmentMismatch)
        assertTrue(different.summaryLines().any { it.startsWith("Предупреждение:") })
    }

    @Test fun snapshotIsUnaffectedByLaterMutationsOfInputLists() {
        val saved = mutableListOf("1.1.1.1")
        val vpn = mutableListOf("1.1.1.1")
        val physical = mutableListOf("192.168.1.1")
        val value = snapshot(saved = saved, vpn = vpn, physical = physical)
        val fingerprint = value.fingerprint.toList()
        saved.clear()
        vpn.add("8.8.8.8")
        physical.clear()
        assertEquals(listOf("1.1.1.1"), value.savedAddresses)
        assertEquals(listOf("1.1.1.1"), value.vpnAssignedAddresses)
        assertEquals(listOf("192.168.1.1"), value.physicalAddresses)
        assertEquals(fingerprint, value.fingerprint)
        assertFalse(value.hasSelectionAssignmentMismatch)
    }

    @Test fun summaryKeepsTransportAndActualResolverUnknown() {
        val lines = snapshot(active = null).summaryLines()
        assertTrue(lines.any { it.contains("Private DNS: состояние неизвестно") })
        assertTrue(lines.any { it.contains("DoH/DoT приложений: неизвестен") })
        assertTrue(lines.any { it.contains("фактический резолвер запросов не установлен") })
        assertTrue(snapshot(active = true, hostname = "dns.example").summaryLines()
            .any { it.contains("Private DNS: активен; имя: dns.example") })
    }
}
