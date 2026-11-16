package com.example.myled.alarm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myled.data.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AlarmStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val alarms: Flow<List<Alarm>> = store.data.map { AlarmCodec.decode(it[KEY_ALARMS]).sortedBy(::minuteOfDay) }

    suspend fun load(): List<Alarm> = alarms.first()

    suspend fun get(id: Int): Alarm? = load().firstOrNull { it.id == id }

    /** Inserts when [alarm] has [Alarm.NEW_ID]; returns the stored alarm. */
    suspend fun upsert(alarm: Alarm): Alarm {
        var saved = alarm
        store.edit { prefs ->
            val list = AlarmCodec.decode(prefs[KEY_ALARMS]).toMutableList()
            if (alarm.id == Alarm.NEW_ID) {
                val id = (prefs[KEY_NEXT_ID] ?: 1)
                prefs[KEY_NEXT_ID] = id + 1
                saved = alarm.copy(id = id)
                list += saved
            } else {
                val i = list.indexOfFirst { it.id == alarm.id }
                if (i >= 0) list[i] = alarm else list += alarm
            }
            prefs[KEY_ALARMS] = AlarmCodec.encode(list)
        }
        return saved
    }

    suspend fun setEnabled(id: Int, enabled: Boolean) = modify { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    suspend fun delete(id: Int) = modify { list -> list.filterNot { it.id == id } }

    private suspend fun modify(change: (List<Alarm>) -> List<Alarm>) {
        store.edit { prefs -> prefs[KEY_ALARMS] = AlarmCodec.encode(change(AlarmCodec.decode(prefs[KEY_ALARMS]))) }
    }

    private companion object {
        val KEY_ALARMS = stringPreferencesKey("alarms")
        val KEY_NEXT_ID = intPreferencesKey("alarm_next_id")

        fun minuteOfDay(a: Alarm) = a.hour * 60 + a.minute
    }
}
