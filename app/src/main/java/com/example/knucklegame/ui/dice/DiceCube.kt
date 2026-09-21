package com.example.knucklegame.ui.dice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.knucklegame.R
import com.example.knucklegame.audio.SoundEvent
import com.example.knucklegame.ui.LocalAnimationsEnabled
import com.example.knucklegame.ui.LocalSoundManager
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberScene
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.random.Random

private fun getRotationForFace(face: Int): Rotation = when (face) {
    1 -> Rotation(-90f, 0f, 0f)
    2 -> Rotation(0f, 0f, 0f)
    3 -> Rotation(0f, -90f, 0f)
    4 -> Rotation(0f, 90f, 0f)
    5 -> Rotation(0f, 180f, 0f)
    6 -> Rotation(90f, 0f, 0f)
    else -> Rotation(0f, 0f, 0f)
}

private data class DiceRotation(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun toRotation() = Rotation(x, y, z)
}

@Composable
fun DiceCube(
    value: Int?,
    rolling: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = LocalAnimationsEnabled.current,
) {
    val sound = LocalSoundManager.current
    val diceBase = stringResource(R.string.dice)
    val diceLabel = when {
        rolling -> "$diceBase rolling"
        value != null -> "$diceBase showing $value"
        enabled -> "$diceBase tap to roll"
        else -> diceBase
    }

    val engine = rememberEngine()
    val scene = rememberScene(engine)
    val cameraNode = rememberCameraNode(engine)
    val modelLoader = rememberModelLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, "models/dice.glb")

    var diceRotation by remember {
        mutableStateOf(
            getRotationForFace(Random.nextInt(1, 7)).let {
                DiceRotation(
                    x = it.x,
                    y = it.y,
                    z = it.z,
                )
            }
        )
    }

    /**
     * Camera setup.
     */
    LaunchedEffect(Unit) {
        // Every final die orientation places the rolled face on +Z. Keep the
        // camera on that axis so it looks straight at the result, rather than
        // viewing it from above and accidentally favoring a neighboring face.
        cameraNode.position = Position(x = 0f, y = 0f, z = 3f)

        cameraNode.lookAt(
            Position(
                x = 0f,
                y = 0f,
                z = 0f,
            )
        )
    }

    /**
     * Animation state machine.
     *
     * While rolling, the centered die spins around X and Y. When the game
     * supplies a value, it immediately uses the corresponding face transform.
     * This keeps the animation deliberately simple while preserving the
     * correct visible result for all six values.
     */
    LaunchedEffect(rolling, value, animationsEnabled) {

        if (!animationsEnabled) {
            if (!rolling && value != null) {
                val target = getRotationForFace(value)

                diceRotation = DiceRotation(
                    x = target.x,
                    y = target.y,
                    z = target.z,
                )

                sound.play(SoundEvent.LAND)
            }

            return@LaunchedEffect
        }

        if (rolling) {
            sound.startLoop(SoundEvent.RATTLE)

            try {
                var previousFrameNanos = 0L

                val velocityX = 720f * if (Random.nextBoolean()) 1f else -1f
                val velocityY = 540f * if (Random.nextBoolean()) 1f else -1f

                while (currentCoroutineContext().isActive) {

                    withFrameNanos { frameTimeNanos ->

                        if (previousFrameNanos == 0L) {
                            previousFrameNanos = frameTimeNanos
                            return@withFrameNanos
                        }

                        val deltaTime =
                            ((frameTimeNanos - previousFrameNanos) / 1_000_000_000f)
                                .coerceAtMost(0.05f)

                        previousFrameNanos = frameTimeNanos

                        diceRotation = diceRotation.copy(
                            x = diceRotation.x + velocityX * deltaTime,
                            y = diceRotation.y + velocityY * deltaTime,
                        )
                    }
                }
            } finally {
                sound.stopLoop()
            }

            return@LaunchedEffect
        }

        if (value != null) {
            val targetRotation = getRotationForFace(value)
            diceRotation = DiceRotation(
                x = targetRotation.x,
                y = targetRotation.y,
                z = targetRotation.z,
            )

            /*
             * Play the landing sound after the die has actually
             * finished moving.
             */
            sound.play(SoundEvent.LAND)
        }
    }

    SceneView(
        modifier = modifier
            .size(120.dp)
            .semantics {
                contentDescription = diceLabel
            }
            .testTag("dice")
            .clickable(
                enabled = enabled && !rolling,
            ) {
                sound.play(SoundEvent.TAP)
                onTap()
            },

        engine = engine,
        scene = scene,
        cameraNode = cameraNode,
        modelLoader = modelLoader,

        isOpaque = false,
        // The die's explicit pivot node below is the scene origin. Do not let
        // SceneView add a second automatic content-centering transform.
        autoCenterContent = false,
        cameraManipulator = null,

        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { _, node ->
                if (
                    node is ModelNode &&
                    enabled &&
                    !rolling
                ) {
                    sound.play(SoundEvent.TAP)
                    onTap()
                }
            }
        ),
    ) {
        modelInstance?.let { instance ->
            // Rotate the parent at the world origin. The child translates the
            // off-center GLB so its geometric center sits on that origin.
            // Keeping those transforms on separate nodes prevents the center
            // translation from being rotated into an orbit.
            Node(rotation = diceRotation.toRotation()) {
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 1.5f,
                    centerOrigin = Position(0f, 0f, 0f),
                    apply = {
                        isTouchable = true
                        isHittable = true
                    }
                )
            }
        }
    }
}
