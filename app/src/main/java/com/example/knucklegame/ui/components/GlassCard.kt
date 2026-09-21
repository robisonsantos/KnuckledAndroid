package com.example.knucklegame.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.knucklegame.ui.theme.GlassBorderGold
import com.example.knucklegame.ui.theme.GlassWhite
import com.example.knucklegame.ui.theme.Gold

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border by animateColorAsState(
        targetValue = if (highlighted) Gold else GlassBorderGold,
        label = "glass-border",
    )
    Surface(
        modifier = modifier
            .then(if (highlighted) Modifier.shadow(12.dp, RoundedCornerShape(16.dp)) else Modifier)
            .semantics { stateDescription = if (highlighted) "active" else "inactive" },
        shape = RoundedCornerShape(16.dp),
        color = GlassWhite,
        border = BorderStroke(1.dp, border),
    ) {
        Column(content = content)
    }
}
