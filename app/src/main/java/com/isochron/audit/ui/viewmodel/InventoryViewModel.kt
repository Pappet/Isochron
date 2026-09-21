package com.isochron.audit.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.isochron.audit.data.db.DiscoveredDeviceEntity
import com.isochron.audit.data.repository.DeviceRepository

class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    val repository = DeviceRepository(app)

    var kind by mutableStateOf("all")
    var favOnly by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    var editDialogDevice by mutableStateOf<DiscoveredDeviceEntity?>(null)

    /** Device awaiting delete confirmation; deletion is irreversible and has no undo. */
    var deleteDialogDevice by mutableStateOf<DiscoveredDeviceEntity?>(null)

    var showExportDialog by mutableStateOf(false)
}
