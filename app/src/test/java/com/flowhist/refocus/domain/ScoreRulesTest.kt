package com.flowhist.refocus.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreRulesTest {
    @Test
    fun completedOnTimeScoresOne() {
        assertEquals(1, ScoreRules.score(penaltyApplied = false, goalCompleted = true))
    }

    @Test
    fun unfinishedOnTimeScoresZero() {
        assertEquals(0, ScoreRules.score(penaltyApplied = false, goalCompleted = false))
    }

    @Test
    fun overdueAlwaysScoresMinusOne() {
        assertEquals(-1, ScoreRules.score(penaltyApplied = true, goalCompleted = true))
        assertEquals(-1, ScoreRules.score(penaltyApplied = true, goalCompleted = false))
    }
}
