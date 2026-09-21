package com.isochron.audit.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isochron.audit.R
import com.isochron.audit.data.WifiNetwork
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.data.repository.DeviceRepository
import com.isochron.audit.util.WardrivingTracker
import com.isochron.audit.util.WifiScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WifiViewModel(app: Application) : AndroidViewModel(app) {

    val wifiScanner = WifiScanner(app)
    val wardrivingTracker = WardrivingTracker(app)
    val repository = DeviceRepository(app)

    var networks by mutableStateOf<List<WifiNetwork>>(emptyList())
        private set
    var isScanning by mutableStateOf(false)
        private set
    var hasScanned by mutableStateOf(false)
        private set
    var gpsEnabled by mutableStateOf(false)
        private set
    var geoTagCount by mutableStateOf(0)
        private set
    var uniqueGeoNetworks by mutableStateOf(0)
        private set
    var filter by mutableStateOf("all")
    var selectedNetwork by mutableStateOf<WifiNetwork?>(null)

    /** Live adapter state so the "Wi-Fi off" banner follows the system toggle. */
    val wifiEnabled: StateFlow<Boolean> = wifiScanner.wifiEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), wifiScanner.isWifiEnabled())

    fun scan() {
        if (!wifiScanner.isWifiEnabled()) {
            UiMessageBus.post(R.string.err_wifi_off)
            return
        }
        isScanning = true
        val startTime = System.currentTimeMillis()
        wifiScanner.startScan { results ->
            networks = results.sortedByDescending { it.signalStrength }
            isScanning = false
            hasScanned = true
            if (gpsEnabled) {
                wardrivingTracker.recordNetworks(results)
                geoTagCount = wardrivingTracker.getEntryCount()
                uniqueGeoNetworks = wardrivingTracker.getUniqueNetworks()
            }
            viewModelScope.launch {
                try {
                    repository.persistWifiScan(
                        networks = results,
                        durationMs = System.currentTimeMillis() - startTime,
                        location = if (gpsEnabled) wardrivingTracker.getCurrentLocation() else null,
                    )
                } catch (e: Exception) {
                    android.util.Log.e("WifiViewModel", "Error persisting scan", e)
                    UiMessageBus.post(R.string.err_persist)
                }
            }
        }
    }

    fun toggleGps(enabled: Boolean) {
        gpsEnabled = enabled
        if (enabled) wardrivingTracker.startTracking() else wardrivingTracker.stopTracking()
    }

    fun toggleFavorite(bssid: String) {
        viewModelScope.launch { repository.toggleFavoriteByAddress(bssid) }
    }

    override fun onCleared() {
        super.onCleared()
        wifiScanner.cleanup()
        wardrivingTracker.cleanup()
    }
}
