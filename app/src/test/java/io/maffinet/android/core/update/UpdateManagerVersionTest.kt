package io.maffinet.android.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerVersionTest {
    @Test fun stableReleaseUpdatesInstalledAlpha() {
        assertTrue(UpdateManager.isNewerVersion("0.1.0-alpha", "v0.1.0"))
        assertTrue(UpdateManager.isNewerVersion("0.1.0-alpha.2", "0.1.0-alpha.10"))
        assertTrue(UpdateManager.isNewerVersion("0.1.0-alpha", "0.1.0-beta"))
    }

    @Test fun stableVersionDoesNotDowngradeToPrerelease() {
        assertFalse(UpdateManager.isNewerVersion("0.1.0", "v0.1.0-alpha"))
        assertFalse(UpdateManager.isNewerVersion("0.1.0-alpha.10", "0.1.0-alpha.2"))
        assertFalse(UpdateManager.isNewerVersion("0.1.0-beta", "0.1.0-alpha"))
    }

    @Test fun numericComponentsOrderVersions() {
        assertTrue(UpdateManager.isNewerVersion("0.9.9", "0.10.0"))
        assertTrue(UpdateManager.isNewerVersion("1.2.9", "1.2.10"))
        assertTrue(UpdateManager.isNewerVersion("1.2.9", "1.3.0-alpha"))
        assertFalse(UpdateManager.isNewerVersion("2.0.0", "1.99.99"))
        assertFalse(UpdateManager.isNewerVersion("0.1.0-alpha", "v0.1.0-alpha"))
        assertFalse(UpdateManager.isNewerVersion("1.2.3+build.1", "1.2.3+build.2"))
    }

    @Test fun malformedAndNonReleaseTagsAreRejected() {
        listOf("", "main", "nightly", "latest", "release-1.2.3", "1.2.x", "1.2.3-", "vV1.2.3").forEach { tag ->
            assertThrows("Invalid release tag: $tag", IllegalArgumentException::class.java) {
                UpdateManager.isNewerVersion("0.1.0-alpha", tag)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManager.isNewerVersion("unknown", "1.2.3")
        }
    }
}
