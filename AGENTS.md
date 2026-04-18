# AGENTS.md - ScannerApp Development Guide

## Project Overview

- **Type**: Native Android Application (Kotlin)
- **UI Framework**: Jetpack Compose + Material Design 3
- **Database**: Room (SQLite)
- **Architecture**: Manual DI via Repository Pattern
- **Async**: Kotlin Coroutines + Flow
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 35 (Android 15)
- **Compose BOM**: 2024.04.01

---

## Build Commands

### Gradle Wrapper

```bash
./gradlew <task>
```

### Build Tasks

| Command | Description |
| --------- | ------------- |
| `./gradlew assembleDebug` | Build debug APK |
| `./gradlew assembleRelease` | Build release APK |
| `./gradlew build` | Full build (debug + release) |
| `./gradlew clean` | Clean build artifacts |

### Single File/Class Compilation (Fast)

```bash
./gradlew compileDebugKotlin
```

### APK Location

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

### Debugging

```bash
# Crash logs
adb logcat -s AndroidRuntime:E | grep -A 20 "FATAL EXCEPTION"

# App-specific logs
adb logcat | grep -E "WifiScanner|BluetoothScanner|GattExplorer|NetworkDiscovery|ScanService"
```

---

## Testing

**Unit tests** for utility classes exist in `app/src/test/java/`.
Currently, there are no instrumented tests (`androidTest`).

If adding tests:

- Unit tests: `app/src/test/java/`
- Instrumented tests: `app/src/androidTest/java/`
- Run single test: `./gradlew testDebugUnitTest --tests "com.scanner.app.MyTestClass"`
- Run instrumented test on device: `./gradlew connectedDebugAndroidTest`

---

## Code Style Guidelines

### Kotlin Version

Kotlin 1.9.22 with JVM target 17

### Import Organization

Standard Kotlin import order:

1. Android framework (`android.*`)
2. Jetpack Compose (`androidx.compose.*`)
3. Third-party libraries (`com.google.*`, `org.jetbrains.*`)
4. Internal app imports (`com.scanner.app.*`)
5. Standard library (`kotlin.*`, `java.*`)

### Naming Conventions

| Element | Convention | Example |
| :--- | :--- | :--- |
| Classes/Objects | PascalCase | `DeviceRepository`, `WifiNetwork` |
| Functions | camelCase | `startScan()`, `persistWifiScan()` |
| Properties/Variables | camelCase | `signalStrength`, `isConnected` |
| Constants (companion) | UPPER_SNAKE | `TAG`, `SCAN_TIMEOUT_MS` |
| Enums | PascalCase | `DeviceType.CLASSIC` |
| Package names | lowercase | `com.scanner.app.data.db` |
| Compose UI state | `mutableStateOf<T>()` | `var networks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }` |

### Data Classes

```kotlin
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,       // dBm — document units in comments
    val frequency: Int,            // MHz
    val channel: Int,
    val securityType: String,
    val isConnected: Boolean = false,
    val band: String,
    val wpsEnabled: Boolean = false,
    val rawCapabilities: String = ""
)
```

### Room Entities

```kotlin
@Entity(
    tableName = "discovered_devices",
    indices = [
        Index(value = ["address"], unique = true),
        Index(value = ["device_category"]),
        Index值 = ["is_favorite"]),
        Index(value = ["last_seen"])
    ]
)
data class DiscoveredDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "address")
    val address: String,
    // ...
)
```

### Enums with Display Methods

```kotlin
enum class DeviceType {
    CLASSIC,
    BLE,
    DUAL,
    UNKNOWN;

    fun displayName(): String = when (this) {
        CLASSIC -> "Classic"
        BLE -> "BLE"
        DUAL -> "Dual"
        UNKNOWN -> "Unbekannt"
    }
}
```

### Coroutine Usage

- Use `viewModelScope.launch` in ViewModels
- Use `rememberCoroutineScope()` in Composables
- Prefer `suspend` functions for repository/dao operations
- Expose Flow from repositories, collect in Composables via `collectAsState()`

### Error Handling Pattern

```kotlin
// Prefer nullable returns over exceptions for recoverable errors
fun isWifiEnabled(): Boolean {
    return try {
        wifiManager?.isWifiEnabled == true
    } catch (e: Exception) {
        false
    }
}

// Use try-catch for operations that may legitimately fail
fun getConnectedSsid(): String? {
    return try {
        // operation
    } catch (e: Exception) {
        Log.e(TAG, "Error getting connected SSID", e)
        null
    }
}

// Suppress specific deprecations when necessary
@Suppress("DEPRECATION")
val wifiInfo = wifiManager?.connectionInfo
```

