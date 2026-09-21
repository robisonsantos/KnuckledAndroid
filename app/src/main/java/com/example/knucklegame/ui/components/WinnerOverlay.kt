package com.example.knucklegame.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.knucklegame.R
import com.example.knucklegame.audio.SoundEvent
import com.example.knucklegame.ui.LocalSoundManager
import com.example.knucklegame.ui.theme.FeltDark
import com.example.knucklegame.ui.theme.Gold

@Composable
fun WinnerOverlay(
    winnerName: String,
    winnerScore: Int,
    loserScore: Int,
    isWinner: Boolean,
    onPlayAgain: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sound = LocalSoundManager.current
    var dropped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (dropped) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "trophy-drop",
    )
    LaunchedEffect(Unit) {
        dropped = true
        sound.play(if (isWinner) SoundEvent.WIN else SoundEvent.LOSE)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FeltDark.copy(alpha = 0.85f))
            .testTag("winner-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        if (isWinner) {
            Confetti()
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                if (isWinner) "🏆" else "🎲",
                fontSize = 72.sp,
                modifier = Modifier.scale(scale),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (isWinner) R.string.you_win else R.string.you_lose),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isWinner) {
                    "$winnerScore – $loserScore"
                } else {
                    "$loserScore – $winnerScore"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Gold,
            )
            if (!isWinner) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.wins, winnerName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(32.dp))
            GoldButton(text = stringResource(R.string.play_again), onClick = onPlayAgain)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disconnect))
            }
        }
    }
}
