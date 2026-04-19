package com.scanner.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.components.HeaderStat
import com.scanner.app.ui.components.SpectrumHeader
import com.scanner.app.ui.components.SpectrumKicker
import com.scanner.app.ui.components.SpectrumScanButton
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.util.LanDevice
import com.scanner.app.util.LanScanProgress
import com.scanner.app.util.LanService
import com.scanner.app.util.MacVendorLookup
import com.scanner.app.util.NetworkDiscovery
import com.scanner.app.util.NetworkInfo
import com.scanner.app.util.PingUtil
import com.scanner.app.util.PortRisk
import com.scanner.app.util.PortScanProgress
import com.scanner.app.util.PortScanResult
import com.scanner.app.util.PortScanner
import com.scanner.app.util.WellKnownPorts
import kotlinx.coroutines.launch

private val WARN_PORTS = setOf(22, 23, 80)

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
    var portScanResults by remember { mutableStateOf<Map<String, List<PortScanResult>>>(emptyMap()) }
    var portScanningIp by remember { mutableStateOf<String?>(null) }
    var portScanProgress by remember { mutableStateOf<PortScanProgress?>(null) }

    val favorites by repository.observeFavorites().collectAsState(initial = emptyList())
    val favoriteAddresses = favorites.map { it.address }.toSet()

    val portScanner = remember { PortScanner() }

    DisposableEffect(Unit) { onDispose { discovery.stopScan() } }

    fun doScan() {
        isScanning = true
        try { networkInfo = pingUtil.getNetworkInfo() } catch (e: Exception) {
            android.util.Log.e("LanScreen", "Error getting network info", e)
        }
        scope.launch {
            try {
                val result = discovery.fullScan(
                    onProgress = { try { progress = it } catch (_: Exception) {} },
                    onDeviceFound = { try { devices = it } catch (_: Exception) {} },
                )
                devices = result
                try { repository.persistLanScan(result) } catch (e: Exception) {
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
                    onProgress = { portScanProgress = it },
                )
                portScanResults = portScanResults + (ip to results)
                try { repository.persistPortScanResults(ip, results) } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.e("LanScreen", "Port scan error", e)
            }
            portScanningIp = null
            portScanProgress = null
        }
    }

    val totalOpenPorts = portScanResults.values.sumOf { it.size }
    val subnet = networkInfo?.deviceIp
        ?.substringBeforeLast(".")
        ?.let { "$it.0/24" } ?: "—"

    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {
        SpectrumHeader(
            kicker = "LAN",
            subtitle = "Local Net",
            stats = listOf(
                HeaderStat(devices.size.toString(), "hosts"),
                HeaderStat(if (totalOpenPorts > 0) totalOpenPorts.toString() else "—", "ports"),
                HeaderStat(subnet, "subnet"),
            ),
            trailing = {
                SpectrumScanButton(scanning = isScanning, onClick = { if (!isScanning) doScan() })
            },
        )

        if (isScanning && progress != null) {
            LanProgressBar(progress!!)
        }

        when {
            devices.isEmpty() && !hasScanned -> LanEmptyState(hasScanned = false)
            devices.isEmpty() && hasScanned -> LanEmptyState(hasScanned = true)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(devices, key = { it.ip }) { device ->
                    val address = device.mac ?: "lan:${device.ip}"
                    val isFavorite = address in favoriteAddresses
                    LanDeviceRow(
                        device = device,
                        portResults = portScanResults[device.ip] ?: emptyList(),
                        hasBeenPortScanned = device.ip in portScanResults,
                        isPortScanning = portScanningIp == device.ip,
                        portProgress = if (portScanningIp == device.ip) portScanProgress else null,
                        isFavorite = isFavorite,
                        onToggleFavorite = { scope.launch { repository.toggleFavoriteByAddress(address) } },
                        onPortScan = { ports -> startPortScan(device.ip, ports) },
                    )
                    HairlineHorizontal()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LanProgressBar(progress: LanScanProgress) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        val pct = progress.current.toFloat() / progress.total.coerceAtLeast(1)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Spectrum.GridLine)) {
            Box(Modifier.fillMaxWidth(pct).height(1.dp).background(Spectrum.Accent))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${progress.phase} · ${progress.devicesFound} gefunden",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurfaceDim,
            letterSpacing = 0.1.em,
        )
    }
}