### Logging

```kotlin
companion object {
    private const val TAG = "WifiScanner"
}

Log.e(TAG, "Failed to get WifiManager", e)  // Error
Log.w(TAG, "Scan timed out, returning cached results")  // Warning
Log.i(TAG, "Scan completed")  // Info (avoid in production)
```

### Compose Guidelines

- Use `remember { }` for expensive objects (scanners, repositories)
- Use `rememberCoroutineScope()` for coroutine launches in Composables
- Use `DisposableEffect` for cleanup (unregister receivers, stop scans)
- Use `mutableStateOf<T>()` for UI state, `StateFlow` for shared state
- Group imports with wildcard where appropriate (e.g., `androidx.compose.material.icons.Icons`)

### Annotations

- `@SuppressLint("MissingPermission")` — always on scanner classes
- `@Suppress("DEPRECATION")` — for deprecated API usage (e.g., WifiManager.connectionInfo)
- `@OptIn(ExperimentalMaterial3Api::class)` — for experimental APIs
- `@Composable` — always on Composable functions
- `@Entity`, `@Dao`, `@ColumnInfo` — Room annotations

### Documentation

- KDoc for public classes/functions explaining purpose
- Inline comments for non-obvious logic or magic values
- Document units in comments (e.g., `// dBm`, `// MHz`)

---

## Project Structure

```text
app/src/main/java/com/scanner/app/
├── MainActivity.kt              # Entry point, navigation
├── data/
│   ├── Models.kt                # WifiNetwork, BluetoothDevice, enums
│   ├── db/
│   │   ├── AppDatabase.kt       # Room singleton
│   │   ├── Converters.kt        # TypeConverters
│   │   ├── DeviceDao.kt         # 30+ queries
│   │   └── Entities.kt          # Room entities
│   └── repository/
│       └── DeviceRepository.kt  # DAO abstraction
├── service/
│   └── ScanService.kt           # Foreground service (monitoring)
├── ui/
│   ├── theme/
│   │   └── Theme.kt            # Material 3 (Dark/Light, Dynamic)
│   ├── components/
│   │   ├── DeviceCards.kt
│   │   ├── SignalChart.kt
│   │   ├── ChannelCharts.kt
│   │   └── ExportDialog.kt
│   └── screens/
│       ├── WifiScreen.kt
│       ├── BluetoothScreen.kt
│       ├── LanScreen.kt
│       ├── MonitorScreen.kt
│       ├── InventoryScreen.kt
│       ├── ChannelAnalysisScreen.kt
│       ├── BleDetailScreen.kt
│       ├── SecurityAuditScreen.kt
│       └── MapScreen.kt
└── util/
    ├── WifiScanner.kt
    ├── BluetoothScanner.kt
    ├── ChannelAnalyzer.kt
    ├── NetworkDiscovery.kt
    ├── PingUtil.kt
    ├── GattExplorer.kt
    ├── BleUuidDatabase.kt
    ├── MacVendorLookup.kt
    ├── SignalHelper.kt
    ├── ExportManager.kt
    ├── PortScanner.kt
    ├── SecurityAuditor.kt
    ├── WardrivingTracker.kt
    └── CsvEscape.kt
```

---

## Dependencies (Key Versions)

| Library | Version |
| :--- | :--- |
| Kotlin | 1.9.22 |
| Compose BOM | 2024.04.01 |
| Room | 2.6.1 |
| Navigation Compose | 2.7.7 |
| Accompanist Permissions | 0.34.0 |
| KSP (Room) | 1.9.22-1.0.17 |
| Lifecycle ViewModel Compose | 2.8.7 |

---

## Lint & Code Quality

**No linter is configured** for this project. If adding one:

- Consider **ktlint** or **detekt** for Kotlin
- Android Lint is built-in: `./gradlew lint`

---

## Common Patterns

### Repository Pattern

```kotlin
class DeviceRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).deviceDao()

    fun observeAllDevices(): Flow<List<DiscoveredDeviceEntity>> = dao.observeAllDevices()
    suspend fun persistWifiScan(networks: List<WifiNetwork>, durationMs: Long? = null) { ... }
}
```

### Flow Collection in Compose

```kotlin
val devices by repository.observeAllDevices().collectAsState()
```

### Upsert Pattern (Room)

```kotlin
@Transaction
suspend fun upsertDevice(...): Long {
    val existing = getDeviceByAddress(address)
    return if (existing != null) {
        updateDevice(existing.copy(lastSeen = now, ...))
        existing.id
    } else {
        insertDevice(...)
    }
}
```
