package com.example.myled.ui

import android.view.ViewConfiguration
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.myled.R
import com.example.myled.ble.BleError
import com.example.myled.ble.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val LONG_PRESS_MS = 600L
private const val ROTARY_STEP_PERCENT = 2
private const val HAPTIC_MIN_INTERVAL_MS = 50L
private const val LABEL_HOLD_MS = 800L

private enum class Zone { Power, Wheel, Arc, None }

@StringRes
fun errorText(kind: BleError): Int = when (kind) {
    BleError.PermissionDenied -> R.string.err_permission
    BleError.BluetoothOff -> R.string.err_bluetooth_off
    BleError.Timeout -> R.string.err_timeout
    BleError.ConnectFailed -> R.string.err_connect_failed
    BleError.NoWritableCharacteristic -> R.string.err_unsupported
    BleError.LinkLost -> R.string.err_link_lost
}

@Composable
fun ControlScreen(
    state: ControlUiState,
    ambient: Boolean,
    viewModel: ControlViewModel,
    onOpenPicker: () -> Unit,
    onOpenAlarms: () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val stateNow by rememberUpdatedState(state)
    val ambientNow by rememberUpdatedState(ambient)
    var showMenu by remember { mutableStateOf(false) }

    val interactive = state.controlsEnabled && !ambient
    val dim = if (interactive || ambient) 1f else 0.35f
    val stripColor = Color(state.r, state.g, state.b)
    val connecting = state.connection is ConnectionState.Connecting || state.connection is ConnectionState.Scanning

    // --- spinning sweep while connecting
    val spin = remember { Animatable(0f) }
    LaunchedEffect(connecting && !ambient) {
        if (connecting && !ambient) {
            while (true) {
                spin.snapTo(0f)
                spin.animateTo(360f, tween(1100, easing = LinearEasing))
            }
        }
    }

    // --- brightness percentage label that fades 800 ms after the last change
    val labelAlpha = remember { Animatable(0f) }
    var firstBrightness by remember { mutableStateOf(true) }
    LaunchedEffect(state.brightness) {
        if (firstBrightness) {
            firstBrightness = false
            return@LaunchedEffect
        }
        labelAlpha.snapTo(1f)
        delay(LABEL_HOLD_MS)
        labelAlpha.animateTo(0f, tween(300))
    }

    // --- rotary input focus
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(50)
            runCatching { focusRequester.requestFocus() }
        }
    }
    val notchPx = remember {
        ViewConfiguration.get(context).scaledVerticalScrollFactor.takeIf { it > 0f } ?: 40f
    }
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    val lastHapticAt = remember { LongArray(1) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { e ->
                val s = stateNow
                if (!s.controlsEnabled || ambientNow) return@onRotaryScrollEvent false
                rotaryAccumulator += e.verticalScrollPixels
                val steps = (rotaryAccumulator / notchPx).toInt()
                if (steps != 0) {
                    rotaryAccumulator -= steps * notchPx
                    val before = s.brightness
                    viewModel.adjustBrightness(steps * ROTARY_STEP_PERCENT)
                    val now = System.nanoTime() / 1_000_000L
                    if (viewModel.uiState.value.brightness != before && now - lastHapticAt[0] >= HAPTIC_MIN_INTERVAL_MS) {
                        lastHapticAt[0] = now
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val layout = remember(wPx, hPx) { ControlLayout(wPx, hPx) }
        val radius = layout.radius
        val center = layout.center
        val wheelCenter = layout.wheelCenter
        val wheelRadius = layout.wheelRadius
        val powerCenter = layout.powerCenter
        val powerDrawRadius = layout.powerDrawRadius
        val powerHitRadius = max(powerDrawRadius, with(density) { 24.dp.toPx() })
        val wheelZone = wheelRadius + with(density) { 4.dp.toPx() }
        val wheel = remember(wheelRadius.roundToInt()) { renderColorWheel(wheelRadius.roundToInt()) }

        Canvas(Modifier.fillMaxSize()) {
            if (ambient) {
                drawCircle(Color.Gray, wheelRadius, wheelCenter, style = Stroke(1.dp.toPx()))
                drawPowerGlyph(powerCenter, powerDrawRadius * 0.9f, Color.Gray, 2.dp.toPx())
            } else {
                drawControlView(
                    layout, wheel, state.r, state.g, state.b, state.brightness,
                    state.connection, state.powerOn, spin.value, dim,
                )
            }
        }

        // Gestures: bound to the zone where the touch started.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(wPx, hPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (ambientNow) return@awaitEachGesture
                        val p = down.position
                        val zone = when {
                            (p - powerCenter).getDistance() <= powerHitRadius -> Zone.Power
                            (p - wheelCenter).getDistance() <= wheelZone -> Zone.Wheel
                            else -> {
                                val d = (p - center).getDistance()
                                val angle = ArcGeometry.angleOf(p.x - center.x, p.y - center.y)
                                if (d >= 0.82f * radius && d <= radius && ArcGeometry.isOnArc(angle)) Zone.Arc
                                else Zone.None
                            }
                        }
                        when (zone) {
                            Zone.Power -> handlePower(
                                down,
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    viewModel.onPowerTap(onOpenPicker)
                                },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMenu = true
                                },
                            )
                            Zone.Wheel -> if (stateNow.controlsEnabled) trackPointer(down) { pos, final ->
                                val pick = WheelGeometry.pick(pos.x - wheelCenter.x, pos.y - wheelCenter.y, wheelRadius)
                                viewModel.onColorChanged(pick.r, pick.g, pick.b, final)
                            }
                            Zone.Arc -> if (stateNow.controlsEnabled) trackPointer(down) { pos, final ->
                                val angle = ArcGeometry.angleOf(pos.x - center.x, pos.y - center.y)
                                viewModel.onBrightnessChanged(ArcGeometry.percentOf(angle), final)
                            }
                            Zone.None -> Unit
                        }
                    }
                },
        )

        val overlayLift = with(density) { (0.06f * radius).toDp() }
        val overlayWidth = with(density) { (1.7f * wheelRadius).toDp() }

        if (!ambient && state.controlsEnabled) {
            Text(
                text = "${state.brightness}%",
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -overlayLift)
                    .alpha(labelAlpha.value),
                style = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    shadow = Shadow(Color.Black, Offset(0f, 2f), 8f),
                ),
            )
        }

        if (!ambient && !state.isConnected) {
            StatusOverlay(
                state = state,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -overlayLift)
                    .widthIn(max = overlayWidth),
                onRetry = { viewModel.retryConnect(onOpenPicker) },
                onChangeDevice = {
                    viewModel.disconnect()
                    onOpenPicker()
                },
            )
        }
    }

    AlertDialog(
        visible = showMenu && !ambient,
        onDismissRequest = { showMenu = false },
        title = { Text(stringResource(R.string.action_disconnect) + "?", textAlign = TextAlign.Center) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                showMenu = false
                viewModel.disconnect()
            })
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { showMenu = false }) },
    ) {
        item {
            Button(
                onClick = {
                    showMenu = false
                    viewModel.disconnect()
                    onOpenPicker()
                },
                label = { Text(stringResource(R.string.action_change_device)) },
            )
        }
        item {
            Button(
                onClick = {
                    showMenu = false
                    onOpenAlarms()
                },
                label = { Text(stringResource(R.string.alarms_title)) },
            )
        }
    }
}

