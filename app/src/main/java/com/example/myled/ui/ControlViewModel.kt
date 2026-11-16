package com.example.myled.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import com.example.myled.alarm.AlarmFiring
import com.example.myled.tile.StripTileService
import com.example.myled.ble.BleError
import com.example.myled.ble.ConnectionState
import com.example.myled.ble.GattLedStrip
import com.example.myled.ble.LedStrip
import com.example.myled.ble.ScannedDevice
import com.example.myled.data.Settings
import com.example.myled.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PermissionState { Granted, Denied, PermanentlyDenied, BluetoothOff }

data class ControlUiState(
    val loaded: Boolean = false,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val powerOn: Boolean = true,
    val r: Int = 0xFF,
    val g: Int = 0x16,
    val b: Int = 0x00,
    val brightness: Int = 100,
    val hasSavedDevice: Boolean = false,
    val permissionState: PermissionState = PermissionState.Denied,
) {
    val isConnected get() = connection is ConnectionState.Connected

    /** Colour and brightness gestures are only accepted while connected and powered on. */
    val controlsEnabled get() = isConnected && powerOn
}

class ControlViewModel(app: Application) : AndroidViewModel(app) {

    private val strip: LedStrip = GattLedStrip(app)
    private val settings = SettingsStore(app)

    private val _ui = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _ui.asStateFlow()

    val scanResults: StateFlow<List<ScannedDevice>> get() = strip.scanResults

    private var savedAddress = ""
    private var active = false
    private var settingsLoaded = false
    private var linkRetryUsed = false
    private var alarmActive = false

    private var graceJob: Job? = null
    private var persistJob: Job? = null
    private var postConnectJob: Job? = null
    private var reconnectJob: Job? = null

    private val colorThrottle = TrailingThrottle<Triple<Int, Int, Int>>(viewModelScope, THROTTLE_MS) { (r, g, b) ->
        strip.setColor(r, g, b)
    }
    private val brightnessThrottle = TrailingThrottle<Int>(viewModelScope, THROTTLE_MS) { strip.setBrightness(it) }

    init {
        viewModelScope.launch {
            val s = settings.load()
            savedAddress = s.lastDeviceAddress
            settingsLoaded = true
            _ui.update {
                it.copy(
                    loaded = true,
                    powerOn = s.powerOn,
                    r = (s.color shr 16) and 0xFF,
                    g = (s.color shr 8) and 0xFF,
                    b = s.color and 0xFF,
                    brightness = s.brightness,
                    hasSavedDevice = s.lastDeviceAddress.isNotEmpty(),
                )
            }
            maybeAutoConnect()
        }
        viewModelScope.launch {
            AlarmFiring.active.collect { firing ->
                if (firing == alarmActive) return@collect
                alarmActive = firing
                if (firing) {
                    // The strip takes one link at a time: hand it to the alarm.
                    persistNow()
                    reconnectJob?.cancel()
                    postConnectJob?.cancel()
                    strip.disconnect()
                } else {
                    reloadSavedState()
                    maybeAutoConnect()
                }
            }
        }
        viewModelScope.launch {
            var previous: ConnectionState = ConnectionState.Disconnected
            strip.state.collect { state ->
                _ui.update { it.copy(connection = state) }
                onConnectionChanged(previous, state)
                previous = state
            }
        }
    }

    // ------------------------------------------------------------ lifecycle

