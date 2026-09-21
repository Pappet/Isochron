package com.isochron.audit.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.isochron.audit.util.AppPrefs

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    // Starts at the default chosen in the settings screen; the chips still override it per session.
    var selectedInterval by mutableStateOf(
        app.getSharedPreferences(AppPrefs.NAME, Context.MODE_PRIVATE)
            .getInt(AppPrefs.KEY_MONITOR_INTERVAL, AppPrefs.DEFAULT_MONITOR_INTERVAL)
    )
}
