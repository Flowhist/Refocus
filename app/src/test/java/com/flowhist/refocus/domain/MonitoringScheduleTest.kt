package com.flowhist.refocus.domain

import com.flowhist.refocus.data.ActiveSession
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringScheduleTest {
    @Test
    fun idleMonitoringUsesLowFrequencyHeartbeat() {
        assertEquals(
            MonitoringSchedule.IDLE_HEARTBEAT_MS,
            MonitoringSchedule.nextDelayMs(
                session = null,
                nowElapsed = 1_000L,
                monitoringEnabled = true,
                deviceLocked = false,
                hasPendingCompletion = false,
                graceDurationMs = 5_000L,
            ),
        )
    }

    @Test
    fun activeSessionWakesAtDeadlineWhenItIsNear() {
        val session = session(plannedDurationMs = 10_000L)

        assertEquals(
            750L,
            MonitoringSchedule.nextDelayMs(
                session = session,
                nowElapsed = 10_250L,
                monitoringEnabled = true,
                deviceLocked = false,
                hasPendingCompletion = false,
                graceDurationMs = 5_000L,
            ),
        )
    }

    @Test
    fun longActiveSessionUsesBoundedHeartbeat() {
        val session = session(plannedDurationMs = 60_000L)

        assertEquals(
            MonitoringSchedule.ACTIVE_HEARTBEAT_MS,
            MonitoringSchedule.nextDelayMs(
                session = session,
                nowElapsed = 5_000L,
                monitoringEnabled = true,
                deviceLocked = false,
                hasPendingCompletion = false,
                graceDurationMs = 5_000L,
            ),
        )
    }

    @Test
    fun graceCountdownUpdatesOncePerSecond() {
        val session = session(plannedDurationMs = 1_000L).apply {
            graceStartedAtActiveMs = 1_000L
        }

        assertEquals(
            MonitoringSchedule.COUNTDOWN_TICK_MS,
            MonitoringSchedule.nextDelayMs(
                session = session,
                nowElapsed = 2_000L,
                monitoringEnabled = true,
                deviceLocked = false,
                hasPendingCompletion = false,
                graceDurationMs = 5_000L,
            ),
        )
    }

    private fun session(plannedDurationMs: Long) = ActiveSession(
        databaseId = 1L,
        packageName = "example.app",
        appLabel = "Example",
        purpose = "Focus",
        plannedDurationMs = plannedDurationMs,
        startedAtWallClock = 0L,
        startedAtElapsed = 1_000L,
    )
}
