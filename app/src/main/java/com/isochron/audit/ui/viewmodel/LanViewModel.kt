package com.isochron.audit.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isochron.audit.R
import com.isochron.audit.data.repository.DeviceRepository
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.util.LanDevice
import com.isochron.audit.util.LanScanProgress
import com.isochron.audit.util.NetworkDiscovery
import com.isochron.audit.util.NetworkInfo
import com.isochron.audit.util.PingUtil
import com.isochron.audit.util.PortScanProgress
import com.isochron.audit.util.PortScanResult
import com.isochron.audit.util.PortScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LanViewModel(app: Application) : AndroidViewModel(app) {

    val discovery = NetworkDiscovery(app)
    val pingUtil = PingUtil(app)
    val portScanner = PortScanner()
    val repository = DeviceRepository(app)

    var devices by mutableStateOf<List<LanDevice>>(emptyList())
        private set
    var isScanning by mutableStateOf(false)
        private set
    var hasScanned by mutableStateOf(false)
        private set
    var progress by mutableStateOf<LanScanProgress?>(null)
        private set
    var networkInfo by mutableStateOf<NetworkInfo?>(null)
        private set
    var portScanResults by mutableStateOf<Map<String, List<PortScanResult>>>(emptyMap())
        private set
    var portScanningIp by mutableStateOf<String?>(null)
        private set
    var portScanProgress by mutableStateOf<PortScanProgress?>(null)
        private set

    // Kept so the user can abort: a full sweep plus a 65k-port scan used to run to
    // completion no matter what (audit A4).
    private var scanJob: Job? = null
    private var portScanJob: Job? = null

    fun scan() {
        if (isScanning) return
        isScanning = true
        try { networkInfo = pingUtil.getNetworkInfo() } catch (e: Exception) {
            android.util.Log.e("LanViewModel", "Error getting network info", e)
        }
        scanJob = viewModelScope.launch {
            try {
                val result = discovery.fullScan(
                    onProgress = { progress = it },
                    onDeviceFound = { devices = it },
                )
                devices = result
                try { repository.persistLanScan(result) } catch (e: Exception) {
                    android.util.Log.e("LanViewModel", "Error persisting LAN scan", e)
                    UiMessageBus.post(R.string.err_persist)
                }
            } catch (e: CancellationException) {
                UiMessageBus.post(R.string.lan_scan_cancelled)
            } catch (e: Exception) {
                android.util.Log.e("LanViewModel", "Error in LAN scan", e)
                UiMessageBus.postError(R.string.err_lan_scan, e)
            } finally {
                discovery.stopScan()
                isScanning = false
                hasScanned = true
                progress = null
                scanJob = null
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    /**
     * Only one port scan at a time. A second call while one runs used to start a
     * parallel coroutine whose result later overwrote the map unannounced (audit A5);
     * the UI now disables the chips, and this guard covers any remaining path.
     */
    fun startPortScan(ip: String, ports: List<Int>) {
        if (portScanningIp != null) return
        portScanningIp = ip
        portScanJob = viewModelScope.launch {
            try {
                val results = portScanner.scan(
                    ip = ip,
                    ports = ports,
                    grabBanners = true,
                    onProgress = { portScanProgress = it },
                )
                portScanResults = portScanResults + (ip to results)
                try { repository.persistPortScanResults(ip, results) } catch (e: Exception) {
                    android.util.Log.e("LanViewModel", "Error persisting port scan", e)
                    UiMessageBus.post(R.string.err_persist)
                }
            } catch (e: CancellationException) {
                UiMessageBus.post(R.string.lan_scan_cancelled)
            } catch (e: Exception) {
                android.util.Log.e("LanViewModel", "Port scan error", e)
                UiMessageBus.postError(R.string.err_port_scan, e)
            } finally {
                portScanningIp = null
                portScanProgress = null
                portScanJob = null
            }
        }
    }

    fun cancelPortScan() {
        portScanJob?.cancel()
    }

    fun toggleFavorite(address: String) {
        viewModelScope.launch { repository.toggleFavoriteByAddress(address) }
    }

    override fun onCleared() {
        super.onCleared()
        discovery.stopScan()
    }
}
