#!/usr/bin/env python3
"""
Download the Nordic Semiconductor bluetooth-numbers-database
and generate BleUuidDatabase.kt with ALL entries.

Usage:
    cd ~/Projekte/ScannerApp
    python3 generate_ble_db.py

This will overwrite:
    app/src/main/java/com/scanner/app/util/BleUuidDatabase.kt
"""

import json
import urllib.request
import sys
import os

BASE_URL = "https://raw.githubusercontent.com/NordicSemiconductor/bluetooth-numbers-database/master/v1"
OUTPUT = "app/src/main/java/com/scanner/app/util/BleUuidDatabase.kt"

def fetch_json(name):
    url = f"{BASE_URL}/{name}.json"
    print(f"  Downloading {url} ...")
    try:
        with urllib.request.urlopen(url) as resp:
            data = json.loads(resp.read())
            print(f"    → {len(data)} entries")
            return data
    except Exception as e:
        print(f"    ERROR: {e}")
        return []

def to_hex(uuid_str):
    """Extract 16-bit UUID number from various formats:
       '0x1800', '1800', '00001800-0000-1000-8000-00805f9b34fb'
    """
    s = uuid_str.strip().upper()
    
    # "0x1800" format
    if s.startswith("0X"):
        return int(s, 16)
    
    # Full 128-bit UUID: extract bytes 4-8
    if len(s) == 36 and s[8] == '-':
        short = s[4:8]
        return int(short, 16)
    
    # Plain hex: "1800", "2A00", "FD06" etc.
    if len(s) <= 6:
        try:
            return int(s, 16)
        except ValueError:
            return None
    
    return None

def escape_kotlin(s):
    return s.replace("\\", "\\\\").replace("\"", "\\\"")

