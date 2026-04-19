package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.R
import com.scanner.app.data.WifiNetwork
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.components.BlinkingDot
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.components.HeaderStat
import com.scanner.app.ui.components.SignalTrace
import com.scanner.app.ui.components.SpectrumFilterChip
import com.scanner.app.ui.components.SpectrumHeader
import com.scanner.app.ui.components.SpectrumKicker
import com.scanner.app.ui.components.SpectrumScanButton
import com.scanner.app.ui.components.rssiColor
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.util.WardrivingTracker
import com.scanner.app.util.WifiScanner
import kotlinx.coroutines.launch

private fun WifiNetwork.isRisk() =
    securityType.equals("Open", ignoreCase = true) ||
    securityType.contains("WEP", ignoreCase = true) ||
    wpsEnabled

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WifiScreen() {
    val context = LocalContext.current
    val wifiScanner = remember { WifiScanner(context) }
    val repository = remember { DeviceRepository(context) }
    val wardrivingTracker = remember { WardrivingTracker(context) }
    val scope = rememberCoroutineScope()

    var networks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var gpsEnabled by remember { mutableStateOf(false) }
    var geoTagCount by remember { mutableStateOf(0) }
    var uniqueGeoNetworks by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("all") }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    DisposableEffect(Unit) {
        onDispose { wifiScanner.cleanup(); wardrivingTracker.cleanup() }
    }

    fun doScan() {
        if (!wifiScanner.isWifiEnabled() || !permissionState.allPermissionsGranted) return
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
            try {
                scope.launch {
                    try {
                        repository.persistWifiScan(
                            networks = results,
                            durationMs = System.currentTimeMillis() - startTime,
                            location = if (gpsEnabled) wardrivingTracker.getCurrentLocation() else null,
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("WifiScreen", "Error persisting scan", e)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val displayed = remember(networks, filter) {
        networks.filter { n ->
            when (filter) {
                "2.4" -> n.band.contains("2.4")
                "5"   -> n.band.contains("5")
                "risk" -> n.isRisk()
                else  -> true
            }
        }
    }

    val riskCount   = remember(networks) { networks.count { it.isRisk() } }
    val count24     = remember(networks) { networks.count { it.band.contains("2.4") } }
    val count5      = remember(networks) { networks.count { it.band.contains("5") } }

    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {

        SpectrumHeader(
            kicker = "WIFI",
            subtitle = "Airspace",
            scanning = isScanning,
            onScan = {
                if (!permissionState.allPermissionsGranted) permissionState.launchMultiplePermissionRequest()
                else doScan()
            },
            stats = if (hasScanned) listOf(
                HeaderStat(networks.size.toString(), "found"),
                HeaderStat(count24.toString(), "2.4GHz"),
                HeaderStat(count5.toString(), "5GHz"),
                HeaderStat(riskCount.toString(), "risks"),
            ) else emptyList(),
        )

        // Permission banner
        if (!permissionState.allPermissionsGranted) {
            WifiBanner(
                text = stringResource(R.string.perm_required_title) + " — " +
                       stringResource(R.string.perm_required_desc),
                color = Spectrum.Danger,
                action = stringResource(R.string.perm_grant_btn),
                onAction = { permissionState.launchMultiplePermissionRequest() },
            )
        }

        // WiFi disabled banner
        if (!wifiScanner.isWifiEnabled()) {
            WifiBanner(
                text = stringResource(R.string.wifi_disabled_warn),
                color = Spectrum.Warning,
            )
        }

        // Filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpectrumFilterChip("ALL",    filter == "all",  { filter = "all" },  count = networks.size)
            SpectrumFilterChip("2.4GHZ", filter == "2.4",  { filter = "2.4" },  count = count24)
            SpectrumFilterChip("5GHZ",   filter == "5",    { filter = "5" },    count = count5)
            SpectrumFilterChip("⚠ RISK", filter == "risk", { filter = "risk" }, count = riskCount)
        }
        HairlineHorizontal()

        // GPS / wardriving strip
        WifiGpsStrip(
            gpsEnabled = gpsEnabled,
            geoTagCount = geoTagCount,
            uniqueGeoNetworks = uniqueGeoNetworks,
            onToggle = { enabled ->
                gpsEnabled = enabled
                if (enabled) wardrivingTracker.startTracking() else wardrivingTracker.stopTracking()
            },
            onExport = {
                scope.launch {
                    try {
                        val csvFile = java.io.File(context.cacheDir, "wardriving.csv")
                        wardrivingTracker.exportWigleCsv(csvFile)
                        val kmlFile = java.io.File(context.cacheDir, "wardriving.kml")
                        wardrivingTracker.exportKml(kmlFile)
                        val csvUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", csvFile)
                        val kmlUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", kmlFile)
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, arrayListOf(csvUri, kmlUri))
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.export_wardriving)))
                    } catch (e: Exception) {
                        android.util.Log.e("WifiScreen", "Export error", e)
                    }
                }
            },
        )
        HairlineHorizontal()

        // List / empty states
        when {
            displayed.isEmpty() && !hasScanned -> WifiEmptyState(stringResource(R.string.wifi_prompt_scan))
            displayed.isEmpty() && hasScanned -> WifiEmptyState(stringResource(R.string.wifi_none_found))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(displayed, key = { it.bssid }) { network ->
                    WifiRow(network)
                    HairlineHorizontal()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────

@Composable
private fun WifiBanner(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.06f))
            .border(width = 0.dp, color = androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, color, RoundedCornerShape(2.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(action, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = color)
            }
        }
    }
    HairlineHorizontal(color = color.copy(alpha = 0.2f))
}

