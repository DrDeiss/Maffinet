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

    @Test fun autoRejectsPartialResultsWhileManualRankingKeepsThemAvailable() {
        val partial = StrategyEvaluation(0, "partial", listOf(service("a", true, 10), service("b", false, 2_500)))
        assertEquals(partial, StrategyScorer.best(listOf(partial)))
        assertNull(StrategyScorer.bestComplete(listOf(partial), listOf("https://a.example", "https://b.example")))
        assertFalse(partial.fullyReachable)
        assertEquals(listOf("https://b.example"), partial.failedTargets.map { it.url })
    }

    @Test fun autoRequiresEvidenceForEveryConfiguredUrl() {
        val one = StrategyEvaluation(0, "one", listOf(service("a", true, 10)))
        assertTrue(one.fullyReachable)
        assertNull(StrategyScorer.bestComplete(listOf(one), listOf("https://a.example", "https://b.example")))
        assertNull(StrategyScorer.bestComplete(listOf(one), listOf("https://other.example")))
        assertNull(StrategyScorer.bestComplete(listOf(one), emptyList()))
        assertNull(StrategyScorer.bestComplete(listOf(StrategyEvaluation(0, "empty", emptyList())), listOf("https://a.example")))
        val duplicate = one.copy(services = listOf(service("a", true, 10), service("duplicate", true, 10).copy(
            targets = listOf(TargetConnectivityResult("https://a.example", true, 10))
        )))
        assertNull(StrategyScorer.bestComplete(listOf(duplicate), listOf("https://a.example")))
    }

    @Test fun autoSelectsFastestCompleteResultWithStablePresetOrder() {
        val required = listOf("https://a.example", "https://b.example")
        val partial = StrategyEvaluation(0, "partial", listOf(service("a", true, 1), service("b", false, 2_500)))
        val slow = StrategyEvaluation(1, "slow", listOf(service("a", true, 400), service("b", true, 400)))
        val fast = StrategyEvaluation(3, "fast", listOf(service("a", true, 30), service("b", true, 30)))
        val tied = fast.copy(candidateIndex = 4, command = "tied")
        assertEquals(fast, StrategyScorer.bestComplete(listOf(partial, tied, slow, fast), required))
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
        val evaluation = StrategyEvaluation(0, "mixed", listOf(mixed))
        assertFalse(evaluation.fullyReachable)
        assertEquals(listOf("https://two.example"), evaluation.failedTargets.map { it.url })
        assertNull(StrategyScorer.bestComplete(listOf(evaluation), mixed.targets.map { it.url }))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateServiceIdsCannotInflateCoverage() {
        StrategyEvaluation(0, "duplicate", listOf(service("a", true, 5), service("a", true, 5)))
    }
}
