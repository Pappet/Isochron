package com.isochron.audit.util

import com.isochron.audit.data.WifiSecurity

/**
 * Pure mapping helpers between raw [android.net.wifi.ScanResult] values and the
 * domain model.
 *
 * Kept free of any Android dependency so the mappings are unit-testable: every one
 * of them is a silent-failure candidate — a wrong branch produces a plausible-looking
 * but false label rather than an error.
 */
object WifiCapabilities {

    /**
     * Determines the security standard from a capabilities string.
     *
     * Android does not spell out "WPA3": a WPA3-Personal network reports
     * `[RSN-SAE-CCMP]` and transition mode reports `[RSN-PSK+SAE-CCMP]`, so the
     * standard has to be recognised by its key management suite. Order matters —
     * SAE and OWE both appear inside RSN brackets, so they must be tested before
     * the generic RSN/WPA2 branch.
     *
     * @param capabilities The raw capabilities string, or null if unavailable.
     */
    fun parseSecurity(capabilities: String?): WifiSecurity {
        if (capabilities == null) return WifiSecurity.UNKNOWN

        return when {
            capabilities.contains("SAE") || capabilities.contains("WPA3") -> WifiSecurity.WPA3
            capabilities.contains("OWE") -> WifiSecurity.OWE
            capabilities.contains("WPA2") || capabilities.contains("RSN") -> WifiSecurity.WPA2
            capabilities.contains("WPA") -> WifiSecurity.WPA
            capabilities.contains("WEP") -> WifiSecurity.WEP
            else -> WifiSecurity.OPEN
        }
    }

    /**
     * Builds the human-readable label shown in the UI and written to exports.
     * Derived from [parseSecurity] so label and [WifiSecurity] can never disagree.
     */
    fun securityLabel(security: WifiSecurity, capabilities: String): String {
        val enterprise = capabilities.contains("EAP")
        return when (security) {
            WifiSecurity.WPA3 -> when {
                enterprise -> "WPA3 Enterprise"
                // Both PSK and SAE present: the AP still accepts WPA2 clients.
                capabilities.contains("PSK") -> "WPA2/WPA3 (SAE)"
                else -> "WPA3 (SAE)"
            }
            WifiSecurity.OWE -> "OWE (Enhanced Open)"
            WifiSecurity.WPA2 -> when {
                enterprise -> "WPA2 Enterprise"
                capabilities.contains("CCMP") -> "WPA2 (CCMP)"
                capabilities.contains("TKIP") -> "WPA2 (TKIP)"
                else -> "WPA2 (PSK)"
            }
            WifiSecurity.WPA -> "WPA"
            WifiSecurity.WEP -> "WEP"
            WifiSecurity.OPEN -> "Offen"
            WifiSecurity.UNKNOWN -> "Unbekannt"
        }
    }

    /**
     * Maps a frequency in MHz to its band label.
     *
     * The 6 GHz band (Wi-Fi 6E) starts at 5925 MHz and must be separated from 5 GHz:
     * its channel numbering restarts at 1, so a 6 GHz AP labelled "5 GHz" also carries
     * a meaningless channel number.
     */
    fun frequencyToBand(freq: Int): String = when {
        freq >= 5925 -> "6 GHz"
        freq >= 4900 -> "5 GHz"
        else -> "2.4 GHz"
    }

    /**
     * Converts a frequency in MHz to its channel number.
     * Covers 2.4 GHz (1-14), 5 GHz (32-177) and 6 GHz (1-233).
     *
     * @return The channel number, or -1 if the frequency belongs to no known plan.
     */
    fun frequencyToChannel(freq: Int): Int = when {
        // Channel 14 sits 12 MHz above channel 13, so it breaks the 5 MHz spacing
        // rule and has to be matched before the 2.4 GHz range.
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        // 6 GHz channel 2 is a single outlier below the contiguous 6 GHz range.
        freq == 5935 -> 2
        freq in 5160..5885 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> -1
    }
}
