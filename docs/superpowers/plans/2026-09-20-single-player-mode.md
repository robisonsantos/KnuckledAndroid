# Single-Player Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a release-ready single-player mode where the human plays a strong CPU opponent (expectimax search over the game rules) via a **Play vs CPU** button on the Start screen.

**Architecture:** The human is always HOST (runs the existing `GameHost`); the CPU is a protocol peer — `CpuClient` — on the other end of an in-process pipe (`LocalConnector`), mirroring how `FakeGamePeer`/`FakeBluetoothConnector` work today. The AI itself is a pure, deterministic function `CpuPlayer.chooseColumn(state)` that maximizes expected final score using depth-limited expectimax with memoization. `GameViewModel`, `GameHost`, `GameScreen`, and the whole protocol stack are unchanged.

**Spec:** `docs/superpowers/specs/2026-09-20-single-player-mode-design.md`

**Tech Stack:** Kotlin, JUnit4 (unit tests), Maestro (UI flows), Gradle (`./gradlew`).

---

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `app/src/main/java/com/example/knucklegame/game/CpuPlayer.kt` | Pure expectimax AI decision function | Create |
| `app/src/main/java/com/example/knucklegame/game/CpuClient.kt` | CPU as a `GameLink` peer + human-feel pacing + `CpuPacing` | Create |
| `app/src/main/java/com/example/knucklegame/bluetooth/LocalPipe.kt` | In-process pipe → pair of loopback `GameLink`s | Create |
| `app/src/main/java/com/example/knucklegame/bluetooth/LocalConnector.kt` | `BluetoothConnector` that starts a CPU session | Create |
| `app/src/main/java/com/example/knucklegame/bluetooth/FakeBluetoothConnector.kt` | Refactor to use `LocalPipe` (DRY) | Modify |
| `app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt` | `startSinglePlayer()` + CPU pacing knobs | Modify |
| `app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt` | StartScreen **Play vs CPU** button | Modify |
| `app/src/main/java/com/example/knucklegame/ui/KnuckleGameApp.kt` | Wire button callback through | Modify |
| `app/src/main/java/com/example/knucklegame/MainActivity.kt` | Route click (no perms) + `maestro_single` hook | Modify |
| `app/src/main/res/values/strings.xml` | `play_vs_cpu` string | Modify |
| `app/src/test/java/com/example/knucklegame/game/CpuPlayerTest.kt` | AI unit tests | Create |
| `app/src/test/java/com/example/knucklegame/game/CpuClientTest.kt` | Full-game integration over a fake link | Create |
| `app/src/test/java/com/example/knucklegame/bluetooth/LocalConnectorTest.kt` | Pipe-path integration test | Create |
| `.maestro/06-single-player.yaml` | UI flow | Create |

---

