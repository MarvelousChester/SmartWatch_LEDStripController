package com.example.myled.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.myled.R
import com.example.myled.presentation.MainActivity

object AlarmNotifications {
    private const val CHANNEL_ID = "led_alarms"
    const val ONGOING_ID = 1001
    private const val FAILURE_ID = 1002

    enum class Failure(val text: Int) {
        Permission(R.string.alarm_fail_permission),
        BluetoothOff(R.string.err_bluetooth_off),
        NoDevice(R.string.state_no_device),
        Unreachable(R.string.alarm_fail_unreachable),
    }

    fun ongoing(context: Context): Notification =
        builder(context)
            .setContentText(context.getString(R.string.alarm_running))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()

    fun showFailure(context: Context, failure: Failure) {
        if (!canPost(context)) return
        val n = builder(context)
            .setContentText(context.getString(failure.text))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(FAILURE_ID, n)
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun canPost(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun builder(context: Context): Notification.Builder {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentIntent(open)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
