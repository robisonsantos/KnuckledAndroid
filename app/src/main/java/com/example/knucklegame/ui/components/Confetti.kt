package com.example.knucklegame.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.knucklegame.ui.LocalAnimationsEnabled
import com.example.knucklegame.ui.theme.FeltLight
import com.example.knucklegame.ui.theme.Gold
import com.example.knucklegame.ui.theme.Ivory
import kotlinx.coroutines.isActive
import kotlin.random.Random

private data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var rot: Float, var vr: Float,
    var size: Float, var color: Color,
)

@Composable
fun Confetti(
    modifier: Modifier = Modifier,
    seed: Int = 42,
    animationsEnabled: Boolean = LocalAnimationsEnabled.current,
) {
    var particles by remember(seed) {
        val rnd = Random(seed)
        val colors = listOf(Gold, Ivory, FeltLight, Color(0xFFE9DFC6), Color(0xFFB8902E))
        mutableStateOf(
            List(80) {
                Particle(
                    x = rnd.nextFloat(), y = -rnd.nextFloat() * 0.3f,
                    vx = (rnd.nextFloat() - 0.5f) * 0.25f,
                    vy = 0.35f + rnd.nextFloat() * 0.5f,
                    rot = rnd.nextFloat() * 360f,
                    vr = (rnd.nextFloat() - 0.5f) * 540f,
                    size = 6f + rnd.nextFloat() * 8f,
                    color = colors[rnd.nextInt(colors.size)],
                )
            }
        )
    }
    if (animationsEnabled) {
        LaunchedEffect(seed) {
            var last = 0L
            while (isActive) {
                val now = withFrameNanos { it }
                if (last == 0L) last = now
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                var alive = false
                for (p in particles) {
                    p.vy += 0.9f * dt          // gravity
                    p.vx *= (1f - 0.4f * dt)   // drag
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.rot += p.vr * dt
                    if (p.y < 1.1f) alive = true
                }
                if (!alive) break
                // Force recompose each frame.
                particles = particles.toList()
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        for (p in particles) {
            if (p.y < 0f || p.y > 1f) continue
            drawCircle(
                color = p.color,
                radius = p.size, // px; sizes 6–14 read well on all densities
                center = Offset(p.x * size.width, p.y * size.height),
                alpha = 0.9f,
            )
        }
    }
}