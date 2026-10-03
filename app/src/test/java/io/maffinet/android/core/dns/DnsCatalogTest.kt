package io.maffinet.android.core.dns

import org.junit.Assert.*
import org.junit.Test

class DnsCatalogTest {
    @Test fun legacySelectionsResolveToTheSameProviderWithCurrentAddresses() {
        val legacy = mapOf(
            "Стандартный (Отключено)" to "system",
            "Cloudflare Secure DNS" to "cloudflare",
            "Google Public DNS" to "google",
            "AdGuard DNS (Блокировка рекламы)" to "adguard",
            "Xbox DNS (xbox-dns.ru / ChatGPT / Brawl)" to "xbox",
            "Supercell Xbox DNS (supercell.xbox-dns.ru)" to "xbox-supercell",
            "NullsProxy DNS (dns.nullsproxy.com)" to "nullsproxy",
            "Comss.one DNS (dns.comss.one)" to "comss",
            "Geohide DNS (dns.geohide.ru)" to "geohide",
        )
        legacy.forEach { (saved, id) ->
            assertEquals(id, DnsCatalog.canonicalSelection(saved))
            assertEquals(DnsCatalog.vpnAddresses(id), DnsCatalog.vpnAddresses(saved))
        }
        assertEquals(listOf("111.88.96.54", "111.88.96.55"), DnsCatalog.vpnAddresses("xbox"))
        assertEquals(listOf("111.88.96.50", "111.88.96.51"), DnsCatalog.vpnAddresses("xbox-supercell"))
        assertEquals(listOf("83.220.169.155", "212.109.195.93"), DnsCatalog.vpnAddresses("comss"))
    }

    @Test fun hostnameOnlyProvidersNeverInheritAnotherProvidersIpv4() {
        assertEquals(DnsMode.PRIVATE_DNS, DnsCatalog.resolve("nullsproxy").mode)
        assertEquals("dns.nullsproxy.com", DnsCatalog.resolve("nullsproxy").privateDnsHostname)
        assertEquals(DnsMode.PRIVATE_DNS, DnsCatalog.resolve("malw-gateway").mode)
        assertEquals("dns.dns-ai.ru", DnsCatalog.resolve("dns-ai").privateDnsHostname)
        assertEquals("dns.astracat.network", DnsCatalog.resolve("astracat").privateDnsHostname)
        listOf("nullsproxy", "malw-gateway", "dns-ai", "astracat").forEach {
            assertEquals(DnsMode.PRIVATE_DNS, DnsCatalog.resolve(it).mode)
            assertTrue(DnsCatalog.vpnAddresses(it).isEmpty())
        }
    }

    @Test fun legacyExternalSelectionsExplainThatVpnUsesNetworkDns() {
        val nulls = "NullsProxy DNS (dns.nullsproxy.com)"
        assertTrue(DnsCatalog.selectionLabel(nulls).contains("VPN использует DNS сети"))
        assertTrue(DnsCatalog.selectionLabel(nulls).contains("настройте Private DNS"))
        assertTrue(DnsCatalog.resolve(nulls).detail.contains("VPN: DNS сети"))
        assertEquals("google", DnsCatalog.canonicalSelection("Google Public DNS"))
        assertEquals("Google Public DNS", DnsCatalog.selectionLabel("google"))
    }

    @Test fun geoHideRegionsApplyPublishedUdpResolversAndKeepLegacySelection() {
        val legacy = "Geohide DNS (dns.geohide.ru)"
        assertEquals("geohide", DnsCatalog.canonicalSelection(legacy))
        assertEquals(listOf("193.233.112.67", "193.233.112.68"), DnsCatalog.vpnAddresses(legacy))
        assertEquals(listOf("217.60.245.219", "217.60.245.233"), DnsCatalog.vpnAddresses("geohide-eu"))
        assertEquals(listOf("192.255.159.240", "192.255.159.241"), DnsCatalog.vpnAddresses("geohide-us"))
        assertEquals("GeoHide: Россия", DnsCatalog.selectionLabel(legacy))
        listOf("geohide", "geohide-eu", "geohide-us").forEach {
            assertEquals(DnsMode.VPN_IPV4, DnsCatalog.resolve(it).mode)
            assertEquals(DnsPurpose.GEO_ACCESS, DnsCatalog.resolve(it).purpose)
        }
    }

