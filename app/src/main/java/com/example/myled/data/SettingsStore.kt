package com.example.myled.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Shared by [SettingsStore] and the alarm store: one file per app. */
internal val Context.dataStore by preferencesDataStore(name = "led_settings")

data class Settings(
    val lastDeviceAddress: String = "",
    val lastDeviceName: String = "",
    /** Packed 0xRRGGBB. */
    val color: Int = DEFAULT_COLOR,
    val brightness: Int = 100,
    val powerOn: Boolean = true,
) {
    companion object {
        const val DEFAULT_COLOR = 0xFF1600
    }
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    suspend fun load(): Settings = store.data.first().toSettings()

    suspend fun saveDevice(address: String, name: String) {
        store.edit {
            it[KEY_ADDRESS] = address
            it[KEY_NAME] = name
        }
    }

    suspend fun saveState(color: Int, brightness: Int, powerOn: Boolean) {
        store.edit {
            it[KEY_COLOR] = "#%06X".format(color and 0xFFFFFF)
            it[KEY_BRIGHTNESS] = brightness.coerceIn(1, 100)
            it[KEY_POWER] = powerOn
        }
    }

    private fun Preferences.toSettings() = Settings(
        lastDeviceAddress = this[KEY_ADDRESS].orEmpty(),
        lastDeviceName = this[KEY_NAME].orEmpty(),
        color = parseColor(this[KEY_COLOR]),
        brightness = (this[KEY_BRIGHTNESS] ?: 100).coerceIn(1, 100),
        powerOn = this[KEY_POWER] ?: true,
    )

    private fun parseColor(hex: String?): Int =
        hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toIntOrNull(16) ?: Settings.DEFAULT_COLOR

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("last_device_address")
        val KEY_NAME = stringPreferencesKey("last_device_name")
        val KEY_COLOR = stringPreferencesKey("last_color")
        val KEY_BRIGHTNESS = intPreferencesKey("last_brightness")
        val KEY_POWER = booleanPreferencesKey("power_on")
    }
}
