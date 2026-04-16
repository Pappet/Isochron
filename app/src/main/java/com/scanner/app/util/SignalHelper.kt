package com.scanner.app.util

import androidx.compose.ui.graphics.Color

object SignalHelper {

    /**
     * Convert WiFi dBm to a quality description.
     */
    fun wifiQuality(dbm: Int): String = when {
        dbm >= -50 -> "Exzellent"
        dbm >= -60 -> "Sehr gut"
        dbm >= -70 -> "Gut"
        dbm >= -80 -> "Mittel"
        else -> "Schwach"
    }

    /**
     * Convert dBm to a 0.0–1.0 signal strength float.
     */
    fun signalFraction(dbm: Int, minDbm: Int = -100, maxDbm: Int = -30): Float {
        return ((dbm - minDbm).toFloat() / (maxDbm - minDbm))
            .coerceIn(0f, 1f)
    }

    /**
     * Color based on signal strength fraction.
     */
    fun signalColor(fraction: Float): Color = when {
        fraction >= 0.7f -> Color(0xFF4CAF50) // Green
        fraction >= 0.4f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336)             // Red
    }

    /**
     * Convert Bluetooth RSSI to quality description.
     */
    fun bluetoothQuality(rssi: Int): String = when {
        rssi >= -60 -> "Stark"
        rssi >= -80 -> "Mittel"
        else -> "Schwach"
    }
}
