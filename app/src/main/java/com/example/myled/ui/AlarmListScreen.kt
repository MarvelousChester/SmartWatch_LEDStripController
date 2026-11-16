package com.example.myled.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SplitSwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.myled.R
import com.example.myled.alarm.Alarm
import com.example.myled.alarm.AlarmSchedule
import com.example.myled.alarm.AlarmScheduler
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AlarmListScreen(
    viewModel: AlarmViewModel,
    onEdit: (Int) -> Unit,
) {
    val context = LocalContext.current
    val alarms by viewModel.alarms.collectAsState()

    // Notifications only explain failures; ask once, but never block alarms on it.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var exactAllowed by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exactAllowed = AlarmScheduler.canScheduleExact(context) }

    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.alarms_title)) }
            }
            if (!exactAllowed) {
                item {
                    Button(
                        onClick = { openExactAlarmSettings(context) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        label = { Text(stringResource(R.string.alarms_exact_needed)) },
                        secondaryLabel = { Text(stringResource(R.string.action_allow)) },
                    )
                }
            }
            val list = alarms.orEmpty()
            if (alarms != null && list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.alarms_empty),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(list.size, key = { list[it].id }) { index ->
                val alarm = list[index]
                val time = formatTime(context, alarm.hour, alarm.minute)
                SplitSwitchButton(
                    checked = alarm.enabled,
                    onCheckedChange = { viewModel.setEnabled(alarm.id, it) },
                    toggleContentDescription = stringResource(R.string.alarms_toggle, time),
                    onContainerClick = { onEdit(alarm.id) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = {
                        ColorDot(alarm.color, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(time)
                    },
                    secondaryLabel = { Text(alarmSummary(alarm), maxLines = 1) },
                )
            }
            item {
                Button(
                    onClick = { onEdit(Alarm.NEW_ID) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.alarms_add)) },
                )
            }
        }
    }
}

@Composable
fun ColorDot(color: Int, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        drawCircle(Color(0xFF000000.toInt() or color))
        drawCircle(Color.White.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
    }
}

fun alarmSummary(alarm: Alarm): String {
    val days = AlarmSchedule.daysLabel(alarm.days)
    return if (alarm.fadeMinutes > 0) "$days · ${alarm.fadeMinutes}m fade" else days
}

fun formatTime(context: Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern))
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
}
