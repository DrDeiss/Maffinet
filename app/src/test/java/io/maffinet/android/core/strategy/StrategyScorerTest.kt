package io.maffinet.android.core.strategy

import org.junit.Assert.*
import org.junit.Test

class StrategyScorerTest {
    private fun service(id: String, passed: Boolean, ms: Long) = ServiceConnectivityResult(
        id, id, listOf(TargetConnectivityResult("https://$id.example", passed, ms))
    )

    @Test fun coverageOutranksLatency() {
        val complete = StrategyEvaluation(17, "complete", listOf(service("a", true, 900), service("b", true, 900)))
        val fast = StrategyEvaluation(32, "fast", listOf(service("a", true, 10), service("b", false, 2_000)))
        assertEquals(complete, StrategyScorer.best(listOf(fast, complete)))
    }

    @Test fun equalCoverageUsesSuccessfulLatencyAndStablePresetOrder() {
        val slow = StrategyEvaluation(1, "slow", listOf(service("a", true, 400), service("b", false, 1)))
        val fast = StrategyEvaluation(3, "fast", listOf(service("a", true, 30), service("b", false, 5_000)))
        val tied = fast.copy(candidateIndex = 4, command = "tied")
        assertEquals(fast, StrategyScorer.best(listOf(tied, slow, fast)))
    }

    @Test fun noSuccessfulTargetsNeverSelectsAStrategy() {
        assertNull(StrategyScorer.best(emptyList()))
        assertNull(StrategyScorer.best(listOf(StrategyEvaluation(0, "empty", emptyList()))))
        assertNull(StrategyScorer.best(listOf(StrategyEvaluation(0, "failed", listOf(service("a", false, 0))))))
    }

    @Test fun allConfiguredTargetsOfAServiceMustPass() {
        val mixed = ServiceConnectivityResult("service", "Service", listOf(
            TargetConnectivityResult("https://one.example", true, 5),
            TargetConnectivityResult("https://two.example", false, 1_000, error = "timeout"),
        ))
        assertFalse(mixed.passed)
        assertNull(mixed.latencyMs)
        assertFalse(mixed.copy(targets = emptyList()).passed)
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateServiceIdsCannotInflateCoverage() {
        StrategyEvaluation(0, "duplicate", listOf(service("a", true, 5), service("a", true, 5)))
    }
}
