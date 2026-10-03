package io.maffinet.android.core.services

import io.maffinet.android.core.domains.DomainParser
import java.net.URI

/** A catalog record links routing, domain selection and probes without conflating them. */
data class ServiceProfile(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val domains: Set<String>,
    val testUrls: List<String>,
    val enabledByDefault: Boolean = false,
    val provider: String = "",
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_-]*"))) { "Invalid service ID" }
        require(name.isNotBlank()) { "A service needs a name" }
        require(domains.isNotEmpty() && domains.all { DomainParser.normalize(it) == it }) { "Use normalized service domains" }
        require(packages.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")) }) { "Invalid Android package" }
        require(testUrls.isNotEmpty() && testUrls.all {
            val uri = URI(it)
            uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null
        }) { "A service needs HTTP/TLS probe URLs" }
    }
}

object ApplicationRouting {
    fun selectedPackages(
        profiles: List<ServiceProfile>,
        enabledServiceIds: Set<String>,
        manualApplications: Set<String>,
        ownPackage: String,
    ): Set<String> {
        val packages = linkedSetOf<String>()
        profiles.filter { it.id in enabledServiceIds }.forEach { packages += it.packages }
        packages += manualApplications.sorted()
        packages.remove(ownPackage)
        return packages
    }

    /** Never let an empty allowlist turn into Android's full-device routing mode. */
    fun installedPackages(selected: Set<String>, installed: Set<String>, ownPackage: String): Set<String> {
        val routed = selected.intersect(installed) - ownPackage
        require(routed.isNotEmpty()) { "Install an enabled service app or select an installed application in Advanced settings" }
        return routed
    }
}