    @Test fun geoAccessCatalogIncludesSmartDnsWithoutPublicAndFamilyResolvers() {
        val geo = DnsCatalog.presets.filter { it.purpose == DnsPurpose.GEO_ACCESS }.map { it.id }
        assertTrue(geo.containsAll(listOf("geohide", "geohide-eu", "geohide-us", "xbox", "xbox-supercell",
            "malw", "comss", "bezmezhau", "nullsproxy", "malw-gateway", "dns-ai", "astracat")))
        assertFalse("cloudflare" in geo)
        assertFalse("google" in geo)
        assertTrue(DnsCatalog.presets.filter { it.id.endsWith("-family") }.all { it.purpose == DnsPurpose.GENERAL })
    }

    @Test fun idsAndLegacyAliasesAreUniqueAndAllVpnProfilesHavePublishedIpv4() {
        assertEquals(DnsCatalog.presets.size, DnsCatalog.presets.map { it.id }.toSet().size)
        val keys = DnsCatalog.presets.flatMap { (it.legacyLabels + it.id + it.label).toList() }
        assertEquals(keys.size, keys.toSet().size)
        DnsCatalog.presets.filter { it.mode == DnsMode.VPN_IPV4 }.forEach {
            assertEquals(2, it.ipv4.size)
            assertTrue(it.ipv4.all(DnsCatalog::isUnicastIpv4))
            assertTrue(it.sourceUrl?.startsWith("https://") == true)
        }
    }

    @Test fun filteredVariantsDoNotReuseTheUnfilteredEndpoints() {
        assertNotEquals(DnsCatalog.vpnAddresses("cloudflare"), DnsCatalog.vpnAddresses("cloudflare-security"))
        assertNotEquals(DnsCatalog.vpnAddresses("adguard"), DnsCatalog.vpnAddresses("adguard-unfiltered"))
        assertNotEquals(DnsCatalog.vpnAddresses("yandex-security"), DnsCatalog.vpnAddresses("yandex-family"))
        assertNotEquals(DnsCatalog.vpnAddresses("controld-ads"), DnsCatalog.vpnAddresses("controld-family"))
    }

    @Test fun customAddressesRoundTripAndRemoveDuplicatesWithoutMixingProviders() {
        val saved = DnsCatalog.customSelection(" 9.9.9.9, 149.112.112.112;9.9.9.9 ")
        assertEquals("custom:9.9.9.9,149.112.112.112", saved)
        assertEquals(listOf("9.9.9.9", "149.112.112.112"), DnsCatalog.vpnAddresses(saved))
        assertEquals(saved, DnsCatalog.canonicalSelection(saved))
        assertEquals("9.9.9.9, 149.112.112.112", DnsCatalog.customInput(saved))
        assertEquals(listOf("192.168.1.1"), DnsCatalog.vpnAddresses(DnsCatalog.customSelection("192.168.1.1")))
    }

    @Test fun malformedHostnamesUrlsIpv6AndInvalidUnicastAreRejected() {
        listOf("", "dns.google", "https://dns.google/dns-query", "tls://dns.google", "2001:4860:4860::8888",
            "8.8.8", "256.8.8.8", "1.1.1.-1", "01.1.1.1", "１.1.1.1", "1.1.1.1:53", "0.0.0.0",
            "127.0.0.1", "224.0.0.1", "255.255.255.255", "169.254.1.1",
            "1.1.1.1,2.2.2.2,3.3.3.3,4.4.4.4,5.5.5.5").forEach {
            assertNull("Rejected custom input: $it", DnsCatalog.customSelection(it))
        }
    }

    @Test fun validZeroFinalOctetAndFourResolverSelectionAreAccepted() {
        assertEquals(listOf("76.76.2.0", "76.76.10.0"), DnsCatalog.vpnAddresses("controld"))
        assertEquals(4, DnsCatalog.vpnAddresses(DnsCatalog.customSelection("1.1.1.1 1.0.0.1 8.8.8.8 8.8.4.4")).size)
    }

    @Test fun corruptOrUnknownSavedSelectionUsesSystemDns() {
        listOf(null, "", "unknown-provider", "custom:invalid", "custom:1.1.1.1,https://dns.google").forEach {
            assertEquals(DnsCatalog.SYSTEM_ID, DnsCatalog.canonicalSelection(it))
            assertTrue(DnsCatalog.vpnAddresses(it).isEmpty())
        }
    }
}
