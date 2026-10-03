package io.maffinet.android.core.strategy

/** HTTP/TLS probe evidence; it does not certify an application's media/API flows. */
data class TargetConnectivityResult(
    val url: String,
    val reachable: Boolean,
    val latencyMs: Long,
    val httpStatus: Int? = null,
    val error: String? = null,
) {
    init { require(latencyMs >= 0) }
}

data class ServiceConnectivityResult(
    val serviceId: String,
    val serviceName: String,
    val targets: List<TargetConnectivityResult>,
) {
    val passed: Boolean get() = targets.isNotEmpty() && targets.all { it.reachable }
    val latencyMs: Long? get() = if (passed) targets.map { it.latencyMs }.average().toLong() else null
}

data class StrategyEvaluation(
    val candidateIndex: Int,
    val command: String,
    val services: List<ServiceConnectivityResult>,
) {
    init { require(services.map { it.serviceId }.distinct().size == services.size) }
    val passedServices: Int get() = services.count { it.passed }
    val totalServices: Int get() = services.size
    val averageLatencyMs: Long? get() = services.mapNotNull { it.latencyMs }
        .takeIf { it.isNotEmpty() }?.average()?.toLong()
}

object StrategyScorer {
    /** Highest coverage, then mean successful-service latency, then preset order. */
    val comparator: Comparator<StrategyEvaluation> =
        compareByDescending<StrategyEvaluation> { it.passedServices }
            .thenBy { it.averageLatencyMs ?: Long.MAX_VALUE }
            .thenBy { it.candidateIndex }

    fun best(evaluations: Iterable<StrategyEvaluation>): StrategyEvaluation? =
        evaluations.filter { it.passedServices > 0 }.minWithOrNull(comparator)
}
