package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.ui.theme.Spectrum

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    
    val permissions = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list
    }
    
    val permissionState = rememberMultiplePermissionsState(permissions)

    val steps = listOf(
        StepData(
            kicker = "SCANNER / 01",
            title = "Instrument your airspace.",
            body = "This app reads the radio around you — WiFi access points, Bluetooth beacons, devices on your local network. Nothing leaves your phone unless you explicitly export.",
            cta = "CONTINUE"
        ),
        StepData(
            kicker = "PERMISSIONS / 02",
            title = "Grant radio access.",
            perms = listOf(
                PermData(Icons.Outlined.Wifi, "Nearby WiFi", "Scan and identify APs in range."),
                PermData(Icons.Outlined.Bluetooth, "Bluetooth scan", "Discover classic + BLE devices."),
                PermData(Icons.Outlined.Map, "Location (fine)", "Required by Android for WiFi/BT. Only used for wardriving when you explicitly enable it."),
                PermData(Icons.Outlined.Notifications, "Notifications", "Status when background monitoring runs.")
            ),
            cta = "GRANT ALL"
        ),
        StepData(
            kicker = "READY / 03",
            title = "Calibrated.",
            body = "Tap Scan on any tab to begin. Channel analysis and security audit refresh automatically after each pass.",
            cta = "ENTER →"
        )
    )

    val s = steps[step]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spectrum.Surface)
            .padding(28.dp)
    ) {
        // Step ticks
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp)
        ) {
            steps.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (i <= step) Spectrum.Accent else Spectrum.GridLine)
                )
            }
        }

        Text(
            text = s.kicker,
            style = MaterialTheme.typography.labelSmall,
            color = Spectrum.Accent,
            letterSpacing = 2.4.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Text(
            text = s.title,
            style = MaterialTheme.typography.displayMedium,
            color = Spectrum.OnSurface,
            lineHeight = 40.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        if (s.body != null) {
            Text(
                text = s.body,
                style = MaterialTheme.typography.bodyLarge,
                color = Spectrum.OnSurfaceDim,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        if (s.perms != null) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                s.perms.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = p.icon,
                                contentDescription = null,
                                tint = Spectrum.Accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Spectrum.OnSurface
                            )
                            Text(
                                text = p.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Spectrum.OnSurfaceDim,
                                modifier = Modifier.padding(top = 4.dp),
                                lineHeight = 16.8.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Spectrum.GridLine)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Spectrum.Accent, RoundedCornerShape(4.dp))
                .clickable {
                    if (step == 1) {
                        if (!permissionState.allPermissionsGranted) {
                            permissionState.launchMultiplePermissionRequest()
                        }
                    }
                    if (step < steps.size - 1) {
                        step += 1
                    } else {
                        onDone()
                    }
                }
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = s.cta,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF07090A),
                letterSpacing = 2.sp
            )
        }

        if (step in 1 until steps.size - 1) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { step -= 1 }
                    .padding(vertical = 8.dp, horizontal = 16.dp)
                    .padding(top = 10.dp)
            ) {
                Text(
                    text = "← BACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 1.6.sp
                )
            }
        }
    }
}

private data class StepData(
    val kicker: String,
    val title: String,
    val body: String? = null,
    val perms: List<PermData>? = null,
    val cta: String
)

private data class PermData(
    val icon: ImageVector,
    val name: String,
    val desc: String
)
