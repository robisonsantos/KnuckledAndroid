package com.example.knucklegame.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.knucklegame.audio.NoopSoundManager
import com.example.knucklegame.audio.SoundManager
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.settings.Settings
import com.example.knucklegame.ui.components.FeltBackground

@Composable
fun KnuckledApp(
    connectionViewModel: ConnectionViewModel,
    onHostClick: () -> Unit,
    onPlayCpuClick: () -> Unit,
    onFindClick: () -> Unit,
    onSubmitPin: (String) -> Unit,
    soundManager: SoundManager = NoopSoundManager,
    settings: Settings? = null,
    animationsEnabled: Boolean = true,
    rollValue: () -> Int = { (1..6).random() },
    rollDelayMs: Long = 2000L,
) {
    var showHint by remember { mutableStateOf(settings?.onboardingSeen == false) }
    var autoRoll by remember { mutableStateOf(settings?.autoRoll ?: false) }
    CompositionLocalProvider(
        LocalSoundManager provides soundManager,
        LocalAnimationsEnabled provides animationsEnabled,
    ) {
        FeltBackground {
            val connected = connectionViewModel.state as? ConnectionState.Connected
            val route = if (connected != null) "game"
                else connectionViewModel.state::class.simpleName ?: "Start"
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (targetState == "game") {
                        (scaleIn(initialScale = 0.92f, animationSpec = tween(300)) + fadeIn(tween(300))) togetherWith
                            fadeOut(tween(200))
                    } else {
                        (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)))
                    }
                },
                label = "route",
            ) { targetRoute ->
                if (targetRoute == "game") {
                    val activeConnection = connectionViewModel.state as? ConnectionState.Connected
                    if (activeConnection == null) return@AnimatedContent
                    val link = activeConnection.link
                    val myId = if (activeConnection.isHost) PlayerId.HOST else PlayerId.CLIENT
                    val gameViewModel: GameViewModel = viewModel(
                        key = "game-${System.identityHashCode(link)}",
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return GameViewModel(
                                    link = link,
                                    myId = myId,
                                    hostName = if (myId == PlayerId.HOST) connectionViewModel.sanitizedPlayerName else null,
                                    clientName = if (myId == PlayerId.CLIENT) connectionViewModel.sanitizedPlayerName else null,
                                    onPeerDisconnected = { connectionViewModel.onPeerDisconnected() },
                                    rollValue = rollValue,
                                    rollDelayMs = rollDelayMs,
                                ) as T
                            }
                        },
                    )
                    Scaffold(containerColor = Color.Transparent, modifier = Modifier.fillMaxSize()) { inner ->
                        Box(Modifier.fillMaxSize().padding(inner).imePadding()) {
                            GameScreen(
                                viewModel = gameViewModel,
                                onDisconnect = { connectionViewModel.disconnect() },
                                autoRoll = autoRoll,
                                onToggleAutoRoll = {
                                    autoRoll = !autoRoll
                                    settings?.autoRoll = autoRoll
                                },
                            )
                        }
                    }
                } else {
                    Scaffold(containerColor = Color.Transparent, modifier = Modifier.fillMaxSize()) { inner ->
                        Box(Modifier.fillMaxSize().padding(inner).imePadding()) {
                            when (val s = connectionViewModel.state) {
                                is ConnectionState.Start -> StartScreen(
                                    name = connectionViewModel.playerName,
                                    onNameChange = connectionViewModel::onPlayerNameChange,
                                    onHostClick = onHostClick,
                                    onPlayCpuClick = onPlayCpuClick,
                                    onFindClick = onFindClick,
                                    isFakeMode = connectionViewModel.inFakeMode,
                                    onToggleFake = connectionViewModel::toggleFakeMode,
                                    showHint = showHint,
                                    onDismissHint = {
                                        settings?.onboardingSeen = true
                                        showHint = false
                                    },
                                    error = connectionViewModel.errorText,
                                    onDismissError = connectionViewModel::dismissError,
                                )
                                is ConnectionState.Hosting -> HostingScreen(
                                    pin = s.pin,
                                    onCancel = connectionViewModel::cancelCurrent,
                                )
                                is ConnectionState.Discovering -> DiscoverScreen(
                                    devices = connectionViewModel.foundDevices,
                                    onDeviceClick = connectionViewModel::onDeviceSelected,
                                    onCancel = connectionViewModel::cancelCurrent,
                                    status = connectionViewModel.statusText,
                                )
                                is ConnectionState.EnterPin -> EnterPinScreen(
                                    device = s.device,
                                    onConfirm = onSubmitPin,
                                    onCancel = connectionViewModel::cancelCurrent,
                                    status = connectionViewModel.statusText,
                                )
                                is ConnectionState.Connected -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
