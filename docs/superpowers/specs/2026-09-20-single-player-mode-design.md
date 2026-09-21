# Single-Player Mode Design

Date: 2026-09-20

Adds a single-player mode to KnuckleGame: a human plays the existing game against a strong AI opponent that chooses its placements using the game rules. The mode is a released, user-facing feature (not DEBUG-gated) entered from a **Play vs CPU** button on the Start screen.

Decision: **one strong AI** (no difficulty levels), whose strength comes from search (expectimax) over the real rules rather than hand-coded heuristics alone.

## Goals

- A released single-player mode, reachable on the Start screen without Bluetooth, PINs, or permissions.
- A CPU opponent that plays strong Knucklebones using the actual game rules.
- CPU behavior feels human-like: a short, slightly varied "thinking" delay before it commits.
- Reuses the existing session stack (GameViewModel / GameHost / GameScreen / protocol / restart / disconnect) unchanged.

## Non-goals (YAGNI)

- No difficulty levels / tuning UI.
- No "CPU first vs player first" choice — first turn stays random, matching PvP.
- No new screens or game-flow changes. The CPU plays on a standard session as the CLIENT.
- The AI makes no decisions over Bluetooth or in real PvP; it is used only by single-player.

## Context: how the game works and where the AI plugs in

Knucklebones: each player has a 3x3 board (three columns, up to three dice each). A turn is *roll then place*. Placing value `v` in column `i` adds to your score and destroys every die of value `v` in the opponent's column `i`. The game ends when a player fills their board; higher score wins, ties draw. Full rules and scoring in `docs/superpowers/specs/2026-09-20-knucklegame-design.md`.

Today the app is PvP over Bluetooth. The host runs `GameHost`, which owns authoritative state; the client sends `NAME`/`ROLL`/`PLACE`/`RESTART` over a `GameLink` and renders `STATE` broadcasts. `FakeBluetoothConnector` + `FakeGamePeer` already prove the pattern of a bot playing the client side of a local pipe.

In single-player the human is always HOST (runs `GameHost`), and the CPU is the CLIENT on the other end of a local pipe — exactly the fake-peer arrangement, minus the DEBUG gate and the trivial "first open column" bot.

## Architecture

### New files

| File | Responsibility |
|---|---|
| `game/CpuPlayer.kt` | Pure, deterministic AI decision function: `fun chooseColumn(state: GameState, me: PlayerId = CLIENT): Int`. No Android dependencies, no I/O, no state. |
| `game/CpuClient.kt` | The CPU as a protocol peer over a `GameLink`, mirroring `runFakeClient`: sends `NAME:CPU`, sends `ROLL` on its turn, and on AWAITING_PLACEMENT computes `chooseColumn` and sends `PLACE`. Owns the human-feel pacing (see below). Delay is injectable and defaults to zero in tests. |
| `bluetooth/LocalConnector.kt` | `BluetoothConnector` implementation for single-player. `listen(pin)` needn't accept a real PIN; it creates a pipe pair (`PipedInputStream`/`PipedOutputStream` wrapped in the same `AsyncOutputStream` scheme as `FakeBluetoothConnector`), hands the client link to a running `CpuClient`, and returns the host link to the app. Not DEBUG-gated. |

The shared pipe/`AsyncOutputStream` machinery currently lives inside `FakeBluetoothConnector`; if reuse is cleaner, extract it to a small internal helper both connectors use. Otherwise duplicate a compact version in `LocalConnector`.

### Changes to existing files

| File | Change |
|---|---|
| `ui/ConnectionViewModel.kt` | Add `startSinglePlayer()`: sanitize the player name (same blank-name check as `onHostClicked`), `isHost = true`, call `LocalConnector().listen(...)`, and transition straight to `ConnectionState.Connected(link, peer = null, isHost = true)`. No PIN, no search threads. |
| `ui/ConnectionScreens.kt` (Start screen) | Add a **Play vs CPU** button next to Host/Find, wired to `startSinglePlayer`. |
| `MainActivity.kt` | Route the Play vs CPU click straight to `startSinglePlayer()` — no Bluetooth permissions, no enable/discoverable dance. |

### Untouched

`GameViewModel`, `GameHost`, `GameScreen`, `GameMessages`, `Protocol`, `GameLinkImpl`, restart, disconnect, peer-disconnect handling. Because the AI is the CLIENT, all host-side behavior (roll randomness, first-player randomness, rules validation, broadcasts) is existing, tested code.

