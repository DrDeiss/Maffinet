package io.maffinet.android.core.access

import io.maffinet.android.core.dns.DnsConfiguration

enum class AutomaticAccessPhase { IDLE, OBSERVING, CHECKING, ROUTE_APPLIED, UNRESOLVED }

/** Describes the current public-host check, never account or whole-application health. */
data class AutomaticAccessStatus(
    val phase: AutomaticAccessPhase = AutomaticAccessPhase.IDLE,
    val host: String? = null,
    val queuedHosts: Int = 0,
    val appliedRoutes: Int = 0,
    val dnsConfiguration: DnsConfiguration? = null,
    val dnsChecks: List<DnsCheckEvidence> = emptyList(),
    val checkingDns: Boolean = false,
)
