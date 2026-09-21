package com.isochron.audit.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.isochron.audit.R
import com.isochron.audit.data.WifiNetwork
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.ui.components.BlinkingDot
import com.isochron.audit.ui.components.HairlineHorizontal
import com.isochron.audit.ui.components.HeaderStat
import com.isochron.audit.ui.components.PermissionBanner
import com.isochron.audit.ui.components.SignalTrace
import com.isochron.audit.ui.components.SpectrumFilterChip
import com.isochron.audit.ui.components.SpectrumHeader
import com.isochron.audit.ui.components.SpectrumKicker
import com.isochron.audit.ui.components.SpectrumScanButton
import com.isochron.audit.ui.components.WifiDisabledBanner
import com.isochron.audit.ui.components.rememberScanPermissions
import com.isochron.audit.ui.components.rssiColor
import com.isochron.audit.ui.theme.JetBrainsMonoFamily
import com.isochron.audit.ui.theme.Spectrum
import com.isochron.audit.ui.viewmodel.WifiViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WifiScreen(vm: WifiViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val networks = vm.networks
    val isScanning = vm.isScanning
    val hasScanned = vm.hasScanned
    val gpsEnabled = vm.gpsEnabled
    val geoTagCount = vm.geoTagCount
    val uniqueGeoNetworks = vm.uniqueGeoNetworks
    val filter = vm.filter

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val scanPermissions = rememberScanPermissions(permissions) { vm.scan() }
    val wifiEnabled by vm.wifiEnabled.collectAsState()

    val displayed = remember(networks, filter) {
        networks.filter { n ->
            when (filter) {
                "2.4" -> n.band.contains("2.4")
                "5"   -> n.band.contains("5 GHz")
                "6"   -> n.band.contains("6 GHz")
                "risk" -> n.isRisk()
                else  -> true
            }
        }
    }

    // ⚡ Bolt Performance Optimization:
    // Ensure `isRisk` and counts are memoized correctly (already done nicely here!).
    val riskCount   = remember(networks) { networks.count { it.isRisk() } }
    val count24     = remember(networks) { networks.count { it.band.contains("2.4") } }
    val count5      = remember(networks) { networks.count { it.band.contains("5 GHz") } }
    val count6      = remember(networks) { networks.count { it.band.contains("6 GHz") } }

    val favorites by vm.repository.observeFavorites().collectAsState(initial = emptyList())
    // ⚡ Bolt Performance Optimization:
    // Remember the mapped favorites Set to avoid creating a new Set on every recomposition, reducing GC pressure.
    val favoriteAddresses = remember(favorites) { favorites.map { it.address }.toSet() }

    vm.selectedNetwork?.let { network ->
        BackHandler { vm.selectedNetwork = null }
        val favEntity by vm.repository.observeDeviceByAddress(network.bssid).collectAsState(initial = null)
        WifiDetailScreen(
            network = network,
            isFavorite = favEntity?.isFavorite == true,
            onClose = { vm.selectedNetwork = null },
            onToggleFavorite = { vm.toggleFavorite(network.bssid) },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {

        SpectrumHeader(
            kicker = "WIFI",
            subtitle = "Airspace",
            scanning = isScanning,
            onScan = { scanPermissions.runOrRequest { vm.scan() } },
            stats = if (hasScanned) listOf(
                HeaderStat(networks.size.toString(), "found"),
                HeaderStat(count24.toString(), "2.4GHz"),
                HeaderStat(count5.toString(), "5GHz"),
                HeaderStat(riskCount.toString(), "risks"),
            ) else emptyList(),
        )

        PermissionBanner(
            permissions = scanPermissions,
            text = stringResource(R.string.perm_required_title) + " — " +
                stringResource(R.string.perm_required_desc),
        )
        if (!wifiEnabled) WifiDisabledBanner()

        // Filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpectrumFilterChip("ALL",    filter == "all",  { vm.filter = "all" },  count = networks.size)
            SpectrumFilterChip("2.4GHZ", filter == "2.4",  { vm.filter = "2.4" },  count = count24)
            SpectrumFilterChip("5GHZ",   filter == "5",    { vm.filter = "5" },    count = count5)
            // Keep the chip while it is the active filter, so a rescan without 6 GHz
            // networks cannot strand the user in a filter they can no longer clear.
            if (count6 > 0 || filter == "6") {
                SpectrumFilterChip("6GHZ", filter == "6",  { vm.filter = "6" },  count = count6)
            }
            SpectrumFilterChip("⚠ RISK", filter == "risk", { vm.filter = "risk" }, count = riskCount)
        }
        HairlineHorizontal()

        // GPS / wardriving strip
        WifiGpsStrip(
            gpsEnabled = gpsEnabled,
            geoTagCount = geoTagCount,
            uniqueGeoNetworks = uniqueGeoNetworks,
            onToggle = { enabled -> vm.toggleGps(enabled) },
            onExport = {
                scope.launch {
                    try {
                        val csvFile = java.io.File(context.cacheDir, "wardriving.csv")
                        vm.wardrivingTracker.exportWigleCsv(csvFile)
                        val kmlFile = java.io.File(context.cacheDir, "wardriving.kml")
                        vm.wardrivingTracker.exportKml(kmlFile)
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
                        UiMessageBus.postError(R.string.err_export, e)
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
                    WifiRow(network, isFavorite = network.bssid in favoriteAddresses, onClick = { vm.selectedNetwork = network })
                    HairlineHorizontal()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────

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
                    Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.export_wardriving), tint = Spectrum.Accent, modifier = Modifier.size(12.dp))
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
private fun WifiRow(network: WifiNetwork, isFavorite: Boolean, onClick: () -> Unit) {
    val risk = network.isRisk()

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
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
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = "Favorit",
                            tint = Spectrum.Accent,
                            modifier = Modifier.size(12.dp)
                        )
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

    }
}
