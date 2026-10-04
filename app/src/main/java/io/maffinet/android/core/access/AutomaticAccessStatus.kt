package io.maffinet.android.core.access

enum class AutomaticAccessPhase { IDLE, OBSERVING, CHECKING, ROUTE_APPLIED, UNRESOLVED }

/** Describes the current public-host check, never account or whole-application health. */
data class AutomaticAccessStatus(
    val phase: AutomaticAccessPhase = AutomaticAccessPhase.IDLE,
    val host: String? = null,
    val queuedHosts: Int = 0,
    val appliedRoutes: Int = 0,
)
