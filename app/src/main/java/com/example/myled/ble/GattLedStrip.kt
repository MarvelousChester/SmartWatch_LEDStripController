package com.example.myled.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LedBle"

/**
 * Direct GATT client for ELK-BLEDOM / duoCo strips. All internal state is confined to a
 * single-threaded dispatcher; binder-thread callbacks only complete deferreds / post work onto it.
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
class GattLedStrip(context: Context) : LedStrip {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScannedDevice>>(emptyList())
    override val scanResults: StateFlow<List<ScannedDevice>> = _scanResults.asStateFlow()

    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var session: Session? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val queue = Channel<ByteArray>(Channel.UNLIMITED)

    /** Packets enqueued but not yet written or dropped; lets callers wait for a flush. */
    private val pendingWrites = AtomicInteger(0)

    init {
        scope.launch { writerLoop() }
    }

    // ---------------------------------------------------------------- scanning

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = addScanResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) =
            results.forEach(::addScanResult)

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            scope.launch { stopScanInternal() }
        }
    }

    private fun addScanResult(result: ScanResult) {
        val address = result.device.address ?: return
        val name = result.scanRecord?.deviceName ?: safeName(result.device)
        _scanResults.update { list ->
            val existing = list.firstOrNull { it.address == address }
            val resolvedName = name ?: existing?.name
            val updated = ScannedDevice(
                address = address,
                name = resolvedName,
                rssi = result.rssi,
                likelyController = DeviceMatcher.isLikelyController(resolvedName),
            )
            (list.filterNot { it.address == address } + updated)
                .sortedWith(compareByDescending<ScannedDevice> { it.likelyController }.thenByDescending { it.rssi })
        }
    }

    override fun startScan(timeoutMs: Long) {
        scope.launch {
            if (!checkReady()) return@launch
            connectJob?.cancel()
            teardownSession()
            stopScanInternal(resetState = false)
            _scanResults.value = emptyList()
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                _state.value = ConnectionState.Error(BleError.BluetoothOff)
                return@launch
            }
            try {
                scanner.startScan(
                    null,
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    scanCallback,
                )
            } catch (e: SecurityException) {
                _state.value = ConnectionState.Error(BleError.PermissionDenied)
                return@launch
            }
            _state.value = ConnectionState.Scanning
            scanJob = launch {
                delay(timeoutMs)
                stopScanInternal()
            }
        }
    }

    override fun stopScan() {
        scope.launch { stopScanInternal() }
    }

    private fun stopScanInternal(resetState: Boolean = true) {
        scanJob?.cancel()
        scanJob = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
        if (resetState && _state.value == ConnectionState.Scanning) {
            _state.value = ConnectionState.Disconnected
        }
    }

    // ---------------------------------------------------------------- connecting

    override fun connect(address: String) {
        val current = _state.value
        if (current is ConnectionState.Connecting && current.address == address) return
        if (current is ConnectionState.Connected && current.address == address) return
        _state.value = ConnectionState.Connecting(address)
        scope.launch {
            connectJob?.cancelAndJoinQuietly()
            stopScanInternal(resetState = false)
            teardownSession()
            _state.value = ConnectionState.Connecting(address)
            connectJob = launch { doConnect(address) }
        }
    }

    private suspend fun Job.cancelAndJoinQuietly() {
        cancel()
        try {
            join()
        } catch (_: Exception) {
        }
    }

    private sealed interface Attempt {
        data class Ok(val session: Session, val characteristic: BluetoothGattCharacteristic) : Attempt
        data object Timeout : Attempt
        data object Failed : Attempt
        data object NoCharacteristic : Attempt
    }

    private suspend fun doConnect(address: String) {
        if (!checkReady()) return
        val device = try {
            adapter!!.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            fail(BleError.ConnectFailed)
            return
        }
        var retried = false
        while (true) {
            val attempt = try {
                attemptConnect(device)
            } catch (e: SecurityException) {
                fail(BleError.PermissionDenied)
                return
            }
            when (attempt) {
                is Attempt.Ok -> {
                    session = attempt.session
                    writeChar = attempt.characteristic
                    try {
                        attempt.session.gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    } catch (_: SecurityException) {
                    }
                    Log.d(TAG, "connected to $address via ${attempt.characteristic.uuid}")
                    _state.value = ConnectionState.Connected(address, safeName(device))
                    return
                }
                Attempt.Timeout -> return fail(BleError.Timeout)
                Attempt.NoCharacteristic -> return fail(BleError.NoWritableCharacteristic)
                Attempt.Failed -> {
                    if (retried) return fail(BleError.ConnectFailed)
                    retried = true
                    delay(500)
                }
            }
        }
    }

    private suspend fun attemptConnect(device: BluetoothDevice): Attempt {
        val s = Session()
        var keep = false
        try {
            val g = device.connectGatt(appContext, false, s, BluetoothDevice.TRANSPORT_LE)
                ?: return Attempt.Failed
            s.gatt = g
            val status = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { s.connected.await() }
                ?: return Attempt.Timeout
            if (status != BluetoothGatt.GATT_SUCCESS) return Attempt.Failed
            if (!g.discoverServices()) return Attempt.Failed
            val discovery = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { s.services.await() }
                ?: return Attempt.Timeout
            if (discovery != BluetoothGatt.GATT_SUCCESS) return Attempt.Failed
            val characteristic = findWriteCharacteristic(g) ?: return Attempt.NoCharacteristic
            keep = true
            return Attempt.Ok(s, characteristic)
        } finally {
            if (!keep) s.close()
        }
    }

    private fun findWriteCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val all = gatt.services.flatMap { it.characteristics }
        all.firstOrNull { it.uuid in WRITE_UUIDS }?.let { return it }
        return all.firstOrNull {
            it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        }
    }

    private fun checkReady(): Boolean {
        val a = adapter
        if (a == null || !a.isEnabled) {
            _state.value = ConnectionState.Error(BleError.BluetoothOff)
            return false
        }
        return true
    }

    private fun fail(kind: BleError) {
        Log.d(TAG, "connect failed: $kind")
        _state.value = ConnectionState.Error(kind)
    }

    override fun disconnect() {
        scope.launch {
            connectJob?.cancelAndJoinQuietly()
            connectJob = null
            stopScanInternal(resetState = false)
            teardownSession()
            _state.value = ConnectionState.Disconnected
        }
    }

    private fun teardownSession() {
        session?.close()
        session = null
        writeChar = null
    }

    private fun onLinkLost(lost: Session) {
        scope.launch {
            if (session !== lost) return@launch
            Log.d(TAG, "link lost")
            teardownSession()
            _state.value = ConnectionState.Error(BleError.LinkLost)
        }
    }

    override fun release() {
        // One coroutine so teardown finishes before the scope is cancelled.
        scope.launch {
            connectJob?.cancel()
            connectJob = null
            stopScanInternal(resetState = false)
            teardownSession()
            _state.value = ConnectionState.Disconnected
            queue.close()
            scope.cancel()
        }
    }

    override suspend fun awaitWritesDone(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            while (pendingWrites.get() > 0) delay(20)
        }
    }

    // ---------------------------------------------------------------- writing

    override fun setPower(on: Boolean) = enqueue(LedProtocol.power(on))
    override fun setColor(r: Int, g: Int, b: Int) = enqueue(LedProtocol.color(r, g, b))
    override fun setBrightness(percent: Int) = enqueue(LedProtocol.brightness(percent))

    private fun enqueue(packets: List<ByteArray>) {
        if (_state.value !is ConnectionState.Connected) return
        for (p in packets) {
            pendingWrites.incrementAndGet()
            if (queue.trySend(p).isFailure) pendingWrites.decrementAndGet()
        }
    }

    private suspend fun writerLoop() {
        val pending = ArrayDeque<ByteArray>()
        while (true) {
            if (pending.isEmpty()) {
                pending.addLast(queue.receiveCatching().getOrNull() ?: return)
            }
            while (true) pending.addLast(queue.tryReceive().getOrNull() ?: break)
            if (_state.value !is ConnectionState.Connected) {
                pendingWrites.addAndGet(-pending.size)
                pending.clear()
                continue
            }
            val survivors = PacketCoalescer.filter(pending.toList())
            pendingWrites.addAndGet(survivors.size - pending.size)
            pending.clear()
            pending.addAll(survivors)
            writePacket(pending.removeFirst())
            pendingWrites.decrementAndGet()
            delay(MIN_SEND_INTERVAL_MS)
        }
    }

    private suspend fun writePacket(packet: ByteArray) {
        val s = session ?: return
        val g = s.gatt ?: return
        val c = writeChar ?: return
        val hex = packet.joinToString("") { "%02x".format(it) }
        try {
            val noResponse = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            var ok = false
            if (noResponse) ok = tryWrite(s, g, c, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            if (!ok) ok = tryWrite(s, g, c, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            Log.d(TAG, "write $hex ${if (ok) "ok" else "FAILED"}")
        } catch (e: SecurityException) {
            _state.value = ConnectionState.Error(BleError.PermissionDenied)
        }
    }

    private suspend fun tryWrite(
        s: Session,
        g: BluetoothGatt,
        c: BluetoothGattCharacteristic,
        packet: ByteArray,
        writeType: Int,
    ): Boolean {
        repeat(WRITE_BUSY_RETRIES) {
            val ack = CompletableDeferred<Int>()
            s.writeAck = ack
            if (startWrite(g, c, packet, writeType)) {
                val status = withTimeoutOrNull(WRITE_TIMEOUT_MS) { ack.await() }
                s.writeAck = null
                return status == BluetoothGatt.GATT_SUCCESS
            }
            s.writeAck = null
            delay(MIN_SEND_INTERVAL_MS)
        }
        return false
    }

    /** Returns true when the stack accepted the request (completion arrives in onCharacteristicWrite). */
    @Suppress("DEPRECATION")
    private fun startWrite(
        g: BluetoothGatt,
        c: BluetoothGattCharacteristic,
        packet: ByteArray,
        writeType: Int,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, packet, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = writeType
            c.value = packet
            g.writeCharacteristic(c)
        }

    // ---------------------------------------------------------------- GATT session

    /** One connectGatt attempt; callbacks of a discarded session can never touch a newer one. */
    private inner class Session : BluetoothGattCallback() {
        val connected = CompletableDeferred<Int>()
        val services = CompletableDeferred<Int>()

        @Volatile var gatt: BluetoothGatt? = null
        @Volatile var writeAck: CompletableDeferred<Int>? = null
        @Volatile var closed = false

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (closed) return
            Log.d(TAG, "connection state status=$status newState=$newState")
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                    connected.complete(BluetoothGatt.GATT_SUCCESS)
                !connected.isCompleted ->
                    connected.complete(if (status != BluetoothGatt.GATT_SUCCESS) status else -1)
                newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS ->
                    onLinkLost(this)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (!closed) services.complete(status)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            writeAck?.complete(status)
        }

        fun close() {
            closed = true
            writeAck?.complete(-1)
            val g = gatt ?: return
            gatt = null
            try {
                g.disconnect()
            } catch (_: SecurityException) {
            }
            try {
                g.close()
            } catch (_: SecurityException) {
            }
        }
    }

    private fun safeName(device: BluetoothDevice): String? =
        try {
            device.name
        } catch (_: SecurityException) {
            null
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val MIN_SEND_INTERVAL_MS = 40L
        const val WRITE_TIMEOUT_MS = 1_000L
        const val WRITE_BUSY_RETRIES = 5

        val WRITE_UUIDS: Set<UUID> = setOf(
            "0000fff3-0000-1000-8000-00805f9b34fb",
            "0000ffe1-0000-1000-8000-00805f9b34fb",
            "0000ffd9-0000-1000-8000-00805f9b34fb",
            "0000fff2-0000-1000-8000-00805f9b34fb",
        ).map(UUID::fromString).toSet()
    }
}
