# Netzwerk-Scanner

Eine native Android-App zum Scannen und Analysieren von **WLAN-Netzwerken**, **Bluetooth-Geräten** und **LAN-Geräten** — mit Echtzeit-Monitoring, Kanalanalyse, BLE-Service-Explorer und Export-Funktionen.

## Features

### WLAN-Scanner
- Erkennung aller WLAN-Netzwerke in Reichweite
- Anzeige des aktuell verbundenen Netzwerks
- Signalstärke mit farbcodiertem Balken (dBm)
- Details: SSID, BSSID, Frequenz, Kanal, Band (2.4/5 GHz), Sicherheitstyp
- Erkennung versteckter Netzwerke
- Automatische Persistierung in die Geräte-Datenbank

### Bluetooth-Scanner
- Classic Bluetooth Discovery + BLE (Bluetooth Low Energy) Scan
- `ACTION_NAME_CHANGED` — Gerätenamen werden nachträglich aktualisiert
- MAC-Vendor-Lookup (Apple, Samsung, Bose, Sony, etc.)
- Bluetooth Minor Class Auflösung (Smartphone, Kopfhörer, Laptop, Drucker, Pulsoximeter, ...)
- BLE Scan Record Auswertung (Service UUIDs, TX Power)
- Gekoppelte und verbundene Geräte mit Statusanzeige

### WLAN-Kanalanalyse
- Kanalauslastung als Balkendiagramm (farbcodiert nach Auslastung)
- 2.4 GHz / 5 GHz Spektrum-Visualisierung mit Bell-Kurven pro Netzwerk
- Empfehlung für optimale Kanäle (1, 6, 11 für 2.4 GHz)
- Erkennung von Kanalüberlappungen
- Anzeige des eigenen Kanals im Kontext

### LAN-Discovery
- ARP-Tabelle auslesen (`/proc/net/arp`)
- Subnet Ping-Sweep (1–254, parallel in 20er-Batches)
- mDNS/Bonjour Service Discovery (HTTP, Drucker, SSH, SMB, AirPlay, Chromecast, HomeKit, ...)
- MAC-Adresse → Hersteller-Lookup (450+ OUI-Einträge)
- Netzwerk-Info: Gateway, eigene IP, DNS, SSID
- Latenz-Messung pro Gerät

### Echtzeit-Monitoring
- Live-Signalstärke-Graph (Compose Canvas, Bézier-Kurven, Gradient-Fill)
- Gateway-Latenz und Internet-Latenz Graphen (Ping zu Gateway + 8.8.8.8)
- Konfigurierbares Scan-Intervall (5s / 10s / 30s / 60s)
- Foreground Service mit Notification
- Sitzungsstatistik (Ø Signal, Ø Latenz)

### BLE GATT Explorer
- Verbindung zu BLE-Geräten herstellen
- GATT Service-Baum (Services → Characteristics → Descriptors)
- ~80 bekannte GATT-Services und ~40 Characteristics mit deutschen Namen
- Automatisches Auslesen lesbarer Characteristic-Werte
- Farbcodierte Property-Chips (Read/Write/Notify/Indicate)
- Service-Kategorien mit Icons (Gesundheit, Fitness, Audio, Batterie, ...)
- iBeacon-Parser und Entfernungsschätzung

### Geräte-Inventar (Room DB)
- Persistente Speicherung aller entdeckten Geräte
- Automatischer Upsert bei jedem Scan
- Custom Labels und Notizen pro Gerät ("Drucker 2. OG")
- Favoriten-System
- Volltextsuche (Name, Label, MAC, Notizen)
- Filter: Alle / WLAN / Bluetooth / Favoriten / Letzte 24h
- "Zuerst gesehen" / "Zuletzt gesehen" / Anzahl Sichtungen
- Signal-History für Monitoring-Graphen

### Export
- **CSV**: Semikolon-getrennt, UTF-8 BOM für Excel-Kompatibilität
- **JSON**: Strukturiert mit Statistiken und Metadaten
- **PDF**: Formatierter A4-Bericht mit Tabelle und Zusammenfassung
- Filterbar: Gerätetyp, nur Favoriten, Zeitraum (1h/6h/24h/7d)
- Share-Intent (Mail, Cloud, WhatsApp, etc.)

