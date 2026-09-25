# Cross-Platform BLE Transport + iOS App Design

Date: 2026-09-24

Adds a native iOS app (iPhone + iPad) for Knuckled and lets iPhone/iPad play against Android over Bluetooth. iOS third-party apps cannot use Bluetooth Classic RFCOMM (the transport the Android app uses today), so cross-play rides on **BLE**, with the existing PIN handshake and newline-framed line protocol preserved unchanged. Android keeps RFCOMM for Android↔Android and gains a BLE connector for cross-play and iOS↔iOS matches.

Decision: **Approach A** — a shared wire contract, but two independent native codebases (Kotlin/Compose on Android, Swift/SwiftUI on iOS). No Kotlin Multiplatform sharing; the ported logic is small and stable, and each platform keeps its idiomatic BLE/3D/audio APIs.

Mirror note: the iOS app lives in a separate repo — `~/XcodeProjects/KnuckleGame-ios`. This spec is the Android repo's copy of the contract and the Android-side design; the iOS repo mirrors the contract with the iOS design (`docs/superpowers/specs/2026-09-24-crossplay-ios-design.md`). The wire/JSON format is frozen; either repo may extend it only with a coordinated change.

## Goals

- iPhone/iPad ↔ Android cross-play over Bluetooth using the same PIN + line-protocol idea the Android app already has.
- A native iOS app matching Android feature and UX parity: two-player PvP, single-player vs CPU, name/PIN flow, rolling 3D die, sound, settings toggles (mute, animations, auto-roll), leave confirmation, win/lose/draw overlays, play-again keeps names.
- Android gains a BLE transport with zero behavior changes to the existing RFCOMM path.

## Non-goals (YAGNI)

- No RFCOMM on iOS; RFCOMM stays Android-only (Android↔Android keeps using it).
- No background-mode support: the app is played in the foreground on both devices.
- No OS-level BLE bonding beyond the app's own 4-digit PIN auth.
- No server/relay, no internet, no Game Center/iCloud.
- No change to the wire format, JSON schema, or game rules. These are frozen.

## Context

Today the whole Android link layer is byte-stream based. `Protocol` frames newline-terminated UTF-8 lines, `Handshake` does the PIN accept/initiate over that stream, and `GameLinkImpl` runs a reader thread that dispatches lines to `onLine` and fires `onClosed` on EOF. `AndroidBluetoothConnector` implements Classic RFCOMM around that seam; `ConnectionViewModel` calls the blocking `BluetoothConnector` interface (`listen(pin)`, `connect(device, pin)`, `discover()`) from background `Thread`s.

Cross-play = giving that same byte-stream interface a second implementation over BLE on both platforms. The contract below is the source of truth for both apps.

## Part 1 — The BLE transport contract

### Roles

- **Host** = GATT *peripheral*: advertises the Knuckled service, accepts the connection, hosts the authoritative `GameHost`.
- **Client** = GATT *central*: scans for the service, connects, runs PIN handshake, renders `STATE` broadcasts.
- Android↔Android over BLE uses the same mapping (one side advertises, the other scans) — no pairing step, PIN is the only auth.

### GATT layout

One custom 128-bit service (UUIDs chosen once, then frozen at first release):

| Element | Type | Role |
|---|---|---|
| Service | 128-bit UUID | Advertised by host; scan filter for client |
| Write Char | Write + WriteWithoutResponse | Client → host message bytes |
| Notify Char | Read + Notify (subscribe via CCCD) | Host → client message bytes |

Two one-way characteristics avoid write/reply ordering tricks; the existing line protocol already conveys direction.

### Framing over GATT

The `Protocol` line framing *is* the wire format (`\n`-terminated UTF-8, `MAX_FRAME_BYTES = 1024`). BLE only swaps the byte pipe:

- **Sender** chunks each line into consecutive GATT writes/notifications of at most (negotiated MTU − 3) bytes; falls back to 20-byte chunks when the peer MTU is unknown. No length prefix.
- **Receiver** appends chunks to a bounded buffer and splits on `\n`, exactly like `Protocol.readLine` (immune to chunk boundaries cutting a line or a multi-byte UTF-8 character; reassembly is byte-wise).
- BLE preserves ordering and reliability on a connected link, so the stream model holds exactly.
- Buffer is capped at `MAX_FRAME_BYTES`; overflow is treated as a fatal read error, matching existing behavior.
- MTU negotiation: Android central `requestMtu`, iOS client `setMaximumWriteValueLength` / `maximumWriteValueLength`; peripheral exposes its accepted value (`iOS maximumUpdateValueLength`). Negotiation "best-effort": framing must work even at 20-byte chunks.

