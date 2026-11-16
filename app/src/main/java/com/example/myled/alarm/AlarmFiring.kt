package com.example.myled.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * True while [AlarmFireService] owns the strip. The strip takes one BLE connection at a time, so
 * the app's control screen drops its link while this is set and reloads the saved state after.
 */
object AlarmFiring {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    internal fun set(value: Boolean) {
        _active.value = value
    }
}
