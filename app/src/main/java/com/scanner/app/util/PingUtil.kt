package com.scanner.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

data class PingResult(
    val host: String,
    val latencyMs: Float?,       // null = timeout
    val isReachable: Boolean,
    val packetLoss: Float = 0f,  // 0.0 - 1.0
    val ttl: Int? = null
)

data class NetworkInfo(
    val gatewayIp: String?,
    val deviceIp: String?,
    val dns: String?,
    val ssid: String?,
    val linkSpeed: Int?,          // Mbps
    val signalStrength: Int?      // dBm
)

class PingUtil(private val context: Context) {

    private val wifiManager: WifiManager? =
        try {
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (_: Exception) { null }

    /**
     * Ping a host using the system ping command.
     * Returns a PingResult with latency and reachability.
     */
    suspend fun ping(host: String, count: Int = 3, timeoutSec: Int = 5): PingResult =
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("ping", "-c", count.toString(), "-W", timeoutSec.toString(), host)
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                reader.close()
                process.waitFor()

                parsePingOutput(host, output)
            } catch (e: Exception) {
                PingResult(
                    host = host,
                    latencyMs = null,
                    isReachable = false,
                    packetLoss = 1f
                )
            }
        }

    /**
     * Quick reachability check via InetAddress.
     */
    suspend fun isReachable(host: String, timeoutMs: Int = 3000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(host).isReachable(timeoutMs)
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Measure HTTP latency to a URL (e.g., for internet connectivity check).
     */
    suspend fun httpLatency(urlString: String = "https://www.google.com"): PingResult =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val start = System.nanoTime()
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.connect()
                val latency = (System.nanoTime() - start) / 1_000_000f
                conn.disconnect()

                PingResult(
                    host = url.host,
                    latencyMs = latency,
                    isReachable = true
                )
            } catch (_: Exception) {
                PingResult(
                    host = urlString,
                    latencyMs = null,
                    isReachable = false,
                    packetLoss = 1f
                )
            }
        }

    /**
     * Get current network information (gateway, IP, DNS, etc.)
     */
    @Suppress("DEPRECATION")
    fun getNetworkInfo(): NetworkInfo {
        return try {
            val mgr = wifiManager ?: return NetworkInfo(null, null, null, null, null, null)
            val dhcpInfo = mgr.dhcpInfo
            val wifiInfo = mgr.connectionInfo

            val gatewayIp = dhcpInfo?.gateway?.takeIf { it != 0 }?.let { intToIp(it) }
            val deviceIp = dhcpInfo?.ipAddress?.takeIf { it != 0 }?.let { intToIp(it) }
            val dns = dhcpInfo?.dns1?.takeIf { it != 0 }?.let { intToIp(it) }
            val ssid = wifiInfo?.ssid?.removeSurrounding("\"")
                ?.takeIf { it != "<unknown ssid>" && it.isNotBlank() }

            NetworkInfo(
                gatewayIp = gatewayIp,
                deviceIp = deviceIp,
                dns = dns,
                ssid = ssid,
                linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 },
                signalStrength = wifiInfo?.rssi?.takeIf { it != 0 && it > -127 }
            )
        } catch (e: Exception) {
            NetworkInfo(null, null, null, null, null, null)
        }
    }

    /**
     * Check if we have internet connectivity.
     */
    fun hasInternetConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    // ─── Private helpers ────────────────────────────────────────

    private fun parsePingOutput(host: String, output: String): PingResult {
        // Parse packet loss: "3 packets transmitted, 3 received, 0% packet loss"
        val lossRegex = """(\d+)% packet loss""".toRegex()
        val lossMatch = lossRegex.find(output)
        val packetLoss = lossMatch?.groupValues?.get(1)?.toFloatOrNull()?.div(100f) ?: 1f

        // Parse avg latency: "rtt min/avg/max/mdev = 1.234/5.678/9.012/1.234 ms"
        val rttRegex = """rtt min/avg/max/mdev = [\d.]+/([\d.]+)/[\d.]+/[\d.]+ ms""".toRegex()
        val rttMatch = rttRegex.find(output)
        val avgLatency = rttMatch?.groupValues?.get(1)?.toFloatOrNull()

        // Parse TTL from first reply: "ttl=64"
        val ttlRegex = """ttl=(\d+)""".toRegex()
        val ttl = ttlRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()

        return PingResult(
            host = host,
            latencyMs = avgLatency,
            isReachable = packetLoss < 1f,
            packetLoss = packetLoss,
            ttl = ttl
        )
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
