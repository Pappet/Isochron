package com.scanner.app.data.db

import androidx.room.*
import java.time.Instant

// ─── Enums ──────────────────────────────────────────────────────

enum class DeviceCategory {
    WIFI,
    BT_CLASSIC,
    BT_BLE,
    BT_DUAL,
    LAN;

    fun displayName(): String = when (this) {
        WIFI -> "WLAN"
        BT_CLASSIC -> "Bluetooth Classic"
        BT_BLE -> "Bluetooth LE"
        BT_DUAL -> "Bluetooth Dual"
        LAN -> "LAN"
    }

    fun shortName(): String = when (this) {
        WIFI -> "WiFi"
        BT_CLASSIC -> "BT"
        BT_BLE -> "BLE"
        BT_DUAL -> "BT"
        LAN -> "LAN"
    }
}

enum class ScanType {
    WIFI,
    BLUETOOTH,
    BOTH,
    LAN
}

// ─── Discovered Device ──────────────────────────────────────────

@Entity(
    tableName = "discovered_devices",
    indices = [
        Index(value = ["address"], unique = true),
        Index(value = ["device_category"]),
        Index(value = ["is_favorite"]),
        Index(value = ["last_seen"])
    ]
)
data class DiscoveredDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "address")
    val address: String,                    // MAC-Adresse / BSSID (eindeutig)

    @ColumnInfo(name = "name")
    val name: String,                       // Erkannter Gerätename / SSID

    @ColumnInfo(name = "custom_label")
    val customLabel: String? = null,        // Benutzerdefiniertes Label

    @ColumnInfo(name = "notes")
    val notes: String? = null,              // Notizen

    @ColumnInfo(name = "device_category")
    val deviceCategory: DeviceCategory,

    @ColumnInfo(name = "first_seen")
    val firstSeen: Instant,

    @ColumnInfo(name = "last_seen")
    val lastSeen: Instant,

    @ColumnInfo(name = "last_signal_strength")
    val lastSignalStrength: Int? = null,    // dBm

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "times_seen")
    val timesSeen: Int = 1,

    @ColumnInfo(name = "metadata")
    val metadata: String? = null            // JSON: Zusatzinfos (Frequenz, Kanal, Sicherheit, Geräteklasse)
) {
    /**
     * Returns the display name: custom label if set, otherwise detected name.
     */
    fun displayName(): String = customLabel ?: name
}

// ─── Scan Session ───────────────────────────────────────────────

@Entity(
    tableName = "scan_sessions",
    indices = [Index(value = ["timestamp"])]
)
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "scan_type")
    val scanType: ScanType,

    @ColumnInfo(name = "timestamp")
    val timestamp: Instant,

    @ColumnInfo(name = "device_count")
    val deviceCount: Int,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null
)

// ─── Signal Reading (für Monitoring & Graphen) ──────────────────

@Entity(
    tableName = "signal_readings",
    foreignKeys = [
        ForeignKey(
            entity = DiscoveredDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["scan_session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["device_id"]),
        Index(value = ["scan_session_id"]),
        Index(value = ["timestamp"])
    ]
)
data class SignalReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "device_id")
    val deviceId: Long,

    @ColumnInfo(name = "signal_strength")
    val signalStrength: Int,                // dBm

    @ColumnInfo(name = "timestamp")
    val timestamp: Instant,

    @ColumnInfo(name = "scan_session_id")
    val scanSessionId: Long? = null
)

// ─── Query Result POJOs ─────────────────────────────────────────

data class DeviceWithReadingCount(
    @Embedded val device: DiscoveredDeviceEntity,
    @ColumnInfo(name = "reading_count") val readingCount: Int
)

data class SignalOverTime(
    @ColumnInfo(name = "signal_strength") val signalStrength: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Instant
)
