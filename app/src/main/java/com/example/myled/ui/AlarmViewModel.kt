package com.example.myled.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myled.alarm.Alarm
import com.example.myled.alarm.AlarmScheduler
import com.example.myled.alarm.AlarmStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AlarmStore(app)

    /** Null until the first load finishes. */
    val alarms: StateFlow<List<Alarm>?> = store.alarms
        .map<List<Alarm>, List<Alarm>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setEnabled(id: Int, enabled: Boolean) = change { store.setEnabled(id, enabled) }

    fun save(alarm: Alarm) = change { store.upsert(alarm.copy(enabled = true)) }

    fun delete(id: Int) = change {
        store.delete(id)
        AlarmScheduler.cancel(getApplication(), id)
    }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            AlarmScheduler.rescheduleAll(getApplication())
        }
    }
}
