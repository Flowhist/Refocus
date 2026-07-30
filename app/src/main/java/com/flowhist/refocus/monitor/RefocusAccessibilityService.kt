package com.flowhist.refocus.monitor

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.flowhist.refocus.R
import com.flowhist.refocus.RefocusApplication
import com.flowhist.refocus.data.ActiveSession
import com.flowhist.refocus.data.PendingCompletion
import com.flowhist.refocus.data.SessionDatabase
import com.flowhist.refocus.data.SettingsRepository
import com.flowhist.refocus.domain.ForegroundRules
import com.flowhist.refocus.domain.MonitoringSchedule
import com.flowhist.refocus.domain.ScoreRules
import com.flowhist.refocus.ui.MainActivity
import com.flowhist.refocus.util.AppCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

class RefocusAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tickRequests = Channel<Unit>(Channel.CONFLATED)
    private val settingsChangeListener: () -> Unit = { requestTick() }
    private lateinit var settings: SettingsRepository
    private lateinit var database: SessionDatabase
    private lateinit var overlays: OverlayController
    private lateinit var keyguardManager: KeyguardManager

    private var activeSession: ActiveSession? = null
    private var pendingCompletion: PendingCompletion? = null
    private var pendingPurposePackage: String? = null
    private var lastEventPackage: String? = null
    private var exitDebounceJob: Job? = null
    private var screenReceiverRegistered = false
    private var lastNotificationState: String? = null
    private val homePackageName: String? by lazy {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as RefocusApplication
        settings = app.settings
        settings.addChangeListener(settingsChangeListener)
        database = app.sessions
        overlays = OverlayController(this)
        keyguardManager = getSystemService(KeyguardManager::class.java)

        val nowElapsed = SystemClock.elapsedRealtime()
        settings.staleActiveSessionId(nowElapsed)?.let(database::closeStale)
        activeSession = settings.loadActiveSession(nowElapsed)
        pendingCompletion = settings.loadPendingCompletion()
        if (activeSession == null) {
            pendingPurposePackage = null
        }

        registerScreenReceiver()
        createNotificationChannel()
        updateStatusNotification(force = true)

        serviceScope.launch {
            while (isActive) {
                tick()
                withTimeoutOrNull(nextTickDelayMs()) {
                    tickRequests.receive()
                }
            }
        }
        serviceScope.launch {
            delay(250)
            restoreVisiblePrompt()
            evaluateForeground()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventPackage = event?.packageName?.toString()
        if (eventPackage != null) {
            lastEventPackage = eventPackage
            if (isExitSurface(eventPackage)) {
                overlays.dismiss()
            }
        }
        requestTick()
    }

    override fun onInterrupt() {
        overlays.dismiss()
    }

    override fun onDestroy() {
        overlays.dismiss()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        if (::settings.isInitialized) {
            settings.removeChangeListener(settingsChangeListener)
        }
        serviceScope.cancel()
        tickRequests.close()
        super.onDestroy()
    }

    private fun requestTick() {
        tickRequests.trySend(Unit)
    }

    private fun nextTickDelayMs(): Long {
        if (!::settings.isInitialized || !::keyguardManager.isInitialized) {
            return MonitoringSchedule.IDLE_HEARTBEAT_MS
        }
        return MonitoringSchedule.nextDelayMs(
            session = activeSession,
            nowElapsed = SystemClock.elapsedRealtime(),
            monitoringEnabled = settings.monitoringEnabled,
            deviceLocked = keyguardManager.isDeviceLocked,
            hasPendingCompletion = pendingCompletion != null,
            graceDurationMs = GRACE_MS,
        )
    }

    private fun tick() {
        if (!::settings.isInitialized) return
        updateStatusNotification()

        if (!settings.monitoringEnabled) {
            stopForDisabledMonitoring()
            return
        }
        if (keyguardManager.isDeviceLocked) return

        if (pendingCompletion != null && overlays.kind == null) {
            if (!isOverlayBlocked()) showPendingCompletion()
            return
        }

        val session = activeSession ?: run {
            evaluateForeground()
            return
        }
        evaluateForeground()
        if (activeSession == null || exitDebounceJob != null) return

        val now = SystemClock.elapsedRealtime()
        val activeMs = session.activeDurationMs(now)
        if (activeMs < session.plannedDurationMs) return

        if (session.graceStartedAtActiveMs == null) {
            session.graceStartedAtActiveMs = activeMs
            settings.saveActiveSession(session)
            showGrace(session, GRACE_MS)
            return
        }

        val graceElapsed = activeMs - (session.graceStartedAtActiveMs ?: activeMs)
        val remaining = GRACE_MS - graceElapsed
        if (remaining > 0L) {
            if (overlays.kind == OverlayController.Kind.GRACE) {
                overlays.updateCountdown(remaining)
            } else if (overlays.kind == null) {
                showGrace(session, remaining)
            }
            return
        }

        if (!session.penaltyApplied) {
            session.penaltyApplied = true
            session.nextReminderAtActiveMs = activeMs
            database.markOverdue(session.databaseId)
            settings.saveActiveSession(session)
        }

        val nextReminder = session.nextReminderAtActiveMs ?: activeMs
        if (
            activeMs >= nextReminder &&
            overlays.kind != OverlayController.Kind.COMPLETION &&
            overlays.kind != OverlayController.Kind.PURPOSE
        ) {
            showOverdue(session, activeMs)
        }
    }

    private fun evaluateForeground() {
        if (!::settings.isInitialized || !settings.monitoringEnabled || keyguardManager.isDeviceLocked) {
            return
        }

        val visible = visibleApps()
        val current = activeSession
        if (current != null) {
            if (isTargetForeground(current.packageName, visible)) {
                exitDebounceJob?.cancel()
                exitDebounceJob = null
                resumeSessionClock(current)
            } else if (current.graceStartedAtActiveMs != null) {
                finishActiveSession(current)
            } else if (exitDebounceJob == null) {
                overlays.dismiss()
                val now = SystemClock.elapsedRealtime()
                val exitStartedAt = current.pauseStartedAtElapsed ?: now.also {
                    current.pauseStartedAtElapsed = it
                    settings.saveActiveSession(current)
                }
                val remainingGrace =
                    (EXIT_GRACE_MS - (now - exitStartedAt)).coerceAtLeast(0L)
                exitDebounceJob = serviceScope.launch {
                    delay(remainingGrace)
                    exitDebounceJob = null
                    val stillVisible =
                        isTargetForeground(current.packageName, visibleApps())
                    if (!stillVisible && !keyguardManager.isDeviceLocked) {
                        finishActiveSession(current)
                    }
                }
            }
            return
        }

        val purposePackage = pendingPurposePackage
        if (purposePackage != null) {
            if (!isTargetForeground(purposePackage, visible)) {
                pendingPurposePackage = null
                if (overlays.kind == OverlayController.Kind.PURPOSE) overlays.dismiss()
            } else if (overlays.kind == null && !isOverlayBlocked()) {
                showPurpose(purposePackage, restoring = true)
            }
            return
        }

        if (pendingCompletion != null || overlays.kind != null) return

        val selected = settings.monitoredPackages()
        val candidate =
            lastEventPackage
                ?.takeIf { it in selected && isTargetForeground(it, visible) }
                ?: visible.packages.firstOrNull {
                    it in selected && isTargetForeground(it, visible)
                }
        if (candidate != null) showPurpose(candidate)
    }

    private fun showPurpose(packageName: String, restoring: Boolean = false) {
        if (
            (!restoring && pendingPurposePackage != null) ||
            activeSession != null ||
            pendingCompletion != null
        ) return
        pendingPurposePackage = packageName
        val label = AppCatalog.appLabel(this, packageName)
        overlays.showPurpose(label) { purpose, minutes ->
            if (
                pendingPurposePackage != packageName ||
                !isTargetForeground(packageName, visibleApps())
            ) {
                pendingPurposePackage = null
                evaluateForeground()
                return@showPurpose
            }
            val nowWall = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val planned = minutes * 60_000L
            val id = database.insertStarted(packageName, label, purpose, planned, nowWall)
            activeSession = ActiveSession(
                databaseId = id,
                packageName = packageName,
                appLabel = label,
                purpose = purpose,
                plannedDurationMs = planned,
                startedAtWallClock = nowWall,
                startedAtElapsed = nowElapsed,
            )
            settings.saveActiveSession(activeSession)
            pendingPurposePackage = null
        }
    }

    private fun finishActiveSession(session: ActiveSession) {
        if (activeSession?.databaseId != session.databaseId) return
        exitDebounceJob?.cancel()
        exitDebounceJob = null
        overlays.dismiss()
        val actual = session.activeDurationMs(SystemClock.elapsedRealtime())
        val pending = PendingCompletion(
            databaseId = session.databaseId,
            packageName = session.packageName,
            appLabel = session.appLabel,
            purpose = session.purpose,
            plannedDurationMs = session.plannedDurationMs,
            actualDurationMs = actual,
            endedAtWallClock = System.currentTimeMillis(),
            penaltyApplied = session.penaltyApplied,
        )
        activeSession = null
        settings.saveActiveSession(null)
        pendingCompletion = pending
        settings.savePendingCompletion(pending)
        showPendingCompletion()
    }

    private fun showPendingCompletion() {
        val pending = pendingCompletion ?: return
        if (keyguardManager.isDeviceLocked || isOverlayBlocked()) return
        overlays.showCompletion(
            appLabel = pending.appLabel,
            purpose = pending.purpose,
            penaltyApplied = pending.penaltyApplied,
        ) { completed ->
            val score = ScoreRules.score(pending.penaltyApplied, completed)
            database.complete(
                id = pending.databaseId,
                endedAt = pending.endedAtWallClock,
                actualDurationMs = pending.actualDurationMs,
                plannedDurationMs = pending.plannedDurationMs,
                goalCompleted = completed,
                score = score,
            )
            pendingCompletion = null
            settings.savePendingCompletion(null)
            evaluateForeground()
        }
    }

    private fun showGrace(session: ActiveSession, remainingMs: Long) {
        overlays.showGrace(session.appLabel, remainingMs) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun showOverdue(session: ActiveSession, activeMs: Long) {
        session.nextReminderAtActiveMs = activeMs + REMINDER_MS
        settings.saveActiveSession(session)
        overlays.showOverdue(
            appLabel = session.appLabel,
            purpose = session.purpose,
            onExit = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onContinue = {
                session.nextReminderAtActiveMs =
                    session.activeDurationMs(SystemClock.elapsedRealtime()) + REMINDER_MS
                settings.saveActiveSession(session)
            },
        )
    }

    private fun visibleApps(): VisibleApps {
        val packages = linkedSetOf<String>()
        val pictureInPicturePackages = linkedSetOf<String>()
        runCatching {
            windows.forEach { window ->
                val windowPackage = window.root?.packageName?.toString() ?: return@forEach
                packages += windowPackage
                if (window.isInPictureInPictureMode) {
                    pictureInPicturePackages += windowPackage
                }
            }
        }
        lastEventPackage?.takeIf { eventPackage ->
            eventPackage != packageName &&
                eventPackage != "com.android.systemui" &&
                eventPackage != "com.android.permissioncontroller"
        }?.let(packages::add)
        return VisibleApps(packages, pictureInPicturePackages)
    }

    private fun isTargetForeground(packageName: String, visible: VisibleApps): Boolean {
        return ForegroundRules.isTargetForeground(
            targetPackage = packageName,
            visiblePackages = visible.packages,
            pictureInPicturePackages = visible.pictureInPicturePackages,
            eventPackage = lastEventPackage,
            homePackage = homePackageName,
        )
    }

    private fun isExitSurface(packageName: String?): Boolean =
        ForegroundRules.isExitSurface(packageName, homePackageName)

    private fun isOverlayBlocked(): Boolean =
        ForegroundRules.isOverlayBlocked(lastEventPackage)

    private fun pauseSession() {
        overlays.dismiss()
        val session = activeSession ?: return
        pauseSessionClock(session)
        exitDebounceJob?.cancel()
        exitDebounceJob = null
    }

    private fun resumeSession() {
        activeSession?.let(::resumeSessionClock)
        restoreVisiblePrompt()
        evaluateForeground()
    }

    private fun pauseSessionClock(session: ActiveSession) {
        if (session.pauseStartedAtElapsed == null) {
            session.pauseStartedAtElapsed = SystemClock.elapsedRealtime()
            settings.saveActiveSession(session)
        }
    }

    private fun resumeSessionClock(session: ActiveSession) {
        val pauseStart = session.pauseStartedAtElapsed ?: return
        session.pausedDurationMs +=
            (SystemClock.elapsedRealtime() - pauseStart).coerceAtLeast(0L)
        session.pauseStartedAtElapsed = null
        settings.saveActiveSession(session)
    }

    private fun restoreVisiblePrompt() {
        when {
            pendingCompletion != null -> showPendingCompletion()
            pendingPurposePackage != null -> {
                val packageName = pendingPurposePackage ?: return
                if (isTargetForeground(packageName, visibleApps())) {
                    showPurpose(packageName, restoring = true)
                } else {
                    pendingPurposePackage = null
                }
            }
            activeSession?.penaltyApplied == true -> {
                val session = activeSession ?: return
                showOverdue(session, session.activeDurationMs(SystemClock.elapsedRealtime()))
            }
            activeSession?.graceStartedAtActiveMs != null -> {
                val session = activeSession ?: return
                val elapsed = session.activeDurationMs(SystemClock.elapsedRealtime())
                val remaining =
                    GRACE_MS - (elapsed - (session.graceStartedAtActiveMs ?: elapsed))
                if (remaining > 0L) showGrace(session, remaining) else tick()
            }
        }
    }

    private fun stopForDisabledMonitoring() {
        overlays.dismiss()
        pendingPurposePackage = null
        activeSession?.let {
            if (it.penaltyApplied) {
                val actual = it.activeDurationMs(SystemClock.elapsedRealtime())
                database.complete(
                    id = it.databaseId,
                    endedAt = System.currentTimeMillis(),
                    actualDurationMs = actual,
                    plannedDurationMs = it.plannedDurationMs,
                    goalCompleted = false,
                    score = -1,
                )
            } else {
                database.closeStale(it.databaseId)
            }
            settings.saveActiveSession(null)
            activeSession = null
        }
        pendingCompletion?.let {
            val score = ScoreRules.score(it.penaltyApplied, goalCompleted = false)
            database.complete(
                id = it.databaseId,
                endedAt = it.endedAtWallClock,
                actualDurationMs = it.actualDurationMs,
                plannedDurationMs = it.plannedDurationMs,
                goalCompleted = false,
                score = score,
            )
            settings.savePendingCompletion(null)
            pendingCompletion = null
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> pauseSession()
                Intent.ACTION_USER_PRESENT -> resumeSession()
                Intent.ACTION_SCREEN_ON -> serviceScope.launch {
                    delay(300)
                    if (!keyguardManager.isDeviceLocked) {
                        resumeSession()
                    } else {
                        requestTick()
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun updateStatusNotification(force: Boolean = false) {
        if (!::settings.isInitialized) return
        val state = if (settings.monitoringEnabled) {
            "正在监视 ${settings.monitoredPackages().size} 个应用"
        } else {
            "监视已暂停"
        }
        if (!force && state == lastNotificationState) return
        lastNotificationState = state

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_refocus)
            .setContentTitle("Refocus")
            .setContentText(state)
            .setContentIntent(openApp)
            .setOngoing(settings.monitoringEnabled)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification) }
            .onFailure {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification)
            }
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabledServices =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: return false
            val component = "${context.packageName}/${RefocusAccessibilityService::class.java.name}"
            return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
        }

        private const val NOTIFICATION_CHANNEL = "monitoring"
        private const val NOTIFICATION_ID = 1001
        private const val EXIT_GRACE_MS = 10_000L
        private const val GRACE_MS = 5_000L
        private const val REMINDER_MS = 5_000L
    }

    private data class VisibleApps(
        val packages: Set<String>,
        val pictureInPicturePackages: Set<String>,
    )
}
