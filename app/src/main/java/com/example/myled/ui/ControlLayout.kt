package com.example.myled.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.myled.ble.ConnectionState
import kotlin.math.min

/** Geometry of the control view (spec 5.1), shared by the app screen, hit-testing and the tile. */
class ControlLayout(val width: Float, val height: Float) {
    /** Half the smaller screen dimension. */
    val radius = min(width, height) / 2f
    val center = Offset(width / 2f, height / 2f)
    val wheelCenter = Offset(center.x, center.y - 0.06f * radius)
    val wheelRadius = 0.62f * radius
    val powerCenter = Offset(center.x, center.y + 0.80f * radius)
    val powerDrawRadius = 0.15f * radius
}

/** Draws the wheel, brightness arc and power button exactly as the app's control screen does. */
fun DrawScope.drawControlView(
    layout: ControlLayout,
    wheel: ImageBitmap,
    r: Int,
    g: Int,
    b: Int,
    brightness: Int,
    connection: ConnectionState,
    powerOn: Boolean,
    spinDegrees: Float,
    alpha: Float,
) {
    val stripColor = Color(r, g, b)
    val marker = WheelGeometry.markerOffset(r, g, b, layout.wheelRadius)
    drawColorWheel(wheel, layout.wheelCenter, layout.wheelRadius, stripColor, Offset(marker.first, marker.second), alpha)
    drawBrightnessArc(layout.center, layout.radius, brightness, stripColor, alpha)
    drawPowerButton(layout.powerCenter, layout.powerDrawRadius, connection, powerOn, stripColor, spinDegrees)
}
