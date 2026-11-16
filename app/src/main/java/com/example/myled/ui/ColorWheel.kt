package com.example.myled.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Pre-renders the wheel disc once per size: hue = angle (clockwise from 12), saturation = radius. */
fun renderColorWheel(radiusPx: Int): ImageBitmap {
    val size = radiusPx * 2
    val pixels = IntArray(size * size)
    val c = radiusPx - 0.5f
    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = x - c
            val dy = y - c
            val dist = hypot(dx, dy)
            val alpha = (radiusPx - dist + 0.5f).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val deg = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
            val sat = (dist / radiusPx).coerceIn(0f, 1f).toDouble()
            val (r, g, b) = HsvMath.hsvToRgb(deg / 360.0, sat, 1.0)
            pixels[y * size + x] = ((alpha * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}

fun DrawScope.drawColorWheel(
    wheel: ImageBitmap,
    center: Offset,
    radius: Float,
    markerColor: Color,
    markerOffset: Offset,
    alpha: Float,
) {
    drawImage(
        image = wheel,
        dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
        dstSize = IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt()),
        alpha = alpha,
    )
    drawCircle(Color.White.copy(alpha = 0.3f * alpha), radius, center, style = Stroke(1.dp.toPx()))
    val marker = center + markerOffset
    drawCircle(markerColor.copy(alpha = alpha), 7.dp.toPx(), marker)
    drawCircle(Color.White.copy(alpha = alpha), 7.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
}
