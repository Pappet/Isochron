package com.isochron.audit.util

import androidx.annotation.StringRes
import com.isochron.audit.R
import com.isochron.audit.data.WifiNetwork
import com.isochron.audit.data.WifiSecurity
import com.isochron.audit.data.BluetoothDevice
import com.isochron.audit.data.BondState
import com.isochron.audit.ui.UiText

/**
 * Represents a specific security vulnerability or observation.
 */
data class SecurityFinding(
    val severity: FindingSeverity,
    val category: FindingCategory,
    val title: UiText,
    val description: UiText,
    val target: String,           // IP, MAC, SSID
    val recommendation: UiText,
    /** Stable identity for list keys; `target` alone repeats across findings. */
    val kind: String,
)

/**
 * Severity levels for [SecurityFinding]s, including risk score and UI color.
 */
enum class FindingSeverity(@StringRes val labelRes: Int, val score: Int, val color: Long) {
    CRITICAL(R.string.severity_critical, 10, 0xFFD32F2F),
    HIGH(R.string.severity_high, 7, 0xFFE64A19),
    MEDIUM(R.string.severity_medium, 4, 0xFFF57C00),
    LOW(R.string.severity_low, 2, 0xFFFBC02D),
    INFO(R.string.severity_info, 0, 0xFF42A5F5)
}

/**
 * Categories for grouping [SecurityFinding]s in the audit report.
 */
enum class FindingCategory(@StringRes val labelRes: Int) {
    WIFI(R.string.category_wifi),
    BLUETOOTH(R.string.category_bluetooth),
    NETWORK(R.string.category_network),
    PORTS(R.string.category_ports),
    ENCRYPTION(R.string.category_encryption)
}

/**
 * Summary report of a completed security audit, containing scores and findings.
 */
data class SecurityAuditReport(
    val overallScore: Int,               // 0 (unsicher) - 100 (sicher)
    val grade: String,                   // A, B, C, D, F
    val findings: List<SecurityFinding>,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val infoCount: Int,
    val wifiFindings: List<SecurityFinding>,
    val btFindings: List<SecurityFinding>,
    val portFindings: List<SecurityFinding>,
    val networkFindings: List<SecurityFinding>,
    val auditedDevices: Int,
    val auditedNetworks: Int
)

/**
 * Analyzes discovered networks and devices for security risks and vulnerabilities.
 * Implements heuristics for WiFi encryption, Bluetooth visibility, and open port risks.
 */
object SecurityAuditor {

    /**
     * Executes a comprehensive security audit on the provided network and device data.
     * Starts with a maximum score of 100 and deducts based on found vulnerabilities.
     *
     * @param wifiNetworks List of discovered WiFi networks.
     * @param btDevices List of discovered Bluetooth devices.
     * @param openPorts List of discovered open TCP ports.
     * @param connectedSsid Currently connected SSID if applicable.
     * @return A [SecurityAuditReport] with summarized findings and a letter grade.
     */
    fun audit(
        wifiNetworks: List<WifiNetwork> = emptyList(),
        btDevices: List<BluetoothDevice> = emptyList(),
        openPorts: List<PortScanResult> = emptyList(),
        connectedSsid: String? = null
    ): SecurityAuditReport {
        val findings = mutableListOf<SecurityFinding>()

        // WiFi checks
        findings.addAll(auditWifi(wifiNetworks, connectedSsid))

        // Bluetooth checks
        findings.addAll(auditBluetooth(btDevices))

        // Port/Service checks
        findings.addAll(auditPorts(openPorts))

        // Sort by severity
        findings.sortByDescending { it.severity.score }

        val criticalCount = findings.count { it.severity == FindingSeverity.CRITICAL }
        val highCount = findings.count { it.severity == FindingSeverity.HIGH }
        val mediumCount = findings.count { it.severity == FindingSeverity.MEDIUM }
        val lowCount = findings.count { it.severity == FindingSeverity.LOW }
        val infoCount = findings.count { it.severity == FindingSeverity.INFO }

        // Calculate score: start at 100, deduct for findings
        val deductions = criticalCount * 15 + highCount * 10 + mediumCount * 5 + lowCount * 2
        val score = (100 - deductions).coerceIn(0, 100)

        val grade = when {
            score >= 90 -> "A"
            score >= 75 -> "B"
            score >= 60 -> "C"
            score >= 40 -> "D"
            else -> "F"
        }

        return SecurityAuditReport(
            overallScore = score,
            grade = grade,
            findings = findings,
            criticalCount = criticalCount,
            highCount = highCount,
            mediumCount = mediumCount,
            lowCount = lowCount,
            infoCount = infoCount,
            wifiFindings = findings.filter { it.category == FindingCategory.WIFI },
            btFindings = findings.filter { it.category == FindingCategory.BLUETOOTH },
            portFindings = findings.filter { it.category == FindingCategory.PORTS },
            networkFindings = findings.filter { it.category == FindingCategory.NETWORK },
            auditedDevices = btDevices.size + openPorts.map { it.ip }.distinct().size,
            auditedNetworks = wifiNetworks.size
        )
    }



