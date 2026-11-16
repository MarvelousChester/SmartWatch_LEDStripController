package com.example.myled.ui

/** Mirrors Python's `colorsys` plus the desktop's `int()` truncation (never rounding). */
object HsvMath {

    /** [h] in 0..1, [s] and [v] in 0..1. Each channel is `(channel * 255).toInt()`. */
    fun hsvToRgb(h: Double, s: Double, v: Double): Triple<Int, Int, Int> {
        if (s == 0.0) {
            val c = (v * 255).toInt()
            return Triple(c, c, c)
        }
        val scaled = h * 6.0
        val i = scaled.toInt()
        val f = scaled - i
        val p = v * (1.0 - s)
        val q = v * (1.0 - s * f)
        val t = v * (1.0 - s * (1.0 - f))
        val (r, g, b) = when (i % 6) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return Triple((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    /** Port of `colorsys.rgb_to_hsv` for 0..255 channels; hue in 0..1. */
    fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val maxc = maxOf(rf, gf, bf)
        val minc = minOf(rf, gf, bf)
        val v = maxc
        if (minc == maxc) return Triple(0.0, 0.0, v)
        val span = maxc - minc
        val s = span / maxc
        val rc = (maxc - rf) / span
        val gc = (maxc - gf) / span
        val bc = (maxc - bf) / span
        val h = when (maxc) {
            rf -> bc - gc
            gf -> 2.0 + rc - bc
            else -> 4.0 + gc - rc
        }
        return Triple(((h / 6.0) % 1.0 + 1.0) % 1.0, s, v)
    }
}
