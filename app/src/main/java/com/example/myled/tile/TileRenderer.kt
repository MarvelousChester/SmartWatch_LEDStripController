package com.example.myled.tile

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.myled.ble.ConnectionState
import com.example.myled.data.Settings
import com.example.myled.ui.ControlLayout
import com.example.myled.ui.drawControlView
import com.example.myled.ui.renderColorWheel
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Raw ARGB_8888 pixels of the control view, ready for a tile's inline image resource. */
class RenderedView(val pixels: ByteArray, val widthPx: Int, val heightPx: Int)

/**
 * Renders the same wheel / brightness arc / power button as the app's control screen into a bitmap.
 * Tiles cannot draw or take drags, so the view is an image; the pixel budget keeps the inline
 * resource well inside the binder transaction limit.
 */
fun renderControlView(
    widthDp: Float,
    heightDp: Float,
    density: Float,
    settings: Settings,
    hasDevice: Boolean,
): RenderedView {
    val budgetScale = sqrt(MAX_BYTES / (4f * widthDp * heightDp))
    val scale = min(density, budgetScale)
    val w = (widthDp * scale).roundToInt()
    val h = (heightDp * scale).roundToInt()

    val image = ImageBitmap(w, h)
    val layout = ControlLayout(w.toFloat(), h.toFloat())
    val wheel = renderColorWheel(layout.wheelRadius.roundToInt())
    val connection: ConnectionState =
        if (hasDevice) ConnectionState.Connected("", null) else ConnectionState.Disconnected
    // Same dimming the app uses when the strip is off or unavailable.
    val alpha = if (hasDevice && settings.powerOn) 1f else 0.35f

    CanvasDrawScope().draw(Density(scale), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
        drawControlView(
            layout, wheel,
            (settings.color shr 16) and 0xFF, (settings.color shr 8) and 0xFF, settings.color and 0xFF,
            settings.brightness, connection, settings.powerOn, 0f, alpha,
        )
    }

    val bitmap = image.asAndroidBitmap()
    val buffer = ByteBuffer.allocate(bitmap.byteCount)
    bitmap.copyPixelsToBuffer(buffer)
    return RenderedView(buffer.array(), w, h)
}

private const val MAX_BYTES = 500_000f
