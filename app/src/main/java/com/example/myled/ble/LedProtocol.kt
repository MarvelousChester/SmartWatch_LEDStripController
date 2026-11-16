package com.example.myled.ble

/**
 * Pure-Kotlin port of `DuoCoProtocol` (core/protocol.py). Every logical command is two 9-byte
 * packets: variant A (duoCo standard) then variant B (ELK-BLEDOM alternate); the strip ignores the
 * variant it does not understand.
 */
object LedProtocol {
    const val PACKET_SIZE = 9

    /** Value of `packet[2]` identifying the command family. */
    const val CMD_BRIGHTNESS = 0x01
    const val CMD_POWER = 0x04
    const val CMD_COLOR = 0x05

    fun power(on: Boolean): List<ByteArray> =
        if (on) {
            listOf(
                bytes(0x7E, 0x00, 0x04, 0xF0, 0x00, 0x01, 0xFF, 0x00, 0xEF),
                bytes(0x7E, 0x04, 0x04, 0x01, 0xFF, 0xFF, 0xFF, 0x00, 0xEF),
            )
        } else {
            listOf(
                bytes(0x7E, 0x00, 0x04, 0x00, 0x00, 0x00, 0xFF, 0x00, 0xEF),
                bytes(0x7E, 0x04, 0x04, 0x00, 0xFF, 0xFF, 0xFF, 0x00, 0xEF),
            )
        }

    fun color(r: Int, g: Int, b: Int): List<ByteArray> {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        return listOf(
            bytes(0x7E, 0x00, 0x05, 0x03, rr, gg, bb, 0x00, 0xEF),
            bytes(0x7E, 0x07, 0x05, 0x03, rr, gg, bb, 0x10, 0xEF),
        )
    }

    /** Strip-side dimmer; the byte is the decimal percent. Never pre-scale RGB by it. */
    fun brightness(level: Int): List<ByteArray> {
        val l = level.coerceIn(0, 100)
        return listOf(
            bytes(0x7E, 0x00, 0x01, l, 0x00, 0x00, 0x00, 0x00, 0xEF),
            bytes(0x7E, 0x04, 0x01, l, 0x00, 0xFF, 0xFF, 0x00, 0xEF),
        )
    }

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
}