### Task 1: `CpuPlayer` — the expectimax AI core

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/game/CpuPlayer.kt`
- Test: `app/src/test/java/com/example/knucklegame/game/CpuPlayerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/knucklegame/game/CpuPlayerTest.kt`:

```kotlin
package com.example.knucklegame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuPlayerTest {

    private fun awaitingTurn(
        lastRoll: Int,
        mine: Grid,
        theirs: Grid,
        currentTurn: PlayerId = PlayerId.CLIENT,
    ): GameState =
        GameState(
            hostName = "Host",
            clientName = CpuPlayer.NAME,
            status = Status.IN_PROGRESS,
            currentTurn = currentTurn,
            phase = Phase.AWAITING_PLACEMENT,
            grid = mapOf(PlayerId.HOST to theirs, PlayerId.CLIENT to mine),
            lastRoll = lastRoll,
        )

    private fun openColumns(state: GameState, player: PlayerId): Set<Int> {
        val grid = state.grid[player]!!
        return grid.indices.filter { !KnucklebonesRules.columnFull(grid, it) }.toSet()
    }

    private fun emptyGrid(): Grid = List(3) { emptyList() }

    @Test
    fun `always returns a legal open column`() {
        val mine: Grid = listOf(listOf(2, 3), listOf(6), emptyList())
        val theirs: Grid = listOf(listOf(1), listOf(4, 4), listOf(5, 5, 5))
        val state = awaitingTurn(4, mine, theirs)
        val chosen = CpuPlayer.chooseColumn(state)
        assertTrue("chosen $chosen must be open", chosen in openColumns(state, PlayerId.CLIENT))
    }

    @Test
    fun `is deterministic for the same state`() {
        val mine: Grid = listOf(listOf(3, 5), listOf(2), emptyList())
        val theirs: Grid = listOf(listOf(6), emptyList(), listOf(1, 1))
        val state = awaitingTurn(2, mine, theirs)
        assertEquals(CpuPlayer.chooseColumn(state), CpuPlayer.chooseColumn(state))
    }

    @Test
    fun `destroys the opponents strong same-value pair`() {
        val mine = emptyGrid()
        val theirs: Grid = listOf(listOf(6, 6), emptyList(), emptyList())
        val state = awaitingTurn(6, mine, theirs)
        assertEquals(0, CpuPlayer.chooseColumn(state))
    }

    @Test
    fun `keeps its own pair together to complete a triple`() {
        val mine: Grid = listOf(listOf(5, 5), emptyList(), emptyList())
        val theirs = emptyGrid()
        val state = awaitingTurn(5, mine, theirs)
        assertEquals(0, CpuPlayer.chooseColumn(state))
    }

    @Test
    fun `never places into a full column`() {
        val mine: Grid = listOf(listOf(1, 2, 3), emptyList(), emptyList())
        val theirs = emptyGrid()
        val state = awaitingTurn(6, mine, theirs)
        val chosen = CpuPlayer.chooseColumn(state)
        assertTrue("chosen $chosen must not be the full column 0", chosen != 0)
    }

    @Test
    fun `fills the board to win when the endgame favours it`() {
        val mine: Grid = listOf(listOf(3, 3), listOf(4, 4, 4), listOf(2, 2))
        val theirs: Grid = listOf(listOf(2, 2), listOf(1), listOf(1))
        val state = awaitingTurn(3, mine, theirs)
        assertEquals(0, CpuPlayer.chooseColumn(state))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.CpuPlayerTest"`

Expected: FAIL — compile error, `unresolved reference: CpuPlayer` (class does not exist yet).

- [ ] **Step 3: Write the minimal implementation**

Create `app/src/main/java/com/example/knucklegame/game/CpuPlayer.kt`:

```kotlin
package com.example.knucklegame.game

object CpuPlayer {
    const val NAME = "CPU"

    private const val WIN = 1_000_000.0
    private const val LOCK_BONUS = 0.10
    private const val RISK_WEIGHT = 0.05

    /** Pick the column for the already-rolled die that maximises expected final score. */
    fun chooseColumn(state: GameState, me: PlayerId = PlayerId.CLIENT): Int {
        val grid = state.grid[me] ?: return 0
        val open = grid.indices.filter { !KnucklebonesRules.columnFull(grid, it) }
        if (open.isEmpty()) return 0
        val search = Search(me)
        var best = open.first()
        var bestValue = Double.NEGATIVE_INFINITY
        for (c in open) {
            val after = KnucklebonesRules.place(state, me, c)
            val v = search.value(after, depthLimit(state))
            if (v > bestValue) {
                bestValue = v
                best = c
            }
        }
        return best
    }

    /** Deeper search the closer the boards are to full. */
    private fun depthLimit(state: GameState): Int {
        val placed = state.grid.values.sumOf { g -> g.sumOf { it.size } }
        return when {
            placed >= 12 -> 6
            placed >= 6 -> 4
            else -> 3
        }
    }

    /** Expectimax: max nodes are the CPU's placements, min nodes the human's, chance nodes the die. */
    private class Search(private val me: PlayerId) {
        private val memo = HashMap<String, Double>()

        fun value(state: GameState, depth: Int): Double =
            when (state.status) {
                Status.FINISHED -> if (state.winner == me) WIN else -WIN
                Status.DRAW -> 0.0
                Status.IN_PROGRESS -> searchValue(state, depth)
            }

        private fun searchValue(state: GameState, depth: Int): Double {
            if (depth <= 0) return evaluate(state)
            val key = stateKey(state, depth)
            memo[key]?.let { return it }
            val result = when (state.phase) {
                Phase.IDLE -> {
                    val roller = state.currentTurn
                    var sum = 0.0
                    for (v in 1..6) {
                        val rolled = KnucklebonesRules.completeRoll(
                            KnucklebonesRules.beginRoll(state, roller),
                            roller,
                            v,
                        )
                        sum += value(rolled, depth)
                    }
                    sum / 6.0
                }
                Phase.AWAITING_PLACEMENT -> {
                    val mover = state.currentTurn
                    val moverGrid = state.grid[mover]!!
                    val open = moverGrid.indices.filter { !KnucklebonesRules.columnFull(moverGrid, it) }
                    val children = open.map { value(KnucklebonesRules.place(state, mover, it), depth - 1) }
                    if (mover == me) children.max() else children.min()
                }
                Phase.ROLLING -> evaluate(state)
            }
            memo[key] = result
            return result
        }

        private fun evaluate(state: GameState): Double {
            val mine = state.grid[me]!!
            val theirs = state.grid[state.opponentOf(me)]!!
            var e = (KnucklebonesRules.totalScore(mine) - KnucklebonesRules.totalScore(theirs)).toDouble()
            for (i in mine.indices) {
                if (KnucklebonesRules.columnFull(mine, i)) {
                    e += KnucklebonesRules.columnScore(mine[i]) * LOCK_BONUS
                }
                if (!KnucklebonesRules.columnFull(theirs, i)) {
                    for ((value, count) in mine[i].groupingBy { it }.eachCount()) {
                        if (count >= 2) e -= value * count * count * RISK_WEIGHT
                    }
                }
            }
            return e
        }

        private fun stateKey(state: GameState, depth: Int): String {
            val host = state.grid[PlayerId.HOST]!!
            val client = state.grid[PlayerId.CLIENT]!!
            return "$depth|${state.currentTurn}|${state.phase}|${state.lastRoll}|" +
                host.joinToString(";") { it.joinToString(",") } + "|" +
                client.joinToString(";") { it.joinToString(",") }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.CpuPlayerTest"`

Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/game/CpuPlayer.kt app/src/test/java/com/example/knucklegame/game/CpuPlayerTest.kt
git commit -m "game: smart CPU placement search (expectimax over rules)"
```

---

### Task 2: `CpuClient` — CPU protocol peer with human-like pacing

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/game/CpuClient.kt`
- Modify: `app/src/main/java/com/example/knucklegame/game/FakeGamePeer.kt:1-30` (use the shared `delayed` helper)
- Test: `app/src/test/java/com/example/knucklegame/game/CpuClientTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/knucklegame/game/CpuClientTest.kt`:

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.FakeGameLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuClientTest {

    private fun playFullGame(firstPlayer: PlayerId): GameState {
        val hostLink = FakeGameLink()
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val gh = GameHost(
            link = hostLink,
            hostName = "Human",
            rollValue = { (counter.getAndIncrement() % 6) + 1 },
            rollDelayMs = 0L,
            firstPlayer = { firstPlayer },
            onState = {},
        )
        gh.connect()
        val forwarded = intArrayOf(0)
        lateinit var botLink: com.example.knucklegame.bluetooth.GameLink
        fun pumpToBot() {
            while (forwarded[0] < hostLink.sent.size) {
                val hostLine = hostLink.sent[forwarded[0]++]
                if (GameMessages.decodeState(hostLine) != null) {
                    try { botLink.onLine(hostLine) } catch (_: Exception) {}
                }
            }
        }
        botLink = object : com.example.knucklegame.bluetooth.GameLink {
            override var onLine: (String) -> Unit = {}
            override var onClosed: () -> Unit = {}
            override fun send(line: String) {
                hostLink.receive(line)
                pumpToBot()
            }
            override fun close() { onClosed() }
        }
        runCpuClient(botLink, preRollDelayMs = 0L, thinkDelay = { 0L })
        pumpToBot()
        var guard = 0
        while (gh.state.status == Status.IN_PROGRESS && guard < 2000) {
            val s = gh.state
            if (KnucklebonesRules.canRoll(s, PlayerId.HOST)) {
                gh.hostRoll()
                pumpToBot()
            } else if (s.phase == Phase.AWAITING_PLACEMENT && s.currentTurn == PlayerId.HOST) {
                gh.hostPlace(FakeGamePeer.firstOpenColumn(s.grid[PlayerId.HOST]!!))
                pumpToBot()
            } else {
                Thread.sleep(20)
                pumpToBot()
            }
            guard += 1
        }
        return gh.state
    }

    @Test
    fun `cpu plays a full session and fills its own board`() {
        // CPU goes first, so it is the one who fills the board and ends the game.
        val state = playFullGame(PlayerId.CLIENT)
        assertTrue(
            "game should finish (state=$state)",
            state.status == Status.FINISHED || state.status == Status.DRAW,
        )
        if (state.status == Status.FINISHED) assertNotNull(state.winner)
        assertEquals(CpuPlayer.NAME, state.clientName)
        assertEquals(9, state.grid[PlayerId.CLIENT]!!.flatten().size)
    }

    @Test
    fun `cpu keeps playing after a restart`() {
        val hostLink = FakeGameLink()
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val gh = GameHost(
            link = hostLink,
            hostName = "Human",
            rollValue = { (counter.getAndIncrement() % 6) + 1 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.CLIENT },
            onState = {},
        )
        gh.connect()
        val forwarded = intArrayOf(0)
        lateinit var botLink: com.example.knucklegame.bluetooth.GameLink
        fun pumpToBot() {
            while (forwarded[0] < hostLink.sent.size) {
                val hostLine = hostLink.sent[forwarded[0]++]
                if (GameMessages.decodeState(hostLine) != null) {
                    try { botLink.onLine(hostLine) } catch (_: Exception) {}
                }
            }
        }
        botLink = object : com.example.knucklegame.bluetooth.GameLink {
            override var onLine: (String) -> Unit = {}
            override var onClosed: () -> Unit = {}
            override fun send(line: String) {
                hostLink.receive(line)
                pumpToBot()
            }
            override fun close() { onClosed() }
        }
        runCpuClient(botLink, preRollDelayMs = 0L, thinkDelay = { 0L })
        pumpToBot()

        fun playWhileInProgress() {
            var guard = 0
            while (gh.state.status == Status.IN_PROGRESS && guard < 2000) {
                val s = gh.state
                if (KnucklebonesRules.canRoll(s, PlayerId.HOST)) {
                    gh.hostRoll()
                    pumpToBot()
                } else if (s.phase == Phase.AWAITING_PLACEMENT && s.currentTurn == PlayerId.HOST) {
                    gh.hostPlace(FakeGamePeer.firstOpenColumn(s.grid[PlayerId.HOST]!!))
                    pumpToBot()
                } else {
                    Thread.sleep(20)
                    pumpToBot()
                }
                guard += 1
            }
        }

        playWhileInProgress()
        assertTrue("first game should finish", gh.state.status != Status.IN_PROGRESS)
        gh.restart()
        pumpToBot()
        assertTrue("restart should be in progress", gh.state.status == Status.IN_PROGRESS)
        assertEquals(CpuPlayer.NAME, gh.state.clientName)
        playWhileInProgress()
        assertTrue("second game should finish", gh.state.status != Status.IN_PROGRESS)
        assertEquals(9, gh.state.grid[PlayerId.CLIENT]!!.flatten().size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.CpuClientTest"`

Expected: FAIL — compile error, `unresolved reference: runCpuClient`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/knucklegame/game/CpuClient.kt`:

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.bluetooth.GameLink
import kotlin.random.Random

/** Pacing knobs that make the CPU feel human. All injectable in tests. */
object CpuPacing {
    const val PRE_ROLL_MS = 600L
    fun naturalThink(): Long = Random.nextLong(700L, 1101L)
}

private fun delayed(delayMs: Long, block: () -> Unit) {
    Thread {
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
        }
        try {
            block()
        } catch (_: Exception) {
            // link may be closed
        }
    }.apply {
        name = "cpu-ai"
        isDaemon = true
        start()
    }
}

/** CPU playing the client side: sends NAME, rolls on its turn, places via [CpuPlayer]. */
fun runCpuClient(
    link: GameLink,
    preRollDelayMs: Long = CpuPacing.PRE_ROLL_MS,
    thinkDelay: () -> Long = CpuPacing::naturalThink,
    choose: (GameState) -> Int = { CpuPlayer.chooseColumn(it) },
) {
    link.send(GameMessages.encodeName(CpuPlayer.NAME))
    link.onLine = cpu@{ line ->
        val state = GameMessages.decodeState(line) ?: return@cpu
        if (KnucklebonesRules.canRoll(state, PlayerId.CLIENT)) {
            delayed(preRollDelayMs) { link.send(GameMessages.encodeRoll()) }
        }
        if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.CLIENT) {
            val col = choose(state)
            delayed(thinkDelay()) { link.send(GameMessages.encodePlace(col)) }
        }
    }
}
```

Refactor `FakeGamePeer.kt` to share the `delayed` helper. In `app/src/main/java/com/example/knucklegame/game/FakeGamePeer.kt`, delete the private `delayed` function (lines 15-31) so both files share the top-level `delayed` defined in `CpuClient.kt` (same package `com.example.knucklegame.game`). The new content of FakeGamePeer.kt is:

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.bluetooth.GameLink

object FakeGamePeer {
    const val NAME = "FakePeer"
    const val PIN = "1234"
    const val HOST_NAME = "FakeHost"
    const val REACT_DELAY_MS = 150L

    fun firstOpenColumn(grid: Grid): Int =
        grid.indices.first { !KnucklebonesRules.columnFull(grid, it) }
}

/** Bot playing the client side: sends NAME, rolls on its turn, places in the first open column. */
fun runFakeClient(link: GameLink) {
    link.send(GameMessages.encodeName(FakeGamePeer.NAME))
    link.onLine = client@{ line ->
        val state = GameMessages.decodeState(line) ?: return@client
        if (KnucklebonesRules.canRoll(state, PlayerId.CLIENT)) {
            delayed(FakeGamePeer.REACT_DELAY_MS) { link.send(GameMessages.encodeRoll()) }
        }
        if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.CLIENT) {
            val col = FakeGamePeer.firstOpenColumn(state.grid[PlayerId.CLIENT]!!)
            delayed(FakeGamePeer.REACT_DELAY_MS) { link.send(GameMessages.encodePlace(col)) }
        }
    }
}

/** Bot playing the host side via a [GameHost], for when the app connects as a client in fake mode. */
fun runFakeHost(link: GameLink): GameHost {
    val counter = java.util.concurrent.atomic.AtomicInteger(0)
    val rollValue: () -> Int = { (counter.getAndIncrement() % 6) + 1 }
    lateinit var host: GameHost
    host = GameHost(
        link = link,
        hostName = FakeGamePeer.HOST_NAME,
        rollValue = rollValue,
        rollDelayMs = 50L,
        onState = { state ->
            if (KnucklebonesRules.canRoll(state, PlayerId.HOST)) {
                delayed(50L) { host.hostRoll() }
            }
            if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.HOST) {
                val col = FakeGamePeer.firstOpenColumn(state.grid[PlayerId.HOST]!!)
                delayed(50L) { host.hostPlace(col) }
            }
        },
    )
    host.connect()
    return host
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.CpuClientTest" --tests "com.example.knucklegame.game.FakeGamePeerTest"`

Expected: PASS — both integration tests green (fake peer still works after the `delayed` refactor).

- [ ] **Step 5: Run the full unit suite (regression)**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS — all unit tests, no fallout from the shared `delayed` refactor.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/game/CpuClient.kt app/src/main/java/com/example/knucklegame/game/FakeGamePeer.kt app/src/test/java/com/example/knucklegame/game/CpuClientTest.kt
git commit -m "game: CPU client peer with human-like pacing"
```

---

### Task 3: `LocalPipe` + `LocalConnector` (in-process session for single-player)

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/bluetooth/LocalPipe.kt`
- Create: `app/src/main/java/com/example/knucklegame/bluetooth/LocalConnector.kt`
- Modify: `app/src/main/java/com/example/knucklegame/bluetooth/FakeBluetoothConnector.kt` (use `LocalPipe`)
- Test: `app/src/test/java/com/example/knucklegame/bluetooth/LocalConnectorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/knucklegame/bluetooth/LocalConnectorTest.kt`:

```kotlin
package com.example.knucklegame.bluetooth

import com.example.knucklegame.game.CpuPlayer
import com.example.knucklegame.game.GameHost
import com.example.knucklegame.game.KnucklebonesRules
import com.example.knucklegame.game.Phase
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.game.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalConnectorTest {

    @Test
    fun `a host game over the local connector finishes with the CPU on the other side`() {
        val connector = LocalConnector(preRollDelayMs = 0L, thinkDelay = { 0L })
        val appLink = connector.listen("0000")

        val host = GameHost(
            link = appLink,
            hostName = "Human",
            rollValue = { 3 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.HOST },
            onState = {},
        )
        host.connect()

        // Drive the human side (a plain first-open-column player) until the game ends.
        var guard = 0
        while (host.state.status == Status.IN_PROGRESS && guard < 2000) {
            val s = host.state
            if (KnucklebonesRules.canRoll(s, PlayerId.HOST)) {
                host.hostRoll()
            } else if (s.phase == Phase.AWAITING_PLACEMENT && s.currentTurn == PlayerId.HOST) {
                val open = s.grid[PlayerId.HOST]!!
                    .indices.first { !KnucklebonesRules.columnFull(s.grid[PlayerId.HOST]!!, it) }
                host.hostPlace(open)
            } else {
                Thread.sleep(20)
            }
            guard += 1
        }
        assertTrue(
            "game should finish (steps=$guard, state=${host.state})",
            host.state.status != Status.IN_PROGRESS,
        )
        assertEquals(CpuPlayer.NAME, host.state.clientName)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.bluetooth.LocalConnectorTest"`

Expected: FAIL — compile error, `unresolved reference: LocalConnector`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/knucklegame/bluetooth/LocalPipe.kt`:

```kotlin
package com.example.knucklegame.bluetooth

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.LinkedBlockingQueue

/** Builds a pair of in-process [GameLink]s connected through pipes, so a bot can play both
 * sides of a session with no transport. */
object LocalPipe {

    /** Returns two [GameLink]s whose streams form a loopback pair. */
    fun gameLinkPair(): Pair<GameLink, GameLink> {
        val (first, second) = pipe()
        return Pair(
            GameLinkImpl.create(first.first, first.second),
            GameLinkImpl.create(second.first, second.second),
        )
    }

    private fun pipe(): Pair<Pair<InputStream, OutputStream>, Pair<InputStream, OutputStream>> {
        val firstInput = PipedInputStream(4096)
        val rawSecondOut = PipedOutputStream(firstInput)
        val secondInput = PipedInputStream(4096)
        val rawFirstOut = PipedOutputStream(secondInput)
        // Wrap outputs so all Piped writes happen on a long-lived thread, avoiding
        // Piped's "Write end dead" when short-lived bot threads die.
        return Pair(
            Pair(firstInput, AsyncOutputStream(rawFirstOut)),
            Pair(secondInput, AsyncOutputStream(rawSecondOut)),
        )
    }

    private class AsyncOutputStream(private val delegate: OutputStream) : OutputStream() {
        private val queue = LinkedBlockingQueue<ByteArray>()
        @Volatile
        private var closed = false
        private val writer = Thread {
            try {
                while (true) {
                    val data = queue.take()
                    if (data.isEmpty() && closed) break
                    if (data.isNotEmpty()) {
                        delegate.write(data)
                        delegate.flush()
                    }
                    if (data.isEmpty() && closed) break
                }
            } catch (_: InterruptedException) {
            } catch (_: IOException) {
            } finally {
                try { delegate.close() } catch (_: IOException) {}
            }
        }.apply {
            name = "local-pipe-writer-${System.identityHashCode(this)}"
            isDaemon = true
            start()
        }

        override fun write(b: Int) {
            if (closed) return
            queue.put(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) return
            val copy = b.copyOfRange(off, off + len)
            queue.put(copy)
        }

        override fun flush() {
            // delegate flush happens on the writer thread after each chunk
        }

        override fun close() {
            if (closed) return
            closed = true
            queue.put(ByteArray(0))
        }
    }
}
```

Create `app/src/main/java/com/example/knucklegame/bluetooth/LocalConnector.kt`:

```kotlin
package com.example.knucklegame.bluetooth

import com.example.knucklegame.game.CpuPacing
import com.example.knucklegame.game.runCpuClient

/** [BluetoothConnector] for single-player: hosts a local session with the CPU as the peer. */
class LocalConnector(
    private val preRollDelayMs: Long = CpuPacing.PRE_ROLL_MS,
    private val thinkDelay: () -> Long = CpuPacing::naturalThink,
) : BluetoothConnector {

    override fun listen(pin: String): GameLink {
        val (humanLink, cpuLink) = LocalPipe.gameLinkPair()
        runCpuClient(cpuLink, preRollDelayMs = preRollDelayMs, thinkDelay = thinkDelay)
        return humanLink
    }

    override fun connect(device: DeviceInfo, pin: String): GameLink =
        throw UnsupportedOperationException("Single-player does not connect to devices")

    override fun discover(): List<DeviceInfo> =
        throw UnsupportedOperationException("Single-player has nothing to discover")
}
```

Refactor `app/src/main/java/com/example/knucklegame/bluetooth/FakeBluetoothConnector.kt` to use `LocalPipe`. Replace the whole file with:

```kotlin
package com.example.knucklegame.bluetooth

import com.example.knucklegame.game.FakeGamePeer
import com.example.knucklegame.game.runFakeClient
import com.example.knucklegame.game.runFakeHost

class FakeBluetoothConnector : BluetoothConnector {

    private val peers = mutableListOf<GameLink>()

    override fun listen(pin: String): GameLink {
        require(pin == FakeGamePeer.PIN) { "Invalid PIN" }
        val (hostLink, clientLink) = LocalPipe.gameLinkPair()
        peers.add(clientLink)
        runFakeClient(clientLink)
        return hostLink
    }

    override fun connect(device: DeviceInfo, pin: String): GameLink {
        require(pin == FakeGamePeer.PIN) { "Invalid PIN" }
        val (clientLink, hostLink) = LocalPipe.gameLinkPair()
        peers.add(hostLink)
        runFakeHost(hostLink)
        return clientLink
    }

    override fun discover(): List<DeviceInfo> =
        listOf(DeviceInfo("Fake Peer", "00:11:22:33:44:55"))
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.bluetooth.LocalConnectorTest"`

Expected: PASS.

- [ ] **Step 5: Run the full unit suite (regression)**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS — the `FakeBluetoothConnector` refactor (now via `LocalPipe`) keeps the whole suite green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/bluetooth/LocalPipe.kt app/src/main/java/com/example/knucklegame/bluetooth/LocalConnector.kt app/src/main/java/com/example/knucklegame/bluetooth/FakeBluetoothConnector.kt app/src/test/java/com/example/knucklegame/bluetooth/LocalConnectorTest.kt
git commit -m "bluetooth: local pipe connector for single-player"
```

---

### Task 4: `Play vs CPU` entry point (release-ready UI wiring)

**Files:**
- Modify: `app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt`
- Modify: `app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt:63-180`
- Modify: `app/src/main/java/com/example/knucklegame/ui/KnuckleGameApp.kt`
- Modify: `app/src/main/java/com/example/knucklegame/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add `startSinglePlayer()` and pacing knobs to `ConnectionViewModel`**

In `app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt`:

Add imports (already present: `com.example.knucklegame.bluetooth.LocalConnector` is new; `com.example.knucklegame.game.CpuPacing` is new):

```kotlin
import com.example.knucklegame.bluetooth.LocalConnector
import com.example.knucklegame.game.CpuPacing
```

Add fields next to `var selectedDevice ...`:

```kotlin
    /** CPU pacing knobs (debug/test overrides, defaults feel human). */
    var cpuPreRollDelayMs: Long = CpuPacing.PRE_ROLL_MS
    var cpuThinkDelay: () -> Long = CpuPacing::naturalThink
```

Add the method after `onHostClicked()`:

```kotlin
    fun startSinglePlayer() {
        val sanitized = GameMessages.sanitizeName(playerName)
        if (sanitized.isBlank()) {
            showError("Enter your name")
            return
        }
        sanitizedPlayerName = sanitized
        isHost = true
        errorText = null
        try {
            val connector = LocalConnector(preRollDelayMs = cpuPreRollDelayMs, thinkDelay = cpuThinkDelay)
            val link = connector.listen("single")
            onConnected(link, peer = null, isHost = true)
        } catch (t: Throwable) {
            Log.e(TAG, "single-player start failed", t)
            resetForError()
            showError(t.message ?: "Could not start single-player game.")
        }
    }
```

- [ ] **Step 2: Add the string resource**

In `app/src/main/res/values/strings.xml`, add after `<string name="join_game">Join a game</string>`:

```xml
    <string name="play_vs_cpu">Play vs CPU</string>
```

- [ ] **Step 3: Add the StartScreen button**

In `app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt`:

Add the parameter to `StartScreen` (after `onHostClick`):

```kotlin
    onPlayCpuClick: () -> Unit,
```

Add the button as the primary CTA, before the `Host a game` button (must keep the existing `host-button` test tag):

```kotlin
        GoldButton(
            text = stringResource(R.string.play_vs_cpu),
            onClick = onPlayCpuClick,
            modifier = Modifier.testTag("single-player-button"),
        )
        Spacer(Modifier.height(12.dp))
```

- [ ] **Step 4: Wire the callback through `KnuckleGameApp`**

In `app/src/main/java/com/example/knucklegame/ui/KnuckleGameApp.kt`, add the parameter to the function signature (after `onHostClick`):

```kotlin
    onPlayCpuClick: () -> Unit,
```

And pass it to `StartScreen` (in the `is ConnectionState.Start -> StartScreen(...)` call, after `onHostClick = onHostClick,`):

```kotlin
                                    onPlayCpuClick = onPlayCpuClick,
```

- [ ] **Step 5: Route the click in `MainActivity` (no Bluetooth permissions) + maestro hook**

In `app/src/main/java/com/example/knucklegame/MainActivity.kt`:

In the `KnuckleGameApp(...)` call, after `onHostClick = ::onHostClick,` add:

```kotlin
                    onPlayCpuClick = { connectionViewModel.startSinglePlayer() },
```

In `onCreate`, after the existing `maestroFake` block, add the single-player maestro hook:

```kotlin
        val maestroSingle = BuildConfig.DEBUG && intent.getStringExtra("maestro_single") == "1"
        if (maestroSingle) {
            if (connectionViewModel.playerName.isBlank()) connectionViewModel.onPlayerNameChange("Maestro")
            if (intent.getStringExtra("maestro_fast") == "1") {
                connectionViewModel.cpuPreRollDelayMs = 0L
                connectionViewModel.cpuThinkDelay = { 0L }
            }
            connectionViewModel.startSinglePlayer()
        }
```

- [ ] **Step 6: Build to verify everything compiles**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt app/src/main/java/com/example/knucklegame/ui/KnuckleGameApp.kt app/src/main/java/com/example/knucklegame/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "ui: Play vs CPU entry from start screen (release)"
```

---

### Task 5: Maestro single-player UI flow

**Files:**
- Create: `.maestro/06-single-player.yaml`

- [ ] **Step 1: Write the flow**

Create `.maestro/06-single-player.yaml` (modeled on `.maestro/05-full-game.yaml`):

```yaml
appId: com.example.knucklegame
---
- launchApp:
    clearState: true
    arguments:
      maestro_single: "1"
      maestro_seed: "42"
      maestro_fast: "1"
- extendedWaitUntil:
    visible: CPU
    timeout: 15000
# Deterministic single-player game: seeded rolls, zero CPU delay, CPU plays the other side.
# Inner steps are optional so iterations after the game ends (overlay up, no "Your turn")
# no-op instead of failing; the final assert is the real verdict.
# 6 iterations x 3 turns (left/middle/right columns, tapped by label) is ample for a 3x3 board.
- repeat:
    times: 6
    commands:
      - extendedWaitUntil:
          visible: Your turn
          timeout: 30000
          optional: true
      - tapOn:
          text: roll die
          optional: true
      - extendedWaitUntil:
          visible: Tap a column to place the die
          timeout: 15000
          optional: true
      - tapOn:
          text: own column 0
          optional: true
      - extendedWaitUntil:
          visible: Your turn
          timeout: 30000
          optional: true
      - tapOn:
          text: roll die
          optional: true
      - extendedWaitUntil:
          visible: Tap a column to place the die
          timeout: 15000
          optional: true
      - tapOn:
          text: own column 1
          optional: true
      - extendedWaitUntil:
          visible: Your turn
          timeout: 30000
          optional: true
      - tapOn:
          text: roll die
          optional: true
      - extendedWaitUntil:
          visible: Tap a column to place the die
          timeout: 15000
          optional: true
      - tapOn:
          text: own column 2
          optional: true
- extendedWaitUntil:
    visible: Play again
    timeout: 60000
```

- [ ] **Step 2: Confirm an emulator is available and the app is installed**

Run: `maestro list_devices` (via MCP). If no Android emulator is running, ask the user to boot one first.

Then build + install the debug app:

Run: `./gradlew :app:installDebug`

Expected: install succeeds.

- [ ] **Step 3: Run the single-player flow**

Run the flow with the Maestro MCP `run` tool (choose the emulator's `device_id` from `list_devices`, files: `[".maestro/06-single-player.yaml"]`).

Expected: PASS — the game auto-starts vs CPU, the human places dice across columns, and the **Play again** overlay appears.

- [ ] **Step 4: Commit**

```bash
git add .maestro/06-single-player.yaml
git commit -m "test: single-player maestro flow"
```

---

### Task 6: Timing check, tuning, and full regression

**Files:**
- Possibly modify: `app/src/main/java/com/example/knucklegame/game/CpuPlayer.kt` (depth/weights)
- Possibly modify: `app/src/main/java/com/example/knucklegame/game/CpuClient.kt` (pacing ranges)

- [ ] **Step 1: Manual on-device pacing & responsiveness check**

Play a full single-player game on the emulator (tap **Play vs CPU**): the CPU should pause ~0.6s before its roll and ~0.7–1.1s before placing, and its decisions should feel fluid (no visible freeze in the UI).

If a decision ever stalls (the UI feels frozen while the CPU "thinks"), reduce `depthLimit` in `CpuPlayer.kt` (e.g. `12->11`, `6->5`, `4->3`) until it is fluid, then re-run Task 1's unit tests.

- [ ] **Step 2: Re-run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 3: Regression — existing PvP / fake-mode maestro flows**

If an emulator is available, run `.maestro/01-host-game.yaml`, `.maestro/04-play-game.yaml`, and `.maestro/05-full-game.yaml` (Maestro MCP `run` with the emulator `device_id`).

Expected: all PASS — the `LocalPipe`/`FakeBluetoothConnector` refactor did not break fake mode.

- [ ] **Step 4: Commit any tuning changes**

If Task 6 changed source in Step 1, commit:

```bash
git add app/src/main/java/com/example/knucklegame/game/CpuPlayer.kt app/src/main/java/com/example/knucklegame/game/CpuClient.kt
git commit -m "game: tune CPU search depth for responsive play"
```

If no changes were needed, skip this step (nothing new to commit).

---

## Self-Review Notes

**Spec coverage:**
- Strong AI via expectimax + memoization + adaptive depth + eval (locked-bonus, cluster-risk) → Task 1.
- Human-feel pacing (pre-roll, randomized think, injectable/zeroable) → Tasks 2 & 4 (`CpuPacing`, `LocalConnector` knobs, maestro fast hook).
- Release-ready **Play vs CPU** button, no Bluetooth perms → Task 4.
- Random first turn → unchanged `GameHost` default (spec: reuse).
- Restart flow → unchanged (verified in Task 2 `cpu keeps playing after a restart`).
- Untouched PvP stack → guaranteed by construction; regression in Tasks 2, 3, 6.
- Testing: unit AI (Task 1), CPU peer integration (Task 2), pipe integration (Task 3), Maestro flow (Task 5), regression (Task 6).

**Known tuning item:** `depthLimit` thresholds and `LOCK_BONUS`/`RISK_WEIGHT` are implementation-tuned; Task 6 validates responsiveness and makes the exact adjusts the spec's "Open tuning item" calls for.