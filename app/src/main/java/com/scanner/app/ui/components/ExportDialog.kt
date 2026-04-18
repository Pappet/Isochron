package com.scanner.app.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.scanner.app.data.db.DeviceCategory
import com.scanner.app.util.ExportFilter
import com.scanner.app.util.ExportFormat
import com.scanner.app.util.ExportManager
import kotlinx.coroutines.launch

/**
 * Dialog for configuring and initiating data exports.
 * Supports multiple formats (CSV, JSON, PDF) and filters for device category,
 * favorite status, and discovery time range.
 *
 * @param onDismiss Callback to close the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportManager = remember { ExportManager(context) }

    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var recentOnly by remember { mutableStateOf(false) }
    var recentHours by remember { mutableStateOf(24) }
    var wifiEnabled by remember { mutableStateOf(true) }
    var btEnabled by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = {
            Text("Daten exportieren", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Text(
                    text = "Format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatChip(
                        format = ExportFormat.CSV,
                        icon = Icons.Outlined.TableChart,
                        selected = selectedFormat == ExportFormat.CSV,
                        onClick = { selectedFormat = ExportFormat.CSV }
                    )
                    FormatChip(
                        format = ExportFormat.JSON,
                        icon = Icons.Outlined.Code,
                        selected = selectedFormat == ExportFormat.JSON,
                        onClick = { selectedFormat = ExportFormat.JSON }
                    )
                    FormatChip(
                        format = ExportFormat.PDF,
                        icon = Icons.Outlined.PictureAsPdf,
                        selected = selectedFormat == ExportFormat.PDF,
                        onClick = { selectedFormat = ExportFormat.PDF }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))


                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Device categories
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = wifiEnabled,
                        onClick = { wifiEnabled = !wifiEnabled },
                        label = { Text("WLAN") }
                    )
                    FilterChip(
                        selected = btEnabled,
                        onClick = { btEnabled = !btEnabled },
                        label = { Text("Bluetooth") }
                    )
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { favoritesOnly = !favoritesOnly }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = favoritesOnly,
                        onCheckedChange = { favoritesOnly = it }
                    )
                    Text(
                        text = "Nur Favoriten",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { recentOnly = !recentOnly }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = recentOnly,
                        onCheckedChange = { recentOnly = it }
                    )
                    Text(
                        text = "Nur letzte ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (recentOnly) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1, 6, 24, 168).forEach { hours ->
                                val label = when (hours) {
                                    1 -> "1h"
                                    6 -> "6h"
                                    24 -> "24h"
                                    168 -> "7d"
                                    else -> "${hours}h"
                                }
                                FilterChip(
                                    selected = recentHours == hours,
                                    onClick = { recentHours = hours },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }


                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (selectedFormat) {
                            ExportFormat.CSV ->
                                "CSV mit Semikolon-Trennung, UTF-8 BOM für Excel-Kompatibilität."
                            ExportFormat.JSON ->
                                "Strukturiertes JSON mit Statistiken und Metadaten."
                            ExportFormat.PDF ->
                                "Formatierter A4-Bericht mit Tabelle und Zusammenfassung."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isExporting = true
                    scope.launch {
                        try {
                            val categories = buildSet {
                                if (wifiEnabled) add(DeviceCategory.WIFI)
                                if (btEnabled) {
                                    add(DeviceCategory.BT_CLASSIC)
                                    add(DeviceCategory.BT_BLE)
                                    add(DeviceCategory.BT_DUAL)
                                }
                            }.takeIf { it.isNotEmpty() }

                            val filter = ExportFilter(
                                categories = categories,
                                favoritesOnly = favoritesOnly,
                                sinceHours = if (recentOnly) recentHours else null
                            )

                            val result = exportManager.export(selectedFormat, filter)
                            val shareIntent = exportManager.share(result)
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Export teilen")
                            )
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Export fehlgeschlagen: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isExporting = false
                        }
                    }
                },
                enabled = !isExporting && (wifiEnabled || btEnabled)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportiere...")
                } else {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportieren & Teilen")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text("Abbrechen")
            }
        }
    )
}



/**
 * Square chip for selecting an export format (CSV, JSON, PDF).
 */
@Composable
fun FormatChip(
    format: ExportFormat,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val contentColor = if (selected)
        MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.clickable { onClick() },
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = format.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}
