package com.isochron.audit.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isochron.audit.R
import com.isochron.audit.data.BluetoothDevice
import com.isochron.audit.data.WifiNetwork
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.util.BluetoothScanner
import com.isochron.audit.util.PingUtil
import com.isochron.audit.util.PortScanProgress
import com.isochron.audit.util.PortScanResult
import com.isochron.audit.util.PortScanner
import com.isochron.audit.util.SecurityAuditReport
import com.isochron.audit.util.SecurityAuditor
import com.isochron.audit.util.WellKnownPorts
import com.isochron.audit.util.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityAuditViewModel(app: Application) : AndroidViewModel(app) {
    private val wifiScanner = WifiScanner(app)
    private val btScanner = BluetoothScanner(app)
    private val portScanner = PortScanner()
    private val pingUtil = PingUtil(app)

    var wifiNetworks by mutableStateOf<List<WifiNetwork>>(emptyList())
    var btDevices by mutableStateOf<List<BluetoothDevice>>(emptyList())
    var openPorts by mutableStateOf<List<PortScanResult>>(emptyList())
    var report by mutableStateOf<SecurityAuditReport?>(null)
    var isAuditing by mutableStateOf(false)
    var auditPhase by mutableStateOf("")
    var portScanProgress by mutableStateOf<PortScanProgress?>(null)

    val wifiEnabled: StateFlow<Boolean> = wifiScanner.wifiEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), wifiScanner.isWifiEnabled())

    fun runAudit() {
        isAuditing = true
        val context = getApplication<Application>()
        openPorts = emptyList()
        viewModelScope.launch {
            try {
                auditPhase = context.getString(R.string.audit_phase_wifi)
                wifiNetworks = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<WifiNetwork>>()
                    wifiScanner.startScan { results -> deferred.complete(results) }
                    kotlinx.coroutines.withTimeoutOrNull(10_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "WiFi scan error", e)
                    UiMessageBus.post(R.string.err_audit_partial, "WLAN")
                    emptyList()
                }

                auditPhase = context.getString(R.string.audit_phase_bt)
                btDevices = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<BluetoothDevice>>()
                    btScanner.startScan(
                        durationMs = 6000L,
                        onProgress = { devices ->
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                try { btDevices = devices } catch (_: Exception) {}
                            }
                        },
                        onComplete = { results -> deferred.complete(results) }
                    )
                    kotlinx.coroutines.withTimeoutOrNull(15_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "BT scan error", e)
                    UiMessageBus.post(R.string.err_audit_partial, "Bluetooth")
                    emptyList()
                }

                try {
                    val gateway = pingUtil.getNetworkInfo().gatewayIp
                    if (gateway != null) {
                        auditPhase = context.getString(R.string.audit_phase_ports, gateway)
                        val scanResults = portScanner.scan(
                            ip = gateway,
                            ports = WellKnownPorts.QUICK_20,
                            grabBanners = true,
                            onProgress = { p ->
                                viewModelScope.launch(Dispatchers.Main.immediate) { portScanProgress = p }
                            }
                        )
                        withContext(Dispatchers.Main) { openPorts = scanResults }
                    }
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "Port scan error", e)
                    UiMessageBus.post(R.string.err_audit_partial, "Port-Scan")
                }

                auditPhase = context.getString(R.string.audit_phase_report)
                report = try {
                    SecurityAuditor.audit(
                        wifiNetworks = wifiNetworks,
                        btDevices = btDevices,
                        openPorts = openPorts,
                        connectedSsid = wifiScanner.getConnectedSsid()
                    )
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "Report error", e)
                    UiMessageBus.postError(R.string.err_audit, e)
                    null
                }
            } catch (e: Exception) {
                Log.e("SecurityAudit", "Audit error", e)
                UiMessageBus.postError(R.string.err_audit, e)
            } finally {
                isAuditing = false
                auditPhase = ""
                portScanProgress = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiScanner.cleanup()
        btScanner.cleanup()
    }
}
