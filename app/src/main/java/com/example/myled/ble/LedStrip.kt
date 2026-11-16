package com.example.myled.ble

import kotlinx.coroutines.flow.StateFlow

enum class BleError {
    PermissionDenied,
    BluetoothOff,
    Timeout,
    ConnectFailed,
    NoWritableCharacteristic,
    LinkLost,
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val address: String) : ConnectionState
    data class Connected(val address: String, val name: String?) : ConnectionState
    data class Error(val kind: BleError) : ConnectionState
}

data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val likelyController: Boolean,
)

interface LedStrip {
    val state: StateFlow<ConnectionState>
    val scanResults: StateFlow<List<ScannedDevice>>
    fun startScan(timeoutMs: Long = 4_000)
    fun stopScan()
    fun connect(address: String)
    fun disconnect()
    fun setPower(on: Boolean)
    fun setColor(r: Int, g: Int, b: Int)
    fun setBrightness(percent: Int)

    /** Suspends until every queued packet has been written (or dropped), or [timeoutMs] passes. */
    suspend fun awaitWritesDone(timeoutMs: Long = 3_000)

    /** Releases the GATT link and all resources; the instance is unusable afterwards. */
    fun release()
}