## The AI: expectimax over the rules

Knucklebones is stochastic, so strong play maximizes **expected final score difference** (terminal payoff), assuming a perfect opponent. `chooseColumn` evaluates each legal column for the already-rolled `state.lastRoll`:

- **Max nodes** (AI to choose): value = max over open columns of `value(place(state, col))`.
- **Chance nodes** (opponent's upcoming roll): average over the six equally likely roll outcomes.
- **Min nodes** (opponent chooses): worst case for us (optimum for them).
- **Terminal nodes**: true end-of-game outcome (win/loss/draw).
- **Depth limit** with a static evaluation when no terminal is reached.

### Evaluation function

`totalScore(mine) - totalScore(theirs)` is the primary signal (it is the exact terminal payoff, so it is a principled cutoff evaluation), refined with two small terms:

- bonus for columns already locked/full (their score is unreachable by destruction),
- penalty for same-value stacks the opponent can wipe on their next turn (an undestroyed 3-of-a-kind pile is a liability, not just an asset).

### Search budget

- **Adaptive depth** by game stage: shallow early (few plies), near-full search near the endgame when fewer dice remain. Tuned so a decision completes comfortably under a second on a mid-range device.
- **Memoization** (transposition table) keyed on the compact board state, since the same 3-column positions recur across roll outcomes.

### Determinism and testability

`chooseColumn` is a pure function of `GameState` — identical input yields identical output. Pacing and RNG live outside it (`CpuClient`). Unit tests assert exact column choices on canonical tactical positions, legality across all open columns, and stability given the same state.

## Human-feel pacing

The CPU's decisions are instant; the *feel* comes from delays applied in `CpuClient` via an injectable delay provider with these defaults:

- before requesting its roll: ~500–700ms (pausing like a person tapping the die),
- after seeing the die, before placing: a randomized ~700–1100ms "thinking" gap.

Tests inject a fixed delay (`0` for unit/UI tests), matching the existing `rollDelayMs` / `maestro_fast` pattern.

## Session lifecycle

1. User taps **Play vs CPU** → `ConnectionViewModel.startSinglePlayer()` → local pipe created, CPU client started, state = Connected(host).
2. `GameViewModel` host branch creates `GameHost` (identical to PvP hosting). CPU sends `NAME:CPU`; `GameLinkImpl` buffers lines until `onLine` is attached, so ordering is safe (the proof already exists via the fake peer).
3. Game proceeds exactly like PvP. First turn is random (`GameHost` default). Auto-roll applies to the human as today.
4. Play Again: human taps the overlay → RESTART → `GameHost` resets → CPU reacts to the fresh state. No special handling.
5. Disconnect → returns to Start like PvP. A local pipe has no realistic async failure, so the peer-disconnected overlay should not spuriously trigger.

## Edge cases & errors

- Blank player name → same "Enter your name" error as Host/Find.
- Illegal CPU move can't happen by construction (rules-validated `chooseColumn` + host-side validation); if one ever were proposed, `GameHost` ignores it as it does for any peer.
- Local pipe failures (writes after close) are swallowed by the same try/catch the fake-peer threads use; the human's own link is the app's session.

## Testing

- **Unit (JVM) — `CpuPlayerTest`:** exact `chooseColumn` on canonical positions (destroy a strong opponent pair vs. cap your own pair, avoid scattering a threatened same-value stack, finish off a filling column, endgame terminal choices); determinism; legality on every open column.
- **Unit (JVM) — `CpuClientTest`** (mirrors `FakeGamePeerTest`): full simulated game between `GameHost` and `CpuClient` over a pipe completes, every CPU move is legal, restart works, and the CPU outplaces "first open column" on a scripted board.
- **Maestro (emulator):** new intent alias auto-starts single-player with a seeded RNG and zeroed CPU delay; assert the CPU rolls and places, the human plays, and the game reaches the end overlay. Reuse the existing maestro hook pattern.
- **Regression:** all existing unit and PvP Maestro flows stay green (no shared-path behavior changes).

## Open tuning item

Exact `chooseColumn` search depth and the two eval weights are implementation-tuned numbers, validated by the tactical unit tests and on-device timing — not fixed ahead of time.