@Composable
private fun WifiGpsStrip(
    gpsEnabled: Boolean,
    geoTagCount: Int,
    uniqueGeoNetworks: Int,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlinkingDot(color = if (gpsEnabled) Spectrum.Accent else Spectrum.OnSurfaceFaint, blink = gpsEnabled, size = 6.dp)
            Text(
                if (gpsEnabled && geoTagCount > 0) "GPS · $geoTagCount fixes · $uniqueGeoNetworks nets"
                else if (gpsEnabled) "GPS · warte auf Fix..."
                else "GPS WARDRIVING",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = if (gpsEnabled) Spectrum.Accent else Spectrum.OnSurfaceDim,
                letterSpacing = 0.1.em,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (gpsEnabled && geoTagCount > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, Spectrum.AccentDim, RoundedCornerShape(2.dp))
                        .clickable { onExport() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Spectrum.Accent, modifier = Modifier.size(12.dp))
                }
            }
            Switch(
                checked = gpsEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.height(20.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Spectrum.Surface,
                    checkedTrackColor = Spectrum.Accent,
                    uncheckedThumbColor = Spectrum.OnSurfaceDim,
                    uncheckedTrackColor = Spectrum.SurfaceRaised,
                    uncheckedBorderColor = Spectrum.GridLine,
                ),
            )
        }
    }
}

@Composable
private fun WifiEmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpectrumKicker("NO SIGNAL", color = Spectrum.OnSurfaceDim)
            Text(message, color = Spectrum.OnSurfaceDim, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WifiRow(network: WifiNetwork) {
    var expanded by remember { mutableStateOf(false) }
    val risk = network.isRisk()

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .background(if (network.isConnected) Spectrum.Accent.copy(alpha = 0.04f) else Spectrum.Surface)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left: RSSI number + trace
            Column(Modifier.width(66.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${network.signalStrength}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 18.sp,
                        color = rssiColor(network.signalStrength),
                        letterSpacing = (-0.02).em,
                    )
                    Text(
                        " dBm",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = Spectrum.OnSurfaceDim,
                    )
                }
                SignalTrace(rssi = network.signalStrength, modifier = Modifier.fillMaxWidth().height(12.dp))
            }

            // Middle: SSID + meta
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (network.isConnected) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Spectrum.Accent))
                    }
                    val isHidden = network.ssid.isBlank() || network.ssid == "(hidden)"
                    Text(
                        text = if (isHidden) "(hidden)" else network.ssid,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 15.sp,
                        color = if (isHidden) Spectrum.OnSurfaceDim else Spectrum.OnSurface,
                        fontStyle = if (isHidden) FontStyle.Italic else FontStyle.Normal,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        letterSpacing = (-0.01).em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    buildString {
                        append(network.band)
                        append(" · CH${network.channel}")
                        network.wifiStandard?.let { append(" · $it") }
                        network.vendor?.let { append(" · $it") }
                    },
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.04.em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right: security + distance
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (risk) "⚠" else "•"} ${network.securityType}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = if (risk) Spectrum.Danger else Spectrum.OnSurfaceDim,
                    letterSpacing = 0.1.em,
                    maxLines = 1,
                )
                network.distance?.let {
                    Text(
                        "~${"%.1f".format(it)}m",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = Spectrum.OnSurfaceFaint,
                    )
                }
            }
        }

        if (expanded) {
            WifiRowDetail(network)
        }
    }
}

@Composable
private fun WifiRowDetail(network: WifiNetwork) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        WifiDetailRow("BSSID", network.bssid)
        WifiDetailRow("KANAL", "${network.channel} (${network.band})")
        WifiDetailRow("FREQUENZ", "${network.frequency} MHz")
        network.channelWidth?.let { WifiDetailRow("BREITE", it) }
        network.wifiStandard?.let { WifiDetailRow("STANDARD", it) }
        network.vendor?.let { WifiDetailRow("HERSTELLER", it) }
        WifiDetailRow("SICHERHEIT", network.securityType)
        WifiDetailRow("WPS", if (network.wpsEnabled) "⚠ AKTIVIERT" else "AUS")
        network.distance?.let { WifiDetailRow("DISTANZ", "~${"%.1f".format(it)}m") }
        if (network.isConnected) WifiDetailRow("STATUS", "VERBUNDEN")
        if (network.rawCapabilities.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                network.rawCapabilities,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = Spectrum.OnSurfaceFaint,
                lineHeight = 13.sp,
            )
        }
    }
}

@Composable
private fun WifiDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = Spectrum.OnSurfaceDim)
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = if (value.startsWith("⚠")) Spectrum.Danger else Spectrum.OnSurface,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
