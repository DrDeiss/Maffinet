package io.maffinet.android.ui.components

import io.maffinet.android.core.dns.DnsCatalog
import io.maffinet.android.core.dns.DnsPurpose
import org.junit.Assert.*
import org.junit.Test

class DnsPresetsTest {
    @Test fun geoAccessChoicesIncludeRegionalSmartDnsWithoutPublicAndFamilyResolvers() {
        val geo = DnsPresets.forPurpose(DnsPresets.values, DnsPurpose.GEO_ACCESS)
        assertEquals(DnsCatalog.SYSTEM_ID, geo.first())
        assertEquals(DnsCatalog.CUSTOM_ID, geo.last())
        assertTrue(geo.containsAll(listOf("geohide", "geohide-eu", "geohide-us", "xbox",
            "xbox-supercell", "comss", "malw", "bezmezhau", "dns-ai", "astracat", "nullsproxy")))
        assertFalse("google" in geo)
        assertFalse("cloudflare-family" in geo)
        assertTrue(DnsCatalog.vpnAddresses(geo.first { it == "geohide" }).isNotEmpty())
    }

    @Test fun switchingGroupsRetainsSystemCustomAndRoutesLegacySelectionsToTheirProvider() {
        val general = DnsPresets.forPurpose(DnsPresets.values, DnsPurpose.GENERAL)
        assertTrue(general.containsAll(listOf(DnsCatalog.SYSTEM_ID, DnsCatalog.CUSTOM_ID, "google", "cloudflare")))
        assertFalse("geohide" in general)
        val legacy = listOf("Geohide DNS (dns.geohide.ru)", "Google Public DNS", "custom:9.9.9.9")
        assertEquals(listOf(legacy[0], legacy[2]), DnsPresets.forPurpose(legacy, DnsPurpose.GEO_ACCESS))
        assertEquals(listOf(legacy[1], legacy[2]), DnsPresets.forPurpose(legacy, DnsPurpose.GENERAL))
        assertEquals(listOf("193.233.112.67", "193.233.112.68"), DnsCatalog.vpnAddresses(legacy[0]))
    }
}
