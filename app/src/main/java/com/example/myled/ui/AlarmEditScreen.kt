package com.example.myled.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextToggleButton
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.myled.R
import com.example.myled.alarm.Alarm
import com.example.myled.alarm.AlarmSchedule
import java.time.LocalTime

private const val DEFAULT_HOUR = 7
private const val DEFAULT_MINUTE = 0
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Edits alarm [alarmId], or a new one ([Alarm.NEW_ID]) that starts from the strip's current
 * [defaultColor] and [defaultBrightness].
 */
@Composable
fun AlarmEditScreen(
    alarmId: Int,
    viewModel: AlarmViewModel,
    defaultColor: Int,
    defaultBrightness: Int,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val alarms by viewModel.alarms.collectAsState()
    var draft by remember { mutableStateOf<Alarm?>(null) }
    var pickingTime by remember { mutableStateOf(false) }

    LaunchedEffect(alarms) {
        val list = alarms ?: return@LaunchedEffect
        if (draft != null) return@LaunchedEffect
        draft = list.firstOrNull { it.id == alarmId } ?: Alarm(
            id = Alarm.NEW_ID,
            hour = DEFAULT_HOUR,
            minute = DEFAULT_MINUTE,
            color = defaultColor,
            brightness = ((defaultBrightness + 5) / 10 * 10).coerceIn(10, 100), // slider steps by 10
        )
    }
    val a = draft ?: return

    if (pickingTime) {
        TimePicker(
            initialTime = LocalTime.of(a.hour, a.minute),
            onTimePicked = {
                draft = a.copy(hour = it.hour, minute = it.minute)
                pickingTime = false
            },
        )
        return
    }

    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val isNew = a.id == Alarm.NEW_ID

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(if (isNew) R.string.alarm_new else R.string.alarm_edit)) }
            }
            item {
                Button(
                    onClick = { pickingTime = true },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text(formatTime(context, a.hour, a.minute), style = MaterialTheme.typography.titleLarge) },
                    secondaryLabel = { Text(stringResource(R.string.alarm_time)) },
                )
            }
            item {
                Column(
                    Modifier.fillMaxWidth().transformedHeight(this, spec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(AlarmSchedule.daysLabel(a.days), style = MaterialTheme.typography.labelMedium)
                    DayRow(a.days, 0..3) { draft = a.copy(days = it) }
                    DayRow(a.days, 4..6) { draft = a.copy(days = it) }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().transformedHeight(this, spec), contentAlignment = Alignment.Center) {
                    ColorPicker(a.color) { draft = a.copy(color = it) }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().transformedHeight(this, spec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.alarm_brightness, a.brightness),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = a.brightness.coerceIn(10, 100) / 10 * 10,
                        onValueChange = { draft = a.copy(brightness = it) },
                        valueProgression = 10..100 step 10,
                    )
                }
            }
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.alarm_turn_on)) }
            }
            items(Alarm.FADE_CHOICES.size) { i ->
                val minutes = Alarm.FADE_CHOICES[i]
                RadioButton(
                    selected = a.fadeMinutes == minutes,
                    onSelect = { draft = a.copy(fadeMinutes = minutes) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = {
                        Text(
                            if (minutes == 0) stringResource(R.string.alarm_instant)
                            else stringResource(R.string.alarm_sunrise, minutes),
                        )
                    },
                )
            }
            item {
                Button(
                    onClick = {
                        viewModel.save(a)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.action_save)) },
                )
            }
            if (!isNew) {
                item {
                    Button(
                        onClick = {
                            viewModel.delete(a.id)
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        label = { Text(stringResource(R.string.action_delete)) },
                    )
                }
            }
        }
    }
}

/** Toggles for days [range] (0 = Monday). */
@Composable
private fun DayRow(days: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in range) {
            val bit = 1 shl i
            TextToggleButton(
                checked = days and bit != 0,
                onCheckedChange = { onChange(if (it) days or bit else days and bit.inv()) },
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = AlarmSchedule.DAY_SHORT[i] },
            ) { Text(DAY_LETTERS[i], textAlign = TextAlign.Center) }
        }
    }
}

/** Tap or drag on the colour wheel; same hue/saturation mapping as the control screen. */
@Composable
private fun ColorPicker(color: Int, onPick: (Int) -> Unit) {
    val sizeDp = 110.dp
    val radiusPx = with(LocalDensity.current) { (sizeDp / 2).toPx() }
    val wheel = remember(radiusPx) { renderColorWheel(radiusPx.toInt()) }
    val onPickNow by rememberUpdatedState(onPick)
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    val desc = stringResource(R.string.alarm_color)

    Canvas(
        Modifier
            .size(sizeDp)
            .semantics { contentDescription = desc }
            .pointerInput(radiusPx) {
                fun pick(p: Offset) {
                    val c = WheelGeometry.pick(p.x - radiusPx, p.y - radiusPx, radiusPx)
                    onPickNow((c.r shl 16) or (c.g shl 8) or c.b)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position)
                    }
                }
            },
    ) {
        val (mx, my) = WheelGeometry.markerOffset(r, g, b, radiusPx)
        drawColorWheel(
            wheel = wheel,
            center = Offset(radiusPx, radiusPx),
            radius = radiusPx,
            markerColor = Color(r, g, b),
            markerOffset = Offset(mx, my),
            alpha = 1f,
        )
    }
}
