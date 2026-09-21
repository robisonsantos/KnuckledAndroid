package com.example.knucklegame.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.knucklegame.R
import com.example.knucklegame.ui.theme.FeltDark
import com.example.knucklegame.ui.theme.Gold

@Composable
fun DrawOverlay(
    scoreA: Int,
    scoreB: Int,
    onPlayAgain: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FeltDark.copy(alpha = 0.85f))
            .testTag("draw-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text("🎲", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.draw),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.draw_score_banner, scoreA, scoreB),
                style = MaterialTheme.typography.headlineMedium,
                color = Gold,
            )
            Spacer(Modifier.height(32.dp))
            GoldButton(text = stringResource(R.string.play_again), onClick = onPlayAgain)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disconnect))
            }
        }
    }
}