    private fun auditWifi(
        networks: List<WifiNetwork>,
        @Suppress("UNUSED_PARAMETER") connectedSsid: String?
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        for (network in networks) {
            val isConnected = network.isConnected

            // Open network (no encryption)
            if (network.security.isUnencrypted) {
                findings.add(SecurityFinding(
                    severity = if (isConnected) FindingSeverity.CRITICAL else FindingSeverity.HIGH,
                    category = FindingCategory.WIFI,
                    title = UiText(if (isConnected) R.string.f_open_wifi_connected_title else R.string.f_open_wifi_title),
                    description = UiText(R.string.f_open_wifi_desc, network.ssid),
                    target = network.ssid,
                    recommendation = UiText(R.string.f_open_wifi_rec),
                    kind = "open-wifi",
                ))
            }

            // WEP encryption
            if (network.security == WifiSecurity.WEP) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.CRITICAL,
                    category = FindingCategory.WIFI,
                    title = UiText(R.string.f_wep_title),
                    description = UiText(R.string.f_wep_desc, network.ssid),
                    target = network.ssid,
                    recommendation = UiText(R.string.f_wep_rec),
                    kind = "wep",
                ))
            }

            // WPA (v1) — deprecated
            if (network.security == WifiSecurity.WPA) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.HIGH,
                    category = FindingCategory.WIFI,
                    title = UiText(R.string.f_wpa1_title),
                    description = UiText(R.string.f_wpa1_desc, network.ssid),
                    target = network.ssid,
                    recommendation = UiText(R.string.f_wpa1_rec),
                    kind = "wpa1",
                ))
            }

            // WPS detection
            if (network.wpsEnabled) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.MEDIUM,
                    category = FindingCategory.WIFI,
                    title = UiText(R.string.f_wps_title),
                    description = UiText(R.string.f_wps_desc, network.ssid),
                    target = network.ssid,
                    recommendation = UiText(R.string.f_wps_rec),
                    kind = "wps",
                ))
            }

            // Hidden network connected
            if (network.isHidden && isConnected) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.LOW,
                    category = FindingCategory.WIFI,
                    title = UiText(R.string.f_hidden_title),
                    description = UiText(R.string.f_hidden_desc),
                    target = network.bssid,
                    recommendation = UiText(R.string.f_hidden_rec),
                    kind = "hidden",
                ))
            }
        }

        // Check if connected network uses WPA3
        val connectedNetwork = networks.find { it.isConnected }
        if (connectedNetwork != null && connectedNetwork.security == WifiSecurity.WPA2) {
            findings.add(SecurityFinding(
                severity = FindingSeverity.INFO,
                category = FindingCategory.WIFI,
                title = UiText(R.string.f_no_wpa3_title),
                description = UiText(R.string.f_no_wpa3_desc, connectedNetwork.ssid),
                target = connectedNetwork.ssid,
                recommendation = UiText(R.string.f_no_wpa3_rec),
                kind = "no-wpa3",
            ))
        }

        return findings
    }



    private fun auditBluetooth(devices: List<BluetoothDevice>): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        for (device in devices) {
            // Device without name — potential for tracking
            if (device.isUnnamed && device.bondState == BondState.NOT_BONDED) {
                // Too many unknowns = noise, skip unless it's BLE with service UUIDs
                if (device.serviceUuids.isNotEmpty()) {
                    findings.add(SecurityFinding(
                        severity = FindingSeverity.INFO,
                        category = FindingCategory.BLUETOOTH,
                        title = UiText(R.string.f_unknown_ble_title),
                        description = UiText(
                            R.string.f_unknown_ble_desc,
                            device.address,
                            device.vendor ?: UiText(R.string.unknown_manufacturer),
                            device.serviceUuids.size,
                        ),
                        target = device.address,
                        recommendation = UiText(R.string.f_unknown_ble_rec),
                        kind = "unknown-ble",
                    ))
                }
                continue
            }

            // Classic BT discoverable without bonding
            if (device.type == com.isochron.audit.data.DeviceType.CLASSIC &&
                device.bondState == BondState.NOT_BONDED
            ) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.LOW,
                    category = FindingCategory.BLUETOOTH,
                    title = UiText(R.string.f_bt_visible_title),
                    description = UiText(R.string.f_bt_visible_desc, device.displayName()),
                    target = device.displayName(),
                    recommendation = UiText(R.string.f_bt_visible_rec),
                    kind = "bt-visible",
                ))
            }
        }

        // General: many BT devices around
        val unknownBt = devices.count {
            it.bondState == BondState.NOT_BONDED && !it.isUnnamed
        }
        if (unknownBt > 10) {
            findings.add(SecurityFinding(
                severity = FindingSeverity.INFO,
                category = FindingCategory.BLUETOOTH,
                title = UiText(R.string.f_many_bt_title, unknownBt),
                description = UiText(R.string.f_many_bt_desc),
                target = "*",
                recommendation = UiText(R.string.f_many_bt_rec),
                kind = "many-bt",
            ))
        }

        return findings
    }



    private fun auditPorts(openPorts: List<PortScanResult>): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        for (result in openPorts) {
            if (result.state != PortState.OPEN) continue

            val risk = WellKnownPorts.riskLevel(result.port)
            val severity = when (risk) {
                PortRisk.CRITICAL -> FindingSeverity.CRITICAL
                PortRisk.HIGH -> FindingSeverity.HIGH
                PortRisk.MEDIUM -> FindingSeverity.MEDIUM
                PortRisk.LOW -> FindingSeverity.LOW
                PortRisk.INFO -> FindingSeverity.INFO
            }

            val description = result.banner?.let { banner ->
                UiText(R.string.f_port_open_desc_banner, result.port, result.serviceName, result.ip, banner.take(100))
            } ?: UiText(R.string.f_port_open_desc, result.port, result.serviceName, result.ip)

            val recommendation = UiText(
                when (result.port) {
                    23 -> R.string.f_port_rec_23
                    21 -> R.string.f_port_rec_21
                    6379 -> R.string.f_port_rec_6379
                    27017 -> R.string.f_port_rec_27017
                    3306 -> R.string.f_port_rec_3306
                    5432 -> R.string.f_port_rec_5432
                    3389 -> R.string.f_port_rec_3389
                    5900 -> R.string.f_port_rec_5900
                    445 -> R.string.f_port_rec_445
                    139 -> R.string.f_port_rec_139
                    1883 -> R.string.f_port_rec_1883
                    9200 -> R.string.f_port_rec_9200
                    80 -> R.string.f_port_rec_80
                    110 -> R.string.f_port_rec_110
                    143 -> R.string.f_port_rec_143
                    else -> R.string.f_port_rec_default
                }
            )

            findings.add(SecurityFinding(
                severity = severity,
                category = FindingCategory.PORTS,
                title = UiText(R.string.f_port_open_title, result.serviceName, UiText(risk.labelRes)),
                description = description,
                target = "${result.ip}:${result.port}",
                recommendation = recommendation,
                kind = "port-open",
            ))

            // Extra finding for banner with version info
            result.banner?.let { banner ->
                if (banner.contains(Regex("\\d+\\.\\d+"))) {
                    findings.add(SecurityFinding(
                        severity = FindingSeverity.INFO,
                        category = FindingCategory.PORTS,
                        title = UiText(R.string.f_version_title),
                        description = UiText(R.string.f_version_desc, result.serviceName, result.ip, banner.take(80)),
                        target = "${result.ip}:${result.port}",
                        recommendation = UiText(R.string.f_version_rec),
                        kind = "version",
                    ))
                }
            }
        }

        return findings
    }
}
