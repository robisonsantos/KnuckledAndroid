package com.example.knucklegame.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.UUID

private const val TAG = "Knuckled"

class AndroidBluetoothConnector(
    private val context: Context,
) : BluetoothConnector {

    private val adapter: BluetoothAdapter? = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    } catch (e: SecurityException) {
        Log.e(TAG, "AndroidBluetoothConnector: Bluetooth adapter lookup threw SecurityException", e)
        null
    } catch (t: Throwable) {
        Log.e(TAG, "AndroidBluetoothConnector: Bluetooth adapter lookup failed", t)
        null
    }

    override fun listen(pin: String): GameLink {
        val a = adapter ?: throw IllegalStateException("Bluetooth is not available on this device.")
        val uuid = UUID.fromString(Protocol.RFCOMM_UUID)
        var serverSocket: BluetoothServerSocket? = null
        var socket: BluetoothSocket? = null
        try {
            serverSocket = try {
                a.listenUsingRfcommWithServiceRecord(Protocol.BT_SERVICE_NAME, uuid)
            } catch (e: SecurityException) {
                Log.e(TAG, "listen: listenUsingRfcommWithServiceRecord threw SecurityException", e)
                throw IllegalStateException("Bluetooth permission not granted.", e)
            } catch (e: IOException) {
                Log.e(TAG, "listen: failed to create server socket", e)
                throw e
            }
            socket = try {
                serverSocket.accept()
            } catch (e: SecurityException) {
                Log.e(TAG, "listen: accept threw SecurityException", e)
                throw IllegalStateException("Bluetooth permission not granted.", e)
            } catch (e: IOException) {
                Log.e(TAG, "listen: accept failed", e)
                throw e
            }
            if (socket == null) throw IOException("accept() returned null socket")
            val input = socket.inputStream
            val output = socket.outputStream
            val ok = try {
                Handshake.accept(input, output, pin)
            } catch (t: Throwable) {
                Log.e(TAG, "listen: handshake failed", t)
                false
            }
            if (!ok) {
                try { socket.close() } catch (_: IOException) {}
                throw IllegalStateException("PIN handshake failed.")
            }
            return GameLinkImpl.create(input, output)
        } finally {
            try { serverSocket?.close() } catch (_: IOException) {}
            // Do not close client socket on success — GameLink owns it now.
            // On failure the socket was already closed above.
        }
    }

    override fun connect(device: DeviceInfo, pin: String): GameLink {
        val a = adapter ?: throw IllegalStateException("Bluetooth is not available on this device.")
        val uuid = UUID.fromString(Protocol.RFCOMM_UUID)
        var socket: BluetoothSocket? = null
        try {
            val remote: BluetoothDevice = try {
                a.getRemoteDevice(device.address)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "connect: invalid address ${device.address}", e)
                throw e
            } catch (e: SecurityException) {
                Log.e(TAG, "connect: getRemoteDevice threw SecurityException", e)
                throw IllegalStateException("Bluetooth permission not granted.", e)
            }
            socket = try {
                remote.createRfcommSocketToServiceRecord(uuid)
            } catch (e: SecurityException) {
                Log.e(TAG, "connect: createRfcommSocket threw SecurityException", e)
                throw IllegalStateException("Bluetooth permission not granted.", e)
            } catch (e: IOException) {
                Log.e(TAG, "connect: createRfcommSocket failed", e)
                throw e
            }
            try { a.cancelDiscovery() } catch (e: SecurityException) {
                Log.e(TAG, "connect: cancelDiscovery threw", e)
            } catch (t: Throwable) {
                Log.w(TAG, "connect: cancelDiscovery failed: ${t.message}")
            }
            try {
                socket.connect()
            } catch (e: SecurityException) {
                Log.e(TAG, "connect: socket.connect threw SecurityException", e)
                throw IllegalStateException("Bluetooth permission not granted.", e)
            } catch (e: IOException) {
                Log.e(TAG, "connect: socket.connect failed", e)
                throw e
            }
            val input = socket.inputStream
            val output = socket.outputStream
            val ok = try {
                Handshake.initiate(input, output, pin)
            } catch (t: Throwable) {
                Log.e(TAG, "connect: handshake failed", t)
                false
            }
            if (!ok) {
                try { socket.close() } catch (_: IOException) {}
                throw IllegalStateException("PIN handshake failed.")
            }
            return GameLinkImpl.create(input, output)
        } catch (t: Throwable) {
            if (t is IllegalStateException) throw t
            try { socket?.close() } catch (_: IOException) {}
            throw t
        }
    }

    override fun discover(): List<DeviceInfo> {
        val a = adapter
        if (a == null) {
            Log.w(TAG, "discover: BluetoothAdapter is null (emulator or no BT)")
            return emptyList()
        }
        val enabled = try {
            a.isEnabled
        } catch (e: SecurityException) {
            Log.e(TAG, "discover: isEnabled threw SecurityException", e)
            return emptyList()
        } catch (t: Throwable) {
            Log.e(TAG, "discover: isEnabled failed", t)
            return emptyList()
        }
        if (!enabled) {
            Log.i(TAG, "discover: Bluetooth is disabled")
            return emptyList()
        }
        val devices = LinkedHashMap<String, DeviceInfo>()
        try {
            val bonded = a.bondedDevices
            if (bonded != null) {
                for (d in bonded) {
                    try {
                        val rawAddress = try { d.address } catch (_: SecurityException) { null } catch (_: Throwable) { null }
                        if (rawAddress.isNullOrBlank()) {
                            Log.w(TAG, "discover: bonded device without usable address, skipped (rawName=${runCatching { d.name }.getOrNull()})")
                            continue
                        }
                        val rawName = try { d.name } catch (_: SecurityException) { null } catch (_: Throwable) { null }
                        val name = rawName?.takeIf { it.isNotBlank() } ?: rawAddress
                        devices[rawAddress] = DeviceInfo(name, rawAddress)
                    } catch (t: Throwable) {
                        Log.w(TAG, "discover: failed to read bonded device: ${t.message}")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "discover: bondedDevices threw SecurityException", e)
        } catch (t: Throwable) {
            Log.w(TAG, "discover: bonded list read failed: ${t.message}")
        }
        try {
            a.cancelDiscovery()
        } catch (e: SecurityException) {
            Log.e(TAG, "discover: cancelDiscovery threw", e)
        } catch (t: Throwable) {
            Log.w(TAG, "discover: cancelDiscovery failed: ${t.message}")
        }
        val started = try {
            a.startDiscovery()
        } catch (e: SecurityException) {
            Log.e(TAG, "discover: startDiscovery threw SecurityException", e)
            false
        } catch (t: Throwable) {
            Log.w(TAG, "discover: startDiscovery failed: ${t.message}")
            false
        }
        if (!started) {
            Log.i(TAG, "discover: startDiscovery returned false or threw - returning bonded devices only (${devices.size})")
        }
        return devices.values.toList()
    }
}
