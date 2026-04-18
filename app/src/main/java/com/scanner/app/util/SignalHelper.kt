package com.scanner.app.util

import androidx.compose.ui.graphics.Color

/**
 * Utility for assessing wireless signal strength and providing UI-friendly descriptions and colors.
 */
object SignalHelper {

    /**
     * Maps a WiFi signal strength (dBm) to a localized quality description.
     */
    fun wifiQuality(dbm: Int): String = when {
        dbm >= -50 -> "Exzellent"
        dbm >= -60 -> "Sehr gut"
        dbm >= -70 -> "Gut"
        dbm >= -80 -> "Mittel"
        else -> "Schwach"
    }

    /**
     * Normalizes a dBm value to a 0.0..1.0 fraction for UI progress bars or charts.
     *
     * @param dbm Current signal level.
     * @param minDbm Floor value (maps to 0.0), default -100 dBm.
     * @param maxDbm Ceiling value (maps to 1.0), default -30 dBm.
     */
    fun signalFraction(dbm: Int, minDbm: Int = -100, maxDbm: Int = -30): Float {
        return ((dbm - minDbm).toFloat() / (maxDbm - minDbm))
            .coerceIn(0f, 1f)
    }

    /**
     * Returns a color representing the signal quality (Green=Good, Orange=Medium, Red=Poor).
     */
    fun signalColor(fraction: Float): Color = when {
        fraction >= 0.7f -> Color(0xFF4CAF50) // Green
        fraction >= 0.4f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336)             // Red
    }

    /**
     * Maps a Bluetooth RSSI value to a localized quality description.
     */
    fun bluetoothQuality(rssi: Int): String = when {
        rssi >= -60 -> "Stark"
        rssi >= -80 -> "Mittel"
        else -> "Schwach"
    }
}
