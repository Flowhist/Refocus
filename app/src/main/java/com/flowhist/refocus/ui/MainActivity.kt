package com.flowhist.refocus.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.flowhist.refocus.R
import com.flowhist.refocus.RefocusApplication
import com.flowhist.refocus.data.ActiveSession
import com.flowhist.refocus.data.InstalledApp
import com.flowhist.refocus.data.SessionRecord
import com.flowhist.refocus.data.SessionSummary
import com.flowhist.refocus.monitor.RefocusAccessibilityService
import com.flowhist.refocus.util.AppCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var refreshToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RefocusTheme {
                RefocusApp(refreshToken)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshToken++
    }

    @Composable
    private fun RefocusApp(refresh: Int) {
        val app = application as RefocusApplication
        var selectedTab by remember { mutableIntStateOf(0) }
        var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
        var selectedPackages by remember { mutableStateOf(app.settings.monitoredPackages()) }
        var monitoringEnabled by remember { mutableStateOf(app.settings.monitoringEnabled) }
        var records by remember { mutableStateOf<List<SessionRecord>>(emptyList()) }
        var summary by remember { mutableStateOf(SessionSummary()) }
        var activeSession by remember { mutableStateOf<ActiveSession?>(null) }
        var accessibilityEnabled by remember { mutableStateOf(false) }
        var notificationsEnabled by remember { mutableStateOf(false) }
        var batteryUnrestricted by remember { mutableStateOf(false) }
        var permissionsLoaded by remember { mutableStateOf(false) }
        var highlightedPermission by remember { mutableStateOf<String?>(null) }

        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { refreshToken++ }

        LaunchedEffect(refresh) {
            val nextAccessibility =
                RefocusAccessibilityService.isEnabled(this@MainActivity)
            val nextNotifications =
                getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            val nextBattery =
                getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(packageName)
            if (permissionsLoaded) {
                highlightedPermission = when {
                    nextAccessibility != accessibilityEnabled -> PERMISSION_ACCESSIBILITY
                    nextNotifications != notificationsEnabled -> PERMISSION_NOTIFICATIONS
                    nextBattery != batteryUnrestricted -> PERMISSION_BATTERY
                    else -> null
                }
            }
            accessibilityEnabled = nextAccessibility
            notificationsEnabled = nextNotifications
            batteryUnrestricted = nextBattery
            permissionsLoaded = true
            monitoringEnabled = app.settings.monitoringEnabled
            selectedPackages = app.settings.monitoredPackages()
            activeSession = app.settings.loadActiveSession(SystemClock.elapsedRealtime())
            val currentApps = installedApps
            val loaded = withContext(Dispatchers.IO) {
                Triple(
                    if (currentApps.isEmpty()) {
                        AppCatalog.loadLaunchableApps(this@MainActivity)
                    } else {
                        currentApps
                    },
                    app.sessions.recent(),
                    app.sessions.todaySummary(),
                )
            }
            installedApps = loaded.first
            records = loaded.second
            summary = loaded.third
        }

        LaunchedEffect(highlightedPermission) {
            if (highlightedPermission != null) {
                delay(1_800)
                highlightedPermission = null
            }
        }

        Scaffold(
            containerColor = Background,
            topBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Background)
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "REFOCUS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            when (selectedTab) {
                                0 -> "守门"
                                1 -> "回看今天"
                                else -> "设置"
                            },
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                        )
                    }
                    StatusPill(
                        active = monitoringEnabled && accessibilityEnabled,
                        count = selectedPackages.size,
                    )
                }
            },
            bottomBar = {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onSelected = { selectedTab = it },
                )
            },
        ) { padding ->
            when (selectedTab) {
                0 -> MonitorScreen(
                    modifier = Modifier.padding(padding),
                    apps = installedApps,
                    selectedPackages = selectedPackages,
                    monitoringEnabled = monitoringEnabled,
                    accessibilityEnabled = accessibilityEnabled,
                    activeSession = activeSession,
                    onMonitoringChanged = { enabled ->
                        if (enabled && !accessibilityEnabled) {
                            openAccessibilitySettings()
                        } else {
                            monitoringEnabled = enabled
                            app.settings.monitoringEnabled = enabled
                        }
                    },
                    onPackageChanged = { packageName, selected ->
                        app.settings.setPackageMonitored(packageName, selected)
                        selectedPackages = app.settings.monitoredPackages()
                    },
                    onSetup = ::openAccessibilitySettings,
                )
                1 -> HistoryScreen(
                    modifier = Modifier.padding(padding),
                    summary = summary,
                    records = records,
                )
                else -> SetupScreen(
                    modifier = Modifier.padding(padding),
                    accessibilityEnabled = accessibilityEnabled,
                    notificationsEnabled = notificationsEnabled,
                    batteryUnrestricted = batteryUnrestricted,
                    highlightedPermission = highlightedPermission,
                    showVivoAutostart = Build.MANUFACTURER.equals("vivo", ignoreCase = true),
                    onAccessibility = ::openAccessibilitySettings,
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                            )
                        }
                    },
                    onBattery = {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                    onAutostart = ::openVivoAutostart,
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openVivoAutostart() {
        val vivoIntent = Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            )
        }
        runCatching { startActivity(vivoIntent) }
            .onFailure {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:$packageName".toUri(),
                    ),
                )
            }
    }
}

