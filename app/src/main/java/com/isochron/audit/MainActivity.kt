package com.isochron.audit

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.ui.components.SpectrumBottomNav
import com.isochron.audit.ui.components.SpectrumTab
import com.isochron.audit.ui.screens.BluetoothScreen
import com.isochron.audit.ui.screens.ChannelAnalysisScreen
import com.isochron.audit.ui.screens.InventoryScreen
import com.isochron.audit.ui.screens.LanScreen
import com.isochron.audit.ui.screens.MapScreen
import com.isochron.audit.ui.screens.MonitorScreen
import com.isochron.audit.ui.screens.SecurityAuditScreen
import com.isochron.audit.ui.screens.WifiScreen
import com.isochron.audit.ui.theme.JetBrainsMonoFamily
import com.isochron.audit.ui.theme.SpectrumTheme
import com.isochron.audit.ui.theme.Spectrum
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.launch

/**
 * Main entry point of the application.
 * Initialises edge-to-edge display and sets the root Composable.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark-only. The default `auto` style follows the system theme and
        // paints dark status-bar icons on our near-black surface (audit D6).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            SpectrumTheme {
                IsochronApp()
            }
        }
    }
}

// Tab order matches the Spectrum nav: WIFI, CH, BT, LAN, MON, SEC, MAP, INV.
private val SpectrumTabs = listOf(
    SpectrumTab("wifi", Icons.Outlined.Wifi, "WIFI"),
    SpectrumTab("ch", Icons.Outlined.BarChart, "CH"),
    SpectrumTab("bt", Icons.Outlined.Bluetooth, "BT"),
    SpectrumTab("lan", Icons.Outlined.Lan, "LAN"),
    SpectrumTab("mon", Icons.Outlined.MonitorHeart, "MON"),
    SpectrumTab("sec", Icons.Outlined.Shield, "SEC"),
    SpectrumTab("map", Icons.Outlined.Map, "MAP"),
    SpectrumTab("inv", Icons.Outlined.Inventory2, "INV"),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IsochronApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("scanner_prefs", android.content.Context.MODE_PRIVATE) }
    var onboardingComplete by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false)) }

    if (!onboardingComplete) {
        com.isochron.audit.ui.screens.OnboardingScreen(onDone = {
            prefs.edit().putBoolean("onboarding_complete", true).apply()
            onboardingComplete = true
        })
        return
    }

    val pagerState = rememberPagerState(pageCount = { SpectrumTabs.size })
    val scope = rememberCoroutineScope()

    // Single sink for every ViewModel/service error (audit C1).
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        UiMessageBus.messages.collect { message ->
            snackbarHostState.showSnackbar(message.resolve(context))
        }
    }
    val selectedKey = remember(pagerState.currentPage) {
        SpectrumTabs[pagerState.currentPage].key
    }

    Scaffold(
        containerColor = Spectrum.Surface,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    containerColor = Spectrum.SurfaceHi,
                    contentColor = Spectrum.OnSurface,
                ) {
                    Text(data.visuals.message, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp)
                }
            }
        },
        bottomBar = {
            SpectrumBottomNav(
                tabs = SpectrumTabs,
                selected = selectedKey,
                onSelect = { key ->
                    val idx = SpectrumTabs.indexOfFirst { it.key == key }
                    if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Spectrum.Surface),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                // Opaque + clipped: prevents neighboring pages (notably the osmdroid
                // MapView) from drawing into the visible page during transitions.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Spectrum.Surface)
                        .clipToBounds(),
                ) {
                    val key = SpectrumTabs[page].key
                    when (key) {
                        "wifi" -> WifiScreen()
                        "ch" -> ChannelAnalysisScreen()
                        "bt" -> BluetoothScreen()
                        "lan" -> LanScreen()
                        "mon" -> MonitorScreen()
                        "sec" -> SecurityAuditScreen()
                        "map" -> MapScreen()
                        "inv" -> InventoryScreen()
                    }
                }
            }
        }
    }
}
