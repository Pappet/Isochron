# Usability-Audit — Isochron

**Datum:** 2026-09-21
**Branch:** `claude/keen-goodall-po6eno`
**Umfang:** Alle 11 Screens, 3 Komponentendateien, 9 ViewModels, Service- und Util-Schicht (14.102 Zeilen Kotlin)

## Methodik & Grenzen

Statischer Review des gesamten UI-Codes plus quantitative Prüfungen:
Kontrastberechnung (WCAG 2.1) für die komplette Spectrum-Palette, Abmessung aller
Touch-Targets aus Padding/Fontgrößen, Abgleich der `strings.xml`-Schlüssel beider
Sprachen, Grep-Bilanz über A11y-Semantik und Fehlerbehandlung.

**Nicht geprüft:** Kein Android SDK in dieser Umgebung → kein Build, kein Lint-Lauf,
kein Emulator/Gerätetest. Alle Befunde stammen aus dem Quelltext. Die als
*„Gerätetest empfohlen"* markierten Punkte sollten vor der Behebung am Gerät
gegengeprüft werden.

## Umsetzungsstand

**Stufe 1 (Korrektheit) ist umgesetzt** — Commit „fix: address correctness and
dead-end findings from the usability audit". Behoben: **A1, A2, A3, B1, B2, B3,
B4, G1**. Die betroffenen Befunde unten tragen die Markierung ✅.

Zwei Einschränkungen dazu:

- **A3 nur dort, wo ein Banner existiert** (WLAN- und Bluetooth-Tab).
  Kanalanalyse und Security-Audit haben gar keinen Berechtigungsbanner; ihr
  Scan-Button bleibt bei fehlender Berechtigung wirkungslos. Das ist **C2/C3**
  und gehört zu Stufe 2.
- **Kein Build in dieser Umgebung.** Die Änderungen sind gegengelesen, aber
  nicht kompiliert (kein Android SDK). `./gradlew compileDebugKotlin` und
  `./gradlew test` müssen lokal laufen, bevor das gemerged wird.

Drei sichtbare Nebenwirkungen, die aus den Fixes folgen und beim Testen
erwartet werden sollten:

- Der Security-Audit zeigt jetzt die INFO-Meldung **„WPA3 nicht aktiv"**. Ihre
  Bedingung verglich auf `securityType == "WPA2"`, während der Scanner
  `"WPA2 (CCMP)"` lieferte — die Prüfung war toter Code und feuert nun erstmals.
- Der Audit meldet **deutlich weniger falsche Kritisch-Befunde**, weil WPA3-Netze
  nicht mehr als offen durchgehen.
- Der **KML-Wardriving-Export färbt Marker anders**: WPA3-Netze bekamen bisher
  die rote „open"-Farbe, jetzt die grüne „wpa3"-Farbe.

**Stufe 2 (Verlässlichkeit) ist umgesetzt** — Commit „feat: reliability pass from
the usability audit (Stufe 2)". Behoben: **A3 (Rest), A4, A5, C1, C2, C3, C6, D6,
H3**. Auf einem Nothing A142P (Android 16) verifiziert.

- **C1** über einen prozessweiten `UiMessageBus` statt eines Flows je ViewModel:
  die App hat genau ein Scaffold, dort hängt die Snackbar.
- **H3** nur User-Agent und `CopyrightOverlay`; Zoom-Buttons, Maßstab und
  „auf meine Position" bleiben offen.
- Beim Test aufgefallen und mitbehoben: das Onboarding fragte
  `NEARBY_WIFI_DEVICES` nicht ab, und `allowBackup="true"` spielte
  `onboarding_complete` aus dem Cloud-Backup zurück, sodass Neuinstallationen
  das Onboarding übersprangen (Commit 2ea0772).

**Stufe 3 (Zugänglichkeit) ist umgesetzt** — Commit „feat: accessibility pass from
the usability audit (Stufe 3)". Behoben: **D1, D2, D3, D4**.

- **D2** über `Modifier.minimumInteractiveComponentSize()` als äußersten Modifier:
  Layout reserviert 48 dp, die sichtbare Größe bleibt. Offen bleibt die
  Bottom-Nav-**Breite** (45 dp bei 8 Tabs) — das löst erst F1.
- **D1** Rollen: Bottom-Nav `selectable(role = Tab)`, Filter-Chips
  `selectable(role = Checkbox)` mit Zustand, alle übrigen Klickflächen
  `Role.Button`; Export-Optionen `toggleable`/`RadioButton`. Text-Alternativen für
  Audit-Note, Kanaldiagramm, Radar, Sparklines und alle Gerätetyp-Icons.
- **D3** `OnSurfaceFaint` auf `#718480` (5,05:1). `GridLine` unverändert — das
  Design-System will ohnehin keine Trennlinien zwischen Listenzeilen (Stufe 4).
- **D4** alle 8–10 sp auf 11 sp (80 Stellen); Severity-Badge auf 72 dp verbreitert,
  damit „KRITISCH" einzeilig bleibt.

**Stufe 4 (Struktur) ist umgesetzt** — Commit „feat: structure pass from the
usability audit (Stufe 4)". Behoben: **B5, B6, B7, E1, E2 (Kern), E3, F1, F4, G2,
H1, H5**; damit ist auch die Bottom-Nav-Breite aus **D2** erledigt.

- **F1** Vier Ziele *Scan · Analyse · Audit · Inventar* mit Untertab-Leiste
  (`SpectrumSubTabs`), Labels aus `strings.xml`. Die Leiste trägt rechts das
  Zahnrad für **F4**.
- **F4/G2** `SettingsScreen` als Overlay im Scaffold: Berechtigungen (App-Info),
  Onboarding erneut, Standard-Intervall für den Monitor, „Alle Daten löschen"
  mit Bestätigung (`DeviceRepository.deleteAllData()` → `clearAllTables`),
  Version + Attribution.
- **E2** `UiText` (Resource-ID + Argumente) für alle Befunde des
  `SecurityAuditor`; `ChannelReason`-Enum statt Text im `ChannelAnalyzer`;
  Severity/Category/PortRisk/DeviceType/BondState mit `labelRes`;
  Notification-Texte aus Ressourcen. **Noch offen:** `GattExplorer`-Statusmeldungen,
  `BleUuidDatabase`, `PortState`/`ServiceType`-Labels, `DeviceCategory.displayName()`.
- **E3** `WifiNetwork.isHidden` (SSID bleibt roh, leer = versteckt) und
  `BluetoothDevice.isUnnamed` mit dokumentierter Konstante `UNKNOWN_NAME`;
  Logik vergleicht nie mehr Anzeigetext. Nebenbefund: `WifiScreen` prüfte auf
  `"(hidden)"`, der Scanner lieferte `"(Verstecktes Netzwerk)"` — dieselbe
  Fehlerklasse wie B1.
