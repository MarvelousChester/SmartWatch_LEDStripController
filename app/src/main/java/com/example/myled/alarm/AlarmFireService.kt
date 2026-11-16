package com.example.myled.alarm

import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.wear.tiles.TileService
import com.example.myled.ble.ConnectionState
import com.example.myled.ble.GattLedStrip
import com.example.myled.data.SettingsStore
import com.example.myled.tile.StripTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "LedAlarm"

/**
 * Connects to the saved strip and turns it on in the alarm's colour, either at once or with a
 * brightness ramp ("sunrise") that reaches the target at the set time. Alarms that fire together
 * run one after another.
 */
class AlarmFireService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runLock = Mutex()
    private var pending = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, AlarmNotifications.ONGOING_ID, AlarmNotifications.ongoing(this), type)
        } catch (e: Exception) {
            Log.w(TAG, "can't start foreground", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val id = intent?.getIntExtra(AlarmReceiver.EXTRA_ID, Alarm.NEW_ID) ?: Alarm.NEW_ID
        if (id == Alarm.NEW_ID) {
            if (pending == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        pending++
        acquireWakeLock()
        scope.launch {
            try {
                runLock.withLock { fire(id) }
            } catch (e: Exception) {
                Log.w(TAG, "alarm $id failed", e)
            } finally {
                launch(Dispatchers.Main) {
                    pending--
                    if (pending == 0) {
                        releaseWakeLock()
                        ServiceCompat.stopForeground(this@AlarmFireService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AlarmFiring.set(false)
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun fire(id: Int) {
        val alarm = advance(this, id) ?: return
        val store = SettingsStore(applicationContext)
        val settings = store.load()
        val failure = when {
            settings.lastDeviceAddress.isEmpty() -> AlarmNotifications.Failure.NoDevice
            !AlarmNotifications.hasBluetoothPermission(this) -> AlarmNotifications.Failure.Permission
            getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled != true ->
                AlarmNotifications.Failure.BluetoothOff
            else -> null
        }
        if (failure != null) {
            AlarmNotifications.showFailure(this, failure)
            return
        }

        val fadeMs = alarm.fadeMinutes * 60_000L
        val startedAt = SystemClock.elapsedRealtime()
        val r = (alarm.color shr 16) and 0xFF
        val g = (alarm.color shr 8) and 0xFF
        val b = alarm.color and 0xFF
        val target = alarm.brightness.coerceIn(1, 100)

        AlarmFiring.set(true)
        delay(APP_RELEASE_MS) // let an open control screen drop its link
        val strip = GattLedStrip(applicationContext)
        try {
            if (!connect(strip, settings.lastDeviceAddress, CONNECT_ATTEMPTS)) {
                AlarmNotifications.showFailure(this, AlarmNotifications.Failure.Unreachable)
                return
            }
            fun levelNow(): Int {
                if (fadeMs <= 0) return target
                val frac = ((SystemClock.elapsedRealtime() - startedAt).toDouble() / fadeMs).coerceIn(0.0, 1.0)
                return max(1, (1 + (target - 1) * frac).roundToInt())
            }
            fun turnOn(level: Int) {
                strip.setPower(true)
                strip.setColor(r, g, b)
                strip.setBrightness(level)
            }

            var level = levelNow()
            turnOn(level)
            var reconnectUsed = false
            while (level < target) {
                delay(RAMP_STEP_MS)
                if (strip.state.value !is ConnectionState.Connected) {
                    if (reconnectUsed || !connect(strip, settings.lastDeviceAddress, 1)) break
                    reconnectUsed = true
                    level = levelNow()
                    turnOn(level)
                    continue
                }
                val next = levelNow()
                if (next != level) {
                    level = next
                    strip.setBrightness(level)
                }
            }
            strip.awaitWritesDone()
            store.saveState(alarm.color, target, powerOn = true)
            runCatching {
                TileService.getUpdater(applicationContext).requestUpdate(StripTileService::class.java)
            }
            Log.d(TAG, "alarm $id done")
        } finally {
            strip.release()
            AlarmFiring.set(false)
        }
    }

    private suspend fun connect(strip: GattLedStrip, address: String, attempts: Int): Boolean {
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS)
            strip.connect(address)
            val result = withTimeoutOrNull(CONNECT_WAIT_MS) {
                strip.state.first { it is ConnectionState.Connected || it is ConnectionState.Error }
            }
            if (result is ConnectionState.Connected) {
                delay(POST_CONNECT_MS)
                return true
            }
            Log.d(TAG, "connect attempt ${attempt + 1} failed: $result")
        }
        return false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyLED:alarm")
            ?.apply { acquire(WAKE_LOCK_MAX_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val APP_RELEASE_MS = 1_500L
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_WAIT_MS = 15_000L
        private const val RETRY_DELAY_MS = 5_000L
        private const val POST_CONNECT_MS = 300L
        private const val RAMP_STEP_MS = 2_000L
        private const val WAKE_LOCK_MAX_MS = 35 * 60_000L

        /**
         * Moves alarm [id] past this firing: disables a one-shot alarm, then re-registers every
         * alarm. Returns the alarm as it was when it fired, or null if it no longer exists.
         */
        suspend fun advance(context: Context, id: Int): Alarm? {
            val store = AlarmStore(context)
            val alarm = store.get(id)
            if (alarm != null && !alarm.repeats) store.setEnabled(id, false)
            AlarmScheduler.rescheduleAll(context)
            return alarm?.takeIf { it.enabled }
        }
    }
}