### UI/UX
- Material Design 3 mit Dynamic Color (Android 12+)
- Dark/Light Theme (folgt dem System)
- 5-Tab Bottom Navigation (WLAN → Bluetooth → LAN → Monitor → Inventar)
- TopBar-Actions: Export, BLE Explorer, Kanalanalyse
- Swipeable Tabs mit `beyondBoundsPageCount` — Scan-Daten bleiben beim Tab-Wechsel erhalten
- Aufklappbare Detail-Karten mit animierten Signalbalken
- Komplett auf Deutsch

## Tech Stack

| Komponente | Technologie |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Datenbank | Room (SQLite) |
| Dependency Injection | Manual (Repository Pattern) |
| Async | Kotlin Coroutines + Flow |
| Permissions | Accompanist Permissions |
| Charts | Compose Canvas (custom) |
| Scanning | WifiManager, BluetoothAdapter, BLE LeScanner, NsdManager |
| Networking | InetAddress, /proc/net/arp, system ping |
| PDF | Android PdfDocument API |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Compose BOM | 2024.04.01 |

## Projekt bauen

### Voraussetzungen
- Android Studio Hedgehog (2023.1.1) oder neuer
- JDK 17
- Android SDK 34

### Schritte

1. **Projekt in Android Studio öffnen:**
   ```
   File → Open → ScannerApp/
   ```

2. **`local.properties` anlegen** (falls nicht vorhanden):
   ```
   sdk.dir=/home/<user>/Android/Sdk
   ```

3. **Gradle Sync + Build:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **APK installieren:**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Debugging

```bash
# Crash-Logs anzeigen
adb logcat -s AndroidRuntime:E | grep -A 20 "FATAL EXCEPTION"

# App-spezifische Logs
adb logcat | grep -E "WifiScanner|BluetoothScanner|GattExplorer|NetworkDiscovery|ScanService"
```

## Berechtigungen

| Berechtigung | Grund |
|---|---|
| `ACCESS_FINE_LOCATION` | WLAN-Scan & Bluetooth-Scan (Android-Pflicht) |
| `ACCESS_COARSE_LOCATION` | Fallback |
| `NEARBY_WIFI_DEVICES` | WLAN-Scan auf Android 13+ |
| `BLUETOOTH_SCAN` | Bluetooth-Scan auf Android 12+ |
| `BLUETOOTH_CONNECT` | BLE GATT-Verbindung, gekoppelte Geräte |
| `ACCESS_WIFI_STATE` | WLAN-Status und Scan-Results |
| `CHANGE_WIFI_STATE` | WLAN-Scan auslösen |
| `ACCESS_NETWORK_STATE` | Netzwerk-Status |
| `INTERNET` | Ping/Latenz-Messung |
| `FOREGROUND_SERVICE` | Monitoring im Hintergrund |
| `POST_NOTIFICATIONS` | Monitoring-Notification |

> Die Standort-Berechtigung ist eine Android-Systemanforderung. Die App erfasst oder speichert keine Standortdaten.

## Projektstruktur