@Composable
private fun LanEmptyState(hasScanned: Boolean) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpectrumKicker(
                if (hasScanned) "KEINE GERÄTE" else "KEIN SCAN · ARP · PING · MDNS · UPNP",
                color = Spectrum.OnSurfaceDim,
            )
            Text(
                if (hasScanned)
                    "Keine Geräte gefunden.\nBist du mit einem WLAN verbunden?"
                else
                    "Tippe auf SCAN um Geräte im lokalen Netzwerk zu finden.",
                color = Spectrum.OnSurfaceDim,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanDeviceRow(
    device: LanDevice,
    portResults: List<PortScanResult>,
    hasBeenPortScanned: Boolean,
    isPortScanning: Boolean,
    portProgress: PortScanProgress?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPortScan: (List<Int>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when {
        device.isGateway -> Icons.Outlined.Router
        device.isOwnDevice -> Icons.Outlined.PhoneAndroid
        device.services.any { it.type.contains("printer") || it.type.contains("ipp") } -> Icons.Outlined.Print
        device.services.any { it.type.contains("airplay") || it.type.contains("raop") } -> Icons.Outlined.Speaker
        device.services.any { it.type.contains("googlecast") } -> Icons.Outlined.Cast
        device.services.any { it.type.contains("smb") } -> Icons.Outlined.Storage
        device.services.any { it.type.contains("ssh") } -> Icons.Outlined.Terminal
        device.services.any { it.type.contains("http") } -> Icons.Outlined.Language
        device.vendor?.contains("Raspberry", ignoreCase = true) == true -> Icons.Outlined.DeveloperBoard
        device.vendor?.contains("ESP", ignoreCase = true) == true -> Icons.Outlined.Memory
        else -> Icons.Outlined.Devices
    }

    // Port scan results take priority; fall back to mDNS service ports
    val displayPorts: List<Int> = if (portResults.isNotEmpty()) {
        portResults.map { it.port }
    } else {
        device.services.map { it.port }.filter { it > 0 }
    }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 32dp icon tile
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Spectrum.SurfaceRaised)
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Spectrum.Accent,
                    modifier = Modifier.size(16.dp),
                )
            }

            // IP + name/vendor meta
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.ip,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 14.sp,
                    color = Spectrum.OnSurface,
                    maxLines = 1,
                )
                val meta = buildString {
                    append(device.hostname ?: device.vendor ?: "—")
                    if (device.hostname != null && device.vendor != null) {
                        append(" · ${device.vendor}")
                    }
                }
                Text(
                    text = meta,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Port chips: first 4, then overflow count
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "Favorit",
                        tint = Spectrum.Accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                displayPorts.take(4).forEach { port -> LanPortChip(port) }
                if (displayPorts.size > 4) {
                    Text(
                        "+${displayPorts.size - 4}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = Spectrum.OnSurfaceDim,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }

        if (expanded) {
            LanDeviceDetail(
                device = device,
                portResults = portResults,
                hasBeenPortScanned = hasBeenPortScanned,
                isPortScanning = isPortScanning,
                portProgress = portProgress,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPortScan = onPortScan,
            )
        }
    }
}

@Composable
private fun LanPortChip(port: Int) {
    val risk = port in WARN_PORTS
    Text(
        text = port.toString(),
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Spectrum.SurfaceRaised)
            .border(
                1.dp,
                if (risk) Spectrum.Warning else Spectrum.GridLine,
                RoundedCornerShape(2.dp),
            )
            .padding(horizontal = 5.dp, vertical = 3.dp),
        color = if (risk) Spectrum.Warning else Spectrum.OnSurfaceDim,
        fontFamily = JetBrainsMonoFamily,
        fontSize = 9.sp,
        letterSpacing = 0.04.em,
    )
}

