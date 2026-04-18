package com.scanner.app.util

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test

/**
 * Note: SignalHelper.signalColor() returns androidx.compose.ui.graphics.Color.
 * Color is a value class backed by a ULong — it can be instantiated in JVM unit
 * tests without an Android device (no Android framework dependency).
 */
class SignalHelperTest {

    // ─── signalFraction ──────────────────────────────────────────────────────────

    @Test
    fun `signalFraction at max dBm returns 1_0`() {
        assertEquals(1.0f, SignalHelper.signalFraction(-30), 0.001f)
    }

    @Test
    fun `signalFraction at min dBm returns 0_0`() {
        assertEquals(0.0f, SignalHelper.signalFraction(-100), 0.001f)
    }

    @Test
    fun `signalFraction clamps above max`() {
        assertEquals(1.0f, SignalHelper.signalFraction(0), 0.001f)
    }

    @Test
    fun `signalFraction clamps below min`() {
        assertEquals(0.0f, SignalHelper.signalFraction(-150), 0.001f)
    }

    @Test
    fun `signalFraction at midpoint is approximately 0_5`() {
        // midpoint between -100 and -30 is -65
        val fraction = SignalHelper.signalFraction(-65)
        assertEquals(0.5f, fraction, 0.02f)
    }

    // ─── wifiQuality ─────────────────────────────────────────────────────────────

    @Test
    fun `wifiQuality at -45 dBm is Exzellent`() {
        assertEquals("Exzellent", SignalHelper.wifiQuality(-45))
    }

    @Test
    fun `wifiQuality at -55 dBm is Sehr gut`() {
        assertEquals("Sehr gut", SignalHelper.wifiQuality(-55))
    }

    @Test
    fun `wifiQuality at -65 dBm is Gut`() {
        assertEquals("Gut", SignalHelper.wifiQuality(-65))
    }

    @Test
    fun `wifiQuality at -75 dBm is Mittel`() {
        assertEquals("Mittel", SignalHelper.wifiQuality(-75))
    }

    @Test
    fun `wifiQuality at -90 dBm is Schwach`() {
        assertEquals("Schwach", SignalHelper.wifiQuality(-90))
    }

    @Test
    fun `wifiQuality boundary at exactly -50 dBm is Exzellent`() {
        assertEquals("Exzellent", SignalHelper.wifiQuality(-50))
    }

    @Test
    fun `wifiQuality boundary at exactly -60 dBm is Sehr gut`() {
        assertEquals("Sehr gut", SignalHelper.wifiQuality(-60))
    }

    // ─── bluetoothQuality ────────────────────────────────────────────────────────

    @Test
    fun `bluetoothQuality at -50 is Stark`() {
        assertEquals("Stark", SignalHelper.bluetoothQuality(-50))
    }

    @Test
    fun `bluetoothQuality at -70 is Mittel`() {
        assertEquals("Mittel", SignalHelper.bluetoothQuality(-70))
    }

    @Test
    fun `bluetoothQuality at -90 is Schwach`() {
        assertEquals("Schwach", SignalHelper.bluetoothQuality(-90))
    }

    // ─── signalColor (Compose Color, accessible in JVM unit tests) ───────────────

    @Test
    fun `signalColor for strong signal is green`() {
        val color = SignalHelper.signalColor(0.8f)
        // Color(0xFF4CAF50) — compare packed value
        assertEquals(0xFF4CAF50.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor for medium signal is orange`() {
        val color = SignalHelper.signalColor(0.5f)
        assertEquals(0xFFFF9800.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor for weak signal is red`() {
        val color = SignalHelper.signalColor(0.2f)
        assertEquals(0xFFF44336.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor boundary 0_7 is green`() {
        // fraction >= 0.7 is green
        val color = SignalHelper.signalColor(0.7f)
        assertEquals(0xFF4CAF50.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor boundary 0_4 is orange`() {
        // fraction >= 0.4 (but < 0.7) is orange
        val color = SignalHelper.signalColor(0.4f)
        assertEquals(0xFFFF9800.toInt(), color.toArgb())
    }
}
