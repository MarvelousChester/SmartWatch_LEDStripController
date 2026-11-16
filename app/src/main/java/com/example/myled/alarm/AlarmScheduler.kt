package com.example.myled.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.myled.presentation.MainActivity
import java.time.ZonedDateTime

private const val TAG = "LedAlarm"

object AlarmScheduler {

    /** Registers the next trigger of every enabled alarm and cancels the rest. */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now()
        for (alarm in AlarmStore(app).load()) {
            val pi = firePendingIntent(app, alarm.id)
            am.cancel(pi)
            val at = AlarmSchedule.nextTrigger(alarm, now) ?: continue
            val ms = at.toInstant().toEpochMilli()
            if (canScheduleExact(am)) {
                val show = PendingIntent.getActivity(
                    app, 0, Intent(app, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(ms, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
            Log.d(TAG, "alarm ${alarm.id} scheduled for $at")
        }
    }

    /** Drops the registration of a deleted alarm. */
    fun cancel(context: Context, id: Int) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(firePendingIntent(app, id))
    }

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.let(::canScheduleExact) ?: false

    private fun canScheduleExact(am: AlarmManager) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun firePendingIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
