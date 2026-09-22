package com.example.knucklegame.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EmeraldColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = PipBrown,
    primaryContainer = GoldDark,
    onPrimaryContainer = PipBrown,
    secondary = Ivory,
    onSecondary = FeltDark,
    background = FeltDark,
    onBackground = Ivory,
    surface = FeltMid,
    onSurface = Ivory,
    surfaceVariant = GlassWhite,
    onSurfaceVariant = Ivory,
    error = Color(0xFFE57373),
    onError = FeltDark,
    errorContainer = Color(0xFF5A2320),
    onErrorContainer = Ivory,
    outline = GlassBorderGold,
)

@Composable
fun KnuckledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EmeraldColorScheme,
        typography = Typography,
        content = content,
    )
}
