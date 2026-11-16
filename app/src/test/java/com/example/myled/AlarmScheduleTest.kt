package com.example.myled

import com.example.myled.alarm.Alarm
import com.example.myled.alarm.AlarmCodec
import com.example.myled.alarm.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmScheduleTest {
    private val utc = ZoneId.of("UTC")

    /** 2026-11-16 is a Monday. */
    private fun at(day: Int, hour: Int, minute: Int = 0, zone: ZoneId = utc) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    private fun alarm(hour: Int, minute: Int = 0, days: Int = 0, fade: Int = 0, enabled: Boolean = true) =
        Alarm(id = 1, hour = hour, minute = minute, days = days, color = 0xFF0000, fadeMinutes = fade, enabled = enabled)

    private fun bits(vararg d: DayOfWeek) = d.fold(0) { acc, day -> acc or Alarm.dayBit(day) }

    @Test fun oneShotLaterToday() {
        assertEquals(at(16, 7, 30), AlarmSchedule.nextTrigger(alarm(7, 30), at(16, 6)))
    }

    @Test fun oneShotEarlierTodayRollsToTomorrow() {
        assertEquals(at(17, 7), AlarmSchedule.nextTrigger(alarm(7), at(16, 8)))
    }

    @Test fun exactlyNowIsNotNext() {
        assertEquals(at(17, 7), AlarmSchedule.nextTrigger(alarm(7), at(16, 7)))
    }

    @Test fun repeatPicksNextChosenDay() {
        val a = alarm(7, days = bits(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(at(18, 7), AlarmSchedule.nextTrigger(a, at(16, 9)))
        assertEquals(at(20, 7), AlarmSchedule.nextTrigger(a, at(18, 7, 1)))
    }

    @Test fun repeatWrapsToNextWeek() {
        val a = alarm(7, days = bits(DayOfWeek.MONDAY))
        assertEquals(at(23, 7), AlarmSchedule.nextTrigger(a, at(16, 7, 1)))
    }

    @Test fun fadeStartsEarly() {
        assertEquals(at(16, 6, 40), AlarmSchedule.nextTrigger(alarm(7, fade = 20), at(16, 6)))
    }

    @Test fun fadeAlreadyStartedMovesToNextOccurrence() {
        // At 06:50 the 20-minute fade for 07:00 began 10 minutes ago.
        assertEquals(at(17, 6, 40), AlarmSchedule.nextTrigger(alarm(7, fade = 20), at(16, 6, 50)))
    }

    @Test fun fadeCrossesMidnightIntoPreviousDay() {
        // Monday-only 00:10 alarm with a 30 min fade starts Sunday 23:40.
        val a = alarm(0, 10, days = bits(DayOfWeek.MONDAY), fade = 30)
        assertEquals(at(22, 23, 40), AlarmSchedule.nextTrigger(a, at(16, 12)))
    }

    @Test fun disabledHasNoTrigger() {
        assertNull(AlarmSchedule.nextTrigger(alarm(7, enabled = false), at(16, 6)))
    }

    @Test fun dstGapShiftsForward() {
        // Europe/London skips 01:00–02:00 on 2026-03-29.
        val london = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, london)
        val next = AlarmSchedule.nextTrigger(alarm(1, 30), now)!!
        assertEquals(ZonedDateTime.of(2026, 3, 29, 2, 30, 0, 0, london), next)
    }

    @Test fun daysLabels() {
        assertEquals("Once", AlarmSchedule.daysLabel(0))
        assertEquals("Every day", AlarmSchedule.daysLabel(Alarm.EVERY_DAY))
        assertEquals("Weekdays", AlarmSchedule.daysLabel(Alarm.WEEKDAYS))
        assertEquals("Weekends", AlarmSchedule.daysLabel(Alarm.WEEKEND))
        assertEquals("Mon Wed", AlarmSchedule.daysLabel(bits(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
    }

    @Test fun codecRoundTrip() {
        val list = listOf(
            Alarm(3, 6, 45, Alarm.WEEKDAYS, 0x12AB34, 70, 20, true),
            Alarm(9, 22, 5, 0, 0xFFFFFF, 100, 0, false),
        )
        assertEquals(list, AlarmCodec.decode(AlarmCodec.encode(list)))
    }

    @Test fun codecSkipsGarbageAndClamps() {
        val decoded = AlarmCodec.decode("1,7,0,0,255,50,0,1;oops;2,30,99,255,16777216,0,99,0")
        assertEquals(2, decoded.size)
        assertEquals(Alarm(2, 23, 59, Alarm.EVERY_DAY, 0, 1, 60, false), decoded[1])
        assertEquals(emptyList<Alarm>(), AlarmCodec.decode(null))
        assertEquals(emptyList<Alarm>(), AlarmCodec.decode(""))
    }
}
