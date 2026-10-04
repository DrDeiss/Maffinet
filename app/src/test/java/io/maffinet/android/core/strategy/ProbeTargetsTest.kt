package io.maffinet.android.core.strategy

import io.maffinet.android.core.domains.DomainList
import org.junit.Assert.*
import org.junit.Test

class ProbeTargetsTest {
    private val lists = listOf(
        DomainList("general", "General", listOf("example.com"), isBuiltIn = true),
        DomainList("user", "User", listOf("custom.org"), isActive = false),
    )
    private fun fingerprint(
        urls: List<String> = listOf("https://example.com"), domains: List<DomainList> = lists,
        active: List<String> = listOf("example.com"), override: Boolean = false,
        mode: String = "disable", hosts: String = "", candidates: List<String> = listOf("-o1", "-s1"),
    ) = ProbeConfigurationFingerprint.create(urls, domains, active, override, mode, hosts, candidates)

    @Test fun configuredAddressesAreIndependentAndNormalizedWithoutChangingEscapedPaths() {
        val parsed = ProbeTargetParser.parse("# my independent checks\nHTTPS://EXAMPLE.COM/a%20b?q=one%26two\nhttps://example.com/a%20b?q=one%26two\nhttp://custom.org:8080/status")
        assertTrue(parsed.isValid)
        assertEquals(listOf("https://example.com/a%20b?q=one%26two", "http://custom.org:8080/status"), parsed.urls)
    }

    @Test fun invalidTargetsRejectProtocolsCredentialsFragmentsPortsAndEmptySelections() {
        listOf("", "# no checks", "ftp://example.com", "example.com", "https://user:secret@example.com",
            "https://example.com/#fragment", "https://example.com:0", "https://example.com:65536",
            "https://example.com/a b").forEach { assertFalse(it, ProbeTargetParser.parse(it).isValid) }
    }

    @Test fun targetCountIsBoundedAndRepeatedLinesDoNotConsumeTheLimit() {
        assertFalse(ProbeTargetParser.parse((1..21).joinToString("\n") { "https://host$it.example" }).isValid)
        assertTrue(ProbeTargetParser.parse((1..21).joinToString("\n") { "https://example.com" }).isValid)
    }

    @Test fun fingerprintsAreStableForEquivalentDomainSets() {
        val reordered = lists.reversed().map { it.copy(domains = it.domains.reversed()) }
        assertEquals(fingerprint(), fingerprint(domains = reordered))
        assertEquals(64, fingerprint().length)
    }

    @Test fun hostEditsAndUserEnableStateInvalidateEvidence() {
        assertNotEquals(fingerprint(), fingerprint(domains = lists.map {
            if (it.id == "user") it.copy(isActive = true) else it
        }, active = listOf("example.com", "custom.org")))
        assertNotEquals(fingerprint(), fingerprint(domains = lists.map {
            if (it.id == "general") it.copy(domains = listOf("changed.com")) else it
        }, active = listOf("changed.com")))
    }

    @Test fun targetChangesAdvancedOverrideAndCandidateChangesInvalidateEvidence() {
        assertNotEquals(fingerprint(), fingerprint(urls = listOf("https://example.com/health")))
        assertNotEquals(fingerprint(), fingerprint(override = true))
        assertNotEquals(fingerprint(), fingerprint(mode = "whitelist", hosts = "custom.org"))
        assertNotEquals(fingerprint(), fingerprint(candidates = listOf("-s2")))
    }

    @Test fun automaticAndManualModesCannotReusePreviousMatrixEvidence() {
        val urls = listOf("https://example.com")
        val candidates = listOf("-o1", "-s1")
        val automatic = ProbeConfigurationFingerprint.create(urls, lists, listOf("example.com"), false, candidates = candidates)
        val manual = ProbeConfigurationFingerprint.create(urls, lists, listOf("example.com"), false,
            candidates = candidates, automaticAccessEnabled = false)
        assertNotEquals(automatic, manual)
        assertEquals(fingerprint(), automatic)
    }
}
