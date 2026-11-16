package com.example.myled.ui

import kotlin.math.atan2
import kotlin.math.roundToInt

/** Brightness arc: starts at 240 deg and sweeps 240 deg clockwise (angles clockwise from 12 o'clock). */
object ArcGeometry {
    const val START_DEG = 240.0
    const val SWEEP_DEG = 240.0
    const val TOLERANCE_DEG = 6.0

    /** Clockwise angle from 12 o'clock in 0..360 for a point relative to the centre (y down). */
    fun angleOf(dx: Float, dy: Float): Double =
        (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0

    /** Degrees past the arc start, 0..360. */
    private fun relative(angle: Double): Double = (angle - START_DEG + 360.0) % 360.0

    fun isOnArc(angle: Double): Boolean {
        val rel = relative(angle)
        return rel <= SWEEP_DEG + TOLERANCE_DEG || rel >= 360.0 - TOLERANCE_DEG
    }

    /** 0..1 along the arc; points in the gap clamp to the nearer end. */
    fun fraction(angle: Double): Double {
        val rel = relative(angle)
        val gapMid = SWEEP_DEG + (360.0 - SWEEP_DEG) / 2
        return when {
            rel <= SWEEP_DEG -> rel / SWEEP_DEG
            rel < gapMid -> 1.0
            else -> 0.0
        }
    }

    fun percentOf(angle: Double): Int = 1 + (fraction(angle) * 99).roundToInt()

    fun fractionOfPercent(percent: Int): Double = (percent.coerceIn(1, 100) - 1) / 99.0
}
