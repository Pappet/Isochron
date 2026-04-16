package com.scanner.app.data.db

import androidx.room.TypeConverter
import java.time.Instant

class Converters {

    // ─── Instant ↔ Long ─────────────────────────────────────────

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // ─── DeviceCategory ↔ String ────────────────────────────────

    @TypeConverter
    fun fromDeviceCategory(value: DeviceCategory): String = value.name

    @TypeConverter
    fun toDeviceCategory(value: String): DeviceCategory =
        DeviceCategory.valueOf(value)

    // ─── ScanType ↔ String ──────────────────────────────────────

    @TypeConverter
    fun fromScanType(value: ScanType): String = value.name

    @TypeConverter
    fun toScanType(value: String): ScanType =
        ScanType.valueOf(value)
}
