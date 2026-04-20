# Network Scanner

A native Android application for scanning and analyzing **WiFi networks**, **Bluetooth devices**, and **LAN devices**. Featuring real-time monitoring, spectrum analysis, BLE GATT exploration, security auditing, and comprehensive export capabilities.

## Key Features

### WiFi Scanner
- Real-time discovery of all nearby WiFi networks.
- Detailed signal strength reporting with color-coded dBm indicators.
- Extracts SSID, BSSID, frequency, channel, band (2.4/5/6 GHz), and security standards.
- Hidden network detection.
- Persistent logging of all encounters in a local Room database.

### Bluetooth Discovery
- Combined Classic Bluetooth and BLE (Bluetooth Low Energy) scanning.
- Dynamic name resolution using `ACTION_NAME_CHANGED` broadcasts.
- Integrated MAC OUI vendor lookup (Apple, Samsung, Sony, etc.).
- Resolution of Bluetooth Device Classes (Minor/Major) to human-readable types.
- BLE Scan Record parsing for Service UUIDs and TX Power.

### Spectrum & Channel Analysis
- Channel congestion visualization using bar charts.
- 2.4 GHz and 5 GHz spectrum visualization with bell-curve overlays.
- Optimal channel recommendations for 2.4 GHz (1, 6, 11).
- Overlap detection and signal density analysis.

### LAN Discovery & Port Scanning
- Multiple discovery techniques: ARP table, Ping Sweep, NetBIOS, mDNS/Bonjour, and UPnP.
- TCP Connect Port Scanning with banner grabbing and version detection.
- Risk assessment for open ports (Critical/High/Medium/Low/Info).
- Service identification (HTTP, SSH, SMB, AirPlay, Chromecast, etc.).

### Real-time Monitoring
- Live signal strength tracking with Bézier curve visualization.
- Dual-metric latency monitoring (Gateway + Public DNS like 8.8.8.8).
- Configurable polling intervals (5/10/30/60 seconds).
- Background operation via Android Foreground Service with status notifications.

### Security Audit & Geotagging
- Automated security scoring (A-F grade) based on network config and open ports.
- Actionable findings for weak encryption (WEP/WPA1), WPS, and exposed services.
- **Wardriving Engine**: GPS-synced scanning with export to WiGLE CSV and Google Earth KML formats.
- Interactive Map view (OSM) showing the geographical distribution of scanned networks.

### Data Management & Export
- **Inventory**: Searchable database of all discovered devices with custom labels and favorites.
- **Formats**: Export data to CSV, JSON, or formatted PDF reports.
- **Sharing**: Integrated Android Share-Intent for easy data transfer to cloud or communication apps.

## Technology Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material Design 3 |
| Database | Room (SQLite) |
| Architecture | MVVM with Repository Pattern (Manual DI) |
| Concurrency | Kotlin Coroutines + Asynchronous Flow |
| Maps | OSMDroid (OpenStreetMap) |
| Networking | WifiManager, BluetoothAdapter, NsdManager, InetAddress |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

## Build & Installation

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Android SDK 35

### Steps

1. **Open Project**:
   `File → Open → ScannerApp/`

2. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Permissions

The application requires specific permissions to function correctly:
- `ACCESS_FINE_LOCATION`: Required by Android for WiFi and Bluetooth scanning.
- `NEARBY_WIFI_DEVICES`: Required on Android 13+ for scanning without location.
- `BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT`: Required on Android 12+ for discovery.
- `FOREGROUND_SERVICE`: For background monitoring.
- `POST_NOTIFICATIONS`: For monitoring alerts.

> **Note**: While location permission is required by the OS for scanning, the app only utilizes GPS data when the "Geotagging" feature is explicitly enabled by the user.

## Project Structure

- `app/src/main/java/com/scanner/app/`
  - `MainActivity.kt`: Main UI orchestration.
  - `data/`: Room entities, DAOs, and domain models.
  - `service/`: Foreground monitoring service.
  - `ui/`: Compose themes, screens, ViewModels, and reusable components.
  - `util/`: Core scanning logic, analysis engines, and export management.

## License

MIT
