package com.scanner.app.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.scanner.app.data.WifiNetwork

@SuppressLint("MissingPermission")
class WifiScanner(private val context: Context) {

    companion object {
        private const val TAG = "WifiScanner"
        private const val SCAN_TIMEOUT_MS = 10_000L
    }

    private val wifiManager: WifiManager? =
        try {
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get WifiManager", e)
            null
        }

    private var scanReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * Start a WiFi scan and return results via callback.
     * Safe to call even without permissions — will return empty list.
     */
    fun startScan(onResults: (List<WifiNetwork>) -> Unit) {
        if (wifiManager == null) {
            Log.w(TAG, "WifiManager not available")
            onResults(emptyList())
            return
        }

        // Cleanup previous scan
        cleanupCurrentScan()

        try {
            scanReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    cancelTimeout()
                    try {
                        val results = parseScanResults()
                        onResults(results)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing scan results", e)
                        onResults(emptyList())
                    }
                    cleanupCurrentScan()
                }
            }

            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

            // API 33+ requires receiver export flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(scanReceiver, intentFilter)
            }

            // Set timeout — if scan never completes, return cached results
            timeoutRunnable = Runnable {
                Log.w(TAG, "Scan timed out, returning cached results")
                try {
                    val cached = parseScanResults()
                    onResults(cached)
                } catch (e: Exception) {
                    onResults(emptyList())
                }
                cleanupCurrentScan()
            }
            mainHandler.postDelayed(timeoutRunnable!!, SCAN_TIMEOUT_MS)

            @Suppress("DEPRECATION")
            val scanStarted = wifiManager.startScan()
            if (!scanStarted) {
                Log.w(TAG, "startScan() returned false, using cached results")
                cancelTimeout()
                try {
                    onResults(parseScanResults())
                } catch (e: Exception) {
                    onResults(emptyList())
                }
                cleanupCurrentScan()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for WiFi scan", e)
            cancelTimeout()
            onResults(emptyList())
            cleanupCurrentScan()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting scan", e)
            cancelTimeout()
            onResults(emptyList())
            cleanupCurrentScan()
        }
    }

    fun getConnectedSsid(): String? {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return null
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager?.connectionInfo ?: return null
            val ssid = wifiInfo.ssid?.removeSurrounding("\"")
            if (ssid == "<unknown ssid>" || ssid.isNullOrBlank()) null else ssid
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connected SSID", e)
            null
        }
    }

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    fun cleanup() {
        cleanupCurrentScan()
    }

    private fun cleanupCurrentScan() {
        cancelTimeout()
        scanReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
        scanReceiver = null
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun parseScanResults(): List<WifiNetwork> {
        val mgr = wifiManager ?: return emptyList()

        val scanResults: List<ScanResult> = try {
            mgr.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied reading scan results", e)
            emptyList()
        }

        val connectedBssid: String? = try {
            @Suppress("DEPRECATION")
            mgr.connectionInfo?.bssid
        } catch (_: Exception) { null }

        return scanResults.mapNotNull { result ->
            try {
                val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.wifiSsid?.toString()?.removeSurrounding("\"") ?: ""
                } else {
                    @Suppress("DEPRECATION")
                    result.SSID ?: ""
                }

                val capabilities = result.capabilities ?: ""
                WifiNetwork(
                    ssid = ssid.ifBlank { "(Verstecktes Netzwerk)" },
                    bssid = result.BSSID ?: return@mapNotNull null,
                    signalStrength = result.level,
                    frequency = result.frequency,
                    channel = frequencyToChannel(result.frequency),
                    securityType = getSecurityType(result),
                    isConnected = result.BSSID == connectedBssid,
                    band = if (result.frequency > 4900) "5 GHz" else "2.4 GHz",
                    wpsEnabled = capabilities.contains("WPS", ignoreCase = true),
                    rawCapabilities = capabilities
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing scan result", e)
                null
            }
        }.sortedByDescending { it.signalStrength }
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        freq == 2484 -> 14
        else -> -1
    }

    private fun getSecurityType(result: ScanResult): String {
        val capabilities = result.capabilities ?: return "Unbekannt"
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("OWE") -> "OWE"
            else -> "Offen"
        }
    }
}
