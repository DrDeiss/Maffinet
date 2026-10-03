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

    @Test fun routingUsesOnlyManualApplicationsAndExcludesProxyUid() {
        val selected = ApplicationRouting.selectedPackages(
            setOf("org.browser.app", "com.linkedin.android", "io.maffinet.android"), "io.maffinet.android"
        )
        assertEquals(setOf("com.linkedin.android", "org.browser.app"), selected)
        assertEquals(setOf("com.linkedin.android"), ApplicationRouting.installedPackages(selected, setOf("com.linkedin.android"), "io.maffinet.android"))
    }

    @Test fun emptyManualSelectionDoesNotInheritDefaultServicePackages() {
        assertTrue(ServiceCatalog.enabledByDefault.isNotEmpty())
        assertTrue(ApplicationRouting.selectedPackages(emptySet(), "io.maffinet.android").isEmpty())
        val selected = ApplicationRouting.selectedPackages(setOf("org.browser.app"), "io.maffinet.android")
        assertEquals(setOf("org.browser.app"), selected)
        assertTrue(ServiceCatalog.profiles.flatMap { it.packages }.none { it in selected })
    }

    @Test fun deselectionDoesNotReinsertKnownApplications() {
        val selection = linkedSetOf("org.browser.app", "com.google.android.youtube")
        selection.remove("com.google.android.youtube")
        assertEquals(setOf("org.browser.app"), ApplicationRouting.selectedPackages(selection, "io.maffinet.android"))
        assertEquals(setOf("org.browser.app"), selection)
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
