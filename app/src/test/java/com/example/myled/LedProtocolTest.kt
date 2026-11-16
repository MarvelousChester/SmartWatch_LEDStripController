package com.example.myled

import com.example.myled.ble.LedProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class LedProtocolTest {
    private fun hex(p: List<ByteArray>) = p.map { b -> b.joinToString("") { "%02x".format(it) } }

    private fun check(packets: List<ByteArray>, a: String, b: String) {
        assertEquals(listOf(a, b), hex(packets))
        packets.forEach {
            assertEquals(9, it.size)
            assertEquals(0x7E, it.first().toInt() and 0xFF)
            assertEquals(0xEF, it.last().toInt() and 0xFF)
        }
    }

    @Test fun power() {
        check(LedProtocol.power(true), "7e0004f00001ff00ef", "7e040401ffffff00ef")
        check(LedProtocol.power(false), "7e0004000000ff00ef", "7e040400ffffff00ef")
    }

    @Test fun colors() {
        check(LedProtocol.color(255, 0, 0), "7e000503ff000000ef", "7e070503ff000010ef")
        check(LedProtocol.color(0, 255, 0), "7e00050300ff0000ef", "7e07050300ff0010ef")
        check(LedProtocol.color(0, 0, 255), "7e0005030000ff00ef", "7e0705030000ff10ef")
        check(LedProtocol.color(255, 255, 255), "7e000503ffffff00ef", "7e070503ffffff10ef")
        check(LedProtocol.color(255, 0, 128), "7e000503ff008000ef", "7e070503ff008010ef")
        check(LedProtocol.color(127, 0, 255), "7e0005037f00ff00ef", "7e0705037f00ff10ef")
    }

    @Test fun brightness() {
        check(LedProtocol.brightness(1), "7e00010100000000ef", "7e04010100ffff00ef")
        check(LedProtocol.brightness(50), "7e00013200000000ef", "7e04013200ffff00ef")
        check(LedProtocol.brightness(75), "7e00014b00000000ef", "7e04014b00ffff00ef")
        check(LedProtocol.brightness(100), "7e00016400000000ef", "7e04016400ffff00ef")
    }

    @Test fun clamping() {
        assertEquals(hex(LedProtocol.color(255, 0, 255)), hex(LedProtocol.color(300, -50, 1000)))
        assertEquals(hex(LedProtocol.brightness(100)), hex(LedProtocol.brightness(200)))
        assertEquals(hex(LedProtocol.brightness(0)), hex(LedProtocol.brightness(-10)))
    }
}
