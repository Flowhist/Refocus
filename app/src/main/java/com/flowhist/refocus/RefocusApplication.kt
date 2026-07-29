package com.flowhist.refocus

import android.app.Application
import com.flowhist.refocus.data.SessionDatabase
import com.flowhist.refocus.data.SettingsRepository

class RefocusApplication : Application() {
    val settings by lazy { SettingsRepository(this) }
    val sessions by lazy { SessionDatabase(this) }
}