@Composable
private fun MonitorScreen(
    modifier: Modifier,
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    monitoringEnabled: Boolean,
    accessibilityEnabled: Boolean,
    activeSession: ActiveSession?,
    onMonitoringChanged: (Boolean) -> Unit,
    onPackageChanged: (String, Boolean) -> Unit,
    onSetup: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAppManager by remember { mutableStateOf(false) }
    val selectedApps = remember(apps, selectedPackages) {
        apps.filter { it.packageName in selectedPackages }
            .sortedBy { it.label.lowercase() }
    }
    val filtered = remember(apps, query, selectedPackages) {
        apps.filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in selectedPackages }
                .thenBy { it.label.lowercase() },
        )
    }

    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MonitoringStatusCard(
                monitoringEnabled = monitoringEnabled,
                accessibilityEnabled = accessibilityEnabled,
                guardedAppCount = selectedPackages.size,
                onMonitoringChanged = onMonitoringChanged,
                onSetup = onSetup,
                onManageApps = { showAppManager = true },
            )
        }
        activeSession?.let { session ->
            item {
                CurrentSessionCard(session)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "守护应用",
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(
                        if (selectedApps.isEmpty()) {
                            "选择容易分心的应用"
                        } else {
                            "已选择 ${selectedApps.size} 个"
                        },
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = { showAppManager = !showAppManager },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentSoft,
                        contentColor = Accent,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (showAppManager) "完成" else "管理应用")
                }
            }
        }
        if (selectedApps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAppManager = true },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        "还没有守护应用，点这里开始选择",
                        modifier = Modifier.padding(18.dp),
                        color = Muted,
                    )
                }
            }
        } else {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(selectedApps, key = { it.packageName }) { installedApp ->
                        GuardedAppChip(installedApp)
                    }
                }
            }
        }
        if (showAppManager) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索应用", color = Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(18.dp),
                )
            }
            if (apps.isEmpty()) {
                item {
                    Text(
                        "正在读取应用…",
                        modifier = Modifier.padding(18.dp),
                        color = Muted,
                    )
                }
            } else {
                items(filtered, key = { it.packageName }) { installedApp ->
                    AppRow(
                        app = installedApp,
                        selected = installedApp.packageName in selectedPackages,
                        onSelected = { selected ->
                            onPackageChanged(installedApp.packageName, selected)
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MonitoringStatusCard(
    monitoringEnabled: Boolean,
    accessibilityEnabled: Boolean,
    guardedAppCount: Int,
    onMonitoringChanged: (Boolean) -> Unit,
    onSetup: () -> Unit,
    onManageApps: () -> Unit,
) {
    val title: String
    val description: String
    val action: String
    val onAction: () -> Unit
    when {
        !accessibilityEnabled -> {
            title = "还需完成设置"
            description = "开启无障碍服务后才能识别应用切换"
            action = "去设置"
            onAction = onSetup
        }
        guardedAppCount == 0 -> {
            title = "还需选择应用"
            description = "添加容易分心的应用，Refocus 才能开始守门"
            action = "管理应用"
            onAction = onManageApps
        }
        monitoringEnabled -> {
            title = "守门中"
            description = "$guardedAppCount 个应用正在守护"
            action = "暂停守门"
            onAction = { onMonitoringChanged(false) }
        }
        else -> {
            title = "已暂停"
            description = "$guardedAppCount 个应用已就绪"
            action = "开始守门"
            onAction = { onMonitoringChanged(true) }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Hero),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 19.dp),
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = Color.White,
            )
            Text(
                description,
                color = if (accessibilityEnabled) HeroMuted else Warning,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Hero,
                ),
                shape = RoundedCornerShape(15.dp),
            ) {
                Text(action, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CurrentSessionCard(session: ActiveSession) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AccentSoft),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                if (session.pauseStartedAtElapsed == null) "当前会话" else "会话已暂停",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                session.appLabel,
                color = Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${session.purpose} · 计划 ${session.plannedDurationMs / 60_000L} 分钟",
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun GuardedAppChip(app: InstalledApp) {
    Surface(
        color = SelectedSurface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bitmap = remember(app.iconPng) {
                BitmapFactory.decodeByteArray(app.iconPng, 0, app.iconPng.size).asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = "${app.label}，已加入守护",
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                app.label,
                modifier = Modifier.width(96.dp),
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(!selected) },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SelectedSurface else Color.White,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bitmap = remember(app.iconPng) {
                BitmapFactory.decodeByteArray(app.iconPng, 0, app.iconPng.size).asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    fontSize = 16.sp,
                )
                if (selected) {
                    Text("已加入守护", color = Accent, fontSize = 12.sp)
                }
            }
            Checkbox(checked = selected, onCheckedChange = onSelected)
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    summary: SessionSummary,
    records: List<SessionRecord>,
) {
    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Hero),
                shape = RoundedCornerShape(26.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("今日得分", color = HeroMuted, fontSize = 13.sp)
                        Text(
                            signed(summary.totalScore),
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (summary.totalScore >= 0) "保持节奏" else "重新聚焦",
                        color = Highlight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryCard("使用", summary.sessionCount.toString(), Modifier.weight(1f))
                SummaryCard("完成", summary.completedCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text(
                "最近记录",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
        }
        if (records.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        "还没有记录",
                        modifier = Modifier.padding(18.dp),
                        color = Muted,
                    )
                }
            }
        } else {
            items(records, key = { it.id }) { record -> SessionRow(record) }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(value, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SessionRow(record: SessionRecord) {
    val dateFormatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.appLabel, color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(dateFormatter.format(Date(record.startedAt)), color = Muted, fontSize = 12.sp)
                }
                Text(
                    record.score?.let(::signed) ?: "进行中",
                    color = when {
                        (record.score ?: 0) > 0 -> Success
                        (record.score ?: 0) < 0 -> Danger
                        else -> Muted
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(record.purpose, color = Ink, fontSize = 16.sp)
            val planned = formatDuration(record.plannedDurationMs)
            val actual = record.actualDurationMs?.let(::formatDuration) ?: "—"
            Text("$planned → $actual", color = Muted, fontSize = 12.sp)
            record.goalCompleted?.let {
                Text(
                    if (it) "✓ 已完成" else "未完成",
                    color = if (it) Success else Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SetupScreen(
    modifier: Modifier,
    accessibilityEnabled: Boolean,
    notificationsEnabled: Boolean,
    batteryUnrestricted: Boolean,
    highlightedPermission: String?,
    showVivoAutostart: Boolean,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onAutostart: () -> Unit,
) {
    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentSoft),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("●", color = Accent, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("数据只在本机", fontWeight = FontWeight.Bold, color = Ink)
                        Text("不读取页面文字或输入内容", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            SettingsSectionHeader("必须")
        }
        item {
            PermissionRow(
                title = "无障碍服务",
                description = "识别应用切换",
                enabled = accessibilityEnabled,
                button = "设置",
                highlighted = highlightedPermission == PERMISSION_ACCESSIBILITY,
                onClick = onAccessibility,
            )
        }
        item {
            SettingsSectionHeader("推荐")
        }
        item {
            PermissionRow(
                title = "通知",
                description = "显示运行状态",
                enabled = notificationsEnabled,
                button = "授权",
                highlighted = highlightedPermission == PERMISSION_NOTIFICATIONS,
                onClick = onNotifications,
            )
        }
        item {
            PermissionRow(
                title = "后台运行",
                description = "设为不限制",
                enabled = batteryUnrestricted,
                button = "设置",
                highlighted = highlightedPermission == PERMISSION_BATTERY,
                onClick = onBattery,
            )
        }
        if (showVivoAutostart) {
            item {
                SettingsSectionHeader("仅 vivo")
            }
            item {
                PermissionRow(
                    title = "vivo 自启动",
                    description = "允许系统唤醒",
                    enabled = null,
                    button = "打开",
                    onClick = onAutostart,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        color = Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    enabled: Boolean?,
    button: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) AccentSoft else Color.White,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (enabled != null) {
                        Text(
                            if (enabled) "●" else "○",
                            color = if (enabled) Success else Danger,
                            fontSize = 10.sp,
                        )
                    }
                }
                Text(description, color = Muted, fontSize = 13.sp)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentSoft,
                    contentColor = Accent,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(button, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusPill(active: Boolean, count: Int) {
    Surface(
        color = if (active) AccentSoft else SurfaceMuted,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "●",
                color = if (active) Success else Muted,
                fontSize = 9.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (active) "$count 监视中" else "已暂停",
                color = if (active) Accent else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: Int,
    onSelected: (Int) -> Unit,
) {
    val destinations = listOf(
        Triple("守门", R.drawable.ic_nav_guard, "打开守门"),
        Triple("记录", R.drawable.ic_nav_history, "打开记录"),
        Triple("设置", R.drawable.ic_nav_settings, "打开设置"),
    )
    NavigationBar(
        containerColor = Color.White,
    ) {
        destinations.forEachIndexed { index, (title, icon, description) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onSelected(index) },
                icon = {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = description,
                    )
                },
                label = {
                    Text(
                        title,
                        fontSize = 12.sp,
                        fontWeight =
                            if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}

@Composable
private fun RefocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            background = Background,
            surface = Color.White,
            surfaceVariant = AccentSoft,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink,
        ),
        content = content,
    )
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = (milliseconds / 60_000.0).roundToInt()
    return if (totalMinutes < 60) {
        "${totalMinutes}分钟"
    } else {
        "${totalMinutes / 60}小时${totalMinutes % 60}分钟"
    }
}

private const val PERMISSION_ACCESSIBILITY = "accessibility"
private const val PERMISSION_NOTIFICATIONS = "notifications"
private const val PERMISSION_BATTERY = "battery"

private val Background = Color(0xFFF4F6F2)
private val SelectedSurface = Color(0xFFE3EEE6)
private val SurfaceMuted = Color(0xFFE9EDE9)
private val AccentSoft = Color(0xFFE2EEE5)
private val Hero = Color(0xFF17231B)
private val HeroMuted = Color(0xFFB9C7BC)
private val Highlight = Color(0xFFD7F59B)
private val Warning = Color(0xFFF1C56C)
private val Ink = Color(0xFF121814)
private val Muted = Color(0xFF68736B)
private val Accent = Color(0xFF2D6346)
private val Success = Color(0xFF2D7B52)
private val Danger = Color(0xFFB53D32)