def main():
    print("=== Nordic Bluetooth Numbers Database → Kotlin Generator ===\n")

    # Download all JSON files
    services_raw = fetch_json("service_uuids")
    chars_raw = fetch_json("characteristic_uuids")
    descs_raw = fetch_json("descriptor_uuids")
    companies_raw = fetch_json("company_ids")

    # Parse services
    services = {}
    skipped = 0
    for entry in services_raw:
        uuid_val = to_hex(entry.get("uuid", ""))
        name = entry.get("name", "")
        if uuid_val is not None and name:
            services[uuid_val] = name
        else:
            skipped += 1
    if skipped: print(f"    ⚠ {skipped} service entries skipped (bad UUID)")

    # Parse characteristics
    chars = {}
    skipped = 0
    for entry in chars_raw:
        uuid_val = to_hex(entry.get("uuid", ""))
        name = entry.get("name", "")
        if uuid_val is not None and name:
            chars[uuid_val] = name
        else:
            skipped += 1
    if skipped: print(f"    ⚠ {skipped} characteristic entries skipped (bad UUID)")

    # Parse descriptors
    descs = {}
    for entry in descs_raw:
        uuid_val = to_hex(entry.get("uuid", ""))
        name = entry.get("name", "")
        if uuid_val is not None and name:
            descs[uuid_val] = name

    # Parse company IDs
    companies = {}
    for entry in companies_raw:
        cid = entry.get("code", None)
        name = entry.get("name", "")
        if cid is not None and name:
            companies[int(cid)] = name

    print(f"\nParsed: {len(services)} services, {len(chars)} characteristics, "
          f"{len(descs)} descriptors, {len(companies)} companies\n")

    # Sanity check
    checks = [
        ("Service 0x1800 (Generic Access)", 0x1800 in services),
        ("Service 0x1801 (Generic Attribute)", 0x1801 in services),
        ("Service 0x180F (Battery)", 0x180F in services),
        ("Char 0x2A00 (Device Name)", 0x2A00 in chars),
        ("Char 0x2A05 (Service Changed)", 0x2A05 in chars),
        ("Descriptor 0x2902 (CCCD)", 0x2902 in descs),
        ("Company ID 0 (Ericsson)", 0 in companies),
    ]
    all_ok = True
    for label, ok in checks:
        status = "✅" if ok else "❌ MISSING"
        if not ok: all_ok = False
        print(f"  {status} {label}")
    
    if not all_ok:
        print("\n⚠ Some basic entries are missing! Check JSON format.")
        print("  Sample service entry:", services_raw[0] if services_raw else "EMPTY")
        print("  Sample char entry:", chars_raw[0] if chars_raw else "EMPTY")

    # Categorize services
    health_uuids = {0x1808, 0x1809, 0x180D, 0x1810, 0x181F, 0x1822, 0x183A, 0x183E, 0x1854}
    fitness_uuids = {0x1814, 0x1816, 0x1818, 0x1826, 0x181B, 0x181D}
    device_info_uuids = {0x180A, 0x1800, 0x1801}
    battery_uuids = {0x180F}
    environmental_uuids = {0x181A, 0x1805}
    connectivity_uuids = {0x1820, 0x1823, 0x1824}
    input_uuids = {0x1812}
    audio_uuids = {0x1843, 0x1844, 0x1846, 0x1848, 0x1849, 0x184E, 0x184F, 0x1850, 0x1851, 0x1853, 0x1858}

    # Generate Kotlin
    lines = []
    lines.append('package com.scanner.app.util')
    lines.append('')
    lines.append('import java.util.UUID')
    lines.append('')
    lines.append('/**')
    lines.append(' * Complete BLE GATT UUID Database.')
    lines.append(f' * Generated from Nordic Semiconductor bluetooth-numbers-database.')
    lines.append(f' * Services: {len(services)}, Characteristics: {len(chars)},')
    lines.append(f' * Descriptors: {len(descs)}, Company IDs: {len(companies)}')
    lines.append(' *')
    lines.append(' * Source: https://github.com/NordicSemiconductor/bluetooth-numbers-database')
    lines.append(' * Regenerate: python3 generate_ble_db.py')
    lines.append(' */')
    lines.append('object BleUuidDatabase {')
    lines.append('')

    # serviceName function
    lines.append('    fun serviceName(uuid: UUID): String {')
    lines.append('        val short = extractShortUuid(uuid)')
    lines.append('        return SERVICES[short]')
    lines.append('            ?: if (short in 0xFD00..0xFDFF) "Registrierter Dienst (0x${"%04X".format(short)})"')
    lines.append('            else if (short in 0xFE00..0xFEFF) "Vendor-Dienst (0x${"%04X".format(short)})"')
    lines.append('            else "Unbekannter Dienst (${formatUuid(uuid)})"')
    lines.append('    }')
    lines.append('')

    # characteristicName function
    lines.append('    fun characteristicName(uuid: UUID): String {')
    lines.append('        val short = extractShortUuid(uuid)')
    lines.append('        return CHARACTERISTICS[short] ?: "Unbekannt (${formatUuid(uuid)})"')
    lines.append('    }')
    lines.append('')

    # descriptorName function
    lines.append('    fun descriptorName(uuid: UUID): String {')
    lines.append('        val short = extractShortUuid(uuid)')
    lines.append('        return DESCRIPTORS[short] ?: formatUuid(uuid)')
    lines.append('    }')
    lines.append('')

    # companyName function
    lines.append('    fun companyName(companyId: Int): String? {')
    lines.append('        return COMPANY_IDS[companyId]')
    lines.append('    }')
    lines.append('')

    # serviceCategory function
    lines.append('    fun serviceCategory(uuid: UUID): ServiceCategory {')
    lines.append('        val short = extractShortUuid(uuid)')
    lines.append('        return SERVICE_CATEGORIES[short] ?: ServiceCategory.OTHER')
    lines.append('    }')
    lines.append('')

    # isStandardUuid
    lines.append('    fun isStandardUuid(uuid: UUID): Boolean {')
    lines.append('        val uuidStr = uuid.toString().lowercase()')
    lines.append('        return uuidStr.endsWith("-0000-1000-8000-00805f9b34fb")')
    lines.append('    }')
    lines.append('')

    # formatUuid
    lines.append('    fun formatUuid(uuid: UUID): String {')
    lines.append('        val str = uuid.toString().uppercase()')
    lines.append('        return if (isStandardUuid(uuid)) {')
    lines.append('            "0x${str.substring(4, 8)}"')
    lines.append('        } else {')
    lines.append('            str.substring(0, 8) + "…"')
    lines.append('        }')
    lines.append('    }')
    lines.append('')

    # extractShortUuid
    lines.append('    private fun extractShortUuid(uuid: UUID): Int {')
    lines.append('        return ((uuid.mostSignificantBits shr 32) and 0xFFFF).toInt()')
    lines.append('    }')
    lines.append('')

    # ServiceCategory enum
    lines.append('    enum class ServiceCategory(val label: String, val emoji: String) {')
    lines.append('        HEALTH("Gesundheit", "❤"),')
    lines.append('        FITNESS("Fitness", "🏃"),')
    lines.append('        DEVICE_INFO("Geräte-Info", "ℹ"),')
    lines.append('        BATTERY("Batterie", "🔋"),')
    lines.append('        ENVIRONMENTAL("Umgebung", "🌡"),')
    lines.append('        CONNECTIVITY("Konnektivität", "📶"),')
    lines.append('        INPUT("Eingabe", "⌨"),')
    lines.append('        AUDIO("Audio", "🎵"),')
    lines.append('        OTHER("Sonstige", "•")')
    lines.append('    }')
    lines.append('')

    # Helper: generate a map, splitting into chunks if large
    def gen_map(name, data, key_format, val_type="String", chunk_size=400):
        """Generate a map, using lazy + builder functions if entries > chunk_size."""
        entries = sorted(data.keys())
        count = len(entries)

        if count <= chunk_size:
            # Small map: inline mapOf
            lines.append(f'    private val {name} = mapOf<Int, {val_type}>(')
            for k in entries:
                v = escape_kotlin(str(data[k]))
                lines.append(f'        {key_format(k)} to "{v}",')
            lines.append('    )')
            lines.append('')
        else:
            # Large map: lazy + chunked builder functions
            num_chunks = (count + chunk_size - 1) // chunk_size
            lines.append(f'    private val {name}: Map<Int, {val_type}> by lazy {{')
            lines.append(f'        val m = HashMap<Int, {val_type}>({count + 100})')
            for i in range(num_chunks):
                lines.append(f'        {name}_chunk{i}(m)')
            lines.append('        m')
            lines.append('    }')
            lines.append('')

            for i in range(num_chunks):
                start = i * chunk_size
                end = min(start + chunk_size, count)
                chunk = entries[start:end]
                lines.append(f'    private fun {name}_chunk{i}(m: HashMap<Int, {val_type}>) {{')
                for k in chunk:
                    v = escape_kotlin(str(data[k]))
                    lines.append(f'        m[{key_format(k)}] = "{v}"')
                lines.append('    }')
                lines.append('')

    hex_fmt = lambda k: f'0x{k:04X}'
    int_fmt = lambda k: str(k)

    # SERVICES map
    lines.append(f'    // ─── GATT Services ({len(services)} entries) ────────────────────')
    gen_map('SERVICES', services, hex_fmt)

    # SERVICE_CATEGORIES map
    lines.append('    private val SERVICE_CATEGORIES = mapOf<Int, ServiceCategory>(')
    for u in sorted(health_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.HEALTH,')
    for u in sorted(fitness_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.FITNESS,')
    for u in sorted(device_info_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.DEVICE_INFO,')
    for u in sorted(battery_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.BATTERY,')
    for u in sorted(environmental_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.ENVIRONMENTAL,')
    for u in sorted(connectivity_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.CONNECTIVITY,')
    for u in sorted(input_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.INPUT,')
    for u in sorted(audio_uuids):
        if u in services: lines.append(f'        0x{u:04X} to ServiceCategory.AUDIO,')
    lines.append('    )')
    lines.append('')

    # CHARACTERISTICS map
    lines.append(f'    // ─── GATT Characteristics ({len(chars)} entries) ──────────────')
    gen_map('CHARACTERISTICS', chars, hex_fmt)

    # DESCRIPTORS map
    lines.append(f'    // ─── GATT Descriptors ({len(descs)} entries) ─────────────────')
    gen_map('DESCRIPTORS', descs, hex_fmt)

    # COMPANY_IDS map
    lines.append(f'    // ─── Bluetooth SIG Company IDs ({len(companies)} entries) ─────')
    gen_map('COMPANY_IDS', companies, int_fmt)

    # iBeacon parser (keep existing)
    lines.append('    // ─── iBeacon / Eddystone ────────────────────────────────────')
    lines.append('')
    lines.append('    val IBEACON_PREFIX = byteArrayOf(0x02, 0x15)')
    lines.append('')
    lines.append('    val EDDYSTONE_SERVICE_UUID: UUID =')
    lines.append('        UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")')
    lines.append('')
    lines.append('    fun parseIBeacon(manufacturerData: ByteArray?): IBeaconData? {')
    lines.append('        if (manufacturerData == null || manufacturerData.size < 23) return null')
    lines.append('        val offset = if (manufacturerData.size >= 25 &&')
    lines.append('            manufacturerData[0] == 0x4C.toByte() &&')
    lines.append('            manufacturerData[1] == 0x00.toByte()') 
    lines.append('        ) 2 else 0')
    lines.append('        if (manufacturerData.size < offset + 21) return null')
    lines.append('        if (manufacturerData[offset] != 0x02.toByte()) return null')
    lines.append('        if (manufacturerData[offset + 1] != 0x15.toByte()) return null')
    lines.append('        val uuidBytes = manufacturerData.sliceArray(offset + 2 until offset + 18)')
    lines.append('        val uuid = bytesToUuid(uuidBytes)')
    lines.append('        val major = ((manufacturerData[offset + 18].toInt() and 0xFF) shl 8) or')
    lines.append('                (manufacturerData[offset + 19].toInt() and 0xFF)')
    lines.append('        val minor = ((manufacturerData[offset + 20].toInt() and 0xFF) shl 8) or')
    lines.append('                (manufacturerData[offset + 21].toInt() and 0xFF)')
    lines.append('        val txPower = if (manufacturerData.size > offset + 22)')
    lines.append('            manufacturerData[offset + 22].toInt() else null')
    lines.append('        return IBeaconData(uuid, major, minor, txPower)')
    lines.append('    }')
    lines.append('')
    lines.append('    fun estimateDistance(rssi: Int, txPower: Int): Double {')
    lines.append('        if (rssi == 0) return -1.0')
    lines.append('        val ratio = rssi.toDouble() / txPower')
    lines.append('        return if (ratio < 1.0) {')
    lines.append('            Math.pow(ratio, 10.0)')
    lines.append('        } else {')
    lines.append('            0.89976 * Math.pow(ratio, 7.7095) + 0.111')
    lines.append('        }')
    lines.append('    }')
    lines.append('')
    lines.append('    private fun bytesToUuid(bytes: ByteArray): UUID {')
    lines.append('        val hex = bytes.joinToString("") { "%02x".format(it) }')
    lines.append('        val formatted = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"')
    lines.append('        return UUID.fromString(formatted)')
    lines.append('    }')
    lines.append('}')
    lines.append('')
    lines.append('data class IBeaconData(')
    lines.append('    val uuid: UUID,')
    lines.append('    val major: Int,')
    lines.append('    val minor: Int,')
    lines.append('    val txPower: Int?')
    lines.append(')')
    lines.append('')

    # Write output
    output_path = OUTPUT
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))

    total = len(services) + len(chars) + len(descs) + len(companies)
    print(f"✅ Generated {output_path}")
    print(f"   {total} total entries ({len(services)} services, {len(chars)} characteristics,")
    print(f"   {len(descs)} descriptors, {len(companies)} company IDs)")
    print(f"   File size: {os.path.getsize(output_path):,} bytes")

if __name__ == "__main__":
    main()
