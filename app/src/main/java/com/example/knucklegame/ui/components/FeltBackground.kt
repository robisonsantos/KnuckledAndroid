package com.example.knucklegame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.knucklegame.ui.theme.FeltDark
import com.example.knucklegame.ui.theme.FeltLight
import com.example.knucklegame.ui.theme.FeltMid

@Composable
fun FeltBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(FeltLight, FeltMid, FeltDark),
                    radius = 1400f,
                ),
            ),
        content = content,
    )
}
