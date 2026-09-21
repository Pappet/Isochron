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
    /** Raw SSID; blank for a hidden network — see [isHidden]. */
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
    /** Hidden networks broadcast an empty SSID. */
    val isHidden: Boolean get() = ssid.isBlank()

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
    /** Advertised name, or [UNKNOWN_NAME] when the device sends none — see [isUnnamed]. */
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
        !isUnnamed -> name
        vendor != null -> vendor
        else -> address
    }

    /** True when the device advertised no name. Compare this, never the [name] text. */
    val isUnnamed: Boolean get() = name.isBlank() || name == UNKNOWN_NAME

    companion object {
        /**
         * Internal marker for "no name advertised". Not for display — UI shows
         * R.string.bt_unknown_device instead (audit E3).
         */
        const val UNKNOWN_NAME = "(Unbekannt)"
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
    @androidx.annotation.StringRes
    fun labelRes(): Int = when (this) {
        CLASSIC -> com.isochron.audit.R.string.bt_type_classic
        BLE -> com.isochron.audit.R.string.bt_type_ble
        DUAL -> com.isochron.audit.R.string.bt_type_dual
        UNKNOWN -> com.isochron.audit.R.string.bt_type_unknown
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
    @androidx.annotation.StringRes
    fun labelRes(): Int = when (this) {
        BONDED -> com.isochron.audit.R.string.bond_bonded
        BONDING -> com.isochron.audit.R.string.bond_bonding
        NOT_BONDED -> com.isochron.audit.R.string.bond_not_bonded
    }
}
