package com.flowhist.refocus.domain

import com.flowhist.refocus.data.ActiveSession

object MonitoringSchedule {
    const val IDLE_HEARTBEAT_MS = 60_000L
    const val ACTIVE_HEARTBEAT_MS = 5_000L
    const val COUNTDOWN_TICK_MS = 1_000L
    const val MIN_DELAY_MS = 100L

    fun nextDelayMs(
        session: ActiveSession?,
        nowElapsed: Long,
        monitoringEnabled: Boolean,
        deviceLocked: Boolean,
        hasPendingCompletion: Boolean,
        graceDurationMs: Long,
    ): Long {
        if (
            !monitoringEnabled ||
            deviceLocked ||
            hasPendingCompletion ||
            session == null ||
            session.pauseStartedAtElapsed != null
        ) {
            return IDLE_HEARTBEAT_MS
        }

        val activeMs = session.activeDurationMs(nowElapsed)
        if (activeMs < session.plannedDurationMs) {
            return (session.plannedDurationMs - activeMs)
                .coerceIn(MIN_DELAY_MS, ACTIVE_HEARTBEAT_MS)
        }

        val graceStart = session.graceStartedAtActiveMs
            ?: return MIN_DELAY_MS
        val graceRemaining = graceDurationMs - (activeMs - graceStart)
        if (graceRemaining > 0L) {
            return graceRemaining.coerceIn(MIN_DELAY_MS, COUNTDOWN_TICK_MS)
        }

        if (!session.penaltyApplied) return MIN_DELAY_MS

        val nextReminder = session.nextReminderAtActiveMs
            ?: return MIN_DELAY_MS
        return (nextReminder - activeMs)
            .coerceIn(MIN_DELAY_MS, ACTIVE_HEARTBEAT_MS)
    }
}
