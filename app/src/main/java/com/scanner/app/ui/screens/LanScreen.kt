package com.scanner.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.util.*
import kotlinx.coroutines.launch

@Composable
fun LanScreen() {
    val context = LocalContext.current
    val discovery = remember { NetworkDiscovery(context) }
    val pingUtil = remember { PingUtil(context) }
    val repository = remember { DeviceRepository(context) }
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<LanDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<LanScanProgress?>(null) }
    var networkInfo by remember { mutableStateOf<NetworkInfo?>(null) }

    // Port scan state: hoisted so it survives scrolling/recomposition
    var portScanResults by remember { mutableStateOf<Map<String, List<PortScanResult>>>(emptyMap()) }
    var portScanningIp by remember { mutableStateOf<String?>(null) }
    var portScanProgress by remember { mutableStateOf<PortScanProgress?>(null) }
    val portScanner = remember { PortScanner() }

    DisposableEffect(Unit) {
        onDispose { discovery.stopScan() }
    }

    fun doScan() {
        isScanning = true
        try {
            networkInfo = pingUtil.getNetworkInfo()
        } catch (e: Exception) {
            android.util.Log.e("LanScreen", "Error getting network info", e)
        }
        scope.launch {
            try {
                val result = discovery.fullScan(
                    onProgress = { try { progress = it } catch (_: Exception) {} },
                    onDeviceFound = { try { devices = it } catch (_: Exception) {} }
                )
                devices = result

                // Persist LAN devices to Room DB
                try {
                    repository.persistLanScan(result)
                } catch (e: Exception) {
                    android.util.Log.e("LanScreen", "Error persisting LAN scan", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("LanScreen", "Error in LAN scan", e)
            } finally {
                isScanning = false
                hasScanned = true
                progress = null
            }
        }
    }

    fun startPortScan(ip: String, ports: List<Int>) {
        portScanningIp = ip
        scope.launch {
            try {
                val results = portScanner.scan(
                    ip = ip,
                    ports = ports,
                    grabBanners = true,
                    onProgress = { portScanProgress = it }
                )
                portScanResults = portScanResults + (ip to results)

                // Persist port results to device metadata
                try {
                    repository.persistPortScanResults(ip, results)
                } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.e("LanScreen", "Port scan error", e)
            }
            portScanningIp = null
            portScanProgress = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Header ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LAN-Geräte",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasScanned) {
                    Text(
                        text = "${devices.size} Geräte im Netzwerk",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = { doScan() },
                enabled = !isScanning
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Scannen")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Scanne..." else "Scannen")
            }
        }

        // ─── Progress ───────────────────────────────────────────
        if (isScanning && progress != null) {
            val p = progress!!
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                LinearProgressIndicator(
                    progress = { p.current.toFloat() / p.total.coerceAtLeast(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${p.phase} (${p.devicesFound} gefunden)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ─── Network info card ──────────────────────────────────
        networkInfo?.let { info ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Netzwerk-Info",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoChip("SSID", info.ssid ?: "—", Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoChip("Eigene IP", info.deviceIp ?: "—", Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoChip("Gateway", info.gatewayIp ?: "—", Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        InfoChip("DNS", info.dns ?: "—", Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ─── Device list ────────────────────────────────────────
        if (devices.isEmpty() && !isScanning && !hasScanned) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Lan,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tippe auf \"Scannen\" um\nGeräte im lokalen Netzwerk zu finden.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ARP-Tabelle · Ping-Sweep · NetBIOS · mDNS · UPnP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else if (devices.isEmpty() && hasScanned) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine Geräte gefunden.\nBist du mit einem WLAN verbunden?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(devices, key = { it.ip }) { device ->
                    LanDeviceCard(
                        device = device,
                        portResults = portScanResults[device.ip] ?: emptyList(),
                        hasBeenPortScanned = device.ip in portScanResults,
                        isPortScanning = portScanningIp == device.ip,
                        portProgress = if (portScanningIp == device.ip) portScanProgress else null,
                        onPortScan = { ports -> startPortScan(device.ip, ports) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// ─── LAN Device Card ────────────────────────────────────────────

@Composable
fun LanDeviceCard(
    device: LanDevice,
    portResults: List<PortScanResult>,
    hasBeenPortScanned: Boolean,
    isPortScanning: Boolean,
    portProgress: PortScanProgress?,
    onPortScan: (List<Int>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val icon = when {
        device.isGateway -> Icons.Outlined.Router
        device.isOwnDevice -> Icons.Outlined.PhoneAndroid
        device.services.any { it.type.contains("printer") || it.type.contains("ipp") } ->
            Icons.Outlined.Print
        device.services.any { it.type.contains("airplay") || it.type.contains("raop") } ->
            Icons.Outlined.Speaker
        device.services.any { it.type.contains("googlecast") } ->
            Icons.Outlined.Cast
        device.services.any { it.type.contains("smb") } ->
            Icons.Outlined.Storage
        device.services.any { it.type.contains("ssh") } ->
            Icons.Outlined.Terminal
        device.services.any { it.type.contains("http") } ->
            Icons.Outlined.Language
        device.vendor?.contains("Raspberry") == true -> Icons.Outlined.DeveloperBoard
        device.vendor?.contains("ESP") == true -> Icons.Outlined.Memory
        else -> Icons.Outlined.Devices
    }

    val accentColor = when {
        device.isGateway -> MaterialTheme.colorScheme.primary
        device.isOwnDevice -> MaterialTheme.colorScheme.tertiary
        device.services.isNotEmpty() -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (device.isGateway || device.isOwnDevice)
                accentColor.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.hostname
                                ?: device.vendor
                                ?: device.ip,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (device.isGateway) {
                            Spacer(modifier = Modifier.width(6.dp))
                            RoleChip("Gateway", accentColor)
                        }
                        if (device.isOwnDevice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            RoleChip("Dieses Gerät", accentColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.ip,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        device.vendor?.let {
                            Text(
                                text = " · $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Method + latency
                Column(horizontalAlignment = Alignment.End) {
                    device.latencyMs?.let { ms ->
                        val latencyColor = when {
                            ms < 5f -> Color(0xFF4CAF50)
                            ms < 30f -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                        Text(
                            text = "${"%.0f".format(ms)} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = latencyColor
                        )
                    }
                    if (device.services.isNotEmpty()) {
                        Text(
                            text = "${device.services.size} Dienste",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (portResults.isNotEmpty()) {
                        Text(
                            text = "${portResults.size} Ports offen",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE64A19)
                        )
                    }
                }
            }

            // ─── Expanded details ───────────────────────────────
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(10.dp))

                // MAC + Vendor
                device.mac?.let { mac ->
                    DetailRowMono("MAC", mac)
                    MacVendorLookup.lookup(mac)?.let { fullVendor ->
                        DetailRowMono("Hersteller", fullVendor)
                    }
                }

                device.hostname?.let {
                    DetailRowMono("Hostname", it)
                }

                DetailRowMono("Entdeckt via", device.discoveredVia.displayName())

                device.latencyMs?.let {
                    DetailRowMono("Latenz", "${"%.1f".format(it)} ms")
                }

                // Services (mDNS)
                if (device.services.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "DIENSTE (mDNS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    device.services.forEach { service ->
                        ServiceRow(service)
                    }
                }

                // UPnP info
                device.upnpInfo?.let { upnp ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "UPnP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF42A5F5)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    upnp.friendlyName?.let { DetailRowMono("Name", it) }
                    upnp.manufacturer?.let { DetailRowMono("Hersteller", it) }
                    upnp.modelName?.let { DetailRowMono("Modell", it) }
                    upnp.modelDescription?.let { DetailRowMono("Beschreibung", it) }
                    upnp.deviceType?.let { DetailRowMono("Gerätetyp", it) }
                    if (upnp.services.isNotEmpty()) {
                        DetailRowMono("UPnP-Dienste", upnp.services.joinToString(", "))
                    }
                }

                // ─── Port Scanner ───────────────────────────────
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasBeenPortScanned) "PORT-SCAN (${portResults.size} offen)" else "PORT-SCAN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onPortScan(WellKnownPorts.QUICK_20) },
                        enabled = !isPortScanning,
                        modifier = Modifier.height(28.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        if (isPortScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        } else {
                            Text("Top 20", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    FilledTonalButton(
                        onClick = { onPortScan(WellKnownPorts.TOP_50) },
                        enabled = !isPortScanning,
                        modifier = Modifier.height(28.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Top 50", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(
                        onClick = { onPortScan(WellKnownPorts.TOP_200) },
                        enabled = !isPortScanning,
                        modifier = Modifier.height(28.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Top 200", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(
                        onClick = { onPortScan(WellKnownPorts.ALL_PORTS) },
                        enabled = !isPortScanning,
                        modifier = Modifier.height(28.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Alle", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Port scan progress
                if (isPortScanning) {
                    portProgress?.let { p ->
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { p.scanned.toFloat() / p.total.coerceAtLeast(1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = buildString {
                                val pct = (p.scanned * 100 / p.total.coerceAtLeast(1))
                                append("Port ${p.currentPort} · $pct% (${p.scanned}/${p.total}) · ${p.openPorts} offen")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Port results
                if (portResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    portResults.forEach { port ->
                        val risk = WellKnownPorts.riskLevel(port.port)
                        val riskColor = when (risk) {
                            PortRisk.CRITICAL -> Color(0xFFD32F2F)
                            PortRisk.HIGH -> Color(0xFFE64A19)
                            PortRisk.MEDIUM -> Color(0xFFF57C00)
                            PortRisk.LOW -> Color(0xFFFBC02D)
                            PortRisk.INFO -> Color(0xFF42A5F5)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(riskColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${port.port}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(44.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${port.serviceName} (${risk.label})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                port.banner?.let {
                                    Text(
                                        text = it.take(60),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            port.latencyMs?.let {
                                Text(
                                    text = "${"%.0f".format(it)}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Browser button for detected HTTP
                            WellKnownPorts.browseUrl(port)?.let { url ->
                                Spacer(modifier = Modifier.width(4.dp))
                                val context = LocalContext.current
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            )
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.OpenInBrowser,
                                        contentDescription = "Im Browser öffnen",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (hasBeenPortScanned && portResults.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Keine offenen Ports gefunden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Sub-components ─────────────────────────────────────────────

@Composable
fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun RoleChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun DetailRowMono(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = if (value.contains(":") || value.contains("."))
                FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun ServiceRow(service: LanService) {
    val serviceIcon = when {
        service.type.contains("http") -> Icons.Outlined.Language
        service.type.contains("printer") || service.type.contains("ipp") -> Icons.Outlined.Print
        service.type.contains("ssh") -> Icons.Outlined.Terminal
        service.type.contains("smb") -> Icons.Outlined.FolderShared
        service.type.contains("ftp") -> Icons.Outlined.CloudUpload
        service.type.contains("airplay") || service.type.contains("raop") -> Icons.Outlined.Speaker
        service.type.contains("googlecast") -> Icons.Outlined.Cast
        service.type.contains("spotify") -> Icons.Outlined.MusicNote
        service.type.contains("homekit") -> Icons.Outlined.Home
        else -> Icons.Outlined.Dns
    }

    val friendlyType = when {
        service.type.contains("_http._tcp") -> "Webserver"
        service.type.contains("_https._tcp") -> "HTTPS"
        service.type.contains("_printer._tcp") -> "Drucker"
        service.type.contains("_ipp._tcp") -> "Drucker (IPP)"
        service.type.contains("_ssh._tcp") -> "SSH"
        service.type.contains("_smb._tcp") -> "Dateifreigabe"
        service.type.contains("_ftp._tcp") -> "FTP"
        service.type.contains("_airplay._tcp") -> "AirPlay"
        service.type.contains("_raop._tcp") -> "AirPlay Audio"
        service.type.contains("_googlecast._tcp") -> "Chromecast"
        service.type.contains("_spotify-connect._tcp") -> "Spotify Connect"
        service.type.contains("_homekit._tcp") -> "HomeKit"
        else -> service.type
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = serviceIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = service.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = friendlyType,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = ":${service.port}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
