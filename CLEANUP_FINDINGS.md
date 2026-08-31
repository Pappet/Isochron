# Cleanup Findings — Pre-Deploy Audit

Date: 2026-04-22
Branch: main
Target: `com.isochron.audit` (active package after rename)

## Summary

| Category | Count |
|---|---|
| Dead functions removed | 7 |
| Unused files deleted | 2 |
| Unused DAO queries removed | 2 |
| Unused imports cleaned | 4 |
| Dirty hacks still open | 1 |
| Compile warnings (minor unused vars) | 13 |

Build verified: `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL**.

## Removed Dead Code

### Functions

| File | Symbol | Reason |
|---|---|---|
| `util/BluetoothScanner.kt:154` | `hasBluetoothSupport()` | No callers anywhere |
| `util/PingUtil.kt:98` | `httpLatency()` | No callers; ICMP `ping()` + `isReachable()` cover use-cases |
| `util/PingUtil.kt:159` | `hasInternetConnection()` | No callers |
| `ui/components/SpectrumComponents.kt:59` | `SpectrumSectionLabel()` | Composable defined, never invoked |
| `data/repository/DeviceRepository.kt:237` | `cleanupOldReadings()` | No callers; no periodic cleanup wired |
| `data/repository/DeviceRepository.kt:242` | `observeScanSessions()` | No callers |
| `data/db/DeviceDao.kt:160` | `observeAllSessions()` | Only called by removed repo wrapper |
| `data/db/DeviceDao.kt:194` | `deleteOldReadings()` | Only called by removed `cleanupOldReadings` |

### Deleted Files

| File | Size | Reason |
|---|---|---|
| `ui/components/ChannelCharts.kt` | 408 lines | `ChannelBarChart` + `SpectrumView` never invoked by any screen |
| `ui/components/SignalChart.kt` | 392 lines | `SignalChart` + `LatencyChart` never invoked by any screen |

Total LOC dropped: **~850 lines** from dead UI + **~80 lines** from dead utilities.

### Cleaned Imports

- `util/PingUtil.kt`: removed `ConnectivityManager`, `NetworkCapabilities`, `HttpURLConnection`, `URL` (no longer referenced after method removal)

## Preserved Dead-Graph Flags (False Positives)

Graph flagged these but Android/Room calls them reflectively or via callback registration — **do not remove**:

- `AppDatabase.deviceDao` — Room generates impl
- All `Converters.*` methods — Room TypeConverter reflection
- All `DeviceDao.*` `@Query` methods still in use
- Framework callbacks: `onScanResult`, `onScanFailed`, `onReceive`, `onServiceConnected/Disconnected`, `onConnectionStateChange`, `onServicesDiscovered`, `onCharacteristicRead`, `onReadRemoteRssi`, `onLocationChanged`, `onProvider*`, `onStatusChanged`, `onDiscovery*`, `onService*`
- `@Preview` composables (`PreviewWifiNetworkCard`, `PreviewBluetoothDeviceCard`) — Android Studio tooling
- `ChannelAnalyzer.getOverlappingChannels` — covered by unit tests
- `GattDescriptorInfo.hashCode` — data class equals/hashCode contract
- `ScanService.updateInterval`, `WardrivingTracker.onLocation*` — service/listener callbacks

## Dirty Hack Still Open

### `ui/screens/MapScreen.kt:326` — User-Agent placeholder

```kotlin
"Isochron/${BuildConfig.VERSION_NAME} (Android; +https://github.com/TODO_REPLACE/Isochron)"
```

**Action required before release**. OSM/Nominatim tile servers require a real, identifying User-Agent. Using `TODO_REPLACE` risks:

- Rate limiting or IP ban from OSM usage policy
- Map tiles failing to load in production

**Fix**: replace with real repo URL or contact email, e.g.

```kotlin
"Isochron/${BuildConfig.VERSION_NAME} (Android; +https://github.com/<your-handle>/isochron)"
```

## Deferred — iBeacon Feature

**Kept** per request. Two functions in `util/BleUuidDatabase.kt`:

- `parseIBeacon(manufacturerData)` — decodes Apple iBeacon ADV frame (company ID `0x004C`, type `0x02 0x15`, UUID + major + minor + txPower)
- `estimateDistance(rssi, txPower)` — path-loss distance model (Kontakt.io formula)

### Could you use it? — Yes, cheap win

Both functions are complete and correct. Only missing: wire into scan pipeline + UI.

**Integration points (est. < 100 LOC total):**

1. **`util/BluetoothScanner.kt`** — in `onScanResult`, capture `scanRecord.manufacturerSpecificData`. Already emitted in `BluetoothDevice` model? If not, add field.
2. **`data/Models.kt`** — add optional `iBeacon: IBeaconData?` + `distanceM: Double?` to `BluetoothDevice`.
3. **`ui/components/DeviceCards.kt`** or **`ui/screens/BleDetailScreen.kt`** — render beacon row when `iBeacon != null`: show UUID, Major/Minor, txPower, estimated distance.

### Why bother

- **Differentiator**: most cheap scanner apps ignore iBeacon. Free feature for a BLE audit tool.
- **Use cases**: asset tracking, indoor-positioning debug, retail beacon presence check, compliance scanning.
- **No extra permissions**: already have `BLUETOOTH_SCAN`.
- **No dependencies**: parser is self-contained.

### Why skip

- Distance is **noisy** in practice (±3 m at 5 m range under clean LoS; much worse with walls). UI must label it "estimate" or show confidence.
- Eddystone beacons (Google) are not parsed — constant `EDDYSTONE_SERVICE_UUID` exists but no decoder. Add later if needed.

**Recommendation**: wire iBeacon in a follow-up ticket post-deploy. Low risk, high polish.

## Compile Warnings (Unused Variables)

Not blocking. Quick cleanup pass if time allows:

```
ChannelAnalysisScreen.kt:46    unused 'context'
InventoryScreen.kt:92          unused param 'onNavigateToDevice'
InventoryScreen.kt:94          unused 'context'
LanScreen.kt:111               unnecessary !! on non-null LanScanProgress
LanScreen.kt:575               Elvis always returns left operand
MapScreen.kt:246               unused 'context'
SecurityAuditScreen.kt:59      unused 'wifiNetworks'
SecurityAuditScreen.kt:60      unused 'btDevices'
ExportManager.kt:158           unused param 'includeHistory'
ExportManager.kt:218           unused 'contentWidth'
GattExplorer.kt:281            override deprecated — add @Deprecated or @Suppress
NetworkDiscovery.kt:391,395    deprecated NsdManager.resolveService + InetAddress.host
```

## Large File List (Split-Candidates, Non-Blocking)

Functional but hard to navigate. Optional refactor post-deploy:

| File | Lines |
|---|---|
| `util/NetworkDiscovery.kt` | 723 |
| `ui/screens/InventoryScreen.kt` | 690 |
| `ui/screens/BleDetailScreen.kt` | 672 |
| `ui/screens/BluetoothScreen.kt` | 646 |
| `ui/screens/MapScreen.kt` | 636 |
| `ui/screens/LanScreen.kt` | 616 |
| `util/PortScanner.kt` | 597 |

## Stale Build Artifacts

`app/build/` contains compiled classes under old package `com/scanner/app/`. Run:

```sh
./gradlew clean
```

before producing release APK/AAB to purge.

## Pre-Deploy Checklist

- [x] Dead code removed (7 functions, 2 files, 2 DAO queries)
- [x] Imports cleaned
- [x] `compileDebugKotlin` passes
- [ ] Replace `TODO_REPLACE` User-Agent in `MapScreen.kt:326`
- [X] `./gradlew clean && ./gradlew assembleRelease`
- [ ] Test map tile loading (UA dependent)
- [ ] Run unit tests: `./gradlew test`
- [ ] Optional: address compile warnings
- [ ] Optional: wire iBeacon to BLE scan pipeline

## Architecture Snapshot

- **Communities**: 44, zero cross-community coupling warnings
- **Nodes**: 1107, **Edges**: 13806
- **Languages**: Kotlin (app), Python (`generate_ble_db.py` — build-time tool, not shipped)
- **Tests**: 96 test nodes (`SignalHelperTest`, `ChannelAnalyzerTest`, `CsvEscapeTest`)
