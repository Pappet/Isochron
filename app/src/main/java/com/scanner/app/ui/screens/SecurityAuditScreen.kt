package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.WifiNetwork
import com.scanner.app.ui.components.*
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SecurityAuditScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wifiScanner = remember { WifiScanner(context) }
    val btScanner = remember { BluetoothScanner(context) }
    val portScanner = remember { PortScanner() }
    val pingUtil = remember { PingUtil(context) }

    var wifiNetworks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var btDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var openPorts by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }
    var report by remember { mutableStateOf<SecurityAuditReport?>(null) }
    var isAuditing by remember { mutableStateOf(false) }
    var auditPhase by remember { mutableStateOf("") }
    var portScanProgress by remember { mutableStateOf<PortScanProgress?>(null) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    DisposableEffect(Unit) {
        onDispose {
            wifiScanner.cleanup()
            btScanner.cleanup()
        }
    }

    fun runAudit() {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }
        isAuditing = true
        openPorts = emptyList()
        scope.launch {
            try {
                auditPhase = "WLAN scannen..."
                wifiNetworks = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<WifiNetwork>>()
                    wifiScanner.startScan { results -> deferred.complete(results) }
                    kotlinx.coroutines.withTimeoutOrNull(10_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) { Log.e("SecurityAudit", "WiFi scan error", e); emptyList() }

                auditPhase = "Bluetooth scannen..."
                btDevices = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<BluetoothDevice>>()
                    btScanner.startScan(
                        durationMs = 6000L,
                        onProgress = { devices ->
                            scope.launch(Dispatchers.Main.immediate) {
                                try { btDevices = devices } catch (_: Exception) {}
                            }
                        },
                        onComplete = { results -> deferred.complete(results) }
                    )
                    kotlinx.coroutines.withTimeoutOrNull(15_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) { Log.e("SecurityAudit", "BT scan error", e); emptyList() }

                try {
                    val gateway = pingUtil.getNetworkInfo().gatewayIp
                    if (gateway != null) {
                        auditPhase = "Port-Scan: $gateway..."
                        val scanResults = portScanner.scan(
                            ip = gateway,
                            ports = WellKnownPorts.QUICK_20,
                            grabBanners = true,
                            onProgress = { p ->
                                scope.launch(Dispatchers.Main.immediate) { portScanProgress = p }
                            }
                        )
                        withContext(Dispatchers.Main) { openPorts = scanResults }
                    }
                } catch (e: Exception) { Log.e("SecurityAudit", "Port scan error", e) }

                auditPhase = "Bericht erstellen..."
                report = try {
                    SecurityAuditor.audit(
                        wifiNetworks = wifiNetworks,
                        btDevices = btDevices,
                        openPorts = openPorts,
                        connectedSsid = wifiScanner.getConnectedSsid()
                    )
                } catch (e: Exception) { Log.e("SecurityAudit", "Report error", e); null }
            } catch (e: Exception) {
                Log.e("SecurityAudit", "Audit error", e)
            } finally {
                isAuditing = false
                auditPhase = ""
                portScanProgress = null
            }
        }
    }

    val r = report
    val headerStats = if (r != null) listOf(
        HeaderStat("${r.findings.size}", "findings"),
        HeaderStat("${r.criticalCount + r.highCount}", "actionable"),
    ) else emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Spectrum.Surface),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        item {
            SpectrumHeader(
                kicker = "AUDIT",
                subtitle = "Security",
                scanning = isAuditing,
                onScan = ::runAudit,
                stats = headerStats,
            )
        }

        // Progress bar while auditing
        if (isAuditing) {
            item {
                val progress = portScanProgress
                val frac = if (progress != null) progress.scanned.toFloat() / progress.total.coerceAtLeast(1) else null
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Spectrum.GridLine),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac ?: 1f)
                            .background(Spectrum.Accent),
                    )
                }
                if (auditPhase.isNotEmpty()) {
                    Text(
                        auditPhase,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = Spectrum.OnSurfaceDim,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Empty state
        if (r == null && !isAuditing) {
            item {
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
                            "AUDIT STARTEN",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            letterSpacing = 0.15.em,
                            color = Spectrum.OnSurfaceDim,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Prüft WLAN · Bluetooth · Gateway-Ports",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = Spectrum.OnSurfaceFaint,
                        )
                    }
                }
            }
        }

        if (r != null) {
            // Grade donut + descriptor
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    AuditDonut(score = r.overallScore, grade = r.grade)
                    Column {
                        SpectrumKicker("GRADE")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            gradeDescriptor(r.overallScore),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 22.sp,
                            color = Spectrum.OnSurface,
                            letterSpacing = (-0.02).em,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildCountSummary(r),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Spectrum.OnSurfaceDim,
                        )
                    }
                }
                HairlineHorizontal()
            }

            // Findings
            if (r.findings.isNotEmpty()) {
                item {
                    SpectrumKicker(
                        "FINDINGS · ${r.findings.size}",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
                items(r.findings, key = { "${it.target}-${it.title}" }) { finding ->
                    SecFindingRow(finding = finding)
                    HairlineHorizontal()
                }
            }

            // Gateway ports
            if (openPorts.isNotEmpty()) {
                item {
                    SpectrumKicker(
                        "GATEWAY-PORTS · ${openPorts.size}",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
                items(openPorts, key = { "${it.ip}:${it.port}" }) { port ->
                    SecPortRow(port = port)
                    HairlineHorizontal()
                }
            }

            // Hint
            item {
                Text(
                    "Port-Scans einzelner Geräte findest du im LAN-Tab.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceFaint,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AuditDonut(score: Int, grade: String) {
    val gradeColor = gradeColor(score)
    val gridColor = Spectrum.GridLine
    val surfaceColor = Spectrum.Surface
    val density = LocalDensity.current

    Canvas(modifier = Modifier.size(96.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.width / 2f
        val strokeW = with(density) { 8.dp.toPx() }
        val arcSize = Size(outerR * 2 - strokeW, outerR * 2 - strokeW)
        val arcOffset = Offset(strokeW / 2f, strokeW / 2f)
        val sweepAngle = (score / 100f) * 360f

        // Background ring
        drawArc(
            color = gridColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcOffset,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Butt),
        )
        // Grade arc
        drawArc(
            color = gradeColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = arcOffset,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Butt),
        )
        // Inner fill
        drawCircle(
            color = surfaceColor,
            radius = outerR - strokeW,
            center = Offset(cx, cy),
        )
        // Grade letter
        val textPaint = android.graphics.Paint().apply {
            color = gradeColor.toArgb()
            textSize = with(density) { 40.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(
            grade,
            cx,
            cy + textPaint.textSize * 0.35f,
            textPaint,
        )
    }
}

@Composable
private fun SecFindingRow(finding: SecurityFinding) {
    var expanded by remember { mutableStateOf(false) }
    val color = severityColor(finding.severity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Severity badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(54.dp)
                    .border(1.dp, color, RoundedCornerShape(2.dp))
                    .padding(vertical = 3.dp),
            ) {
                Text(
                    finding.severity.label.uppercase(),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = color,
                    letterSpacing = 0.12.em,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    finding.title,
                    fontSize = 14.sp,
                    color = Spectrum.OnSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    finding.target,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        finding.description,
                        fontSize = 12.sp,
                        color = Spectrum.OnSurfaceDim,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .border(1.dp, Spectrum.AccentDim, RoundedCornerShape(2.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "↳",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Spectrum.Accent,
                        )
                        Text(
                            finding.recommendation,
                            fontSize = 12.sp,
                            color = Spectrum.Accent,
                        )
                    }
                } else {
                    Text(
                        finding.description,
                        fontSize = 12.sp,
                        color = Spectrum.OnSurfaceDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecPortRow(port: PortScanResult) {
    val risk = WellKnownPorts.riskLevel(port.port)
    val color = when (risk) {
        PortRisk.CRITICAL -> Spectrum.Danger
        PortRisk.HIGH -> Spectrum.SeverityHigh
        PortRisk.MEDIUM -> Spectrum.Warning
        PortRisk.LOW -> Spectrum.SeverityLow
        PortRisk.INFO -> Spectrum.OnSurfaceDim
    }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Port number as badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(54.dp)
                .border(1.dp, color, RoundedCornerShape(2.dp))
                .padding(vertical = 3.dp),
        ) {
            Text(
                "${port.port}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = color,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                port.serviceName,
                fontSize = 13.sp,
                color = Spectrum.OnSurface,
                fontWeight = FontWeight.Medium,
            )
            port.banner?.let { b ->
                Text(
                    b.take(60),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
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
                fontSize = 10.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }
        WellKnownPorts.browseUrl(port)?.let { url ->
            IconButton(
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                        )
                    } catch (_: Exception) {}
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Outlined.OpenInBrowser,
                    contentDescription = "Im Browser öffnen",
                    tint = Spectrum.Accent2,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun gradeColor(score: Int): Color = when {
    score >= 75 -> Spectrum.Accent
    score >= 60 -> Spectrum.Warning
    else -> Spectrum.Danger
}

private fun gradeDescriptor(score: Int): String = when {
    score >= 90 -> "Sehr gut geschützt"
    score >= 75 -> "Gut geschützt"
    score >= 60 -> "Moderate Risiken"
    score >= 40 -> "Mehrere Risiken"
    else -> "Kritische Risiken"
}

private fun buildCountSummary(r: SecurityAuditReport): String =
    buildList {
        if (r.criticalCount > 0) add("${r.criticalCount} kritisch")
        if (r.highCount > 0) add("${r.highCount} hoch")
        if (r.mediumCount > 0) add("${r.mediumCount} mittel")
        if (r.lowCount > 0) add("${r.lowCount} niedrig")
        if (r.infoCount > 0) add("${r.infoCount} info")
    }.joinToString(" · ").ifEmpty { "Keine Findings" }

private fun severityColor(severity: FindingSeverity): Color = when (severity) {
    FindingSeverity.CRITICAL -> Spectrum.Danger
    FindingSeverity.HIGH -> Spectrum.SeverityHigh
    FindingSeverity.MEDIUM -> Spectrum.Warning
    FindingSeverity.LOW -> Spectrum.SeverityLow
    FindingSeverity.INFO -> Spectrum.OnSurfaceDim
}
