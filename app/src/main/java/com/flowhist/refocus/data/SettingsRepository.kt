package com.flowhist.refocus.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val preferences =
        context.getSharedPreferences("refocus_settings", Context.MODE_PRIVATE)
    private val changeListeners = mutableSetOf<() -> Unit>()
    private var monitoringEnabledCache =
        preferences.getBoolean(KEY_MONITORING_ENABLED, false)
    private var monitoredPackagesCache =
        preferences.getStringSet(KEY_MONITORED_PACKAGES, emptySet())?.toSet() ?: emptySet()

    var monitoringEnabled: Boolean
        get() = monitoringEnabledCache
        set(value) {
            if (monitoringEnabledCache == value) return
            monitoringEnabledCache = value
            preferences.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()
            notifyChanged()
        }

    fun monitoredPackages(): Set<String> = monitoredPackagesCache

    fun setPackageMonitored(packageName: String, monitored: Boolean) {
        val packages = monitoredPackagesCache.toMutableSet()
        if (monitored) packages += packageName else packages -= packageName
        if (packages == monitoredPackagesCache) return
        monitoredPackagesCache = packages.toSet()
        preferences.edit().putStringSet(KEY_MONITORED_PACKAGES, monitoredPackagesCache).apply()
        notifyChanged()
    }

    fun isPackageMonitored(packageName: String): Boolean = packageName in monitoredPackagesCache

    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners -= listener
    }

    private fun notifyChanged() {
        changeListeners.toList().forEach { it() }
    }

    fun saveActiveSession(session: ActiveSession?) {
        val edit = preferences.edit()
        if (session == null) {
            ACTIVE_KEYS.forEach { edit.remove(it) }
            edit.apply()
            return
        }
        edit
            .putLong(KEY_ACTIVE_ID, session.databaseId)
            .putString(KEY_ACTIVE_PACKAGE, session.packageName)
            .putString(KEY_ACTIVE_LABEL, session.appLabel)
            .putString(KEY_ACTIVE_PURPOSE, session.purpose)
            .putLong(KEY_ACTIVE_PLANNED, session.plannedDurationMs)
            .putLong(KEY_ACTIVE_WALL_START, session.startedAtWallClock)
            .putLong(KEY_ACTIVE_ELAPSED_START, session.startedAtElapsed)
            .putLong(KEY_ACTIVE_PAUSED, session.pausedDurationMs)
            .putLong(KEY_ACTIVE_PAUSE_START, session.pauseStartedAtElapsed ?: -1L)
            .putLong(KEY_ACTIVE_GRACE_START, session.graceStartedAtActiveMs ?: -1L)
            .putBoolean(KEY_ACTIVE_PENALTY, session.penaltyApplied)
            .putLong(KEY_ACTIVE_NEXT_REMINDER, session.nextReminderAtActiveMs ?: -1L)
            .apply()
    }

    fun loadActiveSession(nowElapsed: Long): ActiveSession? {
        val id = preferences.getLong(KEY_ACTIVE_ID, -1L)
        val elapsedStart = preferences.getLong(KEY_ACTIVE_ELAPSED_START, -1L)
        if (id < 0L || elapsedStart < 0L || nowElapsed < elapsedStart) {
            saveActiveSession(null)
            return null
        }
        return ActiveSession(
            databaseId = id,
            packageName = preferences.getString(KEY_ACTIVE_PACKAGE, null) ?: return null,
            appLabel = preferences.getString(KEY_ACTIVE_LABEL, "") ?: "",
            purpose = preferences.getString(KEY_ACTIVE_PURPOSE, "") ?: "",
            plannedDurationMs = preferences.getLong(KEY_ACTIVE_PLANNED, 0L),
            startedAtWallClock = preferences.getLong(KEY_ACTIVE_WALL_START, 0L),
            startedAtElapsed = elapsedStart,
            pausedDurationMs = preferences.getLong(KEY_ACTIVE_PAUSED, 0L),
            pauseStartedAtElapsed =
                preferences.getLong(KEY_ACTIVE_PAUSE_START, -1L).takeIf { it >= 0L },
            graceStartedAtActiveMs =
                preferences.getLong(KEY_ACTIVE_GRACE_START, -1L).takeIf { it >= 0L },
            penaltyApplied = preferences.getBoolean(KEY_ACTIVE_PENALTY, false),
            nextReminderAtActiveMs =
                preferences.getLong(KEY_ACTIVE_NEXT_REMINDER, -1L).takeIf { it >= 0L },
        )
    }

    fun staleActiveSessionId(nowElapsed: Long): Long? {
        val id = preferences.getLong(KEY_ACTIVE_ID, -1L)
        val elapsedStart = preferences.getLong(KEY_ACTIVE_ELAPSED_START, -1L)
        return id.takeIf { it >= 0L && elapsedStart >= 0L && nowElapsed < elapsedStart }
    }

    fun savePendingCompletion(pending: PendingCompletion?) {
        val edit = preferences.edit()
        if (pending == null) {
            PENDING_KEYS.forEach { edit.remove(it) }
            edit.apply()
            return
        }
        edit
            .putLong(KEY_PENDING_ID, pending.databaseId)
            .putString(KEY_PENDING_PACKAGE, pending.packageName)
            .putString(KEY_PENDING_LABEL, pending.appLabel)
            .putString(KEY_PENDING_PURPOSE, pending.purpose)
            .putLong(KEY_PENDING_PLANNED, pending.plannedDurationMs)
            .putLong(KEY_PENDING_ACTUAL, pending.actualDurationMs)
            .putLong(KEY_PENDING_ENDED, pending.endedAtWallClock)
            .putBoolean(KEY_PENDING_PENALTY, pending.penaltyApplied)
            .apply()
    }

    fun loadPendingCompletion(): PendingCompletion? {
        val id = preferences.getLong(KEY_PENDING_ID, -1L)
        if (id < 0L) return null
        return PendingCompletion(
            databaseId = id,
            packageName = preferences.getString(KEY_PENDING_PACKAGE, null) ?: return null,
            appLabel = preferences.getString(KEY_PENDING_LABEL, "") ?: "",
            purpose = preferences.getString(KEY_PENDING_PURPOSE, "") ?: "",
            plannedDurationMs = preferences.getLong(KEY_PENDING_PLANNED, 0L),
            actualDurationMs = preferences.getLong(KEY_PENDING_ACTUAL, 0L),
            endedAtWallClock = preferences.getLong(KEY_PENDING_ENDED, 0L),
            penaltyApplied = preferences.getBoolean(KEY_PENDING_PENALTY, false),
        )
    }

    private companion object {
        const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        const val KEY_MONITORED_PACKAGES = "monitored_packages"

        const val KEY_ACTIVE_ID = "active_id"
        const val KEY_ACTIVE_PACKAGE = "active_package"
        const val KEY_ACTIVE_LABEL = "active_label"
        const val KEY_ACTIVE_PURPOSE = "active_purpose"
        const val KEY_ACTIVE_PLANNED = "active_planned"
        const val KEY_ACTIVE_WALL_START = "active_wall_start"
        const val KEY_ACTIVE_ELAPSED_START = "active_elapsed_start"
        const val KEY_ACTIVE_PAUSED = "active_paused"
        const val KEY_ACTIVE_PAUSE_START = "active_pause_start"
        const val KEY_ACTIVE_GRACE_START = "active_grace_start"
        const val KEY_ACTIVE_PENALTY = "active_penalty"
        const val KEY_ACTIVE_NEXT_REMINDER = "active_next_reminder"
        val ACTIVE_KEYS = listOf(
            KEY_ACTIVE_ID,
            KEY_ACTIVE_PACKAGE,
            KEY_ACTIVE_LABEL,
            KEY_ACTIVE_PURPOSE,
            KEY_ACTIVE_PLANNED,
            KEY_ACTIVE_WALL_START,
            KEY_ACTIVE_ELAPSED_START,
            KEY_ACTIVE_PAUSED,
            KEY_ACTIVE_PAUSE_START,
            KEY_ACTIVE_GRACE_START,
            KEY_ACTIVE_PENALTY,
            KEY_ACTIVE_NEXT_REMINDER,
        )

        const val KEY_PENDING_ID = "pending_id"
        const val KEY_PENDING_PACKAGE = "pending_package"
        const val KEY_PENDING_LABEL = "pending_label"
        const val KEY_PENDING_PURPOSE = "pending_purpose"
        const val KEY_PENDING_PLANNED = "pending_planned"
        const val KEY_PENDING_ACTUAL = "pending_actual"
        const val KEY_PENDING_ENDED = "pending_ended"
        const val KEY_PENDING_PENALTY = "pending_penalty"
        val PENDING_KEYS = listOf(
            KEY_PENDING_ID,
            KEY_PENDING_PACKAGE,
            KEY_PENDING_LABEL,
            KEY_PENDING_PURPOSE,
            KEY_PENDING_PLANNED,
            KEY_PENDING_ACTUAL,
            KEY_PENDING_ENDED,
            KEY_PENDING_PENALTY,
        )
    }
}
