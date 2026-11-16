package com.example.myled

import com.example.myled.ui.HsvMath
import org.junit.Assert.assertEquals
import org.junit.Test

class HsvMathTest {
    private fun rgb(deg: Double, sat: Double) = HsvMath.hsvToRgb(deg / 360.0, sat, 1.0)

    @Test fun vectors() {
        assertEquals(Triple(255, 0, 0), rgb(0.0, 1.0))
        assertEquals(Triple(255, 255, 0), rgb(60.0, 1.0))
        assertEquals(Triple(0, 255, 0), rgb(120.0, 1.0))
        assertEquals(Triple(0, 0, 255), rgb(240.0, 1.0))
        assertEquals(Triple(127, 0, 255), rgb(270.0, 1.0))
        assertEquals(Triple(255, 255, 255), rgb(0.0, 0.0))
        assertEquals(Triple(255, 127, 127), rgb(0.0, 0.5))
        assertEquals(Triple(255, 159, 63), rgb(30.0, 0.75))
    }

    @Test fun truncatesRatherThanRounds() {
        // 127.5 would round to 128 and 63.75 to 64.
        assertEquals(127, rgb(270.0, 1.0).first)
        assertEquals(63, rgb(30.0, 0.75).third)
    }

    @Test fun rgbToHsv() {
        val (h, s, v) = HsvMath.rgbToHsv(255, 0, 0)
        assertEquals(0.0, h, 1e-9)
        assertEquals(1.0, s, 1e-9)
        assertEquals(1.0, v, 1e-9)
        val (h2, s2, _) = HsvMath.rgbToHsv(0, 255, 0)
        assertEquals(1.0 / 3, h2, 1e-9)
        assertEquals(1.0, s2, 1e-9)
        assertEquals(0.0, HsvMath.rgbToHsv(255, 255, 255).second, 1e-9)
    }
}
