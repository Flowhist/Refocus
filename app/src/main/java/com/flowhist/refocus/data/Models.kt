package com.flowhist.refocus.data

data class InstalledApp(
    val packageName: String,
    val label: String,
    val iconPng: ByteArray,
)

data class SessionRecord(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val purpose: String,
    val startedAt: Long,
    val endedAt: Long?,
    val plannedDurationMs: Long,
    val actualDurationMs: Long?,
    val overtimeMs: Long,
    val goalCompleted: Boolean?,
    val score: Int?,
    val outcome: String,
)

data class SessionSummary(
    val totalScore: Int = 0,
    val sessionCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
)

data class ActiveSession(
    val databaseId: Long,
    val packageName: String,
    val appLabel: String,
    val purpose: String,
    val plannedDurationMs: Long,
    val startedAtWallClock: Long,
    val startedAtElapsed: Long,
    var pausedDurationMs: Long = 0L,
    var pauseStartedAtElapsed: Long? = null,
    var graceStartedAtActiveMs: Long? = null,
    var penaltyApplied: Boolean = false,
    var nextReminderAtActiveMs: Long? = null,
) {
    fun activeDurationMs(nowElapsed: Long): Long {
        val openPause = pauseStartedAtElapsed?.let { nowElapsed - it } ?: 0L
        return (nowElapsed - startedAtElapsed - pausedDurationMs - openPause).coerceAtLeast(0L)
    }
}

data class PendingCompletion(
    val databaseId: Long,
    val packageName: String,
    val appLabel: String,
    val purpose: String,
    val plannedDurationMs: Long,
    val actualDurationMs: Long,
    val endedAtWallClock: Long,
    val penaltyApplied: Boolean,
)
