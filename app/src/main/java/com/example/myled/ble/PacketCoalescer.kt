package com.example.myled.ble

/** Port of `_filter_stale_packets`: keeps drags smooth by dropping superseded colour/brightness. */
object PacketCoalescer {

    /**
     * Walks newest to oldest keeping only the newest colour packet per variant (`packet[1]`) and the
     * newest brightness packet per variant. Power packets (and anything unrecognised) are never
     * dropped. Relative order of the survivors is preserved.
     */
    fun filter(packets: List<ByteArray>): List<ByteArray> {
        val seenColor = HashSet<Byte>()
        val seenBrightness = HashSet<Byte>()
        val keep = BooleanArray(packets.size)
        for (i in packets.indices.reversed()) {
            val p = packets[i]
            if (p.size < 3) {
                keep[i] = true
                continue
            }
            keep[i] = when (p[2].toInt()) {
                LedProtocol.CMD_COLOR -> seenColor.add(p[1])
                LedProtocol.CMD_BRIGHTNESS -> seenBrightness.add(p[1])
                else -> true
            }
        }
        return packets.filterIndexed { i, _ -> keep[i] }
    }
}
