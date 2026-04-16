package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.data.WifiNetwork
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.components.WifiNetworkCard
import com.scanner.app.util.WardrivingTracker
import com.scanner.app.util.WifiScanner
import kotlinx.coroutines.launch

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

    // GPS geotagging state
    var gpsEnabled by remember { mutableStateOf(false) }
    var geoTagCount by remember { mutableStateOf(0) }
    var uniqueGeoNetworks by remember { mutableStateOf(0) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    DisposableEffect(Unit) {
        onDispose {
            wifiScanner.cleanup()
            wardrivingTracker.cleanup()
        }
    }

    fun doScan() {
        if (!wifiScanner.isWifiEnabled()) return
        if (!permissionState.allPermissionsGranted) return
        isScanning = true
        val startTime = System.currentTimeMillis()
        wifiScanner.startScan { results ->
            networks = results
            isScanning = false
            hasScanned = true

            // Geotag if GPS is enabled
            if (gpsEnabled) {
                wardrivingTracker.recordNetworks(results)
                geoTagCount = wardrivingTracker.getEntryCount()
                uniqueGeoNetworks = wardrivingTracker.getUniqueNetworks()
            }

            // Persist to Room DB
            try {
                scope.launch {
                    try {
                        repository.persistWifiScan(
                            networks = results,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("WifiScreen", "Error persisting scan", e)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WLAN-Netzwerke",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasScanned) {
                    val connected = networks.count { it.isConnected }
                    val wpsCount = networks.count { it.wpsEnabled }
                    Text(
                        text = buildString {
                            append("${networks.size} gefunden")
                            if (connected > 0) append(" · $connected verbunden")
                            if (wpsCount > 0) append(" · $wpsCount WPS")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    } else {
                        doScan()
                    }
                },
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

        // GPS Geotagging bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.MyLocation,
                    contentDescription = null,
                    tint = if (gpsEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (gpsEnabled && geoTagCount > 0)
                        "GPS · $geoTagCount Einträge · $uniqueGeoNetworks Netzwerke"
                    else "GPS-Geotagging",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (gpsEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Export button (visible when we have geo data)
                if (gpsEnabled && geoTagCount > 0) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val csvFile = java.io.File(context.cacheDir, "wardriving.csv")
                                    wardrivingTracker.exportWigleCsv(csvFile)
                                    val kmlFile = java.io.File(context.cacheDir, "wardriving.kml")
                                    wardrivingTracker.exportKml(kmlFile)

                                    val csvUri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", csvFile
                                    )
                                    val kmlUri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", kmlFile
                                    )
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                        type = "*/*"
                                        putParcelableArrayListExtra(
                                            android.content.Intent.EXTRA_STREAM,
                                            arrayListOf(csvUri, kmlUri)
                                        )
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Wardriving Export"))
                                } catch (e: Exception) {
                                    android.util.Log.e("WifiScreen", "Export error", e)
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "Export",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Switch(
                    checked = gpsEnabled,
                    onCheckedChange = { enabled ->
                        gpsEnabled = enabled
                        if (enabled) {
                            wardrivingTracker.startTracking()
                        } else {
                            wardrivingTracker.stopTracking()
                        }
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // Permission warning
        if (!permissionState.allPermissionsGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Berechtigungen erforderlich",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Standort-Berechtigung wird für den WLAN-Scan benötigt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { permissionState.launchMultiplePermissionRequest() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Berechtigungen erteilen")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // WiFi disabled warning
        if (!wifiScanner.isWifiEnabled()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "WLAN ist deaktiviert. Bitte WLAN einschalten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Network list
        if (networks.isEmpty() && hasScanned) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine Netzwerke gefunden.\nVersuche es erneut.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (networks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tippe auf \"Scannen\" um\nWLAN-Netzwerke zu finden.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connected networks first
                val connected = networks.filter { it.isConnected }
                val others = networks.filter { !it.isConnected }

                if (connected.isNotEmpty()) {
                    item {
                        Text(
                            text = "VERBUNDEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(connected, key = { it.bssid }) { network ->
                        WifiNetworkCard(network)
                    }
                }

                if (others.isNotEmpty()) {
                    item {
                        Text(
                            text = "IN REICHWEITE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(others, key = { it.bssid }) { network ->
                        WifiNetworkCard(network)
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
