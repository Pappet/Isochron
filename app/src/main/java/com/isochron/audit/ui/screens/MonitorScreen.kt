package com.isochron.audit.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.isochron.audit.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.isochron.audit.service.MonitoringState
import com.isochron.audit.service.ScanService
import com.isochron.audit.ui.components.*
import com.isochron.audit.ui.theme.JetBrainsMonoFamily
import com.isochron.audit.ui.theme.Spectrum
import com.isochron.audit.ui.viewmodel.MonitorViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MonitorScreen(vm: MonitorViewModel = viewModel()) {
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

    DisposableEffect(Unit) {
        val intent = Intent(context, ScanService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { if (bound) context.unbindService(connection) }
    }

    val serviceStateFlow = service?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(MonitoringState()) }
    val state by serviceStateFlow.collectAsState()

    val selectedInterval = vm.selectedInterval

    fun startMonitoring() {
        val srv = service
        if (srv != null) {
            srv.startMonitoring(selectedInterval)
        } else {
            context.startForegroundService(
                Intent(context, ScanService::class.java).apply {
                    action = ScanService.ACTION_START
                    putExtra(ScanService.EXTRA_INTERVAL, selectedInterval)
                }
            )
        }
    }

    // Without POST_NOTIFICATIONS (Android 13+) the foreground service runs invisibly:
    // no status, no stop button. Gate the start on it (audit C6). Older releases need
    // nothing, so the list is empty and the state reports "all granted".
    val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }
    val notifPermissions = rememberScanPermissions(notificationPermissions) { startMonitoring() }

    fun toggle() {
        if (state.isRunning) {
            service?.stopMonitoring() ?: run {
                context.startService(
                    Intent(context, ScanService::class.java).apply { action = ScanService.ACTION_STOP }
                )
            }
        } else {
            notifPermissions.runOrRequest { startMonitoring() }
        }
    }

    val headerStats = listOf(
        HeaderStat("${state.intervalSeconds}s", stringResource(R.string.mon_stat_interval)),
        HeaderStat("${state.wifiSignalHistory.size}×", stringResource(R.string.mon_stat_samples)),
        HeaderStat(if (state.isRunning) "FG" else "OFF", stringResource(R.string.mon_stat_service)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spectrum.Surface)
            .verticalScroll(rememberScrollState()),
    ) {
        SpectrumHeader(
            kicker = "MON",
            subtitle = stringResource(R.string.mon_subtitle),
            stats = headerStats,
            trailing = { MonStartStopPill(running = state.isRunning, onClick = { toggle() }) },
        )

        PermissionBanner(
            permissions = notifPermissions,
            text = stringResource(R.string.perm_required_notif),
            color = Spectrum.Warning,
        )

        if (!state.isRunning) {
            Spacer(Modifier.height(12.dp))
            MonIntervalRow(
                selected = selectedInterval,
                onSelect = { vm.selectedInterval = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        val signalValues = remember(state.wifiSignalHistory) {
            state.wifiSignalHistory.map { it.first.toFloat() }
        }
        val gatewayValues = remember(state.gatewayLatency) {
            state.gatewayLatency.map { it.first }
        }
        val internetValues = remember(state.internetLatency) {
            state.internetLatency.map { it.first }
        }

        val metrics = listOf(
            MonMetric(
                label = stringResource(R.string.mon_metric_signal),
                unit = "dBm",
                values = signalValues,
                color = Spectrum.Accent,
                currentStr = state.currentSignal?.toString() ?: signalValues.lastOrNull()?.let { "%.0f".format(it) } ?: "—",
                axisMin = -95f,
                axisMax = -30f,
            ),
            MonMetric(
                label = stringResource(R.string.mon_metric_gateway),
                unit = "ms",
                values = gatewayValues,
                color = Spectrum.Accent2,
                currentStr = gatewayValues.lastOrNull()?.let { "%.1f".format(it) } ?: "—",
                axisMin = 0f,
                axisMax = niceLatencyCeiling(gatewayValues),
            ),
            MonMetric(
                label = stringResource(R.string.mon_metric_internet),
                unit = "ms",
                values = internetValues,
                color = Spectrum.Warning,
                currentStr = internetValues.lastOrNull()?.let { "%.0f".format(it) } ?: "—",
                axisMin = 0f,
                axisMax = niceLatencyCeiling(internetValues),
            ),
        )

        metrics.forEach { m ->
            MonMetricCard(
                metric = m,
                intervalSeconds = state.intervalSeconds,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        // Session stats
        if (state.scanCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Spectrum.SurfaceRaised)
                    .padding(14.dp),
            ) {
                Column {
                    SpectrumKicker(stringResource(R.string.mon_session))
                    Spacer(Modifier.height(8.dp))
                    MonStatRow(stringResource(R.string.mon_session_scans), "${state.scanCount}")
                    MonStatRow(stringResource(R.string.mon_session_new_devices), "${state.newDeviceCount}")
                    signalValues.takeIf { it.isNotEmpty() }?.average()?.let {
                        MonStatRow(stringResource(R.string.mon_session_avg_signal), "${"%.0f".format(it)} dBm")
                    }
                    gatewayValues.takeIf { it.isNotEmpty() }?.average()?.let {
                        MonStatRow(stringResource(R.string.mon_session_avg_gateway), "${"%.1f".format(it)} ms")
                    }
                    internetValues.takeIf { it.isNotEmpty() }?.average()?.let {
                        MonStatRow(stringResource(R.string.mon_session_avg_internet), "${"%.1f".format(it)} ms")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(100.dp))
    }
}

private data class MonMetric(
    val label: String,
    val unit: String,
    val values: List<Float>,
    val color: Color,
    val currentStr: String,
    /** Fixed y-axis so 11→13 ms and 10→800 ms no longer draw the same mountain (audit B7). */
    val axisMin: Float,
    val axisMax: Float,
)

/** Smallest of 50/100/250/500/1000/2500 ms that still contains every sample. */
private fun niceLatencyCeiling(values: List<Float>): Float {
    val peak = values.maxOrNull() ?: 0f
    return listOf(50f, 100f, 250f, 500f, 1000f, 2500f).firstOrNull { it >= peak } ?: 5000f
}

@Composable
private fun MonMetricCard(metric: MonMetric, intervalSeconds: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, Spectrum.GridLine, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Spectrum.SurfaceRaised)
            .padding(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    metric.label,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.em,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        metric.currentStr,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 26.sp,
                        color = metric.color,
                        letterSpacing = (-0.02).em,
                        lineHeight = 28.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        metric.unit,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = Spectrum.OnSurfaceDim,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Y-axis: the two bounds of the fixed range
                Column(
                    Modifier.height(60.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("%.0f".format(metric.axisMax), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = Spectrum.OnSurfaceDim)
                    Text("%.0f".format(metric.axisMin), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = Spectrum.OnSurfaceDim)
                }
                Spacer(Modifier.width(6.dp))
                MonSparkline(
                    values = metric.values,
                    color = metric.color,
                    axisMin = metric.axisMin,
                    axisMax = metric.axisMax,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        // The numbers above are the readable form; the curve itself only
                        // needs a name so TalkBack does not skip the chart silently.
                        .semantics {
                            contentDescription = "${metric.label}: ${metric.values.size} Messwerte, aktuell ${metric.currentStr} ${metric.unit}"
                        },
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val avg = metric.values.takeIf { it.isNotEmpty() }?.average()
                Text(
                    text = avg?.let { stringResource(R.string.mon_avg, "%.1f".format(it), metric.unit) } ?: stringResource(R.string.mon_avg_none),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Spectrum.OnSurfaceDim,
                )
                // X-axis: how much wall-clock time the curve spans
                Text(
                    stringResource(R.string.mon_span, metric.values.size, metric.values.size * intervalSeconds),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Spectrum.OnSurfaceDim,
                )
            }
        }
    }
}

@Composable
private fun MonSparkline(
    values: List<Float>,
    color: Color,
    axisMin: Float,
    axisMax: Float,
    modifier: Modifier = Modifier,
) {
    val gridColor = Spectrum.GridLine
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Dashed grid lines at 0, 25%, 50%, 75%
        listOf(0f, 0.25f, 0.5f, 0.75f).forEach { frac ->
            val y = frac * h
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = dashEffect,
            )
        }

        if (values.size < 2) return@Canvas

        val range = (axisMax - axisMin).coerceAtLeast(0.001f)

        fun xAt(i: Int) = (i.toFloat() / (values.size - 1)) * w
        fun yAt(v: Float) = h - ((v.coerceIn(axisMin, axisMax) - axisMin) / range) * h

        // Fill path
        val fillPath = Path().apply {
            moveTo(xAt(0), yAt(values[0]))
            for (i in 1 until values.size) lineTo(xAt(i), yAt(values[i]))
            lineTo(xAt(values.size - 1), h)
            lineTo(xAt(0), h)
            close()
        }
        drawPath(fillPath, color = color.copy(alpha = 0.08f), style = Fill)

        // Stroke path
        val strokePath = Path().apply {
            moveTo(xAt(0), yAt(values[0]))
            for (i in 1 until values.size) lineTo(xAt(i), yAt(values[i]))
        }
        drawPath(
            strokePath,
            color = color,
            style = Stroke(
                width = 1.4f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun MonStartStopPill(running: Boolean, onClick: () -> Unit) {
    val bg = if (running) Spectrum.Danger else Color.Transparent
    val fg = if (running) Spectrum.Surface else Spectrum.Accent
    val border = if (running) Spectrum.Danger else Spectrum.AccentDim
    val shape = RoundedCornerShape(20.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        if (running) BlinkingDot(color = fg, blink = true, size = 6.dp)
        Text(
            (if (running) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start)).uppercase(),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = fg,
            letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun MonIntervalRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SpectrumKicker(stringResource(R.string.mon_interval))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                5 to stringResource(R.string.mon_interval_5s),
                10 to stringResource(R.string.mon_interval_10s),
                30 to stringResource(R.string.mon_interval_30s),
                60 to stringResource(R.string.mon_interval_1m),
            ).forEach { (s, label) ->
                SpectrumFilterChip(
                    label = label,
                    selected = selected == s,
                    onClick = { onSelect(s) },
                )
            }
        }
    }
}

@Composable
private fun MonStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = Spectrum.OnSurfaceDim,
        )
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = Spectrum.OnSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
