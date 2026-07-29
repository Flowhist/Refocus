package com.flowhist.refocus.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSessionTest {
    @Test
    fun lockedTimeDoesNotCountAsActiveUsage() {
        val session = session(startedAtElapsed = 1_000L).apply {
            pauseStartedAtElapsed = 4_000L
        }

        assertEquals(3_000L, session.activeDurationMs(nowElapsed = 9_000L))
    }

    @Test
    fun accumulatedPauseIsExcludedAfterUnlock() {
        val session = session(startedAtElapsed = 1_000L).apply {
            pausedDurationMs = 5_000L
        }

        assertEquals(5_000L, session.activeDurationMs(nowElapsed = 11_000L))
    }

    private fun session(startedAtElapsed: Long) = ActiveSession(
        databaseId = 1L,
        packageName = "example.app",
        appLabel = "Example",
        purpose = "Test",
        plannedDurationMs = 60_000L,
        startedAtWallClock = 100L,
        startedAtElapsed = startedAtElapsed,
    )
}
