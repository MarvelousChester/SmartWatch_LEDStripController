package com.example.myled.alarm

import java.time.DayOfWeek

/**
 * Turns the strip on at [hour]:[minute] in [color] at [brightness].
 *
 * [days] is a bitmask with Monday = bit 0 … Sunday = bit 6; 0 means "once" (the alarm disables
 * itself after firing). [fadeMinutes] > 0 ramps brightness up so the target is reached at the set
 * time.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    /** Packed 0xRRGGBB. */
    val color: Int,
    val brightness: Int = 100,
    val fadeMinutes: Int = 0,
    val enabled: Boolean = true,
) {
    val repeats get() = days != 0

    fun isOn(day: DayOfWeek) = days and dayBit(day) != 0

    companion object {
        const val NEW_ID = -1
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000
        const val EVERY_DAY = 0b1111111
        val FADE_CHOICES = listOf(0, 5, 10, 20, 30)

        fun dayBit(day: DayOfWeek) = 1 shl (day.value - 1)
    }
}

/** Compact text encoding for DataStore: one alarm per `;`, fields separated by `,`. */
object AlarmCodec {
    fun encode(alarms: List<Alarm>): String = alarms.joinToString(";") {
        listOf(
            it.id, it.hour, it.minute, it.days, it.color and 0xFFFFFF,
            it.brightness, it.fadeMinutes, if (it.enabled) 1 else 0,
        ).joinToString(",")
    }

    /** Skips malformed entries rather than failing the whole list. */
    fun decode(text: String?): List<Alarm> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(';').mapNotNull { entry ->
            val f = entry.split(',').map { it.trim().toIntOrNull() }
            if (f.size != 8 || f.any { it == null }) return@mapNotNull null
            Alarm(
                id = f[0]!!,
                hour = f[1]!!.coerceIn(0, 23),
                minute = f[2]!!.coerceIn(0, 59),
                days = f[3]!! and Alarm.EVERY_DAY,
                color = f[4]!! and 0xFFFFFF,
                brightness = f[5]!!.coerceIn(1, 100),
                fadeMinutes = f[6]!!.coerceIn(0, 60),
                enabled = f[7] == 1,
            )
        }
    }
}
