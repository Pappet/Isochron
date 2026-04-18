package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.data.WifiNetwork
import com.scanner.app.ui.components.ChannelBarChart
import com.scanner.app.ui.components.SpectrumView
import com.scanner.app.util.ChannelAnalysis
import com.scanner.app.util.ChannelAnalyzer
import com.scanner.app.util.ChannelRecommendation
import com.scanner.app.util.WifiScanner

/**
 * Main screen for WiFi spectrum and channel congested analysis.
 * Visualizes channel usage across 2.4 GHz and 5 GHz bands and provides
 * scored recommendations for optimal router configuration.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChannelAnalysisScreen() {
    val context = LocalContext.current
    val wifiScanner = remember { WifiScanner(context) }

    var networks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var analysis by remember { mutableStateOf<ChannelAnalysis?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedBand by remember { mutableStateOf(0) }  // 0 = 2.4 GHz, 1 = 5 GHz

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    DisposableEffect(Unit) {
        onDispose { wifiScanner.cleanup() }
    }

    fun doScan() {
        if (!wifiScanner.isWifiEnabled()) return
        if (!permissionState.allPermissionsGranted) return
        isScanning = true
        wifiScanner.startScan { results ->
            try {
                networks = results
                analysis = ChannelAnalyzer.analyze(results)
            } catch (e: Exception) {
                android.util.Log.e("ChannelAnalysis", "Error analyzing", e)
            }
            isScanning = false
        }
    }

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
                    text = "Kanalanalyse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                analysis?.let { a ->
                    Text(
                        text = "${a.totalNetworks} Netzwerke · ${a.networks24Count} auf 2.4 GHz · ${a.networks5Count} auf 5 GHz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    } else doScan()
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


        if (analysis == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tippe auf \"Scannen\" für die\nWLAN-Kanalanalyse.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return
        }

        val a = analysis!!


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedBand == 0,
                onClick = { selectedBand = 0 },
                label = { Text("2.4 GHz (${a.networks24Count})") },
                leadingIcon = if (selectedBand == 0) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = selectedBand == 1,
                onClick = { selectedBand = 1 },
                label = { Text("5 GHz (${a.networks5Count})") },
                leadingIcon = if (selectedBand == 1) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }

        Spacer(modifier = Modifier.height(16.dp))


        a.connectedChannel?.let { ch ->
            val connectedBand = a.connectedBand ?: ""
            if ((selectedBand == 0 && connectedBand == "2.4 GHz") ||
                (selectedBand == 1 && connectedBand == "5 GHz")
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aktuell verbunden auf Kanal $ch ($connectedBand)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }


        val channels = if (selectedBand == 0) a.channels24 else a.channels5
        val connectedCh = if (
            (selectedBand == 0 && a.connectedBand == "2.4 GHz") ||
            (selectedBand == 1 && a.connectedBand == "5 GHz")
        ) a.connectedChannel else null

        ChannelBarChart(
            channels = channels,
            connectedChannel = connectedCh,
            title = "Kanalauslastung",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))


        SpectrumView(
            channels = channels,
            band = if (selectedBand == 0) "2.4 GHz" else "5 GHz",
            connectedChannel = connectedCh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))


        val recommendations = if (selectedBand == 0) a.recommendations24 else a.recommendations5

        if (recommendations.isNotEmpty()) {
            Text(
                text = "EMPFOHLENE KANÄLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            recommendations.take(3).forEachIndexed { index, rec ->
                RecommendationCard(
                    recommendation = rec,
                    rank = index + 1,
                    isCurrentChannel = rec.channel == connectedCh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))


        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Legende",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LegendItem(Color(0xFF4CAF50), "Frei — kaum Auslastung")
                LegendItem(Color(0xFFFF9800), "Moderat — einige Netzwerke")
                LegendItem(Color(0xFFF44336), "Stark ausgelastet")
                LegendItem(MaterialTheme.colorScheme.primary, "Dein Netzwerk")

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (selectedBand == 0)
                        "Tipp: Verwende die Kanäle 1, 6 oder 11 — diese überlappen sich nicht gegenseitig."
                    else
                        "Tipp: Im 5-GHz-Band überlappen sich Kanäle kaum. Wähle einen Kanal mit wenig Nachbarn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}



/**
 * UI component for displaying a specific channel recommendation with its score and rationale.
 */
@Composable
fun RecommendationCard(
    recommendation: ChannelRecommendation,
    rank: Int,
    isCurrentChannel: Boolean,
    modifier: Modifier = Modifier
) {
    val scoreColor = when {
        recommendation.score >= 0.7f -> Color(0xFF4CAF50)
        recommendation.score >= 0.4f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentChannel)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(scoreColor.copy(alpha = 0.15f))
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Kanal ${recommendation.channel}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (isCurrentChannel) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Aktuell",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = recommendation.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Score bar
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${(recommendation.score * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(recommendation.score)
                            .clip(RoundedCornerShape(2.dp))
                            .background(scoreColor)
                    )
                }
            }
        }
    }
}



/**
 * Small indicator for the spectrum legend.
 */
@Composable
fun LegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
