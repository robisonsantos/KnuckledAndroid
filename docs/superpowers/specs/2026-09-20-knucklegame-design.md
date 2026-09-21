# KnuckleGame Design

Date: 2026-09-20

A two-player dice board game based on Cult of the Lamb's "Knucklebones", played peer-to-peer over Bluetooth. Implementation is driven by copying and adapting the reference project at `$HOME/AndroidStudioProjects/DiceGame`, which already provides the Bluetooth client/server architecture, the rolling die with 3D model, sound effects, and a debug "fake mode" for emulator testing.

## Game rules

- Two players each have a 3x3 board of three columns (three dice per column max).
- Players alternate turns. A turn is two phases in order: *roll* then *place*.
- On the roll phase, the roller rolls one 6-sided die (server-randomized). The die animates for ~2 seconds.
- On the place phase, the roller must place the shown die in a non-full column of their own board. Drag-to-column and tap-target-column are both supported. The player cannot reroll, skip, or discard a roll, and there is no idle timer.
- A column is scored as the sum over dice values of `value × count(value in that column)`. Reference table:

| Die value | 1 die | 2 dice | 3 dice |
|-----------|-------|--------|--------|
| 1         | 1     | 4      | 9      |
| 2         | 2     | 8      | 18     |
| 3         | 3     | 12     | 27     |
| 4         | 4     | 16     | 36     |
| 5         | 5     | 20     | 45     |
| 6         | 6     | 24     | 54     |

- Per-column score is displayed. Total score is the sum of the three columns.
- **Destruction (same-value only):** when a player places a die, all dice of the placed value in the opponent's *corresponding* column are destroyed; remaining dice fall down. The 3-of-a-kind column wipe from the original game is intentionally NOT implemented (decision: README text is authoritative).
- The game ends when a player fills their entire 3x3 board. The player with the higher score wins; on equal scores the game is a draw with no winner.
- Restart resets both boards and the turn phase; player names are kept. The first player is chosen randomly by the server once per connection session and stays fixed across restarts within that session.

## Architecture

Server/client over Bluetooth. The host is the server and owns all game state and all rules/randomness. The client keeps a copy of the state in memory and re-renders on every `STATE` broadcast. State is reset per session and lives in memory only.

### State model

```kotlin
enum class Status { IN_PROGRESS, FINISHED, DRAW }
enum class PlayerId { HOST, CLIENT }
enum class Phase { IDLE, ROLLING, AWAITING_PLACEMENT }

@Serializable
data class DieRef(val player: PlayerId, val column: Int, val value: Int)

@Serializable
data class GameState(
    val hostName: String,
    val clientName: String,
    val status: Status,
    val currentTurn: PlayerId,
    val phase: Phase,
    val grid: Map<PlayerId, Grid>,   // Grid = 3 columns; each column a bottom-anchored List<Int> (dice 1..6)
    val winner: PlayerId? = null,
    val lastRoll: Int? = null,
    val destroyed: List<DieRef> = emptyList(),  // just-destroyed dice, for the opponent's animation
)
```

- A column is `List<Int>`; placement appends, destruction removes by value (remaining dice fall down).
- Column and total scores are a pure function of the grid (see `KnucklebonesRules`), shared by server (authority) and client (display).
- `destroyed` is cleared at the start of each turn so the client can animate just the most recent destruction.

### Protocol

Line-framed over `GameLink` (DiceGame `Protocol` framing). Messages:

- `NAME:<name>` — client → server, on connect.
- `ROLL` — roller → server, requests a roll.
- `PLACE:<col>` — roller → server, `col` is 0..2, places the just-rolled die.
- `RESTART` — either player → server, only valid when status is FINISHED or DRAW.
- `STATE:<json>` — server → both, full `GameState` after every change.

Server validation rules (invalid messages are ignored):
- `ROLL` only when it is the sender's turn, status is IN_PROGRESS, and phase is IDLE.
- `PLACE` only when phase is AWAITING_PLACEMENT, sender is the roller, `col` in 0..2, and that column has fewer than 3 dice.
- `RESTART` only when status is FINISHED or DRAW.

### Server-side game host