### Discovery & auth

- Host advertises the service UUID (plus friendly name where the platform allows; iOS may advertise UUID only). Android `BluetoothLeAdvertiser`; iOS `CBPeripheralManager`.
- Client scans filtered on the service UUID and shows results by name (`CBPeripheral.name`, Android scan result device name). Android advertising window ~2 min to match the current "visible ~2 min" copy.
- PIN (4 digits) gates the link exactly as today: `Handshake.initiate`/`accept` ride the same byte-pipe with zero changes.

### Wire/JSON freeze

`STATE:` carries a JSON serialization of `GameState` with these exact property names and enum string values:

- `hostName: String`, `clientName: String`, `status` (`IN_PROGRESS|FINISHED|DRAW`), `currentTurn` (`HOST|CLIENT`), `phase` (`IDLE|ROLLING|AWAITING_PLACEMENT`), `grid: Map<HOST|CLIENT, List<[Int]>>` (3 columns, bottom-most die first), `winner: PlayerId|null`, `lastRoll: Int|null`, `destroyed: [{player, column, value}]`.
- Kotlin serializes with `encodeDefaults = true`; Swift `Codable` must decode strictly (unknown field ⇒ invalid state, like `decodeState` returning null).

Command lines: `NAME:<sanitized>`, `ROLL`, `PLACE:<0..2>`, `RESTART`, `STATE:<json>`.

## Part 2 — Android changes

| File | Change |
|---|---|
| `bluetooth/BleBytePipe.kt` (new, internal) | Converts GATT `onCharacteristicChanged` / write notifications into the `GameLink` seam: arbitrary-chunk append → buffer → newline split → `onLine`; `onClosed`; `close`. Mirrors `GameLinkImpl` semantics (buffered early lines, no drops). |
| `bluetooth/AndroidBleConnector.kt` (new) | Implements `BluetoothConnector` as a blocking facade over BLE callbacks (`ScanCallback`, `BluetoothGatt`, `BluetoothLeAdvertiser`, `BluetoothGattServer`), bridged with `CountDownLatch`/timeouts so `ConnectionViewModel` runs it unchanged on its existing worker `Thread`s. |
| `ui/ConnectionScreens.kt` | Transport toggle on the Start screen: **Android (Classic RFCOMM)** vs **Knuckled (BLE, cross-play)**; selects the active connector. Debug *fake link* switch stays, layered on top. |
| `ui/ConnectionViewModel.kt` | Small addition: swap `activeConnector` between RFCOMM and BLE connectors from the toggle. |

Untouched: `Protocol`, `Handshake`, `GameMessages`, `GameHost`, `GameViewModel`, `GameScreen`, rules, `GameLinkImpl`, RFCOMM connector.

Android permissions required by BLE (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, location-not-required) are already declared in `AndroidManifest.xml`; runtime errors surface through the existing error banner.

## Part 3 — iOS app

