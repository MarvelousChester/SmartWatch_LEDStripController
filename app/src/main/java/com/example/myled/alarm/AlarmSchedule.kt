package com.example.myled.alarm

import java.time.ZonedDateTime

object AlarmSchedule {

    /**
     * Next moment the alarm should start working (the set time minus the fade), strictly after
     * [now], or null when disabled. Only the next 8 days are checked, which always covers a week.
     */
    fun nextTrigger(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        for (offset in 0L..8L) {
            val date = now.toLocalDate().plusDays(offset)
            if (alarm.repeats && !alarm.isOn(date.dayOfWeek)) continue
            // atZone resolves DST gaps by shifting forward, so a 02:30 alarm still fires on that day.
            val target = date.atTime(alarm.hour, alarm.minute).atZone(now.zone)
            val start = target.minusMinutes(alarm.fadeMinutes.toLong())
            if (start.isAfter(now)) return start
        }
        return null
    }

    /** Short label for the repeat days: "Once", "Every day", "Weekdays", "Weekends" or "Mon Wed". */
    fun daysLabel(days: Int): String = when (days and Alarm.EVERY_DAY) {
        0 -> "Once"
        Alarm.EVERY_DAY -> "Every day"
        Alarm.WEEKDAYS -> "Weekdays"
        Alarm.WEEKEND -> "Weekends"
        else -> DAY_SHORT.indices.filter { days and (1 shl it) != 0 }.joinToString(" ") { DAY_SHORT[it] }
    }

    val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