- **B5** Distanzdiagramm statt Radar: kein Sweep, kein Fadenkreuz, Ringe mit
  dBm beschriftet, Winkel aus der MAC-Adresse (stabil über Rescans), Hinweis
  „ABSTAND = SIGNALSTÄRKE · RICHTUNG UNBEKANNT".
- **B6** `SignalTrace` (Sinus-Dekoration) durch `SignalLevelBar` ersetzt.
- **B7** Sparklines mit fester Y-Achse (Signal −95…−30 dBm, Latenz 0…nächste
  Stufe aus 50/100/250/500/1000/2500 ms), Min/Max-Label, Zeitspanne.
- **H1** Balken mindestens 28 dp, Diagramm scrollt horizontal.
- Design-System-Regel „kein Italic" nebenbei durchgesetzt (4 Stellen).

Alle übrigen Befunde sind unverändert offen.

## Bewertungsskala

| Stufe | Bedeutung |
|---|---|
| **P0** | Nutzer wird blockiert oder bekommt sachlich falsche Informationen |
| **P1** | Funktion ist nutzbar, aber unzuverlässig, unverständlich oder nicht auffindbar |
| **P2** | Reibung, Inkonsistenz, Politur |

## Befundbilanz

| Kategorie | P0 | P1 | P2 | Summe |
|---|---|---|---|---|
| A — Navigation & Steuerung | 3 | 3 | 1 | 7 |
| B — Falsche Anzeigen | 5 | 2 | 0 | 7 |
| C — Feedback & Fehlerzustände | 2 | 4 | 1 | 7 |
| D — Barrierefreiheit | 2 | 4 | 2 | 8 |
| E — Sprache & Konsistenz | 1 | 3 | 1 | 5 |
| F — Informationsarchitektur | 0 | 3 | 2 | 5 |
| G — Datenhoheit | 1 | 1 | 0 | 2 |
| H — Layout & Robustheit | 1 | 3 | 2 | 6 |
| **Summe** | **15** | **23** | **9** | **47** |

---

## A — Navigation & Steuerung

### A1 (P0) ✅ — Die Zurück-Taste existiert in der ganzen App nicht

`grep -c BackHandler app/src/main` → **0**.

Betroffen sind alle Overlay-Zustände, die über ein State-Flag statt über Navigation
gelöst sind:

| Overlay | Geöffnet durch | Schließt nur über |
|---|---|---|
| WLAN-Detail (`WifiScreen.kt:117`) | Tap auf Netzwerk | X-Button oben links |
| GATT-Explorer (`BluetoothScreen.kt:120`) | „EXPLORE GATT" | Disconnect-Button |
| Export-Sheet (`InventoryScreen.kt:210`) | Export-Pille | X oder Tap auf Scrim |
| BT-Detailpanel, Map-Detailpanel | Tap auf Gerät/Marker | X-Button |

Ein Druck auf die System-Zurück-Taste im WLAN-Detail **beendet die App**, statt
zur Liste zurückzukehren. Das ist die am tiefsten verankerte Erwartung unter
Android und der schwerwiegendste Einzelbefund.

**Empfehlung:** `BackHandler(enabled = vm.selectedNetwork != null) { vm.selectedNetwork = null }`
in jedem Overlay. Aufwand: ~10 Zeilen für alle fünf Stellen.

### A2 (P0) ✅ — Onboarding springt über das Berechtigungsergebnis hinweg

`OnboardingScreen.kt:225-236`:

```kotlin
.clickable {
    if (step == 1) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }
    if (step < steps.size - 1) { vm.step += 1 } else { onDone() }
}
```

Der Schritt wird **im selben Klick** weitergeschaltet, der den Systemdialog
auslöst. Folgen:

