package com.isochron.audit

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.isochron.audit.ui.UiMessageBus
import com.isochron.audit.ui.components.SpectrumBottomNav
import com.isochron.audit.ui.components.SpectrumSubTabs
import com.isochron.audit.ui.components.SpectrumTab
import com.isochron.audit.ui.screens.BluetoothScreen
import com.isochron.audit.ui.screens.ChannelAnalysisScreen
import com.isochron.audit.ui.screens.InventoryScreen
import com.isochron.audit.ui.screens.LanScreen
import com.isochron.audit.ui.screens.MapScreen
import com.isochron.audit.ui.screens.MonitorScreen
import com.isochron.audit.ui.screens.OnboardingScreen
import com.isochron.audit.ui.screens.SecurityAuditScreen
import com.isochron.audit.ui.screens.SettingsScreen
import com.isochron.audit.ui.screens.WifiScreen
import com.isochron.audit.ui.theme.JetBrainsMonoFamily
import com.isochron.audit.ui.theme.Spectrum
import com.isochron.audit.ui.theme.SpectrumTheme
import com.isochron.audit.util.AppPrefs
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

/** One screen inside a destination; the sub-tab label comes from [labelRes]. */
private class SubScreen(val labelRes: Int, val content: @Composable () -> Unit)

/** A bottom-nav destination and the screens grouped under it (audit F1). */
private class Destination(
    val key: String,
    val icon: ImageVector,
    val labelRes: Int,
    val screens: List<SubScreen>,
)

// Four destinations instead of eight equal tabs: Scan, Analyse, Audit, Inventar.
private val Destinations = listOf(
    Destination(
        "scan", Icons.Outlined.Sensors, R.string.nav_scan,
        listOf(
            SubScreen(R.string.nav_sub_wifi) { WifiScreen() },
            SubScreen(R.string.nav_sub_bluetooth) { BluetoothScreen() },
            SubScreen(R.string.nav_sub_lan) { LanScreen() },
        ),
    ),
    Destination(
        "analyse", Icons.Outlined.BarChart, R.string.nav_analyse,
        listOf(
            SubScreen(R.string.nav_sub_channels) { ChannelAnalysisScreen() },
            SubScreen(R.string.nav_sub_monitor) { MonitorScreen() },
        ),
    ),
    Destination(
        "audit", Icons.Outlined.Shield, R.string.nav_audit,
        listOf(
            SubScreen(R.string.nav_sub_security) { SecurityAuditScreen() },
            SubScreen(R.string.nav_sub_map) { MapScreen() },
        ),
    ),
    Destination(
        "inventory", Icons.Outlined.Inventory2, R.string.nav_inventory,
        listOf(
            SubScreen(R.string.nav_inventory) { InventoryScreen() },
        ),
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IsochronApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(AppPrefs.NAME, Context.MODE_PRIVATE) }
    var onboardingComplete by remember {
        mutableStateOf(prefs.getBoolean(AppPrefs.KEY_ONBOARDING_COMPLETE, false))
    }

    if (!onboardingComplete) {
        OnboardingScreen(onDone = {
            prefs.edit().putBoolean(AppPrefs.KEY_ONBOARDING_COMPLETE, true).apply()
            onboardingComplete = true
        })
        return
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { Destinations.size })
    val scope = rememberCoroutineScope()
    val tabs = Destinations.map { SpectrumTab(it.key, it.icon, stringResource(it.labelRes)) }
    val selectedKey = Destinations[pagerState.currentPage].key

    // Single sink for every ViewModel/service error (audit C1).
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        UiMessageBus.messages.collect { message ->
            snackbarHostState.showSnackbar(message.resolve(context))
        }
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
            // Settings is a full-page overlay; the bottom nav would only distract there.
            if (!showSettings) SpectrumBottomNav(
                tabs = tabs,
                selected = selectedKey,
                onSelect = { key ->
                    val idx = Destinations.indexOfFirst { it.key == key }
                    if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                },
            )
        },
    ) { padding ->
        Box(
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
                    DestinationPage(
                        destination = Destinations[page],
                        isCurrent = page == pagerState.currentPage,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
            // Overlay, not replacement: the pager (and its tab state) stays alive
            // underneath, and the Scaffold's Snackbar host can show "data wiped".
            if (showSettings) {
                SettingsScreen(
                    onClose = { showSettings = false },
                    onReplayOnboarding = {
                        showSettings = false
                        onboardingComplete = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DestinationPage(destination: Destination, isCurrent: Boolean, onOpenSettings: () -> Unit) {
    var selected by rememberSaveable(destination.key) { mutableIntStateOf(0) }
    // Back from a secondary sub-tab returns to the first one before leaving the app.
    // Only the visible page may claim the key: the pager keeps neighbours composed.
    BackHandler(enabled = isCurrent && selected != 0) { selected = 0 }

    Column(Modifier.fillMaxSize()) {
        SpectrumSubTabs(
            tabs = destination.screens.map { stringResource(it.labelRes) },
            selected = selected,
            onSelect = { selected = it },
            onOpenSettings = onOpenSettings,
        )
        Box(Modifier.fillMaxSize()) {
            destination.screens[selected].content()
        }
    }
}