Native Swift 6 / SwiftUI app in the sibling iOS repo **`~/XcodeProjects/KnuckleGame-ios`**. iOS 17+ minimum, iPhone + iPad, both orientations. XCTest unit tests + XCUITest; physical devices required for real BLE validation (GATT signaling doesn't work on simulator-paired radios).

### Code layout

| Path | Responsibility |
|---|---|
| `Models/` | `GameState`, `PlayerId`, `Status`, `Phase`, `Grid`, `DieRef` with strict `Codable` matching the frozen schema. |
| `Rules/` | `KnucklebonesRules` port: total/column score, roll/place validity, `canRoll`, status transitions. Test fixtures mirrored from Android tests. |
| `Game/` | `GameHost`, `CpuPlayer`/`CpuClient` + human-feel pacing ports (single-player parity). |
| `Networking/` | `GameLink` (line semantics like `GameLinkImpl`), `MessageCodec` (NAME/ROLL/PLACE/RESTART/STATE), `Handshake`, `PinGenerator`. |
| `BLE/` | `BleConnector`: `CBPeripheralManager` (host) + `CBCentralManager` (client), MTU-aware chunking; `BleGameLink` on the byte-pipe contract. `NSBluetoothAlwaysUsageDescription` in Info.plist. |
| `Audio/` | `SoundManager` over AVFoundation; same `SoundEvent` set (TAP/RATTLE/LAND/WIN/LOSE), mute + loop semantics; reuses checked-in `.wav` files. |
| `Settings/` | `@AppStorage`: player name, mute, animations, auto-roll (3-dot), mirroring Android. |
| `Views/` | `StartView`, `HostingView` (PIN display), `DiscoverView` (device list + scan status), `GameView`, `RollArea`, `TurnPill`, `GameBoard`, `DestroyGhosts`, `Confetti`, `WinnerOverlay`, `DrawOverlay`, `LeaveConfirm`, `FeltBackground`, `GlassCard`, `GoldButton`, `PinDigits`; theme (gold/ivory/glass colors, `cinzel_bold.ttf`). |

### 3D die

`DiceRoller3D` hosts **SceneKit** `SceneView` loading a SceneKit-compatible conversion of `models/dice.glb` (ModelIO / Reality Converter → check in a `.usdz`), replicating `getRotationForFace`, the ~2 s X/Y spin with randomized velocity, camera on +Z, tap gesture, and LAND/RATTLE sound hooks.

**Asset spike (Phase 4, first task):** validate `dice.glb` → loadable model before UI work. Fallback if conversion fails: SceneKit-built die geometry (same behavior and orientation table, no asset dependency).

### iPad

Vertically-stacked boards on every device (column-vs-column comparison is core to play). Regular width: content constrained to ~560 pt and centered. No side-by-side layout.

## Part 4 — Errors, edge cases, testing

### Cross-platform edge cases

- Bluetooth off / permission denied / advertising unsupported / empty scan → clear error banner on the owning platform.
- Wrong PIN → "Wrong code. Try again." on both platforms (Android detection already exists; iOS mirrors it).
- Mid-game drop (`didDisconnectPeripheral` / `onConnectionStateChange` → `onClosed`) → peer-disconnected banner + leave, same as RFCOMM today.
- Oversized line / buffer overflow → fatal read, disconnect, banner (matches existing).
- Foreground-only; no `UIBackgroundModes`. Screen lock / backgrounding during the game degrades to a disconnect handled by the banner.

### Testing

- **Android (JVM):** new pure unit tests for BLE chunk/reassembly framing (radios not required). All existing unit + Maestro suites stay green (RFCOMM path untouched; transport toggle is additive).
- **iOS:** XCTest unit tests for rules port (Android-mirrored fixtures), codec strict decoding, chunk reassembly, handshake. XCUITest on simulator using an in-memory/fake link for the connection→game→overlay choreography.
- **Fakes:** `LocalPipe`/`FakeGamePeer`/`FakeBluetoothConnector`/`FakeSoundManager` equivalents in Swift so simulator and test flows exercise the full loop without radios.
- **Cross-play conformance (physical hardware, scripted choreography doc):** Android↔iOS both host directions; Android↔Android (BLE); iOS↔iOS (BLE). Each pairing: happy path, wrong PIN, mid-game leave, play-again restart.
- Emulators/simulators cannot run real GATT between each other; BLE integration testing is device-only by design.

## Part 5 — Phasing

One contract spec lives in each repo (this one in the Android repo, its mirror in the iOS repo). Sequential implementation plans, each independently testable; the iOS plans are authored and tracked in the iOS repo:

1. **Android BLE transport** *(this repo)* — `BleBytePipe` + `AndroidBleConnector`, framing unit tests, transport toggle UI, Android↔Android BLE happy path on hardware.
2. **iOS core** *(iOS repo)* — project scaffold; `Models`/`Rules`/`Networking`/`Game` ports with unit tests; fake-link game loop runnable on simulator (no radio).
3. **iOS BLE + cross-play** *(iOS repo)* — CoreBluetooth host/client, Info.plist, `BleGameLink`; cross-play matrix on hardware (both host directions).
4. **iOS parity polish** *(iOS repo)* — SceneKit die (asset spike first), audio, settings toggles, iPad sizing, CPU mode, XCUITest, release config.

If any plan oversizes, it splits further (single-player-mode precedent).

## Assets shared unchanged

- `app/src/main/assets/models/dice.glb` (plus a checked-in `.usdz` conversion for iOS).
- `app/src/main/res/raw/tap.wav`, `rattle.wav`, `land.wav`, `win.wav`, `lose.wav`.
- `app/src/main/res/font/cinzel_bold.ttf`.
- Theme colors — the exact values live in `ui/theme/Color.kt` (Gold, Ivory, GlassWhite, GlassBorderGold, DieIvoryLight, …).