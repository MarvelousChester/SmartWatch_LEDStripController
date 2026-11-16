package com.example.myled.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired by AlarmManager; hands the work to [AlarmFireService]. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ID, Alarm.NEW_ID)
        if (id == Alarm.NEW_ID) return

        // A connectedDevice foreground service can't start without BLUETOOTH_CONNECT, so report
        // that here instead; still move the alarm on to its next occurrence.
        if (!AlarmNotifications.hasBluetoothPermission(context)) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    AlarmFireService.advance(context, id)
                    AlarmNotifications.showFailure(context, AlarmNotifications.Failure.Permission)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmFireService::class.java).putExtra(EXTRA_ID, id),
        )
    }

    companion object {
        const val ACTION_FIRE = "com.example.myled.action.ALARM_FIRE"
        const val EXTRA_ID = "alarm_id"
    }
}

/** Re-registers alarms after anything that clears or shifts AlarmManager entries. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                AlarmScheduler.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
