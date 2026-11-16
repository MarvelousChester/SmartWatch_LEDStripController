package com.example.myled.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.myled.R
import com.example.myled.alarm.Alarm
import com.example.myled.presentation.theme.MyLEDTheme
import com.example.myled.ui.AlarmEditScreen
import com.example.myled.ui.AlarmListScreen
import com.example.myled.ui.AlarmViewModel
import com.example.myled.ui.ControlScreen
import com.example.myled.ui.ControlViewModel
import com.example.myled.ui.DevicePickerScreen
import com.example.myled.ui.PermissionState

class MainActivity : ComponentActivity() {
    private val viewModel: ControlViewModel by viewModels()
    private val alarmViewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyLEDTheme {
                LedApp(viewModel, alarmViewModel, this)
            }
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasPermissions(context: Context) = requiredPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun isBluetoothOn(context: Context): Boolean =
    context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

@Composable
private fun LedApp(viewModel: ControlViewModel, alarmViewModel: AlarmViewModel, activity: ComponentActivity) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ui by viewModel.uiState.collectAsState()

    // --- runtime permissions
    var permissionsGranted by remember { mutableStateOf(hasPermissions(context)) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it } || hasPermissions(context)
        permanentlyDenied = !permissionsGranted && requiredPermissions().any {
            !activity.shouldShowRequestPermissionRationale(it)
        }
    }
    LaunchedEffect(Unit) {
        if (!permissionsGranted) permissionLauncher.launch(requiredPermissions())
    }

    // --- bluetooth adapter state, plus re-checks on resume (e.g. back from app settings)
    var bluetoothOn by remember { mutableStateOf(isBluetoothOn(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                bluetoothOn = isBluetoothOn(c)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- activity lifecycle + ambient mode drive the strip's grace timer
    var started by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    started = true
                    permissionsGranted = hasPermissions(context)
                    bluetoothOn = isBluetoothOn(context)
                }
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val ambient = rememberAmbientModeManager()?.currentAmbientMode is AmbientMode.Ambient
    LaunchedEffect(started, ambient) { viewModel.setActive(started && !ambient) }

    val permissionState = when {
        !permissionsGranted -> if (permanentlyDenied) PermissionState.PermanentlyDenied else PermissionState.Denied
        !bluetoothOn -> PermissionState.BluetoothOff
        else -> PermissionState.Granted
    }
    LaunchedEffect(permissionState) { viewModel.onPermissionState(permissionState) }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { bluetoothOn = isBluetoothOn(context) }

    when (permissionState) {
        PermissionState.Denied, PermissionState.PermanentlyDenied -> MessageScreen(
            text = stringResource(R.string.err_permission),
            action = stringResource(R.string.action_grant),
            onAction = {
                if (permissionState == PermissionState.PermanentlyDenied) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                } else {
                    permissionLauncher.launch(requiredPermissions())
                }
            },
        )
        PermissionState.BluetoothOff -> MessageScreen(
            text = stringResource(R.string.err_bluetooth_off),
            action = stringResource(R.string.action_enable),
            onAction = {
                try {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (_: SecurityException) {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            },
        )
        PermissionState.Granted ->
            if (!ui.loaded) {
                MessageScreen(text = "", action = null, onAction = {})
            } else {
                val navController = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(navController = navController, startDestination = "control") {
                    composable("control") {
                        ControlScreen(
                            state = ui,
                            ambient = ambient,
                            viewModel = viewModel,
                            onOpenPicker = { navController.navigate("picker") },
                            onOpenAlarms = { navController.navigate("alarms") },
                        )
                    }
                    composable("picker") {
                        DevicePickerScreen(viewModel = viewModel, onDeviceChosen = { navController.popBackStack() })
                    }
                    composable("alarms") {
                        AlarmListScreen(
                            viewModel = alarmViewModel,
                            onEdit = { id -> navController.navigate("alarm/$id") },
                        )
                    }
                    composable("alarm/{id}") { entry ->
                        AlarmEditScreen(
                            alarmId = entry.arguments?.getString("id")?.toIntOrNull() ?: Alarm.NEW_ID,
                            viewModel = alarmViewModel,
                            defaultColor = (ui.r shl 16) or (ui.g shl 8) or ui.b,
                            defaultBrightness = ui.brightness,
                            onDone = { navController.popBackStack() },
                        )
                    }
                }
            }
    }
}

@Composable
private fun MessageScreen(text: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (text.isNotEmpty()) {
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
        }
        if (action != null) Button(onClick = onAction, label = { Text(action) })
    }
}
