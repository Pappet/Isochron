# Netzwerk-Scanner — Entwicklungs-Roadmap

## Status: Alle 6 Phasen implementiert ✅

```
Phase 1: Geräte-Inventar (Room DB)         ✅ Abgeschlossen
Phase 2: Monitoring & Live-Graphen          ✅ Abgeschlossen
Phase 3: WLAN-Kanalanalyse                  ✅ Abgeschlossen
Phase 4: LAN-Discovery (ARP/mDNS)          ✅ Abgeschlossen
Phase 5: BLE Service Discovery              ✅ Abgeschlossen
Phase 6: Export (CSV/JSON/PDF)              ✅ Abgeschlossen
```

---

## Phase 1 — Geräte-Inventar (Room DB) ✅

Room-Datenbank mit drei Tabellen: `discovered_devices`, `scan_sessions`, `signal_readings`. Automatischer Upsert bei jedem WLAN- und Bluetooth-Scan. Custom Labels, Notizen, Favoriten, Volltextsuche, Filter nach Kategorie und Zeitraum.

**Dateien:** `AppDatabase.kt`, `DeviceDao.kt`, `Entities.kt`, `Converters.kt`, `DeviceRepository.kt`, `InventoryScreen.kt`

---

## Phase 2 — Monitoring & Live-Graphen ✅

Compose-Canvas-basierte Live-Graphen für WLAN-Signalstärke, Gateway-Latenz und Internet-Latenz. Foreground Service mit konfigurierbarem Intervall (5s/10s/30s/60s). Sitzungsstatistik mit Durchschnittswerten.

**Dateien:** `SignalChart.kt`, `MonitorScreen.kt`, `ScanService.kt`, `PingUtil.kt`

---

## Phase 3 — WLAN-Kanalanalyse ✅

Kanalauslastungs-Balkendiagramm (farbcodiert: grün→rot), Frequenzspektrum mit Bell-Kurven pro Netzwerk, Kanalempfehlungen mit Score. Getrennte Ansicht 2.4 GHz / 5 GHz, Berücksichtigung der 2.4 GHz Kanalüberlappung (±2 Kanäle).

**Dateien:** `ChannelAnalyzer.kt`, `ChannelCharts.kt`, `ChannelAnalysisScreen.kt`

---

## Phase 4 — LAN-Discovery (ARP/mDNS) ✅

Drei-Phasen-Scan: ARP-Tabelle → Ping-Sweep (parallel, 20er-Batches) → mDNS Service Discovery (12 Diensttypen). MAC-Vendor-Datenbank mit 450+ OUI-Einträgen. Kontextabhängige Icons (Router, Drucker, NAS, Chromecast, ESP32).

**Dateien:** `NetworkDiscovery.kt`, `MacVendorLookup.kt`, `LanScreen.kt`

---

## Phase 5 — BLE Service Discovery ✅

GATT-Explorer mit Verbindung, Service-Baum, Characteristic-Auslesen, 80+ bekannte GATT-Services, 40+ Characteristics mit deutschen Namen. Service-Kategorien (Gesundheit, Fitness, Audio, ...). iBeacon-Parser mit Entfernungsschätzung.

Bluetooth-Scanner erweitert: `ACTION_NAME_CHANGED` für nachträgliche Namensauflösung, MAC-Vendor-Lookup als Fallback-Name, Minor-Class-Auflösung (50+ Gerätearten), BLE Scan Record Auswertung.

**Dateien:** `GattExplorer.kt`, `BleUuidDatabase.kt`, `BleDetailScreen.kt`, `BluetoothScanner.kt` (erweitert)

---

## Phase 6 — Export (CSV/JSON/PDF) ✅

Drei Export-Formate mit Filter-Dialog (Gerätetyp, Favoriten, Zeitraum). CSV mit Semikolon + UTF-8 BOM. JSON mit Statistik-Header. PDF als A4-Bericht mit Tabelle und automatischem Seitenumbruch. Share via Android-Intent.

**Dateien:** `ExportManager.kt`, `ExportDialog.kt`, `file_paths.xml`

---

## Architektur (Endausbau)

```
Navigation:
├── Bottom Bar (5 Tabs)
│   ├── WLAN (Scanner + Persist)
│   ├── Bluetooth (Classic + BLE + Vendor)
│   ├── LAN (ARP + Ping + mDNS)
│   ├── Monitor (Live-Graphen + Service)
│   └── Inventar (Room DB + Suche + Filter)
└── Top Bar (3 Actions)
    ├── ⬇ Export (CSV/JSON/PDF Dialog)
    ├── 📡 BLE Explorer (GATT-Baum)
    └── 📊 Kanalanalyse (Spektrum)
```

---

## Bugfixes & Stabilisierung

Die folgenden Probleme wurden während der Entwicklung identifiziert und behoben:

- **Compose BOM Versionskonflikt**: BOM `2024.01.00` → `2024.04.01` — Accompanist 0.34 braucht Compose 1.6.x
- **`RECEIVER_NOT_EXPORTED`**: Ab API 33 Pflicht für `registerReceiver()` — WiFi- und BT-Scanner gefixt
- **`drawText` Crash**: Negative Canvas-Constraints bei kleinen Bildschirmen → `safeDrawText()` Wrapper
- **Tab-Wechsel löscht Daten**: `beyondBoundsPageCount = 6` hält alle Seiten im Speicher
- **BT `SecurityException`**: Jeder `device.name`/`bondState`-Zugriff einzeln in try-catch
- **`HorizontalDivider` → `Divider`**: API existierte noch nicht in Compose BOM 2024.01
- **`FilterChip` ExperimentalMaterial3Api**: Opt-in fehlte auf mehreren Screens
- **ScanService WiFi-Callback**: BroadcastReceiver aus Coroutine-Thread entfernt
- **System Service unsafe casts**: Alle `as` → `as?` mit null-Fallback

---

## Mögliche Erweiterungen

- **Home-Screen-Widget** mit aktuellem WLAN-Status und Signalstärke
- **Quick Settings Tile** für Sofort-Scan
- **Automatischer täglicher Export** (WorkManager)
- **Port-Scan** auf häufige Ports (optional, konfigurierbar)
- **Netzwerk-Topologie** als visuelle Darstellung
- **Historische Graphen** aus der Room DB (nicht nur Live)
- **Push-Benachrichtigung** bei neuem unbekanntem Gerät im Netzwerk
- **OUI-Datenbank-Update** via Download der IEEE-Liste
- **Lokalisierung** (Englisch, weitere Sprachen)
- **Onboarding-Screen** mit Berechtigungserklärung