1. Der Android-Berechtigungsdialog erscheint über Schritt 3 („Bereit") — der
   Nutzer sieht „Bereit", während er noch gefragt wird.
2. Das Ergebnis wird nie ausgewertet. Wer **ablehnt**, landet trotzdem auf
   „Bereit" und danach in einer App, in der jeder Scan leer bleibt.
3. Es gibt keinen zweiten Anlauf: Onboarding ist per `SharedPreferences`
   einmalig (`MainActivity.kt:80`), und einen Einstellungs-Screen gibt es nicht.

**Empfehlung:** Weiterschalten erst in einem `LaunchedEffect` auf
`permissionState.allPermissionsGranted`; bei Ablehnung einen erklärenden
Zwischenschritt mit „Trotzdem fortfahren" / „Erneut fragen" anbieten.

### A3 (P0) ✅ — Sackgasse bei dauerhaft abgelehnter Berechtigung

`WifiScreen.kt:147-155`, `BluetoothScreen.kt:161-168`, analog in Channel-Analysis
und Security-Audit: Der Banner-Button ruft immer
`permissionState.launchMultiplePermissionRequest()` auf.

Nach zweimaliger Ablehnung liefert Android diesen Aufruf **wirkungslos** zurück
(„don't ask again"). Der Button bleibt sichtbar und aktiv, tut aber nichts. Für
den Nutzer ist die App ab diesem Punkt unreparierbar kaputt — der Weg über
Einstellungen → Apps → Berechtigungen wird nirgends angeboten.

**Empfehlung:** `shouldShowRationale` auswerten und bei permanenter Ablehnung auf
`Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)` umschalten, Button-Text
„Einstellungen öffnen".

### A4 (P1) ✅ — Kein Abbruch für laufende Scans

`LanViewModel.kt:44-68` und `startPortScan:70-88`: `discovery.stopScan()` wird
ausschließlich in `onCleared()` gerufen. Während eines Scans ist der Scan-Button
inaktiv (`if (isScanning) return`), ohne eine Abbruch-Alternative.

Kritisch beim Port-Chip „ALLE PORTS" (`LanScreen.kt:427`, `WellKnownPorts.ALL_PORTS`):
65.535 TCP-Verbindungsversuche, ausgelöst durch einen einzigen Tap, ohne
Rückfrage, ohne Zeitschätzung, ohne Stopp. Der Chip ist rot eingefärbt — das ist
die einzige Warnung.

**Empfehlung:** Scan-Button während des Laufs zu „STOPP" umschalten; für „ALLE
PORTS" einen Bestätigungsdialog mit Dauerhinweis.

### A5 (P1) ✅ — Zweiter Port-Scan überschreibt den ersten

`LanScreen.kt` übergibt `enabled = !isPortScanning`, wobei
`isPortScanning = portScanningIp == device.ip` (Zeile 128). Die Chips **anderer**
Geräte bleiben also aktiv. Ein Tap dort startet eine zweite Coroutine und setzt
`portScanningIp` um (`LanViewModel.kt:71`) — die Fortschrittsanzeige springt auf
das neue Gerät, der erste Scan läuft unsichtbar weiter und schreibt sein Ergebnis
später kommentarlos in die Map.

### A6 (P1) — Das Export-Sheet ist kein Modal

`ExportDialog.kt:64-77` baut das Bottom-Sheet als gewöhnliche `Box` im
Seiteninhalt, nicht als `Dialog`/`ModalBottomSheet`. Konsequenzen:

- Zurück-Taste schließt es nicht (siehe A1).
- Der `HorizontalPager` verarbeitet weiterhin Wischgesten: Ein horizontaler Wisch
  über das „Modal" wechselt den Tab und lässt das Sheet stehen.
- Es liegt innerhalb der Scaffold-Contentfläche, endet also über der Bottom-Nav,
  statt sie zu überdecken.

### A7 (P2) — Kontextmenü nur per Long-Press auffindbar

`InventoryScreen.kt:327` (`combinedClickable(onLongClick = { showMenu = true })`).
Bearbeiten, Favorit und Löschen hängen an einer Geste ohne sichtbare Affordanz;
das `DropdownMenu` ist an eine leere `Box` (Zeile 409) ohne Anker-Icon gebunden.

---

## B — Falsche oder irreführende Anzeigen

### B1 (P0) ✅ — Offene WLANs werden im WLAN-Tab nie als Risiko markiert

Zwei Vokabulare treffen aufeinander:

- `WifiScanner.kt:297` liefert für unverschlüsselte Netze den deutschen String **`"Offen"`**.
- `WifiScreen.kt:68` und `WifiDetailScreen.kt:330` prüfen auf das englische
  **`"Open"`**: `securityType.equals("Open", ignoreCase = true)`.

Ergebnis: Der Filter „⚠ RISK", der Zähler „risks" im Header und die rote
Risiko-Markierung in der Zeile erfassen **kein einziges offenes Netz**. Übrig
bleiben WEP und WPS. Gleichzeitig meldet der Security-Audit-Tab dasselbe Netz
korrekt als kritisch (`SecurityAuditor.kt:145` vergleicht gegen `"Offen"`) — die
App widerspricht sich also sichtbar zwischen zwei Tabs.

**Empfehlung:** Sicherheitstyp als `enum` statt als String durch die Schichten
reichen; Anzeigetext erst in der UI aus `strings.xml` erzeugen.

### B2 (P0) ✅ — WPA3-Netze werden als „Offen" eingestuft

`WifiScanner.kt:286-297` prüft der Reihe nach auf die Literale `"WPA3-Enterprise"`,
`"WPA3-Personal"`, `"WPA3"`, `"WPA2"`, `"WPA"`, `"WEP"`, `"OWE"` — sonst `"Offen"`.

Android liefert in `ScanResult.capabilities` für WPA3-Personal aber
`[RSN-SAE-CCMP][ESS]`, für den Übergangsmodus `[RSN-PSK+SAE-CCMP]`. Keiner dieser
Strings enthält „WPA3", „WPA2" oder „WPA" → die `else`-Zweig greift → **„Offen"**.

Doppelter Schaden: Im Security-Audit erzeugt das eine falsche Kritisch-Meldung
(„Offenes WLAN (verbunden!)", `SecurityAuditor.kt:149`) für das am besten
gesicherte Netz überhaupt. Der Audit-Grade und damit die Kernaussage der App
wird dadurch systematisch verfälscht.

*Gerätetest empfohlen:* Exakte Capability-Strings auf einem WPA3-Router
gegenprüfen, dann auf `SAE`/`RSN-SAE`/`OWE` matchen.

### B3 (P0) ✅ — 6-GHz-Netze bekommen falsches Band und Kanal „-1"

`WifiScanner.kt:254`: `band = if (result.frequency > 4900) "5 GHz" else "2.4 GHz"`
— eine Wi-Fi-6E-Zelle bei 5975 MHz wird zu „5 GHz".

`WifiScanner.kt:273-277`: `frequencyToChannel` deckt nur 2412–2484 und 5170–5825
ab, sonst `-1`. Dieselbe 6-GHz-Zelle erscheint in der Liste also als
**„5 GHz · CH-1"**. Betroffen sind auch die 5-GHz-Kanäle 169–177.

Die README verspricht ausdrücklich „band (2.4/5/6 GHz)" — das Feature existiert
nicht.

### B4 (P0) ✅ — Kanal 14 wird als „15" angezeigt

`WifiScanner.kt:274-276`:

```kotlin
freq in 2412..2484 -> (freq - 2412) / 5 + 1
freq in 5170..5825 -> ...
freq == 2484      -> 14      // unerreichbar
```

2484 MHz fällt bereits in den ersten Zweig: `(2484-2412)/5+1 = 15`. Die
Sonderbehandlung für Kanal 14 ist toter Code, japanische APs werden falsch
beschriftet.

### B5 (P0) ✅ — Das Bluetooth-Radar suggeriert eine Richtung, die es nicht kennt

`BluetoothScreen.kt:315`: `val angleDeg = (i * 137.5f) % 360f` — der Winkel ist
der **Listenindex**, multipliziert mit dem goldenen Winkel. Nur der Abstand zum
Zentrum bildet den RSSI ab.

Ein Radar-Display kommuniziert unmissverständlich Peilung. Nutzer werden in eine
Richtung laufen, die nichts mit dem Gerät zu tun hat. Zusätzlich springen die
Punkte bei jedem Scan neu, weil sich die Listenposition ändert — ein einzelnes
Gerät lässt sich nicht verfolgen.

**Empfehlung:** Entweder als konzentrisches Distanzdiagramm ohne Radar-Metaphorik
zeichnen (Kreuz und Sweep entfernen) oder explizit beschriften: „Winkel ohne
Bedeutung — nur Entfernung". Position je Gerät über die MAC-Adresse stabil
ableiten statt über den Index.

### B6 (P1) ✅ — `SignalTrace` sieht aus wie Messdaten, ist aber Dekoration

`SpectrumComponents.kt:299-315`: `y = h/2 + sin(i * 0.9f + rssi) * amp * 0.5f`.
Eine Sinuskurve, deren Phase aus dem RSSI-Wert stammt. In jeder WLAN-Zeile
(`WifiScreen.kt:364`) steht damit neben der dBm-Zahl eine Oszilloskop-Kurve, die
keinerlei Zeitverlauf oder Messung abbildet. In einem Analyse-Werkzeug ist das
ein Glaubwürdigkeitsproblem.

### B7 (P1) ✅ — Sparklines ohne Skala

`MonitorScreen.kt:314-319`: Die Y-Achse wird auf `min..max` der Daten normiert,
die gestrichelten Gitterlinien bei 0/25/50/75 % sind unbeschriftet, es gibt keine
Zeitachse. Eine Latenzschwankung von 11 ms auf 13 ms erzeugt dasselbe Gebirge wie
eine von 10 ms auf 800 ms. Ohne Achsenbeschriftung ist das Diagramm nicht lesbar,
nur dekorativ.

---

## C — Feedback & Fehlerzustände

### C1 (P0) ✅ — 47 Fehlerpfade, 1 Fehlermeldung

`grep -c "Log.e"` → **47**. `grep -c "Toast\|Snackbar"` → **2** (beides im
Export-Dialog). Alles andere scheitert lautlos:

| Stelle | Was passiert | Was der Nutzer sieht |
|---|---|---|
| `WifiScreen.kt:203` | Wardriving-Export wirft | Nichts. Kein Share-Sheet, keine Meldung |
| `LanViewModel.kt:61` | LAN-Scan wirft | „Keine Geräte gefunden" |
| `LanViewModel.kt:83` | Port-Scan wirft | Fortschritt verschwindet, keine Ergebnisse |
| `WifiViewModel.kt:59` | Persistieren scheitert | Inventar bleibt leer |
| `ScanService.kt:207` | Monitoring-Zyklus wirft | Diagramm bleibt stehen |

**Empfehlung:** Einen Fehlerkanal (`SharedFlow<UiMessage>`) je ViewModel, im
Scaffold als Snackbar anzeigen.

### C2 (P0) ✅ — Der Scan-Button ist tot, wenn WLAN aus ist

`WifiViewModel.kt:38-39`: `fun scan() { if (!wifiScanner.isWifiEnabled()) return ... }`
— kein State wird gesetzt, kein Fehler gemeldet. In `ChannelAnalysisScreen.kt:62`
dasselbe, dort **ohne** jeden Warnbanner.

Der WLAN-Tab zeigt immerhin „WLAN ist deaktiviert. Bitte WLAN einschalten."
(`WifiScreen.kt:158-163`) — allerdings ohne Button, der die WLAN-Einstellungen
öffnet, und der Banner wird über `vm.isWifiEnabled()` **während der Composition**
gelesen: Schaltet der Nutzer WLAN ein, verschwindet der Banner erst bei der
nächsten Recomposition aus anderem Grund.

**Empfehlung:** WLAN-Status als `StateFlow` aus einem `BroadcastReceiver` auf
`WIFI_STATE_CHANGED_ACTION`; Banner mit Aktion „WLAN-Einstellungen öffnen".

### C3 (P1) ✅ — Erster Tap fordert nur an, zweiter scannt

`WifiScreen.kt:134-137`, `BluetoothScreen.kt:148-152`,
`ChannelAnalysisScreen.kt:63-66`: Bei fehlender Berechtigung wird angefordert und
`return` — nach dem Erteilen passiert nichts, der Nutzer muss erneut tippen.
Ein `LaunchedEffect(permissionState.allPermissionsGranted)` würde den Scan
anstoßen.

### C4 (P1) — Android-Scan-Drosselung bleibt unerklärt

`WifiScanner.kt:97-106`: Liefert `startScan()` wegen der Android-Drosselung
(ab Android 9: 4 Scans / 2 Minuten für Vordergrund-Apps) `false`, werden
kommentarlos die **zwischengespeicherten** Ergebnisse zurückgegeben. Der Nutzer
tippt „Scannen", die Liste ändert sich nicht, und nichts erklärt warum. Es gibt
auch nirgends einen „zuletzt aktualisiert"-Zeitstempel.

**Empfehlung:** Zeitstempel im Header („Stand: 14:32:10") und bei Drosselung ein
Hinweisbanner.

### C5 (P1) — Fortschrittsbalken von 1 dp Höhe

`LanScreen.kt:154` und `:443` — `Modifier.fillMaxWidth().height(1.dp)`. Bei einem
Ping-Sweep über 254 Adressen ist der Fortschritt praktisch unsichtbar. Der
Security-Audit macht es mit 2 dp (`SecurityAuditScreen.kt:118`) kaum besser.

### C6 (P1) ✅ — Monitoring startet ohne Benachrichtigungsberechtigung

`MonitorScreen.kt:73-93` startet den Foreground-Service ohne Prüfung von
`POST_NOTIFICATIONS`. Ab Android 13 ist die Berechtigung optional — wurde sie im
Onboarding abgelehnt (siehe A2), läuft der Dienst **unsichtbar** weiter: keine
Notification, damit auch kein Stopp-Button und kein Hinweis auf den
Akkuverbrauch.

### C7 (P2) — Systemfremdes Benachrichtigungs-Icon

`ScanService.kt:243`: `.setSmallIcon(android.R.drawable.ic_menu_manage)` — ein
Legacy-System-Icon statt einer App-eigenen monochromen Silhouette. In der
Statusleiste wirkt das wie eine fremde App.

---

## D — Barrierefreiheit

### D1 (P0) ✅ — Keine einzige Semantik-Annotation in der App

```
grep -c "Role\."     → 0
grep -c "semantics"  → 0
grep -c ".clickable" → 52
```

52 klickbare Elemente sind für TalkBack namenlose Flächen ohne Rolle. Konkrete
Folgen:

- **Bottom-Nav** (`SpectrumComponents.kt:249-252`): `Box.clickable` statt
  `Modifier.selectable(role = Role.Tab)` → der aktive Tab wird nicht angesagt.
  Zusätzlich dupliziert `contentDescription = t.label` (Zeile 268) den sichtbaren
  Text, TalkBack liest „WIFI WIFI".
- **Filter-Chips** (`:184-221`): kein `Role.Button`, kein Selektionszustand.
- **Alle Canvas-Grafiken** (Radar, Sparklines, Kanalbalken, Audit-Donut) haben
  keine Text-Alternative. Der Sicherheits-Grade wird in `SecurityAuditScreen.kt:301`
  per `nativeCanvas.drawText` gezeichnet — die zentrale Note „A" bis „F" ist für
  Screenreader nicht vorhanden.
- **Gerätesymbole** tragen 11× `contentDescription = null`
  (`LanScreen.kt:249`, `InventoryScreen.kt:341`, `BluetoothScreen.kt:360`): Der
  Gerätetyp wird ausschließlich über das Icon vermittelt und geht verloren.

### D2 (P0) ✅ — Fast alle Bedienelemente unterschreiten 48 dp

Gemessen aus Padding + Schriftgröße; Material-3- und WCAG-2.5.5-Mindestmaß ist
48×48 dp:

| Element | Fundstelle | Größe |
|---|---|---|
| GPS-Export-Icon | `WifiScreen.kt:302` | ~20 dp |
| GPS-Switch (Höhe erzwungen) | `WifiScreen.kt:310` | **20 dp** |
| Favoriten-Stern Inventar | `InventoryScreen.kt:362` | 16 dp |
| LAN-Browse-Button „↗" | `LanScreen.kt:617` | ~18 dp |
| Banner-Aktion „Berechtigungen erteilen" | `WifiScreen.kt:258` | ~22 dp |
| Radar-Punkte | `BluetoothScreen.kt:335` | 22 dp |
| LAN-Favoriten-Button | `LanScreen.kt:378` | 24 dp |
| Filter-Chips | `SpectrumComponents.kt:195` | ~26 dp |
| Port-Scan-Chips | `LanScreen.kt:545` | ~26 dp |
| BT-Panel Schließen | `BluetoothScreen.kt:444` | 28 dp |
| Scan-Button | `SpectrumComponents.kt:136` | ~31 dp |
| Monitor START/STOPP | `MonitorScreen.kt:363` | ~29 dp |
| IconSquareButton (Detail-Header) | `WifiDetailScreen.kt:306`, `BleDetailScreen.kt:585` | 30 dp |
| Export-Sheet Schließen | `ExportDialog.kt:113` | 30 dp |
| Bottom-Nav-Tab (Breite) | `SpectrumComponents.kt:249` | **45 dp @360dp, 40 dp @320dp** |

Kein einziges der aufgeführten Elemente erreicht das Minimum. Die Bottom-Nav ist
besonders heikel, weil 8 Tabs die Bildschirmbreite achteln.

**Empfehlung:** `Modifier.minimumInteractiveComponentSize()` bzw.
`.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` auf alle Klickflächen — die
visuelle Größe bleibt erhalten, nur der Trefferbereich wächst.

### D3 (P1) ✅ — `OnSurfaceFaint` erreicht 2,01:1 statt 4,5:1

Berechnete Kontraste der Palette gegen `Surface #07090A`:

| Farbe | Kontrast | WCAG AA (Text) |
|---|---|---|
| `OnSurface #E8EFEC` | 17,09:1 | ✅ |
| `Accent #C8FF4F` | 16,98:1 | ✅ |
| `Warning #FFCB5E` | 13,26:1 | ✅ |
| `Danger #FF7A66` | 7,82:1 | ✅ |
| `OnSurfaceDim #7C8A86` | 5,55:1 | ✅ |
| `AccentDim #5E7A20` | 4,07:1 | ⚠️ nur Großtext |
| **`OnSurfaceFaint #3B4543`** | **2,01:1** | ❌ |
| `GridLine #1A2023` | 1,21:1 | ❌ (als Rahmen/Trenner) |

`OnSurfaceFaint` trägt echten Text: Entfernungsangabe im WLAN-Eintrag
(`WifiScreen.kt:425`), „zuletzt gesehen" im Inventar (`InventoryScreen.kt:406`),
LAN-Hinweistext (`SecurityAuditScreen.kt:243`) und — besonders ungünstig — den
**Deaktiviert-Zustand** der Port-Scan-Chips (`LanScreen.kt:535`). Deaktivierte
Elemente sind damit faktisch unlesbar.

`GridLine` als einziger Trenner zwischen Listeneinträgen liegt bei 1,21:1; die
für Nicht-Text-Elemente geforderten 3:1 werden deutlich verfehlt.

### D4 (P1) ✅ — Schriftgrößen von 8–10 sp als Regelfall

Bottom-Nav-Labels stehen auf **8 sp** (`SpectrumComponents.kt:277`), der
Standardfall in Kickern, Chips, Metazeilen und Bannern ist 9–10 sp, zusätzlich mit
0,18 em Laufweite in einer Monospace-Schrift. Empfohlene Untergrenze für
Fließtext ist 12 sp; unter 11 sp gilt Text als nicht zumutbar lesbar.

### D5 (P1) — Erzwungenes Dark-Theme ohne Hellvariante

`Theme.kt:104-111` — `darkColorScheme` fest verdrahtet, kein `isSystemInDarkTheme()`,
keine dynamischen Farben. Als Designentscheidung nachvollziehbar, in der
Hauptnutzungssituation dieser App (Wardriving, Außenbereich, Sonnenlicht) jedoch
die schlechteste Wahl. Mindestens erwähnenswert als bewusste Einschränkung.

### D6 (P1) ✅ — Statusleisten-Icons sind auf Schwarz unsichtbar

`res/values/themes.xml`:

```xml
<style name="Theme.Spectrum" parent="android:Theme.Material.Light.NoActionBar">
    <item name="android:windowLightStatusBar">true</item>
</style>
```

`windowLightStatusBar = true` bedeutet „heller Hintergrund → **dunkle** Icons".
Die App zeichnet darunter aber `#07090A`. Uhrzeit, Akku- und Netzanzeige stehen
damit dunkelgrau auf fast-schwarz.

Verschärfend: `enableEdgeToEdge()` (`MainActivity.kt:54`) verwendet per Default
`SystemBarStyle.auto`, das sich an der **System**-Einstellung orientiert — auf
einem Gerät im Hellmodus bleibt es bei dunklen Icons. Zusätzlich sorgt das
Light-Eltern-Theme für einen weißen Blitz beim Kaltstart, bevor Compose zeichnet.

**Empfehlung:** Parent auf `Theme.Material.NoActionBar` umstellen,
`windowLightStatusBar` auf `false`, `android:windowBackground` auf `#07090A`, und
`enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(TRANSPARENT))`.

### D7 (P2) — Kein `rememberSaveable`, keine Zustandswiederherstellung

`grep -c rememberSaveable` → **0**. Aufgeklappte Listeneinträge
(`LanScreen.kt:204`, `InventoryScreen.kt:307`, `SecurityAuditScreen.kt:312`)
klappen beim Wegscrollen zu, weil die `remember`-Zustände mit dem
LazyColumn-Item verworfen werden. Nach Bildschirmdrehung ist außerdem die
Formularauswahl im Export-Sheet zurückgesetzt.

### D8 (P2) — Blinken ohne Rücksicht auf reduzierte Bewegung

`BlinkingDot` (`SpectrumComponents.kt:157`) und der Radar-Sweep
(`BluetoothScreen.kt:227`) laufen als `infiniteRepeatable` dauerhaft — der Sweep
auch dann, wenn gerade nicht gescannt wird. Die Systemeinstellung „Animationen
entfernen" wird nicht ausgewertet. Mit 1,25 Hz bleibt das Blinken unterhalb der
Photosensitivitätsschwelle, ist aber unnötiger Dauerverbrauch.

---

## E — Sprache & Konsistenz

### E1 (P0) ✅ — Deutsch und Englisch mischen sich innerhalb einzelner Screens

Die Ressourcenlage ist formal sauber — 240 Schlüssel in `values/` (Deutsch,
Default) und exakt 240 in `values-en/`, keine Lücke in beiden Richtungen. Nur wird
ein erheblicher Teil der sichtbaren Texte daran vorbei erzeugt.

Drei Screens verwenden **keinen einzigen** `stringResource`-Aufruf:

| Datei | `stringResource`-Aufrufe |
|---|---|
| `ChannelAnalysisScreen.kt` | 0 |
| `MonitorScreen.kt` | 0 |
| `WifiDetailScreen.kt` | 0 |

`MonitorScreen.kt` zeigt das Problem am dichtesten — dieselbe Ansicht enthält:

> „SIGNAL", „GATEWAY LATENCY", „INTERNET (8.8.8.8)", „AVG", „n=" (Zeilen 136–156, 271–277)
> neben
> „SITZUNG", „Neue Geräte", „Ø Signal", „SCAN-INTERVALL", „5 SEK", „STOPP" (Zeilen 181–193, 367, 383–386)

`ChannelAnalysisScreen.kt` ebenso: Überschriften „UTILIZATION // DB/CH",
„RECOMMENDED", „Lowest utilization on band", „CLEAR/MED/CONGESTED" — der Leerzustand
darunter auf Deutsch: „Tippe auf \"Scannen\" für die WLAN-Kanalanalyse."
(Zeile 323).

Das Dokument `docs/UI_String_externalization.md` erklärt die Externalisierung als
„Phase 4 complete!" — für diese drei Screens und die gesamte Domänenschicht trifft
das nicht zu.

### E2 (P1) ✅ (teilweise) — Deutsche Texte sind in der Domänenschicht fest verdrahtet

Diese Strings erreichen die UI unübersetzbar und erscheinen auch auf einem
englischsprachigen Gerät auf Deutsch:

| Fundstelle | Text |
|---|---|
| `WifiScanner.kt:284,297` | „Unbekannt", „Offen" |
| `Models.kt:75,89-91` | „Unbekannt", „Gekoppelt", „Kopplung...", „Nicht gekoppelt" |
| `Entities.kt:17-21` | „WLAN", „Bluetooth Classic", … |
| `BleUuidDatabase.kt:20,26` | „Unbekannter Dienst (…)", „Unbekannt (…)" |
| `PortScanner.kt:27,53` | „Offen", „Unbekannt" |
| `GattExplorer.kt:110-115` | „Verbinde...", „Fehlgeschlagen" |
| `GattExplorer.kt:155-271` | sämtliche Fehlermeldungen |
| `SecurityAuditor.kt` | **alle** Befundtitel, -beschreibungen und -empfehlungen |
| `ScanService.kt:106,216,241-244` | Notification-Titel, -Text, Kanalname, „Stopp" |

Der Security-Audit — das namensgebende Feature — ist damit ausschließlich auf
Deutsch verfügbar.

### E3 (P1) ✅ — Sentinel-Werte als Fachlogik

`"(Unbekannt)"` wird an sechs Stellen per String-Vergleich ausgewertet
(`BluetoothScreen.kt:401,526`, `SecurityAuditor.kt:228,261`, `Models.kt:43,52`,
`DeviceRepository.kt:350`). Sobald jemand diesen String lokalisiert, bricht die
Logik still — dieselbe Klasse von Fehler wie B1.

### E4 (P1) — Keine Plurale, kein lokalisiertes Datum

`grep -c "<plurals"` → **0**. Formate wie `„%1$d gefunden"` funktionieren im
Deutschen zufällig, im Englischen entsteht „1 devices". Das Datumsformat ist mit
`DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")` (`InventoryScreen.kt:440`)
deutsch fixiert statt über `FormatStyle.SHORT` lokalisiert.

### E5 (P2) — Widersprüchliche Bezeichner für dieselbe Sache

Das WLAN heißt je nach Stelle „WLAN" (`strings.xml`), „WIFI"
(`MainActivity.kt:65`), „WiFi" (`Entities.kt:25`) oder „Airspace"
(`WifiScreen.kt:132`). Der Scan-Button heißt „Scannen", der Leerzustand verweist
auf „Scannen" — im Englischen heißt der Button aber „Scan", der Hinweistext im
Kanal-Screen weiterhin „Tippe auf \"Scannen\"".

---

## F — Informationsarchitektur

### F1 (P1) ✅ — Acht gleichrangige Tabs in der Bottom-Navigation

`MainActivity.kt:64-73`. Material 3 sieht für die Bottom-Navigation **3 bis 5**
Ziele vor. Acht bedeuten: 45 dp Breite je Tab auf einem 360-dp-Gerät (siehe D2),
8-sp-Labels (D4) und Abkürzungen, die ohne Vorwissen nicht dekodierbar sind:

`WIFI · CH · BT · LAN · MON · SEC · MAP · INV`

„CH" (Channel Analysis), „SEC" (Security), „INV" (Inventory) und „MON" (Monitor)
erschließen sich weder aus dem Kürzel noch aus dem Icon. Die Labels sind zudem
hart kodiert und umgehen `strings.xml`.

**Empfehlung:** Auf vier Ziele verdichten — *Scan* (WLAN/BT/LAN mit Untertabs),
*Analyse* (Kanal + Monitor), *Audit* (Security + Map), *Inventar* — und die Labels
ausschreiben.

### F2 (P1) — Export nur im letzten Tab auffindbar

`ExportDialog` wird ausschließlich aus `InventoryScreen.kt:211` geöffnet. Nach
einem WLAN-Scan, einem LAN-Scan oder einem Security-Audit gibt es an Ort und
Stelle keine Export-Möglichkeit. Die einzige Ausnahme ist der Wardriving-Export,
der im GPS-Streifen des WLAN-Tabs versteckt ist und **nur erscheint, wenn bereits
GPS-Fixes vorliegen** (`WifiScreen.kt:296`).

### F3 (P1) — Der Security-Audit-Report lässt sich nicht exportieren

Die README verspricht „Export data to CSV, JSON, or formatted PDF reports".
`ExportManager` exportiert jedoch ausschließlich `DiscoveredDeviceEntity`-Zeilen
aus dem Inventar. Der Audit-Bericht mit Note, Befunden und Empfehlungen — also
genau das Dokument, das man weitergeben würde — existiert nur flüchtig im Screen.

### F4 (P2) ✅ — Kein Einstellungs-Screen

Es gibt keinen Ort für: Berechtigungen nachträglich verwalten, Onboarding erneut
ansehen, Sprache wählen, Standardintervall setzen, Datenbank leeren,
Lizenz-/Attributionshinweise. Für eine App mit dieser Funktionstiefe eine
auffällige Lücke.

### F5 (P2) — Kanalanalyse zeigt Nullwerte vor dem ersten Scan

`ChannelAnalysisScreen.kt:83-87` stellt die Kopfzeilen-Statistiken immer dar —
vor dem ersten Scan also „CH- · 0% · 0 channels". Der WLAN-Tab blendet seine
Statistiken bis zum ersten Scan korrekt aus (`WifiScreen.kt:138`); beide
Verhalten widersprechen sich.

---

## G — Datenhoheit

### G1 (P0) ✅ — Löschen ohne Rückfrage, ohne Rückgängig

`InventoryScreen.kt:419-422`: Der Menüpunkt „Löschen" ruft direkt
`repository.deleteDevice(device.id)`. Kein Bestätigungsdialog, kein
Undo-Snackbar. Da das Menü per Long-Press geöffnet wird (A7), ist ein
versehentliches Auslösen realistisch — und der Eintrag mit allen Notizen, Labels
und der Historie ist endgültig fort.

### G2 (P1) ✅ — Keine Möglichkeit, alle Daten zu löschen

`DeviceDao.kt` enthält genau eine Löschabfrage:
`@Query("DELETE FROM discovered_devices WHERE id = :id")`. Es gibt kein „Alle
Daten löschen".

Die App speichert dauerhaft MAC-Adressen, SSIDs, Gerätenamen, Hersteller,
Zeitstempel und — bei aktiviertem Wardriving — GPS-Koordinaten. Für einen solchen
Datenbestand sollte ein Löschweg innerhalb der App existieren; aktuell bleibt nur
„App-Daten löschen" in den Systemeinstellungen.

---

## H — Layout & Robustheit

### H1 (P0) ✅ — 5-GHz-Kanalbalken sind unlesbar

`ChannelAnalyzer.kt:52` definiert **24** 5-GHz-Kanäle. `ChannelAnalysisScreen.kt:160-221`
legt sie als `Row` mit je `weight(1f)` und 4 dp Abstand nebeneinander.

Rechnung für ein 360-dp-Gerät: 360 − 36 (Screen-Padding) − 28 (Box-Padding)
= 296 dp, minus 23 × 4 dp Lücken = 204 dp, geteilt durch 24 → **8,5 dp je Balken**.
Darin sollen dreistellige Kanalnummern („149", „161") in 10-sp-Monospace stehen,
die rund 18–20 dp benötigen, plus die 16-dp-Zählerkreise über den Balken. Die
Beschriftung überlappt bzw. wird abgeschnitten.

**Empfehlung:** Horizontal scrollbare Darstellung mit fester Mindestbreite je
Balken (~28 dp) oder Gruppierung nach UNII-Bändern.

### H2 (P1) — Kartengesten kollidieren mit dem Pager

`MapScreen.kt:330` setzt `setMultiTouchControls(true)`, die `MapView` steckt aber
in einem `HorizontalPager` (`MainActivity.kt:115`) und ruft nirgends
`requestDisallowInterceptTouchEvent(true)` auf. Horizontales Verschieben der Karte
wird daher vom Pager abgefangen und wechselt den Tab, statt zu schwenken.

*Gerätetest empfohlen* — der genaue Punkt, an dem der Pager die Geste übernimmt,
hängt vom Touch-Slop ab.

### H3 (P1) ✅ (teilweise) — OSM-Attribution fehlt, User-Agent ist ein Platzhalter

`MapScreen.kt:323`:

```kotlin
userAgentValue = "Isochron/${BuildConfig.VERSION_NAME} (Android; +https://github.com/TODO_REPLACE/Isochron)"
```

Der Platzhalter geht so an die OSM-Tile-Server. Die OSM Tile Usage Policy
verlangt einen identifizierenden User-Agent; Verstöße werden blockiert — dann
lädt die Karte bei Nutzern schlicht nicht mehr. Zusätzlich fehlt die nach ODbL
erforderliche Attribution („© OpenStreetMap contributors"): weder
`CopyrightOverlay` noch ein eigener Hinweis ist vorhanden.

Ebenfalls fehlend: Zoom-Buttons, „auf meine Position zentrieren", Maßstabsleiste.
Die Karte zentriert genau einmal (`hasRecentered`, Zeile 316) — wer wegschwenkt,
findet seine Daten nicht mehr zurück.

### H4 (P1) — Querformat und Tablets sind nicht vorgesehen

Keine Orientierungssperre im Manifest, aber auch keine adaptive Gestaltung:
`WindowSizeClass` wird nirgends verwendet, `BoxWithConstraints` nur im Radar.

Konkret bricht das Bluetooth-Radar (`BluetoothScreen.kt:243-247`):
`fillMaxWidth().aspectRatio(1f)` ergibt im Querformat ein Quadrat von
Bildschirmbreite — also höher als der Bildschirm. Die Geräteliste darunter ist
erst nach langem Scrollen erreichbar.

### H5 (P2) ✅ — 458 Zeilen toter UI-Code

`ui/components/DeviceCards.kt` — keines der exportierten Composables wird
verwendet:

| Composable | Verwendungen |
|---|---|
| `SignalBar` | 0 |
| `WifiNetworkCard` | 0 |
| `BluetoothDeviceCard` | 0 |
| `StatusChip` | 0 |
| `SecurityChip` | 0 |
| `deviceIcon` | 0 |

Die Datei stammt aus dem Material-Design vor dem Spectrum-Redesign und enthält
eine zweite, widersprüchliche Farblogik (`Color(0xFFF44336)`, Zeile 369). Sie
sollte entfernt werden — sonst wird sie irgendwann wieder aufgegriffen.

### H6 (P2) — Export erlaubt Dateien mit null Einträgen

`ExportDialog.kt:230`: `canExport = !isExporting && (wifiEnabled || btEnabled || lanEnabled)`
— die tatsächliche Trefferzahl `totalCount` wird nicht geprüft. Der Button
beschriftet sich dann mit „0 … exportieren", erzeugt eine leere Datei und öffnet
das Share-Sheet.

---

## Was gut funktioniert

Damit die Liste nicht den falschen Eindruck hinterlässt — diese Dinge sind
sorgfältig gemacht:

- **`WifiScanner.startScan`** behandelt Timeout, `SecurityException`, fehlenden
  `WifiManager` und den `false`-Rückgabewert von `startScan()` sauber ab und
  räumt den `BroadcastReceiver` in jedem Pfad auf. Ein Aufhängen im
  „Scanne…"-Zustand ist ausgeschlossen.
- **Der Export-Dialog** ist der einzige Ort mit vollständigem Feedback-Zyklus:
  Ladeindikator während des Laufs, `Toast` mit Fehlertext bei Misserfolg,
  automatisches Schließen bei Erfolg.
- **Leerzustände** sind durchgängig zweistufig differenziert („noch nicht
  gescannt" vs. „gescannt, nichts gefunden") — in `WifiScreen`, `LanScreen`,
  `BluetoothScreen` und `InventoryScreen` gleichermaßen.
- **ViewModel-Migration**: Scanergebnisse, Filter und Auswahl liegen in
  `AndroidViewModel`s und überstehen Tab-Wechsel und Rotation. Die
  `remember`-Memoisierung der Listenoperationen ist korrekt auf die richtigen
  Keys gesetzt.
- **GATT-Explorer**: Der einzige Bereich mit vollständigem Zustandsmodell
  (`CONNECTING`/`READY`/`FAILED` inklusive sichtbarer Fehlerzeile,
  `BleDetailScreen.kt:176-180`).
- **Ressourcenparität**: 240 Schlüssel in beiden Sprachen, keine Lücke in
  beide Richtungen.

---

## Empfohlene Reihenfolge

### Stufe 1 — Korrektheit (bricht sonst das Kernversprechen) — ✅ umgesetzt

1. **B1** Sicherheitstyp als `enum` statt `"Offen"`/`"Open"` — behebt zugleich E3
2. **B2** WPA3/SAE-Erkennung (falsche Kritisch-Meldungen im Audit)
3. **B3/B4** 6-GHz-Band und Kanalberechnung
4. **A1** `BackHandler` in allen fünf Overlays
5. **A2** Onboarding wertet das Berechtigungsergebnis aus
6. **A3** Weg in die Systemeinstellungen bei permanenter Ablehnung
7. **G1** Bestätigung vor dem Löschen

*Alle sieben sind kleine, lokal begrenzte Änderungen.*

### Stufe 2 — Verlässlichkeit — ✅ umgesetzt

8. **C1** Fehlerkanal + Snackbar statt 47 stiller `Log.e`
9. **C2** WLAN-Status reaktiv, Banner mit Aktion
10. **D6** Theme-Parent und Statusleisten-Icons
11. **A4/A5** Scan-Abbruch, Port-Scan-Serialisierung
12. **C6** `POST_NOTIFICATIONS` vor dem Servicestart prüfen
13. **H3** OSM-User-Agent und Attribution

### Stufe 3 — Zugänglichkeit — ✅ umgesetzt

14. **D2** `minimumInteractiveComponentSize()` flächendeckend
15. **D1** `Role`/`semantics` für die 52 Klickflächen, Canvas-Alternativtexte
16. **D3** `OnSurfaceFaint` auf ≥ 4,5:1 anheben (Vorschlag: `#718480` — 5,05:1 / 4,76:1 / 4,56:1 auf den drei Flächenfarben)
17. **D4** Schriftuntergrenze auf 11 sp anheben

### Stufe 4 — Struktur — ✅ umgesetzt

18. **E1/E2** Restliche Strings externalisieren, Domänenschicht entkoppeln
19. **F1** Bottom-Navigation auf vier Ziele verdichten
20. **F4** Einstellungs-Screen, darin **G2** (Daten löschen)
21. **H1** Kanaldiagramm scrollbar
22. **B5/B6/B7** Visualisierungen ehrlich machen: Radar-Winkel, `SignalTrace`, Sparkline-Achsen
23. **H5** `DeviceCards.kt` entfernen

---

## Offene Punkte

Stand nach Stufe 4. Alles, was in keiner Stufe eingeplant war, plus die
Reste teilweise umgesetzter Befunde.

### Reste aus umgesetzten Befunden

| Befund | Was noch fehlt |
|---|---|
| **E2** | `GattExplorer`-Statusmeldungen und Fehlertexte, `BleUuidDatabase` („Unbekannter Dienst"), `PortState`/`ServiceType`-Labels in `PortScanner.kt`, `DeviceCategory.displayName()` in `Entities.kt` (erreicht Inventar-Detail und CSV/JSON-Export) |
| **H3** | Zoom-Buttons, Maßstabsleiste, „auf meine Position zentrieren"; die Karte zentriert weiterhin nur einmal |

### Nicht eingeplante Befunde

Nach Priorität, dann Reihenfolge im Dokument.

**P1**

| Befund | Kurzfassung |
|---|---|
| **A6** | Export-Sheet ist kein Modal — Pager-Wisch unter dem Sheet wechselt den Tab, Sheet endet über der Bottom-Nav |
| **C4** | Android-Scan-Drosselung (4 Scans / 2 min) bleibt unerklärt; kein „zuletzt aktualisiert"-Zeitstempel |
| **C5** | Fortschrittsbalken 1 dp (LAN, Port-Scan) bzw. 2 dp (Audit) — praktisch unsichtbar |
| **D5** | Erzwungenes Dark-Theme ohne Hellvariante — als Designentscheidung dokumentieren oder Sonnenlicht-Modus anbieten |
| **E4** | Keine `<plurals>` außer `ch_rec_aps`; Datumsformat `dd.MM.yy HH:mm` fest statt `FormatStyle.SHORT` |
| **F2** | Export nur im Inventar erreichbar; nach WLAN-/LAN-Scan oder Audit keine Export-Möglichkeit an Ort und Stelle |
| **F3** | Security-Audit-Report (Note, Befunde, Empfehlungen) lässt sich nicht exportieren |
| **H2** | `MapView` im `HorizontalPager` ohne `requestDisallowInterceptTouchEvent` — horizontales Schwenken wechselt ggf. den Tab; Gerätetest steht aus |
| **H4** | Querformat und Tablets nicht vorgesehen |

**P2**

| Befund | Kurzfassung |
|---|---|
| **A7** | Kontextmenü im Inventar nur per Long-Press auffindbar |
| **C7** | Notification nutzt `android.R.drawable.ic_menu_manage` statt eines App-eigenen monochromen Icons |
| **D7** | Kaum `rememberSaveable`; Filter, Auswahl und Scroll-Position überleben eine Rotation nicht |
| **D8** | `BlinkingDot` und Pulse-Animationen ignorieren „Animationen reduzieren" |
| **E5** | Uneinheitliche Bezeichner: WLAN / WIFI / WiFi / Airspace; Scannen / Scan |
| **F5** | Kanalanalyse zeigt „CH- · 0% · 0 Kanäle" vor dem ersten Scan |
| **H6** | Export erlaubt Dateien mit null Einträgen (`canExport` prüft `totalCount` nicht) |

### Bekannte Einschränkungen der Umsetzung

- **Stufe 1** wurde ohne Compiler geschrieben und erst danach lokal gebaut,
  getestet und auf einem Gerät geprüft (Nothing A142P, Android 16). Alle
  Stufen 2–4 wurden direkt auf diesem Gerät verifiziert; andere Android-
  Versionen und Hersteller stehen aus.
- Es gibt keine Unit-Tests für `SecurityAuditor`, `ChannelAnalyzer`-Empfehlungen,
  die ViewModels oder den `ScanService`. Die 69 bestehenden Tests decken
  `WifiCapabilities`, `ChannelAnalyzer.analyze`, `CsvEscape` und `SignalHelper`.
