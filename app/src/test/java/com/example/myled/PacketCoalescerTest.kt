package com.example.myled

import com.example.myled.ble.LedProtocol
import com.example.myled.ble.PacketCoalescer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketCoalescerTest {
    private fun assertSame(expected: List<ByteArray>, actual: List<ByteArray>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertArrayEquals(expected[it], actual[it]) }
    }

    @Test fun colorsKeepOnlyNewest() {
        val input = LedProtocol.color(255, 0, 0) + LedProtocol.color(0, 255, 0) + LedProtocol.color(0, 0, 255)
        assertEquals(6, input.size)
        assertSame(LedProtocol.color(0, 0, 255), PacketCoalescer.filter(input))
    }

    @Test fun brightnessKeepOnlyNewest() {
        val input = LedProtocol.brightness(10) + LedProtocol.brightness(50) + LedProtocol.brightness(100)
        assertSame(LedProtocol.brightness(100), PacketCoalescer.filter(input))
    }

    @Test fun powerIsNeverDroppedAndOrderIsPreserved() {
        val input = LedProtocol.color(255, 0, 0) + LedProtocol.power(true) + LedProtocol.color(0, 255, 0)
        assertSame(LedProtocol.power(true) + LedProtocol.color(0, 255, 0), PacketCoalescer.filter(input))
    }

    @Test fun mixedKeepsRelativeOrder() {
        val input = LedProtocol.brightness(10) + LedProtocol.color(1, 2, 3) + LedProtocol.brightness(20) +
            LedProtocol.power(false)
        assertSame(
            LedProtocol.color(1, 2, 3) + LedProtocol.brightness(20) + LedProtocol.power(false),
            PacketCoalescer.filter(input),
        )
    }
}