Adapted from DiceGame's `GameHost`:
- `connect()` wires `GameLink.onLine` to a dispatcher for NAME/ROLL/PLACE/RESTART.
- `ROLL` → `beginRoll` (phase ROLLING), broadcast, 2s delay thread (injected for tests), `completeRoll` picks a random 1..6, sets `lastRoll`, phase AWAITING_PLACEMENT, broadcast.
- `PLACE` → validate, `place` mutates grid, computes destruction, recomputes turn/phase, evaluates board-full end; broadcast.
- The `GameHost` instance runs the whole session on the server. The host player rolls locally on touch; the client player rolls by sending `ROLL`. Both roles place dice by sending `PLACE`.
- Restart resets both boards to the next game. The first player stays the same for the whole session (set once when the client connects).

## UI

Portrait Compose. Reuses DiceGame visual language and components (FeltBackground, GlassCard, GoldButton, top bar, Confetti, WinnerOverlay, theme, fonts).

### Screens

1. **Start / connection** — copied from DiceGame verbatim (renamed): player name entry, Host (shows PIN), Discover (device list), Enter Pin, plus debug fake-mode toggle. Picking either role requires a non-blank player name.
2. **Game** — three-zone layout:
   - Top: opponent name, total score, opponent 3x3 grid with per-column score chips.
   - Middle: turn indicator ("Your turn" / opponent's name) and the dice roll area.
   - Bottom: own name, total score, own 3x3 grid with per-column score chips.
   - Orientations are column-aligned (own columns line up vertically with opponent columns).
3. **End overlay** — Win (Confetti), Lose, or Draw overlay with "Play Again" and "Disconnect" actions.

### Turn flow interactions

- Phase IDLE + current turn: the die shows its last value, or an idle die before the first roll. Tapping the die sends `ROLL` (or rolls locally as host).
- Phase ROLLING: die animates for ~2s with rattle loop sound; both devices show the motion.
- Phase AWAITING_PLACEMENT: die settles (land sound); roller can drag it onto a column or tap any non-full column (highlighted). Client sends `PLACE`. Opponent sees the grid update, including a fade/fall animation of destroyed dice (from `destroyed`).
- After placement a fresh turn starts; a new die appears in the roll area.
- Peer-disconnected mid-game behaves like DiceGame (banner + return to start).

### Reused resources

- 3D die: DiceGame `DiceCube` composable + `assets/models/dice.glb`, kept alive by `keepRules/rules.keep`.
- Sounds: `rattle.wav` (rolling loop), `land.wav` (settle), `tap.wav`, `win.wav`, `lose.wav`.

## Infrastructure (copied from DiceGame, renamed to KnuckleGame)

- Bluetooth: `Protocol`, `GameLink`/`GameLinkImpl`, `Handshake`, `PinGenerator`, `BluetoothConnector`, `AndroidBluetoothConnector`, `FakeBluetoothConnector` (in-memory loopback used by fake mode). Service name `KnuckleGame`.
- Fake mode: debug-only toggle toggling the real connector ↔ fake connector, and a `FakeGamePeer` bot that plays automatically against a human or another bot, so the game is fully playable and testable on a single emulator.
- Audio: `SoundManager`/`AndroidSoundManager`.
- Gradle deltas vs. the scaffold: add `kotlinx-serialization` plugin + json, `sceneview`, `material-icons-extended`, `core-splashscreen`, `lifecycle-viewmodel-compose` (mirroring DiceGame's `libs.versions.toml`); add `buildConfig` to the app.

## Testing

- **Unit (JVM, TDD-first):**
  - `KnucklebonesRules`: column/total scoring per the README table; multi-die multiplication; same-value destruction in the opponent's corresponding column; dice fall after destruction; full-column placement rejection; board-full ends the game; winner vs. draw resolution; `beginRoll`/`completeRoll`/`place` phase transitions; restart resets boards but keeps names.
  - `GameMessages`: en/decode of NAME/ROLL/PLACE/RESTART/STATE, corrupt-frame rejection, unknown prefixes ignored.
  - `Protocol`/`Handshake`/`GameLink`/`PinGenerator`: ported from DiceGame.
  - `GameHost` over a fake `GameLink`: full simulated game reaches an end state; invalid messages ignored; state broadcast after every mutation.
  - Fake mode session: host + client through `FakeBluetoothConnector`/`FakeGamePeer` complete a game.
- **Maestro (emulator, fake mode):** start → name → host with fake peer → connect → a complete game against `FakeGamePeer` → assert win/lose/draw overlay → Play Again resets → Disconnect returns to start. Use roll injection (fixed roll sequence) to force a deterministic outcome where needed.
- **Manual:** final pass on two physical devices over real Bluetooth.