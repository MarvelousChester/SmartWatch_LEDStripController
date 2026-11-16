package com.example.myled

import com.example.myled.ble.DeviceMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMatcherTest {
    @Test fun likelyControllers() {
        assertTrue(DeviceMatcher.isLikelyController("ELK-BLEDOM"))
        assertTrue(DeviceMatcher.isLikelyController("duoCo-Strip"))
        assertTrue(DeviceMatcher.isLikelyController("LEDBLE-1234"))
        assertTrue(DeviceMatcher.isLikelyController("My Smart Light"))
    }

    @Test fun others() {
        assertFalse(DeviceMatcher.isLikelyController("Logitech Mouse"))
        assertFalse(DeviceMatcher.isLikelyController(null))
        assertFalse(DeviceMatcher.isLikelyController(""))
    }
}
