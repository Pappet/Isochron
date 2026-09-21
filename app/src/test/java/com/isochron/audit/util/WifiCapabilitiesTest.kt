package com.isochron.audit.util

import com.isochron.audit.data.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the ScanResult mappings. Each of these silently produced a wrong but
 * plausible value before: WPA3 read as an open network, 6 GHz read as 5 GHz,
 * and channel 14 read as 15.
 */
class WifiCapabilitiesTest {

    // ─── parseSecurity ───────────────────────────────────────────────────────────

    @Test
    fun `WPA3 personal is detected from its SAE key management`() {
        // Android never writes "WPA3" here — this is what a WPA3-Personal AP reports.
        assertEquals(
            WifiSecurity.WPA3,
            WifiCapabilities.parseSecurity("[RSN-SAE-CCMP][ESS]")
        )
    }

    @Test
    fun `WPA2 WPA3 transition mode is detected as WPA3`() {
        assertEquals(
            WifiSecurity.WPA3,
            WifiCapabilities.parseSecurity("[RSN-PSK+SAE-CCMP][ESS][MFPC]")
        )
    }

    @Test
    fun `OWE is not mistaken for WPA2 despite sitting inside RSN`() {
        assertEquals(
            WifiSecurity.OWE,
            WifiCapabilities.parseSecurity("[RSN-OWE-CCMP][ESS]")
        )
    }

    @Test
    fun `WPA2 is detected in both the WPA2 and the RSN spelling`() {
        assertEquals(WifiSecurity.WPA2, WifiCapabilities.parseSecurity("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA2, WifiCapabilities.parseSecurity("[RSN-PSK-CCMP][ESS]"))
    }

    @Test
    fun `WPA1 is not swallowed by the WPA2 branch`() {
        assertEquals(WifiSecurity.WPA, WifiCapabilities.parseSecurity("[WPA-PSK-TKIP][ESS]"))
    }

    @Test
    fun `WEP is detected`() {
        assertEquals(WifiSecurity.WEP, WifiCapabilities.parseSecurity("[WEP][ESS]"))
    }

    @Test
    fun `a network without any key management is open`() {
        assertEquals(WifiSecurity.OPEN, WifiCapabilities.parseSecurity("[ESS]"))
        assertEquals(WifiSecurity.OPEN, WifiCapabilities.parseSecurity("[WPS][ESS]"))
    }

    @Test
    fun `missing capabilities yield UNKNOWN rather than open`() {
        // Reporting "open" for a network we could not read would be a false alarm.
        assertEquals(WifiSecurity.UNKNOWN, WifiCapabilities.parseSecurity(null))
    }

    @Test
    fun `only a truly open network counts as unencrypted`() {
        assertEquals(true, WifiSecurity.OPEN.isUnencrypted)
        assertEquals(false, WifiSecurity.OWE.isUnencrypted)
        assertEquals(false, WifiSecurity.UNKNOWN.isUnencrypted)
    }

    @Test
    fun `only WEP and WPA1 count as deprecated`() {
        assertEquals(true, WifiSecurity.WEP.isDeprecated)
        assertEquals(true, WifiSecurity.WPA.isDeprecated)
        assertEquals(false, WifiSecurity.WPA2.isDeprecated)
        assertEquals(false, WifiSecurity.WPA3.isDeprecated)
    }

    // ─── securityLabel ───────────────────────────────────────────────────────────

    @Test
    fun `label distinguishes pure WPA3 from transition mode`() {
        assertEquals(
            "WPA3 (SAE)",
            WifiCapabilities.securityLabel(WifiSecurity.WPA3, "[RSN-SAE-CCMP][ESS]")
        )
        assertEquals(
            "WPA2/WPA3 (SAE)",
            WifiCapabilities.securityLabel(WifiSecurity.WPA3, "[RSN-PSK+SAE-CCMP][ESS]")
        )
    }

    @Test
    fun `label marks enterprise variants`() {
        assertEquals(
            "WPA2 Enterprise",
            WifiCapabilities.securityLabel(WifiSecurity.WPA2, "[RSN-EAP-CCMP][ESS]")
        )
        assertEquals(
            "WPA3 Enterprise",
            WifiCapabilities.securityLabel(WifiSecurity.WPA3, "[RSN-EAP+SAE-CCMP][ESS]")
        )
    }

    @Test
    fun `label names the WPA2 cipher`() {
        assertEquals(
            "WPA2 (CCMP)",
            WifiCapabilities.securityLabel(WifiSecurity.WPA2, "[WPA2-PSK-CCMP][ESS]")
        )
        assertEquals(
            "WPA2 (TKIP)",
            WifiCapabilities.securityLabel(WifiSecurity.WPA2, "[WPA2-PSK-TKIP][ESS]")
        )
    }

    // ─── frequencyToBand ─────────────────────────────────────────────────────────

    @Test
    fun `band boundaries separate 2_4, 5 and 6 GHz`() {
        assertEquals("2.4 GHz", WifiCapabilities.frequencyToBand(2412))
        assertEquals("2.4 GHz", WifiCapabilities.frequencyToBand(2484))
        assertEquals("5 GHz", WifiCapabilities.frequencyToBand(5180))
        assertEquals("5 GHz", WifiCapabilities.frequencyToBand(5885))
        assertEquals("6 GHz", WifiCapabilities.frequencyToBand(5935))
        assertEquals("6 GHz", WifiCapabilities.frequencyToBand(5955))
        assertEquals("6 GHz", WifiCapabilities.frequencyToBand(7115))
    }

    // ─── frequencyToChannel ──────────────────────────────────────────────────────

    @Test
    fun `2_4 GHz channels map to 1 through 13`() {
        assertEquals(1, WifiCapabilities.frequencyToChannel(2412))
        assertEquals(6, WifiCapabilities.frequencyToChannel(2437))
        assertEquals(13, WifiCapabilities.frequencyToChannel(2472))
    }

    @Test
    fun `channel 14 is not reported as 15`() {
        // 2484 MHz falls inside the 2412-range arithmetic and used to yield 15.
        assertEquals(14, WifiCapabilities.frequencyToChannel(2484))
    }

    @Test
    fun `5 GHz channels keep their established numbering`() {
        assertEquals(32, WifiCapabilities.frequencyToChannel(5160))
        assertEquals(36, WifiCapabilities.frequencyToChannel(5180))
        assertEquals(64, WifiCapabilities.frequencyToChannel(5320))
        assertEquals(149, WifiCapabilities.frequencyToChannel(5745))
        assertEquals(165, WifiCapabilities.frequencyToChannel(5825))
    }

    @Test
    fun `5 GHz channels above 165 are no longer unknown`() {
        assertEquals(169, WifiCapabilities.frequencyToChannel(5845))
        assertEquals(177, WifiCapabilities.frequencyToChannel(5885))
    }

    @Test
    fun `6 GHz channels restart at 1 instead of returning -1`() {
        assertEquals(1, WifiCapabilities.frequencyToChannel(5955))
        assertEquals(5, WifiCapabilities.frequencyToChannel(5975))
        assertEquals(45, WifiCapabilities.frequencyToChannel(6175))
        assertEquals(233, WifiCapabilities.frequencyToChannel(7115))
    }

    @Test
    fun `6 GHz channel 2 outlier is mapped`() {
        assertEquals(2, WifiCapabilities.frequencyToChannel(5935))
    }

    @Test
    fun `frequencies outside every plan report -1`() {
        assertEquals(-1, WifiCapabilities.frequencyToChannel(1000))
        assertEquals(-1, WifiCapabilities.frequencyToChannel(8000))
    }
}
