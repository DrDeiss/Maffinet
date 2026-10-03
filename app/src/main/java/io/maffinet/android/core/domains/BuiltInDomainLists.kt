package io.maffinet.android.core.domains

import io.maffinet.android.core.services.ServiceCatalog

/** Compatibility adapter; ServiceCatalog is the only source of service definitions. */
object BuiltInDomainLists {
    val services: List<DomainList> get() = ServiceCatalog.profiles.map {
        DomainList(it.id, it.name, it.domains.toList(), isBuiltIn = true)
    }
    val enabledByDefault: Set<String> get() = ServiceCatalog.enabledByDefault
}
