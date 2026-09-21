package com.example.knucklegame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.knucklegame.ui.theme.DisplayFont
import com.example.knucklegame.ui.theme.GlassBorderGold
import com.example.knucklegame.ui.theme.GlassWhite
import com.example.knucklegame.ui.theme.Gold
import com.example.knucklegame.ui.theme.Ivory
import kotlinx.coroutines.delay

@Composable
fun PinDigitsDisplay(pin: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pin.padEnd(4, ' ').take(4).forEach { ch ->
            Box(
                Modifier
                    .size(64.dp)
                    .background(GlassWhite, RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorderGold, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ch.toString(),
                    fontFamily = DisplayFont,
                    fontSize = 32.sp,
                    color = Gold,
                )
            }
        }
    }
}

@Composable
fun PinDigitsInput(
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    shakeKey: Int? = null,
) {
    var pin by remember { mutableStateOf("") }
    var amp by remember { mutableFloatStateOf(0f) }
    val focusRequester = remember { FocusRequester() }
    // Null initial key: no shake/clear on first composition. Parent bumps the
    // key (0, 1, 2, …) on every wrong-code status, which shakes and clears.
    LaunchedEffect(shakeKey) {
        if (shakeKey != null) {
            amp = 16f
            repeat(5) {
                delay(50)
                amp = -amp * 0.6f
            }
            amp = 0f
            pin = ""
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier
            .graphicsLayer { translationX = amp }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusRequester.requestFocus() },
            )
    ) {
        // Invisible text field captures keyboard input; tiles render the digits.
        // It fills the Box (alpha 0) so it has real bounds for focus and
        // accessibility/tooling, while the tile Row renders on top of it.
        BasicTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .testTag("pin-input")
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Gold),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { i ->
                val ch = pin.getOrNull(i)?.toString() ?: ""
                Box(
                    Modifier
                        .size(56.dp)
                        .background(GlassWhite, RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (ch.isNotEmpty()) Gold else GlassBorderGold,
                            RoundedCornerShape(12.dp),
                        )
                        .testTag("pin-digit-$i"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ch,
                        fontSize = 28.sp,
                        color = Ivory,
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontFamily = DisplayFont),
                    )
                }
            }
        }
    }
    LaunchedEffect(pin) {
        if (pin.length == 4) onConfirm(pin)
    }
}
