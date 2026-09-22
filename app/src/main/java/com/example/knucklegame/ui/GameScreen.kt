package com.example.knucklegame.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.knucklegame.R
import com.example.knucklegame.game.DieRef
import com.example.knucklegame.game.GameState
import com.example.knucklegame.game.Grid
import com.example.knucklegame.game.KnucklebonesRules
import com.example.knucklegame.game.Phase
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.game.Status
import com.example.knucklegame.ui.components.DiceGameTopBar
import com.example.knucklegame.ui.components.DrawOverlay
import com.example.knucklegame.ui.components.GlassCard
import com.example.knucklegame.ui.components.WinnerOverlay
import com.example.knucklegame.ui.dice.DiceCube
import com.example.knucklegame.ui.theme.GlassBorderGold
import com.example.knucklegame.ui.theme.GlassWhite
import com.example.knucklegame.ui.theme.Gold
import com.example.knucklegame.ui.theme.Ivory
import kotlinx.coroutines.delay

private const val RESULT_OVERLAY_DELAY_MS = 1_200L

@Suppress("UNUSED") // Wired to GameViewModel (Task 11); kept for the nav graph in KnuckledApp.
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onDisconnect: () -> Unit,
    autoRoll: Boolean = false,
    onToggleAutoRoll: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val error by viewModel.errorText.collectAsState()
    val peerGone by viewModel.peerDisconnected.collectAsState()
    val sound = LocalSoundManager.current
    val muted by sound.muted.collectAsState()
    GameScreenContent(
        state = state,
        myId = viewModel.myId,
        onDiceTap = { viewModel.onDiceTap() },
        onPlaceColumn = { viewModel.onPlaceColumn(it) },
        onPlayAgain = { viewModel.onPlayAgain() },
        onDisconnect = { viewModel.disconnect(); onDisconnect() },
        onLeave = { viewModel.disconnect(); onDisconnect() },
        muted = muted,
        onToggleMute = { sound.setMuted(!muted) },
        peerDisconnected = peerGone,
        errorText = error,
        autoRoll = autoRoll,
        onToggleAutoRoll = onToggleAutoRoll,
    )
}

@Composable
fun GameScreen(
    state: GameState?,
    myId: PlayerId,
    onDiceTap: () -> Unit,
    onPlaceColumn: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    onDisconnect: () -> Unit,
    onLeave: () -> Unit,
    muted: Boolean = false,
    onToggleMute: () -> Unit = {},
    peerDisconnected: Boolean = false,
    errorText: String? = null,
    autoRoll: Boolean = false,
    onToggleAutoRoll: () -> Unit = {},
) {
    GameScreenContent(
        state = state,
        myId = myId,
        onDiceTap = onDiceTap,
        onPlaceColumn = onPlaceColumn,
        onPlayAgain = onPlayAgain,
        onDisconnect = onDisconnect,
        onLeave = onLeave,
        muted = muted,
        onToggleMute = onToggleMute,
        peerDisconnected = peerDisconnected,
        errorText = errorText,
        autoRoll = autoRoll,
        onToggleAutoRoll = onToggleAutoRoll,
    )
}