    /** True while the activity is started and not in ambient mode. */
    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        if (isActive) {
            graceJob?.cancel()
            graceJob = null
            maybeAutoConnect()
        } else {
            persistNow()
            graceJob?.cancel()
            graceJob = viewModelScope.launch {
                delay(GRACE_MS)
                reconnectJob?.cancel()
                postConnectJob?.cancel()
                strip.disconnect()
            }
        }
    }

    fun onPermissionState(state: PermissionState) {
        _ui.update { it.copy(permissionState = state) }
        if (state == PermissionState.Granted) maybeAutoConnect()
    }

    /** Picks up the colour/brightness/power an alarm just wrote. */
    private suspend fun reloadSavedState() {
        val s = settings.load()
        _ui.update {
            it.copy(
                powerOn = s.powerOn,
                r = (s.color shr 16) and 0xFF,
                g = (s.color shr 8) and 0xFF,
                b = s.color and 0xFF,
                brightness = s.brightness,
            )
        }
    }

    private fun maybeAutoConnect() {
        val ui = _ui.value
        if (alarmActive) return
        if (!settingsLoaded || !active || ui.permissionState != PermissionState.Granted) return
        if (savedAddress.isEmpty()) return
        val c = ui.connection
        if (c is ConnectionState.Disconnected || c is ConnectionState.Error) strip.connect(savedAddress)
    }

    private fun onConnectionChanged(previous: ConnectionState, state: ConnectionState) {
        if (state is ConnectionState.Connected && previous !is ConnectionState.Connected) {
            linkRetryUsed = false
            postConnectJob?.cancel()
            postConnectJob = viewModelScope.launch { runPostConnect() }
        }
        if (state is ConnectionState.Error && state.kind == BleError.LinkLost && active && !linkRetryUsed) {
            linkRetryUsed = true
            reconnectJob?.cancel()
            reconnectJob = viewModelScope.launch {
                delay(1_000)
                if (active && savedAddress.isNotEmpty() && strip.state.value is ConnectionState.Error) {
                    strip.connect(savedAddress)
                }
            }
        }
    }

    /** Forces a strip stuck in a built-in animation back to solid colour, then restores level/power. */
    private suspend fun runPostConnect() {
        delay(300)
        val s = _ui.value
        strip.setColor(s.r, s.g, s.b)
        strip.setBrightness(s.brightness)
        if (!s.powerOn) strip.setPower(false)
    }

    // ------------------------------------------------------------ controls

    fun onColorChanged(r: Int, g: Int, b: Int, final: Boolean) {
        if (!_ui.value.controlsEnabled) return
        _ui.update { it.copy(r = r, g = g, b = b) }
        colorThrottle.submit(Triple(r, g, b), final)
        schedulePersist()
    }

    fun onBrightnessChanged(percent: Int, final: Boolean) {
        if (!_ui.value.controlsEnabled) return
        val p = percent.coerceIn(1, 100)
        _ui.update { it.copy(brightness = p) }
        brightnessThrottle.submit(p, final)
        schedulePersist()
    }

    fun adjustBrightness(deltaPercent: Int) = onBrightnessChanged(_ui.value.brightness + deltaPercent, false)

    fun togglePower() {
        val s = _ui.value
        if (!s.isConnected) return
        val on = !s.powerOn
        _ui.update { it.copy(powerOn = on) }
        if (on) {
            strip.setPower(true)
            strip.setColor(s.r, s.g, s.b)
            strip.setBrightness(s.brightness)
        } else {
            strip.setPower(false)
        }
        persistNow()
    }

    /** Power button tap: toggle when connected, otherwise (re)connect or ask for a device. */
    fun onPowerTap(openPicker: () -> Unit) {
        when (_ui.value.connection) {
            is ConnectionState.Connected -> togglePower()
            is ConnectionState.Scanning, is ConnectionState.Connecting -> Unit
            else -> retryConnect(openPicker)
        }
    }

    fun retryConnect(openPicker: () -> Unit) {
        if (savedAddress.isEmpty()) openPicker() else if (!alarmActive) strip.connect(savedAddress)
    }

    fun disconnect() {
        graceJob?.cancel()
        reconnectJob?.cancel()
        postConnectJob?.cancel()
        strip.disconnect()
    }

    // ------------------------------------------------------------ device picker

    fun startScan() = strip.startScan()

    fun stopScan() = strip.stopScan()

    fun selectDevice(device: ScannedDevice) {
        strip.stopScan()
        savedAddress = device.address
        _ui.update { it.copy(hasSavedDevice = true) }
        viewModelScope.launch { settings.saveDevice(device.address, device.name.orEmpty()) }
        strip.connect(device.address)
    }

    // ------------------------------------------------------------ persistence

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            save(_ui.value)
        }
    }

    private fun persistNow() {
        persistJob?.cancel()
        if (!settingsLoaded) return
        val snapshot = _ui.value
        viewModelScope.launch { save(snapshot) }
    }

    private suspend fun save(s: ControlUiState) {
        settings.saveState((s.r shl 16) or (s.g shl 8) or s.b, s.brightness, s.powerOn)
        runCatching {
            TileService.getUpdater(getApplication<Application>()).requestUpdate(StripTileService::class.java)
        }
    }

    override fun onCleared() {
        strip.release()
    }

    private companion object {
        const val THROTTLE_MS = 45L
        const val GRACE_MS = 10_000L
        const val PERSIST_DEBOUNCE_MS = 400L
    }
}
