package com.example.myled.ui

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Colour-wheel maths. Hue runs clockwise from 12 o'clock; saturation grows from centre to rim. */
object WheelGeometry {
    /** Within this fraction of the radius the pick snaps to pure white. */
    const val WHITE_SNAP = 0.06

    data class Pick(val hueDeg: Double, val saturation: Double, val r: Int, val g: Int, val b: Int)

    /** [dx], [dy] are relative to the wheel centre in px, y growing downward. */
    fun pick(dx: Float, dy: Float, radius: Float): Pick {
        val dist = hypot(dx, dy)
        var sat = (dist / radius).coerceIn(0f, 1f).toDouble()
        if (dist < WHITE_SNAP * radius) sat = 0.0
        val deg = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
        val (r, g, b) = HsvMath.hsvToRgb(deg / 360.0, sat, 1.0)
        return Pick(deg, sat, r, g, b)
    }

    /** Marker offset from the wheel centre (px, y down) for an RGB colour. */
    fun markerOffset(r: Int, g: Int, b: Int, radius: Float): Pair<Float, Float> {
        val (h, s, _) = HsvMath.rgbToHsv(r, g, b)
        val angle = h * 2 * Math.PI
        val dist = s * radius
        return Pair((sin(angle) * dist).toFloat(), (-cos(angle) * dist).toFloat())
    }
}
