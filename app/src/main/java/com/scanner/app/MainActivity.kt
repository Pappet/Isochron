package com.scanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanner.app.ui.screens.*
import com.scanner.app.ui.theme.ScannerAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerAppTheme {
                ScannerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScannerApp() {
    // 7 pages: 5 in bottom nav + 2 top bar actions (Channel Analysis, Security Audit)
    val pagerState = rememberPagerState(pageCount = { 7 })
    val coroutineScope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }

    val bottomTabs = listOf(
        TabItem("WLAN", Icons.Outlined.Wifi),
        TabItem("Bluetooth", Icons.Outlined.Bluetooth),
        TabItem("LAN", Icons.Outlined.Lan),
        TabItem("Monitor", Icons.Outlined.MonitorHeart),
        TabItem("Inventar", Icons.Outlined.Inventory2)
    )

    // Export Dialog
    if (showExportDialog) {
        com.scanner.app.ui.components.ExportDialog(
            onDismiss = { showExportDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Netzwerk-Scanner",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Security Audit action
                    IconButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(6)
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = "Security Audit",
                            tint = if (pagerState.currentPage == 6)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Export action
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "Exportieren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Channel Analysis action
                    IconButton(onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(5)
                        }
                    }) {
                        Icon(
                            Icons.Outlined.BarChart,
                            contentDescription = "Kanalanalyse",
                            tint = if (pagerState.currentPage == 5)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                bottomTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 6,  // Keep all 7 pages alive in memory
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                0 -> WifiScreen()
                1 -> BluetoothScreen()
                2 -> LanScreen()
                3 -> MonitorScreen()
                4 -> InventoryScreen()
                5 -> ChannelAnalysisScreen()
                6 -> SecurityAuditScreen()
            }
        }
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector
)
