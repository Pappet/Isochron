package com.isochron.audit.data

/**
 * Link-layer security standard of a WiFi network.
 *
 * This is the value all risk logic must branch on. [WifiNetwork.securityType] carries
 * the human-readable label for the same information and must never be string-compared:
 * it is localized and carries cipher/enterprise detail, so equality checks against it
 * silently stop matching.
 */
enum class WifiSecurity {
    /** No link-layer encryption at all. */
    OPEN,

    /** WEP — broken, crackable in minutes. */
    WEP,

    /** WPA1 / TKIP era — deprecated. */
    WPA,

    /** WPA2 (RSN), personal or enterprise. */
    WPA2,

    /** WPA3 (SAE), including WPA2/WPA3 transition mode. */
    WPA3,

    /** Opportunistic Wireless Encryption — unauthenticated but encrypted. */
    OWE,

    /** Capabilities were unavailable, so the standard could not be determined. */
    UNKNOWN;

    /** True when traffic travels unencrypted over the air. */
    val isUnencrypted: Boolean
        get() = this == OPEN

    /** True when the standard itself is broken or deprecated. */
    val isDeprecated: Boolean
        get() = this == WEP || this == WPA
}

/**
 * Generic model for a discovered WiFi network, used by [WifiScanner].
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,       // Signal level in dBm
    val frequency: Int,            // Frequency in MHz
    val channel: Int,              // Channel number
    val securityType: String,      // Localized display label of the encryption type
    val security: WifiSecurity = WifiSecurity.UNKNOWN, // Machine-readable counterpart; branch on this
    val isConnected: Boolean = false,
    val band: String,              // "2.4 GHz", "5 GHz" or "6 GHz"
    val wpsEnabled: Boolean = false,
    val rawCapabilities: String = "",
    val vendor: String? = null,
    val wifiStandard: String? = null,
    val channelWidth: String? = null,
    val distance: Double? = null  // Estimated distance in meters (FSPL)
) {
    /**
     * True when the network is worth flagging to the user: no encryption,
     * a broken standard, or WPS left enabled.
     */
    fun isRisk(): Boolean =
        security.isUnencrypted || security.isDeprecated || wpsEnabled
}

/**
 * Generic model for a discovered Bluetooth device (Classic or BLE).
 */
data class BluetoothDevice(
    val name: String,
    val address: String,           // MAC address
    val rssi: Int?,                // Signal level in dBm, null if not available
    val type: DeviceType,
    val bondState: BondState,
    val isConnected: Boolean = false,
    val deviceClass: String?,      // Major device class description
    val vendor: String? = null,    // MAC OUI vendor name
    val minorClass: String? = null, // e.g., "Smartphone", "Laptop", "Headphones"
    val serviceUuids: List<String> = emptyList(),
    val txPower: Int? = null       // Advertising TX Power (BE v5+)
) {
    /**
     * Best available display name: name > vendor > address
     */
    fun displayName(): String = when {
        name != "(Unbekannt)" && name.isNotBlank() -> name
        vendor != null -> vendor
        else -> address
    }

    /**
     * Subtitle info line
     */
    fun subtitle(): String = buildString {
        if (name != "(Unbekannt)" && vendor != null) append(vendor)
        if (minorClass != null) {
            if (isNotEmpty()) append(" · ")
            append(minorClass)
        }
        if (isEmpty()) append(type.displayName())
    }
}

/**
 * Supported Bluetooth technology types.
 */
enum class DeviceType {
    CLASSIC,
    BLE,
    DUAL,
    UNKNOWN;

    /** Returns a localized display name for the device type. */
    fun displayName(): String = when (this) {
        CLASSIC -> "Classic"
        BLE -> "BLE"
        DUAL -> "Dual"
        UNKNOWN -> "Unbekannt"
    }
}

/**
 * Android Bluetooth bond (pairing) states.
 */
enum class BondState {
    BONDED,
    BONDING,
    NOT_BONDED;

    /** Returns a localized display name for the bond state. */
    fun displayName(): String = when (this) {
        BONDED -> "Gekoppelt"
        BONDING -> "Kopplung..."
        NOT_BONDED -> "Nicht gekoppelt"
    }
}