@Composable
private fun GameScreenContent(
    state: GameState?,
    myId: PlayerId,
    onDiceTap: () -> Unit,
    onPlaceColumn: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    onDisconnect: () -> Unit,
    onLeave: () -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    peerDisconnected: Boolean,
    errorText: String?,
    autoRoll: Boolean = false,
    onToggleAutoRoll: () -> Unit = {},
) {
    var showResultOverlay by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(state?.currentTurn, state?.status) {
        if (state?.status == Status.IN_PROGRESS && state.currentTurn == myId) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            try { vibrator?.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
        }
    }
    LaunchedEffect(state?.status) {
        showResultOverlay = false
        if (state?.status == Status.FINISHED || state?.status == Status.DRAW) {
            delay(RESULT_OVERLAY_DELAY_MS)
            showResultOverlay = true
        }
    }
    LaunchedEffect(state, autoRoll) {
        val gridsNonEmpty = state?.grid?.values?.any { cols -> cols.any { it.isNotEmpty() } } == true
        if (autoRoll && state != null && state.status == Status.IN_PROGRESS &&
            state.phase == Phase.IDLE && state.currentTurn == myId && gridsNonEmpty
        ) {
            onDiceTap()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DiceGameTopBar(
                leading = {
                    TextButton(onClick = { showLeaveConfirm = true }, modifier = Modifier.testTag("leave")) {
                        Text(stringResource(R.string.leave))
                    }
                },
                trailing = {
                    IconButton(onClick = onToggleMute, modifier = Modifier.testTag("mute")) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(if (muted) R.string.unmute else R.string.mute),
                            tint = Gold,
                        )
                    }
                    IconButton(onClick = onToggleAutoRoll, modifier = Modifier.testTag("auto-roll")) {
                        Icon(
                            Icons.Filled.Casino,
                            contentDescription = if (autoRoll) "Auto-roll on" else "Auto-roll off",
                            tint = if (autoRoll) Gold else Color.Gray,
                        )
                    }
                },
            )
            if (state == null) {
                Text(stringResource(R.string.waiting_for_state))
            } else {
                val peerId = state.opponentOf(myId)
                GameBoard(
                    isMine = false,
                    name = state.playerName(peerId),
                    grid = state.grid[peerId]!!,
                    destroyed = state.destroyed.filter { it.player == peerId },
                    active = state.currentTurn == peerId && state.status == Status.IN_PROGRESS,
                    onColumnTap = null,
                    modifier = Modifier.testTag("peer-board"),
                )
                Spacer(Modifier.height(14.dp))
                RollArea(
                    state = state,
                    myId = myId,
                    canRoll = KnucklebonesRules.canRoll(state, myId),
                    canPlace = state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == myId,
                    onDiceTap = onDiceTap,
                    onPlaceColumn = onPlaceColumn,
                    peerId = peerId,
                )
                Spacer(Modifier.height(14.dp))
                GameBoard(
                    isMine = true,
                    name = state.playerName(myId),
                    grid = state.grid[myId]!!,
                    destroyed = state.destroyed.filter { it.player == myId },
                    active = state.currentTurn == myId && state.status == Status.IN_PROGRESS,
                    onColumnTap = onPlaceColumn,
                    modifier = Modifier.testTag("own-board"),
                )
            }
            if (peerDisconnected || errorText != null) {
                Spacer(Modifier.height(15.dp))
                GlassCard(modifier = Modifier.fillMaxWidth().testTag("error-banner")) {
                    Text(
                        errorText ?: stringResource(R.string.peer_disconnected),
                        modifier = Modifier.padding(12.dp),
                        color = Ivory,
                    )
                }
            }
        }
        if (showLeaveConfirm) {
            AlertDialog(
                modifier = Modifier.testTag("leave-confirm"),
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text(stringResource(R.string.leave_confirm_title)) },
                text = { Text(stringResource(R.string.leave_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLeaveConfirm = false
                            onLeave()
                        },
                        modifier = Modifier.testTag("confirm-leave"),
                    ) {
                        Text(stringResource(R.string.leave))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLeaveConfirm = false },
                        modifier = Modifier.testTag("stay"),
                    ) {
                        Text(stringResource(R.string.stay))
                    }
                },
            )
        }
        if (state != null && showResultOverlay) {
            when (state.status) {
                Status.FINISHED -> {
                    val winner = state.winner
                    WinnerOverlay(
                        winnerName = winner?.let { state.playerName(it) } ?: "",
                        winnerScore = winner?.let { KnucklebonesRules.totalScore(state.grid[it]!!) } ?: 0,
                        loserScore = winner?.let { KnucklebonesRules.totalScore(state.grid[state.opponentOf(it)]!!) } ?: 0,
                        isWinner = winner == myId,
                        onPlayAgain = onPlayAgain,
                        onDisconnect = onDisconnect,
                    )
                }
                Status.DRAW -> DrawOverlay(
                    scoreA = KnucklebonesRules.totalScore(state.grid[PlayerId.HOST]!!),
                    scoreB = KnucklebonesRules.totalScore(state.grid[PlayerId.CLIENT]!!),
                    onPlayAgain = onPlayAgain,
                    onDisconnect = onDisconnect,
                )
                Status.IN_PROGRESS -> Unit
            }
        }
    }
}

