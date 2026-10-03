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
}
