package com.example.myled

import com.example.myled.ui.ArcGeometry
import com.example.myled.ui.HsvMath
import com.example.myled.ui.WheelGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WheelGeometryTest {
    private val rw = 100f

    private fun colorOf(dx: Float, dy: Float) =
        WheelGeometry.pick(dx, dy, rw).let { Triple(it.r, it.g, it.b) }

    @Test fun topRimIsRed() = assertEquals(Triple(255, 0, 0), colorOf(0f, -rw))

    @Test fun rightRimIsHue90() {
        val p = WheelGeometry.pick(rw, 0f, rw)
        assertEquals(90.0, p.hueDeg, 1e-6)
        assertEquals(HsvMath.hsvToRgb(0.25, 1.0, 1.0), Triple(p.r, p.g, p.b))
    }

    @Test fun centreIsWhite() = assertEquals(Triple(255, 255, 255), colorOf(0f, 0f))

    @Test fun pastRimClampsToRimColour() {
        assertEquals(colorOf(0f, -rw), colorOf(0f, -3 * rw))
        assertEquals(1.0, WheelGeometry.pick(rw * 2, 0f, rw).saturation, 1e-9)
    }

    @Test fun nearCentreSnapsToWhite() = assertEquals(Triple(255, 255, 255), colorOf(0.05f * rw, 0f))

    @Test fun markerRoundTripsThroughRgb() {
        val p = WheelGeometry.pick(60f, -30f, rw)
        val (mx, my) = WheelGeometry.markerOffset(p.r, p.g, p.b, rw)
        assertTrue(abs(mx - 60f) < 1.5f && abs(my + 30f) < 1.5f)
    }

    @Test fun arcMapsAnglesToPercent() {
        assertEquals(1, ArcGeometry.percentOf(240.0))
        assertEquals(100, ArcGeometry.percentOf(120.0))
        assertTrue(ArcGeometry.percentOf(0.0) in 50..51)
        assertTrue(ArcGeometry.isOnArc(0.0))
        assertTrue(ArcGeometry.isOnArc(236.0))
        assertTrue(ArcGeometry.isOnArc(125.0))
        assertFalse(ArcGeometry.isOnArc(180.0))
        // Points in the gap clamp to the nearer end.
        assertEquals(100, ArcGeometry.percentOf(150.0))
        assertEquals(1, ArcGeometry.percentOf(210.0))
    }
}
