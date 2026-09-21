package com.example.knucklegame.bluetooth

enum class ConnectionResult {
    SUCCESS,
    FAIL_DISCOVERABLE,
    FAIL_PERMISSION,
    FAIL_PAIRING,
    FAIL_LOCATION_OFF,
    FAIL_DISCOVERY,
    FAIL_TIMEOUT,
    UNKNOWN,
}

data class DeviceInfo(val name: String?, val address: String)

interface BluetoothConnector {
    fun listen(pin: String): GameLink
    fun connect(device: DeviceInfo, pin: String): GameLink
    fun discover(): List<DeviceInfo>
}