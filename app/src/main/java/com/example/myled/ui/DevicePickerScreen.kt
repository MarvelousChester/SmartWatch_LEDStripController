package com.example.myled.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.compose.runtime.collectAsState
import com.example.myled.R
import com.example.myled.ble.ConnectionState
import com.example.myled.ble.ScannedDevice

@Composable
fun DevicePickerScreen(
    viewModel: ControlViewModel,
    onDeviceChosen: () -> Unit,
) {
    val devices by viewModel.scanResults.collectAsState()
    val ui by viewModel.uiState.collectAsState()
    val scanning = ui.connection is ConnectionState.Scanning

    LaunchedEffect(Unit) { viewModel.startScan() }
    DisposableEffect(Unit) { onDispose { viewModel.stopScan() } }

    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.picker_title)) }
            }
            if (scanning) {
                item {
                    Row(
                        Modifier.fillMaxWidth().transformedHeight(this, spec),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spinner(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.picker_scanning),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (!scanning && devices.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.picker_empty),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(devices.size, key = { devices[it].address }) { index ->
                val device = devices[index]
                DeviceRow(
                    device = device,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    onClick = {
                        viewModel.selectDevice(device)
                        onDeviceChosen()
                    },
                )
            }
            item {
                Button(
                    onClick = { viewModel.startScan() },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    enabled = !scanning,
                    label = { Text(stringResource(R.string.action_rescan)) },
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: ScannedDevice,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.alpha(if (device.likelyController) 1f else 0.6f),
        transformation = transformation,
        colors = if (device.likelyController) ButtonDefaults.buttonColors()
        else ButtonDefaults.filledTonalButtonColors(),
        icon = { RssiBars(device.rssi, Modifier.size(18.dp)) },
        secondaryLabel = { Text(device.address, style = MaterialTheme.typography.labelSmall) },
        label = { Text(device.name ?: stringResource(R.string.unknown_device), maxLines = 1) },
    )
}

/** 1-3 bars: stronger signal fills more of them. */
@Composable
private fun RssiBars(rssi: Int, modifier: Modifier = Modifier) {
    val bars = when {
        rssi >= -60 -> 3
        rssi >= -75 -> 2
        else -> 1
    }
    Canvas(modifier) {
        val barWidth = size.width / 5f
        for (i in 0 until 3) {
            val h = size.height * (i + 1) / 3f
            drawRect(
                color = Color.White.copy(alpha = if (i < bars) 1f else 0.25f),
                topLeft = Offset(i * barWidth * 2f, size.height - h),
                size = Size(barWidth, h),
            )
        }
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            angle.snapTo(0f)
            angle.animateTo(360f, tween(1000, easing = LinearEasing))
        }
    }
    Canvas(modifier) {
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        drawArc(
            Color(0xFFFFB300), angle.value, 100f, false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke.width, size.height - stroke.width),
            style = stroke,
        )
    }
}
