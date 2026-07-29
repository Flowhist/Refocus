package com.flowhist.refocus.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.flowhist.refocus.RefocusApplication
import com.flowhist.refocus.data.InstalledApp
import com.flowhist.refocus.data.SessionRecord
import com.flowhist.refocus.data.SessionSummary
import com.flowhist.refocus.monitor.RefocusAccessibilityService
import com.flowhist.refocus.util.AppCatalog
import kotlinx.coroutines.Dispatchers
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
        var accessibilityEnabled by remember { mutableStateOf(false) }
        var notificationsEnabled by remember { mutableStateOf(false) }
        var batteryUnrestricted by remember { mutableStateOf(false) }

        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { refreshToken++ }

        LaunchedEffect(refresh) {
            accessibilityEnabled = RefocusAccessibilityService.isEnabled(this@MainActivity)
            notificationsEnabled =
                getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            batteryUnrestricted =
                getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(packageName)
            monitoringEnabled = app.settings.monitoringEnabled
            selectedPackages = app.settings.monitoredPackages()
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
                                0 -> "守住注意力"
                                1 -> "回看今天"
                                else -> "运行状态"
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Background)
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("应用", "记录", "设置").forEachIndexed { index, title ->
                        TabButton(
                            title = title,
                            selected = selectedTab == index,
                            modifier = Modifier.weight(1f),
                        ) { selectedTab = index }
                    }
                }
            },
        ) { padding ->
            when (selectedTab) {
                0 -> MonitorScreen(
                    modifier = Modifier.padding(padding),
                    apps = installedApps,
                    selectedPackages = selectedPackages,
                    monitoringEnabled = monitoringEnabled,
                    accessibilityEnabled = accessibilityEnabled,
                    onMonitoringChanged = { enabled ->
                        monitoringEnabled = enabled
                        app.settings.monitoringEnabled = enabled
                        if (enabled && !accessibilityEnabled) openAccessibilitySettings()
                    },
                    onPackageChanged = { packageName, selected ->
                        app.settings.setPackageMonitored(packageName, selected)
                        selectedPackages = app.settings.monitoredPackages()
                    },
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
    onMonitoringChanged: (Boolean) -> Unit,
    onPackageChanged: (String, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
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
            Card(
                colors = CardDefaults.cardColors(containerColor = Hero),
                shape = RoundedCornerShape(26.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (monitoringEnabled) "专注守门开启" else "专注守门暂停",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color.White,
                        )
                        Text(
                            when {
                                !accessibilityEnabled -> "需要无障碍权限"
                                selectedPackages.isEmpty() -> "选择需要守住的应用"
                                else -> "${selectedPackages.size} 个应用正在守护"
                            },
                            color = if (accessibilityEnabled) HeroMuted else Warning,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = monitoringEnabled,
                        onCheckedChange = onMonitoringChanged,
                    )
                }
            }
        }
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
        item { Spacer(Modifier.height(8.dp)) }
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
                contentDescription = null,
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
            PermissionRow(
                title = "无障碍服务",
                description = "识别应用切换",
                enabled = accessibilityEnabled,
                button = "设置",
                onClick = onAccessibility,
            )
        }
        item {
            PermissionRow(
                title = "通知",
                description = "显示运行状态",
                enabled = notificationsEnabled,
                button = "授权",
                onClick = onNotifications,
            )
        }
        item {
            PermissionRow(
                title = "后台运行",
                description = "设为不限制",
                enabled = batteryUnrestricted,
                button = "设置",
                onClick = onBattery,
            )
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
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    enabled: Boolean?,
    button: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
private fun TabButton(
    title: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Hero else Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Box(
            Modifier.padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                color = if (selected) Color.White else Ink,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