@Composable
private fun StatusOverlay(
    state: ControlUiState,
    modifier: Modifier,
    onRetry: () -> Unit,
    onChangeDevice: () -> Unit,
) {
    val connection = state.connection
    val (message, primary, secondary) = when (connection) {
        is ConnectionState.Connecting, is ConnectionState.Scanning ->
            Triple(R.string.state_connecting, null, null)
        is ConnectionState.Disconnected ->
            if (state.hasSavedDevice) Triple(R.string.state_disconnected, R.string.action_connect, null)
            else Triple(R.string.state_no_device, R.string.action_choose_strip, null)
        is ConnectionState.Error -> when (connection.kind) {
            BleError.PermissionDenied, BleError.BluetoothOff -> Triple(errorText(connection.kind), null, null)
            BleError.ConnectFailed ->
                Triple(errorText(connection.kind), R.string.action_retry, R.string.action_change_short)
            BleError.NoWritableCharacteristic ->
                Triple(errorText(connection.kind), R.string.action_change_short, null)
            else -> Triple(errorText(connection.kind), R.string.action_retry, null)
        }
        is ConnectionState.Connected -> return
    }
    val primaryIsChange = connection is ConnectionState.Error && connection.kind == BleError.NoWritableCharacteristic ||
        connection is ConnectionState.Disconnected && !state.hasSavedDevice

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(message),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        if (primary != null) {
            CompactButton(
                onClick = if (primaryIsChange) onChangeDevice else onRetry,
                label = { Text(stringResource(primary), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            )
        }
        if (secondary != null) {
            CompactButton(
                onClick = onChangeDevice,
                label = { Text(stringResource(secondary), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            )
        }
    }
}

/** Tap or 600 ms long-press on the power button; a long-press never toggles power. */
private suspend fun AwaitPointerEventScope.handlePower(
    down: PointerInputChange,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    down.consume()
    var released: PointerInputChange? = null
    val completed = withTimeoutOrNull(LONG_PRESS_MS) { released = waitForUpOrCancellation() }
    if (completed == null) {
        onLongPress()
        waitForUpOrCancellation()?.consume()
    } else {
        released?.let {
            it.consume()
            onTap()
        }
    }
}

/** Follows [down]'s pointer until it lifts, reporting every position; the lift reports `final`. */
private suspend fun AwaitPointerEventScope.trackPointer(
    down: PointerInputChange,
    onPosition: (Offset, Boolean) -> Unit,
) {
    down.consume()
    onPosition(down.position, false)
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) {
            onPosition(change.position, true)
            change.consume()
            return
        }
        onPosition(change.position, false)
        change.consume()
    }
}
