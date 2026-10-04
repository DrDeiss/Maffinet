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
    val fullyReachable: Boolean get() = services.isNotEmpty() && services.all { it.passed }
    val failedTargets: List<TargetConnectivityResult> get() = services.flatMap { it.targets }
        .filterNot { it.reachable }
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

    /** Auto may replace the active strategy only when every configured URL was checked and passed. */
    fun bestComplete(
        evaluations: Iterable<StrategyEvaluation>,
        requiredUrls: Collection<String>,
    ): StrategyEvaluation? {
        val required = requiredUrls.toSet()
        if (required.isEmpty()) return null
        return evaluations.filter { evaluation ->
            val checked = evaluation.services.flatMap { it.targets }.map { it.url }
            evaluation.fullyReachable && checked.size == required.size && checked.toSet() == required
        }.minWithOrNull(comparator)
    }
}