@Composable
private fun RollArea(
    state: GameState,
    myId: PlayerId,
    canRoll: Boolean,
    canPlace: Boolean,
    onDiceTap: () -> Unit,
    onPlaceColumn: (Int) -> Unit,
    peerId: PlayerId,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TurnPill(state = state, myId = myId, peerId = peerId)
        Spacer(Modifier.height(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics { contentDescription = "roll die" },
        ) {
            // SurfaceView (SceneView) renders above the translucent result
            // overlay, so remove the 3D die once the game is over and keep a
            // same-size Spacer so the layout doesn't shift underneath it.
            if (state.status == Status.IN_PROGRESS) {
                DiceCube(
                    value = state.lastRoll,
                    rolling = state.phase == Phase.ROLLING,
                    enabled = canRoll,
                    onTap = onDiceTap,
                )
            } else {
                Spacer(Modifier.size(120.dp))
            }
        }
        if (canPlace) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.place_hint), style = MaterialTheme.typography.bodySmall, color = Ivory)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TurnPill(state: GameState, myId: PlayerId, peerId: PlayerId) {
    val text = when {
        state.phase == Phase.ROLLING -> stringResource(R.string.rolling)
        state.status == Status.FINISHED || state.status == Status.DRAW -> ""
        state.currentTurn == myId -> stringResource(R.string.your_turn)
        else -> stringResource(R.string.turn_other, state.playerName(state.currentTurn))
    }
    if (text.isEmpty()) return
    Surface(
        shape = CircleShape,
        color = GlassWhite,
        border = BorderStroke(1.dp, GlassBorderGold),
        modifier = Modifier.testTag("turn-pill"),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** 3x3 board with column score chips. `onColumnTap` is set only for the player's own placeable board. */
@Composable
private fun GameBoard(
    isMine: Boolean,
    name: String,
    grid: Grid,
    destroyed: List<DieRef>,
    active: Boolean,
    onColumnTap: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val score = KnucklebonesRules.totalScore(grid)
    val own = isMine
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (own) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 3) {
                    val full = grid[col].size >= KnucklebonesRules.COLUMN_SIZE
                    val canTap = onColumnTap != null && !full && active
                    val target = destroyed.filter { it.column == col }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.testTag((if (own) "own-col-" else "peer-col-") + col),
                    ) {
                        ColumnScoreChip(
                            value = KnucklebonesRules.columnScore(grid[col]),
                            tag = (if (own) "own-score-" else "peer-score-") + col,
                        )
                        Spacer(Modifier.height(4.dp))
                        DieColumn(
                            dice = grid[col],
                            destroyed = target,
                            placeable = canTap,
                            onTap = {
                                onColumnTap?.invoke(col)
                            },
                            cellTag = (if (own) "own-cell-" else "peer-cell-") + col + "-",
                            columnLabel = (if (own) "own column " else "peer column ") + col,
                            anchorTop = isMine,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Gold)
                Text(
                    "$score",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Ivory,
                    modifier = Modifier.testTag(if (own) "score-mine" else "score-peer"),
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Gold)
                Text(
                    "$score",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Ivory,
                    modifier = Modifier.testTag(if (own) "score-mine" else "score-peer"),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 3) {
                    val full = grid[col].size >= KnucklebonesRules.COLUMN_SIZE
                    val canTap = onColumnTap != null && !full && active
                    val target = destroyed.filter { it.column == col }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.testTag((if (own) "own-col-" else "peer-col-") + col),
                    ) {
                        DieColumn(
                            dice = grid[col],
                            destroyed = target,
                            placeable = canTap,
                            onTap = {
                                onColumnTap?.invoke(col)
                            },
                            cellTag = (if (own) "own-cell-" else "peer-cell-") + col + "-",
                            columnLabel = (if (own) "own column " else "peer column ") + col,
                            anchorTop = isMine,
                        )
                        Spacer(Modifier.height(4.dp))
                        ColumnScoreChip(
                            value = KnucklebonesRules.columnScore(grid[col]),
                            tag = (if (own) "own-score-" else "peer-score-") + col,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScoreChip(value: Int, tag: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 18.dp)
            .background(GlassWhite, RoundedCornerShape(6.dp))
            .testTag(tag),
    ) {
        Text("$value", style = MaterialTheme.typography.labelSmall, color = Gold)
    }
}

@Composable
private fun DieColumn(
    dice: List<Int>,
    destroyed: List<DieRef>,
    placeable: Boolean,
    onTap: () -> Unit,
    cellTag: String,
    columnLabel: String,
    anchorTop: Boolean,
) {
    val base = Modifier.size(52.dp).padding(3.dp)
    val emptyColor = GlassWhite.copy(alpha = 0.25f)
    val borderColor by animateColorAsState(
        targetValue = if (placeable) Gold else GlassBorderGold,
        label = "col-border",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(2.dp)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .semantics { contentDescription = columnLabel }
            .clickable(enabled = placeable) { onTap() }
            .testTag("column"),
    ) {
        // Outward stacking from the middle: own (bottom) board is top-anchored
        // (oldest die closest to the middle, at the top); peer (top) board is
        // bottom-anchored with oldest at the bottom (closest to the middle),
        // newest stacking on top. See ColumnDisplay for the tested mapping.
        val rows = if (anchorTop) {
            ColumnDisplay.ownColumnTopToBottom(dice)
        } else {
            ColumnDisplay.topColumnTopToBottom(dice)
        }
        for (row in 0 until 3) {
            val dieValue = rows[row]
            if (dieValue != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .then(base)
                        .background(GlassWhite, RoundedCornerShape(8.dp)),
                ) {
                    Text("$dieValue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ivory)
                }
            } else {
                Box(
                    Modifier
                        .then(base)
                        .background(emptyColor, RoundedCornerShape(8.dp)),
                ) {}
            }
        }
    }
    DestroyGhosts(destroyed = destroyed)
}

@Composable
private fun DestroyGhosts(destroyed: List<DieRef>) {
    if (destroyed.isEmpty()) return
    var gone by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (gone) 0f else 1f, label = "ghost")
    LaunchedEffect(destroyed) {
        delay(500L)
        gone = true
    }
    if (gone) return
    Row {
        repeat(destroyed.size) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .padding(2.dp)
                    .background(Color.Red.copy(alpha = 0.35f * alpha), RoundedCornerShape(8.dp)),
            ) {
                Text("×", color = Color.White)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
