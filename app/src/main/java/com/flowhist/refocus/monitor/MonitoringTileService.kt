package com.flowhist.refocus.monitor

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flowhist.refocus.RefocusApplication

class MonitoringTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!RefocusAccessibilityService.isEnabled(this)) {
            openAccessibilitySettings()
            return
        }

        val settings = (application as RefocusApplication).settings
        settings.monitoringEnabled = !settings.monitoringEnabled
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val accessibilityEnabled = RefocusAccessibilityService.isEnabled(this)
        val monitoringEnabled =
            accessibilityEnabled && (application as RefocusApplication).settings.monitoringEnabled

        tile.state = when {
            !accessibilityEnabled -> Tile.STATE_UNAVAILABLE
            monitoringEnabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "Refocus"
        tile.subtitle = when {
            !accessibilityEnabled -> "需要无障碍权限"
            monitoringEnabled -> "监测中"
            else -> "已暂停"
        }
        tile.updateTile()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
