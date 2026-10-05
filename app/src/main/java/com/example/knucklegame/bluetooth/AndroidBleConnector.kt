package com.example.knucklegame.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** BLE transport behind the blocking BluetoothConnector facade (RFCOMM
 *  untouched). Host = GATT server + advertiser; client = scanner + GATT.
 *  Frozen UUIDs match iOS. Cancel mirrors the RFCOMM limitation (state reset;
 *  a blocked listen/connect is not unblocked — documented). */
@SuppressLint("MissingPermission")
class AndroidBleConnector(private val context: Context) : BluetoothConnector {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString(Protocol.BLE_SERVICE_UUID)
        val WRITE_UUID: UUID = UUID.fromString(Protocol.BLE_WRITE_UUID)
        val NOTIFY_UUID: UUID = UUID.fromString(Protocol.BLE_NOTIFY_UUID)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private fun manager(): BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw IllegalStateException("Bluetooth unavailable.")

    private fun adapter(): BluetoothAdapter =
        manager().adapter ?: throw IllegalStateException("Bluetooth unavailable.")

    /** GameLink that also releases BLE resources on close. Handler vars delegate
     *  so later assignments keep working. */
    private class ManagedBleLink(
        private val delegate: GameLink,
        private val onCloseResources: () -> Unit,
    ) : GameLink by delegate {
        override fun close() {
            delegate.close()
            try {
                onCloseResources()
            } catch (_: Exception) {
            }
        }
    }

