package com.example.myled.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** [R] is half the smaller screen dimension; the arc centreline sits at 0.93 R. */
fun DrawScope.drawBrightnessArc(
    center: Offset,
    screenRadius: Float,
    percent: Int,
    color: Color,
    alpha: Float,
) {
    val radius = 0.93f * screenRadius
    val strokeWidth = 0.07f * screenRadius
    val topLeft = Offset(center.x - radius, center.y - radius)
    val size = Size(radius * 2, radius * 2)
    // Compose measures arc angles clockwise from 3 o'clock; ours are clockwise from 12.
    val composeStart = (ArcGeometry.START_DEG - 90.0).toFloat()
    val sweep = ArcGeometry.SWEEP_DEG.toFloat()
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)

    drawArc(Color.White.copy(alpha = 0.2f * alpha), composeStart, sweep, false, topLeft, size, style = stroke)

    val fraction = ArcGeometry.fractionOfPercent(percent)
    val filled = (sweep * fraction).toFloat()
    if (filled > 0f) {
        drawArc(color.copy(alpha = alpha), composeStart, filled, false, topLeft, size, style = stroke)
    }

    val angle = Math.toRadians(ArcGeometry.START_DEG + ArcGeometry.SWEEP_DEG * fraction)
    val knob = Offset(
        center.x + (sin(angle) * radius).toFloat(),
        center.y - (cos(angle) * radius).toFloat(),
    )
    drawCircle(Color.White.copy(alpha = alpha), 0.05f * screenRadius, knob)
}
