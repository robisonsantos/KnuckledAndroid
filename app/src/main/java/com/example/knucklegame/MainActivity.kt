
package com.example.knucklegame

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.knucklegame.audio.AndroidSoundManager
import com.example.knucklegame.audio.SoundManager
import com.example.knucklegame.settings.AndroidPrefs
import com.example.knucklegame.settings.Settings
import com.example.knucklegame.ui.ConnectionViewModel
import com.example.knucklegame.ui.KnuckledApp
import com.example.knucklegame.ui.theme.KnuckledTheme

class MainActivity : ComponentActivity() {

    private val connectionViewModel: ConnectionViewModel by viewModels<ConnectionViewModel>()

    private lateinit var soundManager: SoundManager
    private lateinit var settings: Settings

    private var readyThen: (() -> Unit)? = null
    private var pendingPerms: Array<String>? = null
    private var pendingDeniedMessage: String? = null

    private val bluetoothAdapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java)?.adapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val onReady = readyThen
        readyThen = null
        val perms = pendingPerms
        pendingPerms = null
        val deniedMessage = pendingDeniedMessage
        pendingDeniedMessage = null
        val allGranted = perms.isNullOrEmpty() ||
            perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
            onReady?.invoke()
        } else {
            connectionViewModel.showError(
                deniedMessage ?: "A required permission was not granted. Enable it in Settings > Apps > Knuckled, then retry.",
            )
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val onReady = readyThen
        readyThen = null
        if (result.resultCode == RESULT_OK) {
            onReady?.invoke()
        } else {
            connectionViewModel.showError("Bluetooth is off. Turn it on and retry.")
        }
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val onReady = readyThen
        readyThen = null
        val scanMode = try {
            bluetoothAdapter?.scanMode
        } catch (_: SecurityException) {
            null
        }
        val becameDiscoverable = result.resultCode > 0 ||
            scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
        if (becameDiscoverable) {
            onReady?.invoke()
        } else {
            connectionViewModel.showError("Your phone must be visible to other devices. Tap Allow on the popup and retry.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        settings = Settings(AndroidPrefs(getSharedPreferences("knucklegame", MODE_PRIVATE)))
        soundManager = AndroidSoundManager(this, settings)
        val animationsEnabled =
            !(BuildConfig.DEBUG && intent.getBooleanExtra("disableIdleAnimations", false))
        enableEdgeToEdge()
        val maestroFake = BuildConfig.DEBUG && intent.getStringExtra("maestro_fake") == "1"
        if (maestroFake) {
            connectionViewModel.setFakeMode(true)
            if (connectionViewModel.playerName.isBlank()) connectionViewModel.onPlayerNameChange("Maestro")
        }
        val maestroSeed = if (BuildConfig.DEBUG) intent.getStringExtra("maestro_seed")?.toLongOrNull() else null
        val maestroRng = maestroSeed?.let { kotlin.random.Random(it) }
        val maestroRollValue: () -> Int = if (maestroRng != null) ({ maestroRng.nextInt(1, 7) }) else ({ (1..6).random() })
        val maestroRollDelay = if (BuildConfig.DEBUG && intent.getStringExtra("maestro_fast") == "1") 0L else 2000L
        setContent {
            KnuckledTheme {
                KnuckledApp(
                    connectionViewModel = connectionViewModel,
                    onHostClick = ::onHostClick,
                    onPlayCpuClick = { connectionViewModel.startSinglePlayer() },
                    onFindClick = ::onFindClick,
                    onSubmitPin = ::onSubmitPin,
                    soundManager = soundManager,
                    settings = settings,
                    animationsEnabled = animationsEnabled,
                    rollValue = maestroRollValue,
                    rollDelayMs = maestroRollDelay,
                )
            }
        }
        if (maestroFake && intent.getStringExtra("maestro_auto") == "host") onHostClick()
        val maestroSingle = BuildConfig.DEBUG && intent.getStringExtra("maestro_single") == "1"
        if (maestroSingle) {
            if (connectionViewModel.playerName.isBlank()) connectionViewModel.onPlayerNameChange("Maestro")
            if (intent.getStringExtra("maestro_fast") == "1") {
                connectionViewModel.cpuPreRollDelayMs = 0L
                connectionViewModel.cpuThinkDelay = { 0L }
            }
            connectionViewModel.startSinglePlayer()
        }
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }

    private fun onHostClick() {
        if (connectionViewModel.inFakeMode) {
            connectionViewModel.onHostClicked()
            return
        }
        requestOrRun(
            permissionsForConnect() + permissionsForAdvertise(),
            deniedMessage = "Bluetooth permission was denied. Allow it in Settings > Apps > Knuckled, then retry.",
        ) {
            ensureBluetoothEnabled {
                ensureDiscoverable {
                    connectionViewModel.onHostClicked()
                }
            }
        }
    }

    private fun onFindClick() {
        if (connectionViewModel.inFakeMode) {
            connectionViewModel.onDiscoverClicked()
            return
        }
        requestOrRun(
            permissionsForScan(),
            deniedMessage = "The Nearby devices permission was denied. Allow it in Settings > Apps > Knuckled, then retry.",
        ) {
            ensureBluetoothEnabled {
                connectionViewModel.onDiscoverClicked()
            }
        }
    }

    private fun onSubmitPin(pin: String) {
        if (connectionViewModel.inFakeMode) {
            connectionViewModel.onPinEntered(pin)
            return
        }
        requestOrRun(
            permissionsForConnect(),
            deniedMessage = "Bluetooth permission was denied. Allow it in Settings > Apps > Knuckled, then retry.",
        ) {
            ensureBluetoothEnabled {
                connectionViewModel.onPinEntered(pin)
            }
        }
    }

    private fun requestOrRun(perms: Array<String>, deniedMessage: String, onReady: () -> Unit) {
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onReady()
        } else {
            readyThen = onReady
            pendingPerms = perms
            pendingDeniedMessage = deniedMessage
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun ensureBluetoothEnabled(onReady: () -> Unit) {
        val enabled = try {
            bluetoothAdapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
        if (enabled) {
            onReady()
        } else {
            readyThen = onReady
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun ensureDiscoverable(onReady: () -> Unit) {
        readyThen = onReady
        discoverableLauncher.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
        )
    }

    private fun permissionsForConnect(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    private fun permissionsForScan(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun permissionsForAdvertise(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }
}
