package com.example.myled.tile

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tiles.TileService
import com.example.myled.ble.BleError
import com.example.myled.ble.ConnectionState
import com.example.myled.ble.GattLedStrip
import com.example.myled.data.SettingsStore
import com.example.myled.presentation.MainActivity
import com.example.myled.presentation.theme.MyLEDTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Launched from the tile: connects to the saved strip, flips power, persists it and releases the
 * link again so the PC app can reconnect. Shows a short status while it works.
 */
class TileToggleActivity : ComponentActivity() {

    private var status by mutableStateOf("Connecting…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyLEDTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(status, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        lifecycleScope.launch {
            status = toggle()
            delay(RESULT_HOLD_MS)
            finish()
        }
    }

    /** Returns the text to show once finished. */
    private suspend fun toggle(): String {
        val store = SettingsStore(applicationContext)
        val settings = store.load()
        if (settings.lastDeviceAddress.isEmpty() || !canUseBluetooth()) {
            // Needs the full app to pick a strip or ask for permissions.
            startActivity(Intent(this, MainActivity::class.java))
            return ""
        }

        val strip = GattLedStrip(applicationContext)
        try {
            strip.connect(settings.lastDeviceAddress)
            val result = withTimeoutOrNull(CONNECT_WAIT_MS) {
                strip.state.first { it is ConnectionState.Connected || it is ConnectionState.Error }
            }
            if (result !is ConnectionState.Connected) {
                return when ((result as? ConnectionState.Error)?.kind) {
                    BleError.ConnectFailed -> "Can't connect. App open elsewhere?"
                    BleError.BluetoothOff -> "Turn on Bluetooth"
                    else -> "Strip not responding"
                }
            }
            delay(POST_CONNECT_MS)
            val on = !settings.powerOn
            val r = (settings.color shr 16) and 0xFF
            val g = (settings.color shr 8) and 0xFF
            val b = settings.color and 0xFF
            if (on) {
                strip.setPower(true)
                strip.setColor(r, g, b)
                strip.setBrightness(settings.brightness)
            } else {
                strip.setPower(false)
            }
            strip.awaitWritesDone()
            store.saveState(settings.color, settings.brightness, on)
            TileService.getUpdater(applicationContext).requestUpdate(StripTileService::class.java)
            return if (on) "On" else "Off"
        } finally {
            strip.release()
        }
    }

    private fun canUseBluetooth(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        val enabled = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        return granted && enabled
    }

    private companion object {
        const val CONNECT_WAIT_MS = 15_000L
        const val POST_CONNECT_MS = 300L
        const val RESULT_HOLD_MS = 800L
    }
}
