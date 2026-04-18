package com.scanner.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.scanner.app.BuildConfig
import com.scanner.app.data.db.DeviceCategory
import com.scanner.app.data.repository.DeviceRepository
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Parsed geo-coordinates extracted once per device entity for map visualization.
 */
private data class GeoDevice(
    val bssid: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val security: String
)

/**
 * Main screen for visualizing geo-tagged WiFi networks on an OpenStreetMap.
 * Parses device metadata for coordinates and renders optimized markers with security-based coloring.
 * Implements incremental marker diffing for performance and centroid centering for UX.
 */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val repository = remember { DeviceRepository(context) }

    // Observe all stored WiFi networks from the database
    val devices by repository.observeDevicesByCategory(DeviceCategory.WIFI)
        .collectAsState(initial = emptyList())

    // Parse metadata once per device, filter to those with geo-coordinates.
    val geoDevices: List<GeoDevice> = remember(devices) {
        devices.mapNotNull { device ->
            try {
                if (device.metadata.isNullOrBlank()) return@mapNotNull null
                val meta = JSONObject(device.metadata)
                if (!meta.has("latitude") || !meta.has("longitude")) return@mapNotNull null
                GeoDevice(
                    bssid = device.address,
                    name = device.name,
                    lat = meta.getDouble("latitude"),
                    lon = meta.getDouble("longitude"),
                    security = meta.optString("security", "Unbekannt")
                )
            } catch (e: Exception) { null }
        }
    }

    val hasInitiallyRecentered = remember { mutableStateOf(false) }
    val markerCache = remember { mutableMapOf<String, Marker>() }

    // Helper: draw a colored circle marker for the given security type
    val createMarkerIcon = { ctx: Context, sec: String ->
        val color = when {
            sec.contains("Offen", ignoreCase = true) -> android.graphics.Color.RED
            sec.contains("OWE", ignoreCase = true) -> android.graphics.Color.rgb(255, 152, 0)
            else -> android.graphics.Color.GREEN
        }
        val size = 50
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { this.color = color; isAntiAlias = true }
        val borderPaint = Paint().apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, borderPaint)
        BitmapDrawable(ctx.resources, bmp)
    }

    // Show Empty-State when no geo-tagged networks exist — no MapView rendered at all
    if (geoDevices.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "Noch keine geolokalisierten Netzwerke vorhanden.\nFühre einen WLAN-Scan mit aktiviertem GPS durch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        return
    }

    // Map is only rendered when geo-devices are available
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = "ScannerApp/${BuildConfig.VERSION_NAME} (Android; +https://github.com/TODO_REPLACE/ScannerApp)"
                osmdroidBasePath = ctx.filesDir
                osmdroidTileCache = ctx.filesDir.resolve("osmdroid/tiles")
            }
            val map = MapView(ctx)
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)
            map.controller.setZoom(16.0)
            map
        },
        update = { map ->
            // Overlay diff: only create/remove markers that actually changed.
            // Avoids O(n) Bitmap allocation + overlay rebuild on every scan cycle.
            val currentByBssid = geoDevices.associateBy { it.bssid }
            val cachedBssids = markerCache.keys.toSet()

            // 1. Remove stale markers
            (cachedBssids - currentByBssid.keys).forEach { bssid ->
                map.overlays.remove(markerCache.remove(bssid))
            }

            // 2. Update existing + create new
            currentByBssid.forEach { (bssid, geo) ->
                val cached = markerCache[bssid]
                if (cached == null) {
                    // New marker
                    val marker = Marker(map).apply {
                        position = GeoPoint(geo.lat, geo.lon)
                        title = geo.name.ifBlank { "WLAN Netzwerk" }
                        snippet = "BSSID: ${geo.bssid}\nSicherheit: ${geo.security}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createMarkerIcon(map.context, geo.security)
                    }
                    map.overlays.add(marker)
                    markerCache[bssid] = marker
                } else {
                    // Existing marker — update position if drifted
                    val newPos = GeoPoint(geo.lat, geo.lon)
                    if (cached.position.latitude != geo.lat || cached.position.longitude != geo.lon) {
                        cached.position = newPos
                    }
                    // Update snippet/icon if security changed
                    if (!cached.snippet.contains(geo.security)) {
                        cached.snippet = "BSSID: ${geo.bssid}\nSicherheit: ${geo.security}"
                        cached.icon = createMarkerIcon(map.context, geo.security)
                    }
                }
            }

            // One-time initial centering on the centroid of all geo-devices.
            // Performed here (inside update) rather than in a LaunchedEffect so that
            // setCenter() is called only when the MapView is guaranteed to be live —
            // avoiding the LaunchedEffect-vs-factory race condition (Pkt. 3 review).
            if (!hasInitiallyRecentered.value && geoDevices.isNotEmpty()) {
                val avgLat = geoDevices.sumOf { it.lat } / geoDevices.size
                val avgLon = geoDevices.sumOf { it.lon } / geoDevices.size
                map.controller.setCenter(GeoPoint(avgLat, avgLon))
                hasInitiallyRecentered.value = true
            }

            map.invalidate()
        },
        // Release OSMDroid Tile-Loader threads + Bitmap-Cache on Tab-detach to prevent memory leaks.
        // Also clear markerCache: cached Marker instances reference the destroyed MapView, so they
        // must not be reused on next factory() call (otherwise empty→non-empty cycles leave stale
        // markers attached to a dead MapView, causing the new MapView to render no markers).
        onRelease = {
            it.onDetach()
            markerCache.clear()
        }
    )
}