    override fun listen(pin: String): GameLink {
        val adapter = adapter()
        val advertiser = adapter.bluetoothLeAdvertiser
            ?: throw IllegalStateException("BLE advertising not supported.")
        val serverRef = AtomicReference<BluetoothGattServer?>()
        val peerRef = AtomicReference<BluetoothDevice?>()
        val subscribed = CountDownLatch(1)
        // Route notify chunks once a peer subscribes (set after server exists).
        val pipeWithNotify = BleBytePipe(onChunk = { chunk ->
            val server = serverRef.get()
            val peer = peerRef.get()
            val char = server?.getService(SERVICE_UUID)?.getCharacteristic(NOTIFY_UUID)
            if (server != null && peer != null && char != null) {
                char.value = chunk
                if (Build.VERSION.SDK_INT >= 33) {
                    server.notifyCharacteristicChanged(peer, char, false, chunk)
                } else {
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(peer, char, false)
                }
            }
        })
        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) pipeWithNotify.close()
            }

            override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
                pipeWithNotify.feed(value)
                serverRef.get()?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
                serverRef.get()?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
            }

            override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
                if (descriptor.uuid == CCCD_UUID) {
                    peerRef.set(device)
                    subscribed.countDown()
                }
                serverRef.get()?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
        val server = manager().openGattServer(context, callback)
            ?: throw IllegalStateException("Bluetooth unavailable.")
        serverRef.set(server)
        try {
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val writeChar = BluetoothGattCharacteristic(
                WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
            val notifyChar = BluetoothGattCharacteristic(
                NOTIFY_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
            notifyChar.addDescriptor(BluetoothGattDescriptor(CCCD_UUID, BluetoothGattCharacteristic.PERMISSION_WRITE))
            service.addCharacteristic(writeChar)
            service.addCharacteristic(notifyChar)
            server.addService(service)

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setTimeout(0)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            val started = CountDownLatch(1)
            var advOk = false
            val advCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    advOk = true
                    started.countDown()
                }

                override fun onStartFailure(errorCode: Int) {
                    started.countDown()
                }
            }
            advertiser.startAdvertising(settings, data, advCallback)
            try {
                started.await()
                if (!advOk) throw IllegalStateException("BLE advertising failed.")
                subscribed.await()
                advertiser.stopAdvertising(advCallback)
                if (!Handshake.accept(pipeWithNotify.inputStream(), pipeWithNotify.outputStream(), pin)) {
                    throw IllegalStateException("PIN handshake failed.")
                }
                val raw = GameLinkImpl.create(pipeWithNotify.inputStream(), pipeWithNotify.outputStream())
                return ManagedBleLink(raw) {
                    try { server.close() } catch (_: Exception) { }
                }
            } finally {
                try {
                    advertiser.stopAdvertising(advCallback)
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            try {
                server.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    override fun discover(): List<DeviceInfo> {
        val adapter = try { adapter() } catch (_: Exception) { return emptyList() }
        if (!adapter.isEnabled) return emptyList()
        val scanner = adapter.bluetoothLeScanner ?: return emptyList()
        val found = ConcurrentHashMap<String, DeviceInfo>()
        for (d in adapter.bondedDevices) {
            if (d.address.isNotBlank()) found[d.address] = DeviceInfo(d.name, d.address)
        }
        val done = CountDownLatch(1)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (device.address.isNotBlank()) {
                    found[device.address] = DeviceInfo(device.name, device.address)
                }
            }
        }
        try {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
            // 5s scan window (documented; callers run discover off the UI thread).
            done.await(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            try { scanner.stopScan(callback) } catch (_: Exception) { }
        }
        return found.values.toList()
    }

    override fun connect(device: DeviceInfo, pin: String): GameLink {
        val adapter = adapter()
        val remote = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: IllegalArgumentException) {
            throw e
        }
        // 30s connect cap (documented; callers already background connect).
        val pipe = BleBytePipe(onChunk = {})
        val connected = CountDownLatch(1)
        val ready = CountDownLatch(1)
        var readyOk = false
        val subscribedAck = CountDownLatch(1)
        var subscribedOk = false
        val mtuLatch = CountDownLatch(1)
        var gattRef: BluetoothGatt? = null
        // write path needs the gatt + characteristic; set after discovery.
        lateinit var writeTarget: Pair<BluetoothGatt, BluetoothGattCharacteristic>
        val chunkPipe = BleBytePipe(onChunk = { chunk ->
            val (gatt, char) = writeTarget
            char.value = chunk
            gatt.writeCharacteristic(char)
        })
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gattRef = gatt
                    connected.countDown()
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    chunkPipe.close()
                    pipe.close()
                    connected.countDown()
                    ready.countDown()
                    subscribedAck.countDown()
                    mtuLatch.countDown()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                readyOk = (status == BluetoothGatt.GATT_SUCCESS)
                ready.countDown()
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                chunkPipe.feed(characteristic.value)
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                chunkPipe.feed(value)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                subscribedOk = (status == BluetoothGatt.GATT_SUCCESS)
                subscribedAck.countDown()
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) chunkPipe.mtu = mtu
                mtuLatch.countDown()
            }
        }
        val gatt = remote.connectGatt(context, false, callback)
        try {
            if (!connected.await(30, TimeUnit.SECONDS)) throw IllegalStateException("Could not connect.")
            if (!ready.await(10, TimeUnit.SECONDS) || !readyOk) throw IllegalStateException("BLE service not found.")
            val service = gatt.getService(SERVICE_UUID)
                ?: throw IllegalStateException("BLE service not found.")
            val writeChar = service.getCharacteristic(WRITE_UUID)
                ?: throw IllegalStateException("BLE service not found.")
            val notifyChar = service.getCharacteristic(NOTIFY_UUID)
                ?: throw IllegalStateException("BLE service not found.")
            writeTarget = gatt to writeChar
            gatt.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(CCCD_UUID)
                ?: throw IllegalStateException("BLE service not found.")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
            if (!subscribedAck.await(10, TimeUnit.SECONDS) || !subscribedOk) {
                throw IllegalStateException("Could not subscribe.")
            }
            try { gatt.requestMtu(185) } catch (_: Exception) { }
            mtuLatch.await(3, TimeUnit.SECONDS)
            if (!Handshake.initiate(chunkPipe.inputStream(), chunkPipe.outputStream(), pin)) {
                throw IllegalStateException("PIN handshake failed.")
            }
            val raw = GameLinkImpl.create(chunkPipe.inputStream(), chunkPipe.outputStream())
            return ManagedBleLink(raw) {
                try { gatt.disconnect() } catch (_: Exception) { }
                try { gatt.close() } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            try { gatt.disconnect() } catch (_: Exception) { }
            try { gatt.close() } catch (_: Exception) { }
            throw e
        }
    }
}
