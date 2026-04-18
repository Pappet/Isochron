package com.scanner.app.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanner.app.service.MonitoringState
import com.scanner.app.service.ScanService
import com.scanner.app.ui.components.LatencyChart
import com.scanner.app.ui.components.SignalChart
import com.scanner.app.ui.components.SignalDataPoint
import com.scanner.app.util.SignalHelper
import java.time.Instant

/**
 * Monitoring screen for real-time tracking of network health.
 * Binds to [ScanService] to display live WiFi signal strength and gateway/internet latency metrics.
 * Provides controls for starting/stopping the background monitoring service.
 */
@Composable
fun MonitorScreen() {
    val context = LocalContext.current
    var service by remember { mutableStateOf<ScanService?>(null) }
    var bound by remember { mutableStateOf(false) }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as ScanService.LocalBinder).getService()
                bound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }
    }

    // Bind to service
    DisposableEffect(Unit) {
        val intent = Intent(context, ScanService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            if (bound) context.unbindService(connection)
        }
    }

    val state by service?.state?.collectAsState()
        ?: remember { mutableStateOf(MonitoringState()) }

    var selectedInterval by remember { mutableStateOf(10) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.isRunning) "Aktiv · ${state.scanCount} Scans"
                           else "Inaktiv",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Start/Stop button
            FilledTonalButton(
                onClick = {
                    if (state.isRunning) {
                        context.startService(
                            Intent(context, ScanService::class.java).apply {
                                action = ScanService.ACTION_STOP
                            }
                        )
                    } else {
                        context.startForegroundService(
                            Intent(context, ScanService::class.java).apply {
                                action = ScanService.ACTION_START
                                putExtra(ScanService.EXTRA_INTERVAL, selectedInterval)
                            }
                        )
                    }
                },
                colors = if (state.isRunning)
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                else ButtonDefaults.filledTonalButtonColors()
            ) {
                Icon(
                    if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.isRunning) "Stopp" else "Starten")
            }
        }


        if (!state.isRunning) {
            IntervalSelector(
                selectedInterval = selectedInterval,
                onIntervalSelected = { selectedInterval = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusCard(
                icon = Icons.Outlined.Wifi,
                label = state.currentSsid ?: "Kein WLAN",
                value = state.currentSignal?.let { "$it dBm" } ?: "—",
                valueColor = state.currentSignal?.let {
                    SignalHelper.signalColor(SignalHelper.signalFraction(it))
                } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                icon = Icons.Outlined.Router,
                label = "Gateway",
                value = if (state.gatewayReachable) {
                    state.gatewayLatency.lastOrNull()?.let { "${"%.0f".format(it.first)} ms" } ?: "—"
                } else "Offline",
                valueColor = if (state.gatewayReachable)
                    MaterialTheme.colorScheme.primary
                else Color(0xFFF44336),
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                icon = Icons.Outlined.Language,
                label = "Internet",
                value = if (state.internetReachable) {
                    state.internetLatency.lastOrNull()?.let { "${"%.0f".format(it.first)} ms" } ?: "—"
                } else "Offline",
                valueColor = if (state.internetReachable)
                    MaterialTheme.colorScheme.primary
                else Color(0xFFF44336),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))


        val signalDataPoints = remember(state.wifiSignalHistory) {
            state.wifiSignalHistory.map { (dbm, ts) ->
                SignalDataPoint(value = dbm, timestamp = ts)
            }
        }
        SignalChart(
            dataPoints = signalDataPoints,
            label = "WLAN-Signalstärke",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))


        LatencyChart(
            dataPoints = state.gatewayLatency,
            label = "Gateway-Latenz",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))


        LatencyChart(
            dataPoints = state.internetLatency,
            label = "Internet-Latenz (8.8.8.8)",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))


        if (state.scanCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sitzungsstatistik",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatRow("Scans durchgeführt", "${state.scanCount}")
                    StatRow("Neue Geräte entdeckt", "${state.newDeviceCount}")
                    StatRow("Scan-Intervall", "${state.intervalSeconds}s")

                    val avgSignal = state.wifiSignalHistory
                        .takeIf { it.isNotEmpty() }
                        ?.map { it.first }
                        ?.average()
                    avgSignal?.let {
                        StatRow("Ø Signalstärke", "${"%.0f".format(it)} dBm")
                    }

                    val avgGateway = state.gatewayLatency
                        .takeIf { it.isNotEmpty() }
                        ?.map { it.first }
                        ?.average()
                    avgGateway?.let {
                        StatRow("Ø Gateway-Latenz", "${"%.1f".format(it)} ms")
                    }

                    val avgInternet = state.internetLatency
                        .takeIf { it.isNotEmpty() }
                        ?.map { it.first }
                        ?.average()
                    avgInternet?.let {
                        StatRow("Ø Internet-Latenz", "${"%.1f".format(it)} ms")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}



/**
 * UI component for selecting the background scan frequency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalSelector(
    selectedInterval: Int,
    onIntervalSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val intervals = listOf(5 to "5s", 10 to "10s", 30 to "30s", 60 to "1min")

    Column(modifier = modifier) {
        Text(
            text = "Scan-Intervall",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            intervals.forEach { (seconds, label) ->
                FilterChip(
                    selected = selectedInterval == seconds,
                    onClick = { onIntervalSelected(seconds) },
                    label = { Text(label) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}

/**
 * Status indicator displaying a metric (Signal, Latency) with an icon and label.
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(valueColor.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Small key-value row for session statistics.
 */
@Composable
fun StatRow(label: String, value: String) {
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
            fontWeight = FontWeight.Medium
        )
    }
}
