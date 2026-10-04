package io.maffinet.android.core.domains

import org.junit.Assert.*
import org.junit.Test

class DomainParserTest {
    @Test fun normalizesCommentsIdnCaseAndDuplicates() {
        val parsed = DomainParser.parse("\uFEFF# imported\nYouTube.COM. youtube.com # duplicate\nпример.рф,example.net")
        assertTrue(parsed.isValid)
        assertEquals(listOf("youtube.com", "xn--e1afmkfd.xn--p1ai", "example.net"), parsed.domains)
    }

    @Test fun rejectsUrlsPortsWildcardsAndArgumentsWithLineNumbers() {
        val parsed = DomainParser.parse("example.com\nhttps://example.net\nexample.org:443\n*.example.net\n--hosts\n127.0.0.1")
        assertEquals(listOf("example.com"), parsed.domains)
        assertEquals(listOf(2, 3, 4, 5, 6), parsed.errors.map { it.line })
    }

    @Test fun mergingIncludesOnlyActiveNondeletedListsAndHasStableOrder() {
        val lists = listOf(
            DomainList("one", "One", listOf("EXAMPLE.COM", "first.net")),
            DomainList("two", "Two", listOf("example.com", "second.net")),
            DomainList("disabled", "Disabled", listOf("disabled.net"), isActive = false),
            DomainList("deleted", "Deleted", listOf("deleted.net"), isDeleted = true),
        )
        assertEquals(listOf("example.com", "first.net", "second.net"), DomainParser.merge(lists))
    }

    @Test fun importExtractsPublicIpv4AndIpv6AliasesAndPlainDomainsInStableOrder() {
        val parsed = DomainParser.parseImport("\uFEFF# hosts\r\n176.99.11.77 EXAMPLE.COM alias.net # redirect\n" +
            "2001:4860:4860::8888 example.com ipv6.org\n::ffff:176.99.11.77 mapped.org\n" +
            "пример.рф,example.com\n")
        assertTrue(parsed.errors.toString(), parsed.isValid)
        assertEquals(listOf("example.com", "alias.net", "ipv6.org", "mapped.org", "xn--e1afmkfd.xn--p1ai"), parsed.domains)
        assertFalse(DomainParser.parse("176.99.11.77 example.com").isValid)
    }

    @Test fun importDoesNotTurnBlockedOrLocalMappingsIntoBypassDomains() {
        val parsed = DomainParser.parseImport("""
            0.0.0.0 ads.example.com tracker.example.net
            127.0.0.1 loopback.example.org localhost
            127.25.10.2 another.example.org
            :: blocked-v6.example.org
            ::1 localhost ip6-localhost ip6-loopback loopback-v6.example.org
            192.168.1.20 lan.example.org
            10.0.0.1 private.example.org
            fe80::1 linklocal.example.org
            fc00::1 unique-local.example.org
            ff02::1 multicast.example.org
            255.255.255.255 broadcast.example.org
            176.99.11.77 public.org localhost printer принтер printer.local localhost.localdomain
            localhost localdomain service.local
        """.trimIndent())
        assertTrue(parsed.errors.toString(), parsed.isValid)
        assertEquals(listOf("public.org"), parsed.domains)
    }

    @Test fun importKeepsStrictDomainValidationAndReportsOriginalLines() {
        val parsed = DomainParser.parseImport("176.99.11.77 valid.org https://invalid.net\n999.0.0.1 alias.org\n" +
            "2001:4860:4860::8888 *.invalid.org\n^dns.google\n176.99.11.77\n<html>error</html>\n176.99.11.77 --hosts\nhttps://bad.local")
        assertFalse(parsed.isValid)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), parsed.errors.map { it.line })
        assertEquals(listOf("valid.org", "alias.org"), parsed.domains)
    }
}
