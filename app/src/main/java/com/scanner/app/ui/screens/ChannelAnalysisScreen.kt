package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.ui.components.*
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.util.ChannelAnalysis
import com.scanner.app.util.ChannelAnalyzer
import com.scanner.app.util.ChannelInfo
import com.scanner.app.util.WifiScanner

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChannelAnalysisScreen() {
    val context = LocalContext.current
    val wifiScanner = remember { WifiScanner(context) }

    var analysis by remember { mutableStateOf<ChannelAnalysis?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedBand by remember { mutableStateOf(0) } // 0=2.4GHz 1=5GHz

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    DisposableEffect(Unit) { onDispose { wifiScanner.cleanup() } }

    fun doScan() {
        if (!wifiScanner.isWifiEnabled()) return
        if (!permissionState.allPermissionsGranted) return
        isScanning = true
        wifiScanner.startScan { results ->
            try {
                analysis = ChannelAnalyzer.analyze(results)
            } catch (e: Exception) {
                android.util.Log.e("ChannelAnalysis", "Error analyzing", e)
            }
            isScanning = false
        }
    }

    val a = analysis
    val channels = if (selectedBand == 0) a?.channels24 ?: emptyList() else a?.channels5 ?: emptyList()
    val recommendations = if (selectedBand == 0) a?.recommendations24 ?: emptyList() else a?.recommendations5 ?: emptyList()
    val topRec = recommendations.firstOrNull()
    val topChannel = topRec?.let { r -> channels.find { it.channel == r.channel } }
    val connectedCh = if (
        (selectedBand == 0 && a?.connectedBand == "2.4 GHz") ||
        (selectedBand == 1 && a?.connectedBand == "5 GHz")
    ) a?.connectedChannel else null

    val headerStats = if (a != null && topRec != null) listOf(
        HeaderStat("CH${topRec.channel}", "BEST"),
        HeaderStat("${(topChannel?.overlapScore?.times(100f))?.toInt() ?: 0}%", "UTIL"),
        HeaderStat("${channels.count { it.networkCount > 0 }}", "AKTIV"),
    ) else emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spectrum.Surface)
            .verticalScroll(rememberScrollState()),
    ) {
        SpectrumHeader(
            kicker = "CH",
            subtitle = "Spektrum",
            scanning = isScanning,
            onScan = {
                if (!permissionState.allPermissionsGranted) permissionState.launchMultiplePermissionRequest()
                else doScan()
            },
            stats = headerStats,
        )

        if (a == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "— —",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 40.sp,
                        color = Spectrum.OnSurfaceFaint,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SCAN STARTEN",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        letterSpacing = 0.15.em,
                        color = Spectrum.OnSurfaceDim,
                    )
                }
            }
            return@Column
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpectrumFilterChip(
                label = "2.4 GHZ",
                selected = selectedBand == 0,
                count = a.networks24Count,
                onClick = { selectedBand = 0 },
            )
            SpectrumFilterChip(
                label = "5 GHZ",
                selected = selectedBand == 1,
                count = a.networks5Count,
                onClick = { selectedBand = 1 },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Bar chart card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(Spectrum.SurfaceRaised),
        ) {
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                SpectrumKicker(
                    "KANALAUSLASTUNG",
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Spacer(Modifier.height(8.dp))
                ChBarChart(
                    channels = channels,
                    connectedChannel = connectedCh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(horizontal = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Top recommendation card
        if (topRec != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Spectrum.SurfaceRaised)
                    .padding(16.dp),
            ) {
                Column {
                    SpectrumKicker("EMPFEHLUNG")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${topRec.channel}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Medium,
                            color = Spectrum.Accent,
                            lineHeight = 56.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.padding(bottom = 8.dp)) {
                            Text(
                                "KANAL",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = Spectrum.OnSurfaceDim,
                                letterSpacing = 0.15.em,
                            )
                            Text(
                                if (selectedBand == 0) "2.4 GHZ BAND" else "5 GHZ BAND",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = Spectrum.OnSurfaceDim,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        topRec.reason,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = Spectrum.OnSurface,
                    )
                    topChannel?.let { ch ->
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            ChStatPill("${(ch.overlapScore * 100f).toInt()}%", "UTIL")
                            ChStatPill("${ch.networkCount}", "APs")
                            ChStatPill("${(topRec.score * 100f).toInt()}%", "SCORE")
                        }
                    }
                }
            }

            // #2 and #3 picks
            val secondary = recommendations.drop(1).take(2)
            if (secondary.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    secondary.forEachIndexed { i, rec ->
                        val recCh = channels.find { it.channel == rec.channel }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Spectrum.SurfaceRaised)
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    "#${i + 2}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.sp,
                                    color = Spectrum.OnSurfaceDim,
                                    letterSpacing = 0.12.em,
                                )
                                Text(
                                    "CH ${rec.channel}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Spectrum.OnSurface,
                                )
                                Text(
                                    "${(rec.score * 100f).toInt()}% score",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = utilColor((recCh?.overlapScore?.times(100f)?.toInt()) ?: 0),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Legend
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChLegendDot(Spectrum.Accent, "FREI")
            ChLegendDot(Spectrum.Warning, "MED")
            ChLegendDot(Spectrum.Danger, "BELEGT")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (selectedBand == 0)
                "Tipp: Verwende Kanal 1, 6 oder 11 — diese überlappen sich nicht."
            else
                "Tipp: Im 5-GHz-Band überlappen sich Kanäle kaum. Wähle einen mit wenig Nachbarn.",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ChBarChart(
    channels: List<ChannelInfo>,
    connectedChannel: Int?,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return

    val density = LocalDensity.current
    val gridColor = Spectrum.GridLine
    val onSurfaceDimColor = Spectrum.OnSurfaceDim
    val accentColor = Spectrum.Accent

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val labelHeightPx = with(density) { 20.dp.toPx() }
        val chartH = h - labelHeightPx
        val barStep = w / channels.size
        val barPad = barStep * 0.15f
        val barW = barStep - barPad * 2f

        // Oscilloscope grid (4 horizontal lines)
        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { frac ->
            val y = chartH * (1f - frac)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        channels.forEachIndexed { i, ch ->
            val barLeft = i * barStep + barPad
            val barH = (ch.overlapScore * chartH).coerceAtLeast(2f)
            val barTop = chartH - barH
            val barColor = utilColor((ch.overlapScore * 100f).toInt())

            // Highlight column for connected channel
            if (ch.channel == connectedChannel) {
                drawRect(
                    color = accentColor.copy(alpha = 0.08f),
                    topLeft = Offset(i * barStep, 0f),
                    size = Size(barStep, chartH),
                )
            }

            // Bar fill
            drawRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(barLeft, barTop),
                size = Size(barW, barH),
            )

            // Accent cap on connected channel bar
            if (ch.channel == connectedChannel) {
                drawLine(
                    color = accentColor,
                    start = Offset(barLeft, barTop),
                    end = Offset(barLeft + barW, barTop),
                    strokeWidth = 2f,
                )
            }

            val barCx = barLeft + barW / 2f

            // AP count badge above bar
            if (ch.networkCount > 0) {
                val badgeR = with(density) { 8.dp.toPx() }
                val badgeCy = (barTop - badgeR - with(density) { 3.dp.toPx() }).coerceAtLeast(badgeR + 2f)
                drawCircle(
                    color = barColor.copy(alpha = 0.18f),
                    radius = badgeR,
                    center = Offset(barCx, badgeCy),
                )
                drawIntoCanvas { canvas ->
                    val p = android.graphics.Paint().apply {
                        color = barColor.toArgb()
                        textSize = with(density) { 8.dp.toPx() }
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText(
                        ch.networkCount.toString(),
                        barCx,
                        badgeCy + p.textSize * 0.35f,
                        p,
                    )
                }
            }

            // Channel label
            drawIntoCanvas { canvas ->
                val show = channels.size <= 14 || ch.networkCount > 0 ||
                    ch.channel in listOf(1, 6, 11, 36, 100, 149)
                if (!show) return@drawIntoCanvas
                val p = android.graphics.Paint().apply {
                    color = if (ch.channel == connectedChannel) accentColor.toArgb()
                            else onSurfaceDimColor.toArgb()
                    textSize = with(density) { 8.dp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText(
                    ch.channel.toString(),
                    barCx,
                    chartH + labelHeightPx * 0.8f,
                    p,
                )
            }
        }
    }
}

@Composable
private fun ChStatPill(value: String, label: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 13.sp,
            color = Spectrum.OnSurface,
        )
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun ChLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(1.dp)),
        )
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = Spectrum.OnSurfaceDim,
            letterSpacing = 0.10.em,
        )
    }
}
