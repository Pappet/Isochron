# ScannerApp - Project Overview

## 1. What it is?
Die **ScannerApp** ist eine native Android-Applikation (geschrieben in Kotlin), die fortgeschrittene Netzwerk-Analysewerkzeuge zur Verfügung stellt. Sie vereint Tools zum Scannen von WLAN (inkl. Wardriving-GPS Trackings), Bluetooth (Classic & BLE), LAN und Ports sowie spezifische Module für eine tiefgehende Kanalsuche (Channel Analysis) und Security Audits.

## 2. Project Stats
- **App Type**: Native Android Kotlin App
- **Min SDK**: 26 (Android 8.0)
- **Target / Compile SDK**: 35
- **UI Toolkit**: Jetpack Compose mit Material Design 3
- **Local Persistence**: Room Database (SQLite)
- **Concurrency**: Kotlin Coroutines & Asynchronous Flow

## 3. Architectural Decisions
Das Projekt setzt zur Trennung der Ebenen auf das **Repository-Pattern** (manuelles Dependency Injection), gekoppelt mit modernem **MVI/MVVM State-Management** über Compose:
- **UI Layer**: Vollständig deklarativ in Jetpack Compose aufgebaut. UI-Zustände (`State<T>`) werden als Parameter an pure Components durchgereicht. Für teure Objekte (wie Repositories oder Scanner) kommt lokales `remember` und `DisposableEffect` (Lifecycle-Clearing) zum Einsatz.
- **Repository Layer**: Abstrahiert die Datenzugriffe der Room-Database (`AppDatabase` und dessen DAOs) in Coroutine-gestützte `Flows`, die von der UI über `collectAsState()` direkt gebunden und visualisiert werden können.
- **Service Layer**: Eine Foreground-Service (`ScanService.kt`) übernimmt persistentes Monitoring (z.B. im Background), ungestört von UI-Lifecycle-Aktionen.
- **Util Layer / Scanner Integrations**: Kapselt Hardware-APIs (WifiManager, BluetoothAdapter) separat (z.B. `WifiScanner.kt`), um die UI von Framework-Vorgaben zu entkoppeln und Testbarkeit zu begünstigen.

## 4. Detailed Architecture
Der generelle Datenfluss einer Scanner-Funktion ist wie folgt aufgebaut:
1. Der **User** triggert in einer *ScreenComposable* einen Scan-Vorgang.
2. Ein *ScannerUtil* (z.B. `WifiScanner`) führt über System APIs die asynchronen Scans aus und gibt ein Callback/Domain Model (z.B. `WifiNetwork`) zurück.
3. Die erhaltenen Daten updaten den *UI State*, triggern gleichzeitig aber auch asynchron (über eine Coroutine / `viewModelScope.launch`) den passenden *Repository-Aufruf*.
4. Das *Repository* wandelt das Domain Model in ein *Room Entity* um und persistiert via *DAO*.
5. Die *Compose UI* (bspw. Historical Data View) ist an den `Flow<List<DiscoveredDeviceEntity>>` gebunden und aktualisiert sich reaktiv bei Datenbank-Inserts.

## 5. Source Files Description (Structure)

- `app/src/main/java/com/scanner/app/`
  - `MainActivity.kt` – Haupteinstiegspunkt. Setzt EdgeToEdge und das Basis-Compose Layout (`Scaffold`, `NavigationBar`).
  - `data/`
    - `Models.kt` – Repräsentation der Scan-Ergebnisse in Domain-Objekten (`WifiNetwork`, `BluetoothDevice`).
    - `db/` – Beinhaltet `AppDatabase`, Entities (`DiscoveredDeviceEntity`), DAOs (`DeviceDao`) und Type-Converters für Room.
    - `repository/` – Beinhaltet `DeviceRepository`, welches Room-Operationen abstrahiert.
  - `service/`
    - `ScanService.kt` – Android Foreground-Service für kontinuierliche Hintergrundscans (z.B. für Wardriving).
  - `ui/`
    - `theme/` – Konfiguration der Material 3 Typografie, Farben (Dynamic Colors) und Shapes.
    - `components/` – Wiederverwendbare, pure Jetpack Compose UI-Snippets (Cards, Charts, Dialogs).
    - `screens/` – Komplexe View-Hierarchien pro Funktionalitäts-Reiter (z.B. `WifiScreen.kt`, `SecurityAuditScreen.kt`).
  - `util/`
    - `*Scanner.kt` / `*Analyzer.kt` – Brücken-Klassen zur Android System-API, die Netzwerk/Hardware-Zugriffe wrappen.

## 6. Dependencies and their purpose
Die genauen Abhängigkeiten befinden sich in `app/build.gradle.kts`. Die Key-Dependencies sind:
- **Jetpack Compose (BOM 2024.04.01)**: Deklaratives UI-Toolkit und Navigation Compose (`navigation-compose:2.7.7`).
- **Accompanist Permissions (`v0.34.0`)**: Vereinfacht die Berechtigungsanfrage-Loops (z.B. für Location/Bluetooth) von Jetpack Compose aus.
- **Room (`v2.6.1`)**: Persistenz-Layer für sauberes, SQL-überprüftes Data-Binding und Flow-Schnittstellen.
- **Lifecycle / Activity (`v2.8.x`)**: Verbindet den Android Lifecycle organisch mit Coroutines (`lifecycle-runtime-ktx`) und State.
- **KSP** (Kotlin Symbol Processing): Beschleunigt den Buildvorgang von annotierten Klassen (wie Room DAOs, Entities) verglichen mit KAPT.

## 7. Additional References
- [ROADMAP.md](./ROADMAP.md) - Zukünftige geplante Features.
- [AGENTS.md](./AGENTS.md) - Kompletter Development Guide inkl. Konventionen.
- [README.md](./README.md) - Quickstart und Installation.