@Composable
private fun LanDeviceDetail(
    device: LanDevice,
    portResults: List<PortScanResult>,
    hasBeenPortScanned: Boolean,
    isPortScanning: Boolean,
    portProgress: PortScanProgress?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPortScan: (List<Int>) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        device.mac?.let { LanDetailRow("MAC", it) }
        device.mac?.let { mac ->
            MacVendorLookup.lookup(mac)?.let { LanDetailRow("Hersteller", it) }
        }
        device.hostname?.let { LanDetailRow("Hostname", it) }
        LanDetailRow("Entdeckt via", device.discoveredVia.displayName())
        device.latencyMs?.let { LanDetailRow("Latenz", "${"%.1f".format(it)} ms") }
        if (device.isGateway) LanDetailRow("Rolle", "Gateway")
        if (device.isOwnDevice) LanDetailRow("Rolle", "Dieses Gerät")

        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Favorit", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = Spectrum.OnSurfaceDim)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) Spectrum.Accent else Spectrum.OnSurface,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (device.services.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SpectrumKicker("DIENSTE (mDNS) · ${device.services.size}", color = Spectrum.OnSurfaceDim)
            Spacer(Modifier.height(4.dp))
            device.services.forEach { LanServiceRow(it) }
        }

        device.upnpInfo?.let { upnp ->
            Spacer(Modifier.height(4.dp))
            SpectrumKicker("UPNP", color = Spectrum.Accent2)
            Spacer(Modifier.height(4.dp))
            upnp.friendlyName?.let { LanDetailRow("Name", it) }
            upnp.manufacturer?.let { LanDetailRow("Hersteller", it) }
            upnp.modelName?.let { LanDetailRow("Modell", it) }
            upnp.modelDescription?.let { LanDetailRow("Beschreibung", it) }
            upnp.deviceType?.let { LanDetailRow("Gerätetyp", it) }
            if (upnp.services.isNotEmpty()) {
                LanDetailRow("UPnP-Dienste", upnp.services.joinToString(", "))
            }
        }

        Spacer(Modifier.height(6.dp))
        HairlineHorizontal()
        Spacer(Modifier.height(8.dp))

        SpectrumKicker(
            text = if (hasBeenPortScanned) "PORT-SCAN · ${portResults.size} OFFEN" else "PORT-SCAN",
            color = Spectrum.OnSurfaceDim,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Top 20" to WellKnownPorts.QUICK_20,
                "Top 50" to WellKnownPorts.TOP_50,
                "Top 200" to WellKnownPorts.TOP_200,
                "Alle" to WellKnownPorts.ALL_PORTS,
            ).forEach { (label, ports) ->
                LanActionChip(
                    label = label,
                    enabled = !isPortScanning,
                    danger = label == "Alle",
                    onClick = { onPortScan(ports) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (isPortScanning && portProgress != null) {
            Spacer(Modifier.height(6.dp))
            val pct = portProgress.scanned.toFloat() / portProgress.total.coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Spectrum.GridLine)) {
                Box(Modifier.fillMaxWidth(pct).height(1.dp).background(Spectrum.Accent))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "Port ${portProgress.currentPort} · ${(pct * 100).toInt()}% · ${portProgress.openPorts} offen",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }

        if (portResults.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            portResults.forEach { LanOpenPortRow(it) }
        } else if (hasBeenPortScanned) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Keine offenen Ports gefunden.",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }
    }
}

@Composable
private fun LanDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurfaceDim,
        )
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun LanServiceRow(service: LanService) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            service.name.ifBlank { service.type },
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            ":${service.port}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LanActionChip(
    label: String,
    enabled: Boolean,
    danger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        !enabled -> Spectrum.GridLine
        danger -> Spectrum.Danger.copy(alpha = 0.5f)
        else -> Spectrum.GridLine
    }
    val textColor = when {
        !enabled -> Spectrum.OnSurfaceFaint
        danger -> Spectrum.Danger
        else -> Spectrum.OnSurfaceDim
    }
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Spectrum.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = textColor,
            letterSpacing = 0.1.em,
        )
    }
}

@Composable
private fun LanOpenPortRow(port: PortScanResult) {
    val context = LocalContext.current
    val riskColor = when (WellKnownPorts.riskLevel(port.port)) {
        PortRisk.CRITICAL -> Spectrum.Danger
        PortRisk.HIGH -> Spectrum.SeverityHigh
        PortRisk.MEDIUM -> Spectrum.Warning
        PortRisk.LOW -> Spectrum.SeverityLow
        PortRisk.INFO -> Spectrum.OnSurfaceDim
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(riskColor))
        Text(
            "${port.port}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = riskColor,
            modifier = Modifier.width(40.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${port.serviceName} · ${WellKnownPorts.riskLevel(port.port).label}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = Spectrum.OnSurface,
            )
            port.banner?.let {
                Text(
                    it.take(60),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = Spectrum.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        port.latencyMs?.let {
            Text(
                "${"%.0f".format(it)}ms",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }
        WellKnownPorts.browseUrl(port)?.let { url ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(2.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
                Text("↗", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = Spectrum.OnSurfaceDim)
            }
        }
    }
}
