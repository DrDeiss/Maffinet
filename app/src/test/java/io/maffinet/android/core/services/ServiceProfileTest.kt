package io.maffinet.android.core.services

import org.junit.Assert.*
import org.junit.Test

class ServiceProfileTest {
    @Test fun catalogKeepsLinkedInVerifiedPackageAndLegacyYouTubeClients() {
        assertEquals(setOf("com.linkedin.android"), ServiceCatalog.get("linkedin")!!.packages)
        assertEquals(setOf("linkedin.com", "licdn.com"), ServiceCatalog.get("linkedin")!!.domains)
        assertTrue(ServiceCatalog.get("youtube")!!.packages.contains("org.smarttube.stable"))
        assertEquals(setOf("youtube"), ServiceCatalog.enabledByDefault)
        assertEquals(ServiceCatalog.profiles.size, ServiceCatalog.profiles.map { it.id }.toSet().size)
    }

    @Test fun addingProfileFeedsDomainsAndRoutingWithoutSpecialCases() {
        val custom = ServiceProfile("example", "Example", setOf("org.example.app"), setOf("example.org"), listOf("https://example.org"))
        val profiles = ServiceCatalog.profiles + custom
        val domains = ServiceCatalog.domainLists(profiles, setOf("example"), emptyList(), true)
        assertEquals(listOf("example.org"), domains.first { it.id == "general" }.domains)
        assertEquals(setOf("org.example.app"), ApplicationRouting.selectedPackages(profiles, setOf("example"), emptySet(), "io.maffinet.android"))
    }

    @Test fun serviceAndUserSelectionChangesGeneralWithoutChangingCatalog() {
        val lists = ServiceCatalog.domainLists(setOf("linkedin"), listOf("custom.org", "linkedin.com"), true)
        assertEquals(listOf("linkedin.com", "licdn.com", "custom.org"), lists.first { it.id == "general" }.domains)
        assertFalse(lists.first { it.id == "youtube" }.isActive)
        val disabled = ServiceCatalog.domainLists(emptySet(), listOf("custom.org"), false)
        assertTrue(disabled.first { it.id == "general" }.domains.isEmpty())
        assertEquals(4, ServiceCatalog.get("youtube")!!.domains.size)
    }

    @Test fun manualAndProfilePackagesUnionAndExcludeProxyUid() {
        val selected = ApplicationRouting.selectedPackages(
            ServiceCatalog.profiles, setOf("linkedin"), setOf("org.browser.app", "com.linkedin.android", "io.maffinet.android"), "io.maffinet.android"
        )
        assertEquals(setOf("com.linkedin.android", "org.browser.app"), selected)
        assertEquals(setOf("com.linkedin.android"), ApplicationRouting.installedPackages(selected, setOf("com.linkedin.android"), "io.maffinet.android"))
    }

    @Test fun emptyOrUninstalledApplicationSelectionCannotBecomeFullDeviceVpn() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplicationRouting.installedPackages(emptySet(), setOf("org.browser.app"), "io.maffinet.android")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApplicationRouting.installedPackages(setOf("com.linkedin.android"), emptySet(), "io.maffinet.android")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApplicationRouting.installedPackages(setOf("io.maffinet.android"), setOf("io.maffinet.android"), "io.maffinet.android")
        }
    }

    @Test fun malformedCatalogDataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ServiceProfile("bad", "Bad", setOf("invalid package"), setOf("example.org"), listOf("https://example.org"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceProfile("bad", "Bad", emptySet(), setOf("https://example.org"), listOf("https://example.org"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceProfile("bad", "Bad", emptySet(), setOf("example.org"), listOf("ftp://example.org"))
        }
    }
}
