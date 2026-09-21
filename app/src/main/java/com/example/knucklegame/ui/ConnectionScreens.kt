package com.example.knucklegame.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.knucklegame.BuildConfig
import com.example.knucklegame.R
import com.example.knucklegame.bluetooth.DeviceInfo
import com.example.knucklegame.ui.components.GlassCard
import com.example.knucklegame.ui.components.GoldButton
import com.example.knucklegame.ui.components.GoldSecondaryButton
import com.example.knucklegame.ui.components.PinDigitsDisplay
import com.example.knucklegame.ui.components.PinDigitsInput
import com.example.knucklegame.ui.theme.DieIvoryLight
import com.example.knucklegame.ui.theme.DisplayFont
import com.example.knucklegame.ui.theme.Gold

@Composable
fun StartScreen(
    name: String,
    onNameChange: (String) -> Unit,
    onHostClick: () -> Unit,
    onPlayCpuClick: () -> Unit,
    onFindClick: () -> Unit,
    isFakeMode: Boolean,
    onToggleFake: () -> Unit,
    showHint: Boolean,
    onDismissHint: () -> Unit,
    error: String? = null,
    onDismissError: () -> Unit = {},
) {
    val sound = LocalSoundManager.current
    val muted by sound.muted.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚂", fontSize = 56.sp, color = DieIvoryLight)
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = DisplayFont,
            color = Gold,
            modifier = Modifier.testTag("start-title"),
        )
        Spacer(Modifier.height(16.dp))
        if (showHint) {
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("hint-card")) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.onboarding_hint),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = onDismissHint) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.dismiss))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.your_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("name-field"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = com.example.knucklegame.ui.theme.GlassBorderGold,
                focusedLabelColor = Gold,
                cursorColor = Gold,
            ),
        )
        Spacer(Modifier.height(24.dp))
        GoldButton(
            text = stringResource(R.string.play_vs_cpu),
            onClick = onPlayCpuClick,
            modifier = Modifier.testTag("single-player-button"),
        )
        Spacer(Modifier.height(12.dp))
        GoldButton(
            text = stringResource(R.string.host_game),
            onClick = onHostClick,
            modifier = Modifier.testTag("host-button"),
        )
        Spacer(Modifier.height(12.dp))
        GoldSecondaryButton(
            text = stringResource(R.string.join_game),
            onClick = onFindClick,
            modifier = Modifier.testTag("join-button"),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { sound.setMuted(!muted) },
                modifier = Modifier.testTag("mute"),
            ) {
                Icon(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(if (muted) R.string.unmute else R.string.mute),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (BuildConfig.DEBUG) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onToggleFake).testTag("fake-switch"),
                ) {
                    Text(stringResource(R.string.fake_link), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = isFakeMode, onCheckedChange = { onToggleFake() })
                }
            }
        }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (isFakeMode) R.string.fake_link_on else R.string.fake_link_off),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        error?.let { message ->
            Spacer(Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("error-banner")) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }
}

@Composable
fun HostingScreen(pin: String, onCancel: () -> Unit = {}) {
    val animEnabled = LocalAnimationsEnabled.current
    val alpha = if (!animEnabled) {
        1f
    } else {
        val pulse = rememberInfiniteTransition(label = "host-pulse")
        pulse.animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "a").value
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.pairing_code), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        PinDigitsDisplay(pin = pin, modifier = Modifier.testTag("pin-display"))
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.waiting_for_device),
            modifier = Modifier.alpha(alpha).testTag("hosting-status"),
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
fun DiscoverScreen(
    devices: List<DeviceInfo>,
    onDeviceClick: (DeviceInfo) -> Unit,
    onCancel: () -> Unit = {},
    status: String = "",
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        if (status.isNotEmpty()) {
            Text(status, modifier = Modifier.testTag("scan-status"))
            Spacer(Modifier.height(12.dp))
        }
        if (devices.isEmpty()) {
            repeat(3) { ShimmerRow() }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(devices, key = { it.address }) { device ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(device.name ?: device.address) },
                        supportingContent = { Text(device.address) },
                        leadingContent = {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Gold)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable { onDeviceClick(device) }
                            .testTag("device-row"),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun ShimmerRow() {
    val animEnabled = LocalAnimationsEnabled.current
    val alpha = if (!animEnabled) {
        0.5f
    } else {
        val t = rememberInfiniteTransition(label = "shimmer")
        t.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), "s").value
    }
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(alpha)) {
        Spacer(Modifier.height(56.dp))
    }
}

@Composable
fun EnterPinScreen(
    device: DeviceInfo,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    status: String = "",
) {
    var shakeKey by remember { mutableStateOf<Int?>(null) }
    var lastStatus by remember { mutableStateOf("") }
    // Any new non-empty status from the ViewModel means the last attempt failed.
    LaunchedEffect(status) {
        if (status.isNotEmpty() && status != lastStatus) {
            lastStatus = status
            shakeKey = (shakeKey ?: -1) + 1
        } else if (status.isEmpty()) {
            lastStatus = ""
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.enter_pin_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(device.name ?: device.address, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        PinDigitsInput(onConfirm = onConfirm, shakeKey = shakeKey)
        Spacer(Modifier.height(16.dp))
        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("pin-status"),
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = com.example.knucklegame.ui.theme.Ivory)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}