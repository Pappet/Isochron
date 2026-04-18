# ScannerApp - Project Overview

## 1. What is it?

**ScannerApp** is a native Android application written in Kotlin that provides advanced network analysis tools. It combines modules for WiFi scanning (including Wardriving GPS tracking), Bluetooth (Classic & BLE) discovery, LAN device identification, and port scanning. It also features specific components for deep spectrum analysis (Channel Analysis) and comprehensive Security Audits.

## 2. Project Stats

- **App Type**: Native Android Kotlin App
- **Min SDK**: 26 (Android 8.0)
- **Target / Compile SDK**: 35 (Android 15)
- **UI Toolkit**: Jetpack Compose with Material Design 3
- **Local Persistence**: Room Database (SQLite) with StateFlow observation
- **Concurrency**: Kotlin Coroutines & Asynchronous Flow
- **Documentation**: Standardized KDoc in English across all layers

## 3. Architectural Decisions

The project follows the **Repository Pattern** with manual Dependency Injection, coupled with modern **state management** via Compose:

- **UI Layer**: Fully declarative using Jetpack Compose. UI state is hoisted and managed using `mutableStateOf` and `StateFlow`. Performance-critical views (like `MapScreen`) implement incremental marker diffing to minimize UI rebuilds.
- **Repository Layer**: Abstracts data access for the Room database. It exposes Coroutine-powered `Flows` which are collected in the UI using `collectAsState()`, providing a reactive data pipeline.
- **Service Layer**: A foreground service (`ScanService.kt`) handles persistent monitoring tasks independently of the UI lifecycle.
- **Util Layer**: Encapsulates hardware and network APIs (WifiManager, BluetoothAdapter, InetAddress) in dedicated classes (e.g., `WifiScanner.kt`). This decouples the UI from Android framework specifics and enhances testability.

## 4. Detailed Architecture

The data flow for a typical scanning feature follows this pattern:

1. The **User** triggers a scan in a Screen Composable.
2. A **Scanner Utility** executes the asynchronous scan using system APIs and returns domain models (e.g., `WifiNetwork`).
3. The results update the **UI State** for immediate feedback and are simultaneously passed to the **Repository** via a Coroutine.
4. The **Repository** converts the domain models into Room entities and persists them via **DAOs**.
5. The **Compose UI** (e.g., Inventory or History views) observes the reactive `Flow` from the database and updates automatically upon persistence.

## 5. Source Files Description

### Core Application
- `MainActivity.kt`: Entry point, orchestrates the main navigation (BottomNav + TopBar actions).

### Data Layer (`data/`)
- `Models.kt`: Domain representations of scan results.
- `db/`: Room database implementation, including entities, DAOs, and type converters.
- `repository/`: Single source of truth for the application data.

### Service Layer (`service/`)
- `ScanService.kt`: Foreground service for continuous background monitoring and notification management.

### UI Layer (`ui/`)
- `theme/`: Material 3 theme configuration including dynamic color support.
- `components/`: Reusable, atomic UI components (Cards, Charts, Dialogs).
- `screens/`: Complex feature-specific view hierarchies.

### Utility Layer (`util/`)
- `*Scanner.kt` / `*Analyzer.kt`: Wrappers for Android system APIs and complex logic engines.

## 6. Dependencies and their Purpose

- **Jetpack Compose**: Modern declarative UI toolkit.
- **Room**: Type-safe persistence layer with reactive streams support.
- **Accompanist Permissions**: Streamlined runtime permission handling.
- **OSMDroid**: Open-source map engine for WiFi geotag visualization.
- **KSP**: Kotlin Symbol Processing for optimized build times.
- **R8 / ProGuard**: Optimized for release builds with specific rules for Room and mapping libraries.

## 7. Additional References

- [README.md](./README.md): Quickstart and installation guide.
- [AGENTS.md](./AGENTS.md): Development guide and project conventions for AI assistants.
- [ROADMAP.md](./ROADMAP.md): Future feature plans.