```
app/src/main/java/com/scanner/app/
├── MainActivity.kt                    # Entry Point, Navigation (5 Tabs + 2 TopBar)
├── data/
│   ├── Models.kt                      # WifiNetwork, BluetoothDevice, Enums
│   ├── db/
│   │   ├── AppDatabase.kt             # Room Database Singleton
│   │   ├── Converters.kt              # TypeConverters (Instant, Enums)
│   │   ├── DeviceDao.kt               # Data Access Object (30+ Queries)
│   │   └── Entities.kt                # DiscoveredDevice, ScanSession, SignalReading
│   └── repository/
│       └── DeviceRepository.kt        # Abstraktion über DAO + Scan-Persistierung
├── service/
│   └── ScanService.kt                 # Foreground Service (Monitoring)
├── ui/
│   ├── theme/
│   │   └── Theme.kt                   # Material 3 (Dark/Light, Dynamic Color)
│   ├── components/
│   │   ├── DeviceCards.kt             # WiFi + Bluetooth Karten
│   │   ├── SignalChart.kt             # Live Signal + Latenz Graphen
│   │   ├── ChannelCharts.kt           # Kanal-Balkendiagramm + Spektrum
│   │   └── ExportDialog.kt            # Export-Dialog (CSV/JSON/PDF)
│   └── screens/
│       ├── WifiScreen.kt              # WLAN-Scanner
│       ├── BluetoothScreen.kt         # Bluetooth-Scanner
│       ├── LanScreen.kt               # LAN-Discovery (ARP/Ping/mDNS)
│       ├── MonitorScreen.kt           # Echtzeit-Monitoring Dashboard
│       ├── InventoryScreen.kt         # Geräte-Inventar (Room DB)
│       ├── ChannelAnalysisScreen.kt   # WLAN-Kanalanalyse
│       └── BleDetailScreen.kt         # BLE GATT Explorer
└── util/
    ├── WifiScanner.kt                 # WLAN-Scan (API 33+ kompatibel)
    ├── BluetoothScanner.kt            # BT Classic + BLE + Vendor + Minor Class
    ├── ChannelAnalyzer.kt             # Kanal-Auslastung + Empfehlungen
    ├── NetworkDiscovery.kt            # ARP + Ping-Sweep + mDNS
    ├── PingUtil.kt                    # ICMP Ping + HTTP Latenz + Netzwerk-Info
    ├── GattExplorer.kt                # BLE GATT Service/Characteristic Browser
    ├── BleUuidDatabase.kt             # 80+ GATT Services, iBeacon-Parser
    ├── MacVendorLookup.kt             # 450+ OUI → Hersteller (Apple, Samsung, ...)
    ├── SignalHelper.kt                # Signal-Farben und -Qualität
    └── ExportManager.kt              # CSV/JSON/PDF-Export + Share-Intent

30 Kotlin-Dateien, ~8.700 Zeilen
```

## Lizenz

MIT

---

## Phase 7 — Security Audit (neu)

### Port-Scanner
- TCP Connect Scan (Quick 20 / Top 50 Ports)
- Banner-Grabbing mit Versions-Erkennung
- Risiko-Bewertung pro Port (Kritisch/Hoch/Mittel/Niedrig/Info)
- Spezifische Empfehlungen pro Dienst (Telnet→SSH, Redis Auth, MongoDB Auth, ...)

### Security Auditor
- Automatischer Netzwerk-Audit: WiFi + Bluetooth + Ports in einem Durchlauf
- Scoring-System (0-100, Note A-F)
- Checks: Offene WLANs, WEP, WPA1, WPS, versteckte SSIDs
- Checks: Unbekannte BLE-Geräte mit Services, sichtbare BT-Geräte
- Checks: Kritische offene Ports (Telnet, Redis, MongoDB), exponierte Datenbanken, Banner Disclosure
- Aufklappbare Finding-Karten mit Beschreibung + Handlungsempfehlung

### Wardriving (GPS)
- GPS-Tracking mit Location-Updates alle 2 Sekunden
- Automatischer WiFi-Scan alle 5 Sekunden
- WiGLE-kompatibles CSV-Export
- KML-Export für Google Earth (farbcodiert nach Verschlüsselungstyp)
- Share via Android-Intent

### Neue Dateien
- `util/PortScanner.kt` — TCP Scanner mit Banner-Grabbing, 50+ Port-Definitionen, Risk Levels
- `util/SecurityAuditor.kt` — Audit-Engine mit WiFi/BT/Port-Checks, Score-Berechnung, Findings
- `util/WardrivingTracker.kt` — GPS-Logger mit WiGLE-CSV und KML-Export
- `ui/screens/SecurityAuditScreen.kt` — Audit-Dashboard, Port-Scanner-Dialog, Wardriving-Controls

**34 Kotlin-Dateien, ~10.400 Zeilen**
