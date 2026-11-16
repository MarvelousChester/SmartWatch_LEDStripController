package com.example.myled.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.myled.ble.ConnectionState

private val Amber = Color(0xFFFFB300)
private val ErrorRed = Color(0xFFE53935)
private val DiscGrey = Color(0xFF9E9E9E)
private val DiscDark = Color(0xFF1C1C1C)

/** Ring colour/animation and glyph style for each [ConnectionState] (see spec 5.4). */
fun DrawScope.drawPowerButton(
    center: Offset,
    radius: Float,
    connection: ConnectionState,
    powerOn: Boolean,
    stripColor: Color,
    spinDegrees: Float,
) {
    val ringWidth = radius * 0.14f
    val ringRadius = radius - ringWidth / 2
    val ringTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
    val ringSize = Size(ringRadius * 2, ringRadius * 2)
    val ring = Stroke(ringWidth, cap = StrokeCap.Round)

    var disc = DiscDark
    val glyphColor: Color
    var glyphAlpha = 1f
    var glyphWidth = radius * 0.12f

    when {
        connection is ConnectionState.Connected && powerOn -> {
            disc = stripColor
            glyphColor = Color(0xFF101010)
            glyphWidth = radius * 0.16f
        }
        connection is ConnectionState.Connected -> glyphColor = Color.White
        else -> {
            glyphColor = DiscGrey
            glyphAlpha = 0.5f
        }
    }
    drawCircle(disc, radius - ringWidth * 1.5f, center)

    when (connection) {
        is ConnectionState.Connected -> {
            val c = if (powerOn) stripColor else Color.White.copy(alpha = 0.4f)
            drawCircle(c, ringRadius, center, style = ring)
        }
        is ConnectionState.Scanning, is ConnectionState.Connecting -> {
            drawCircle(Amber.copy(alpha = 0.25f), ringRadius, center, style = ring)
            drawArc(Amber, spinDegrees - 90f, 100f, false, ringTopLeft, ringSize, style = ring)
        }
        is ConnectionState.Error -> drawCircle(ErrorRed, ringRadius, center, style = ring)
        ConnectionState.Disconnected -> drawCircle(DiscGrey.copy(alpha = 0.6f), ringRadius, center, style = ring)
    }
    drawPowerGlyph(center, radius * 0.42f, glyphColor.copy(alpha = glyphAlpha), glyphWidth)
}

fun DrawScope.drawPowerGlyph(center: Offset, radius: Float, color: Color, strokeWidth: Float) {
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
    drawArc(
        color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = stroke,
    )
    drawLine(
        color,
        Offset(center.x, center.y - radius * 1.1f),
        Offset(center.x, center.y - radius * 0.1f),
        strokeWidth,
        StrokeCap.Round,
    )
}
