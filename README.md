# MyLED

A standalone **Wear OS** app for controlling cheap Bluetooth LE LED strips (ELK-BLEDOM / duoCo-style controllers) straight from your watch — no phone required.

Pick a colour on a wheel, dim with the rotating bezel, toggle power from a tile, and wake up to a "sunrise" alarm that fades the strip on at a set time.

## Features

### Strip control
- **Colour wheel** – drag to choose hue and saturation; the strip updates live.
- **Brightness arc** – drag the arc around the edge of the screen, or turn the **rotating bezel / crown** (2% per notch, with haptic ticks).
- **Power button** – tap to toggle the strip; when disconnected it reconnects or opens the device picker.
- **Smooth dragging** – colour and brightness updates are throttled and stale packets are coalesced, so the strip keeps up without lagging behind your finger.
- **Ambient-aware** – controls lock in ambient mode, and the BLE link is released when the app isn't in the foreground.

### Device discovery
- Scans for nearby BLE controllers and filters by common advertised names (`ELK-BLEDOM`, `duoCo`, `LEDBLE`, `MELK`, `Triones`, …).
- Remembers the last strip and reconnects automatically.
- After connecting, forces the strip out of any built-in animation and restores your saved colour, brightness and power state.

### LED alarms
- **Timed by the watch, not the strip** – the watch keeps the schedule and sends the commands when an alarm fires, so the strip controller's own timer or clock isn't used. This works with cheap controllers that have no timer or one that drifts.
- Schedule the strip to turn on at a given time in a chosen **colour and brightness**.
- One-off or repeating (any combination of weekdays).
- **Sunrise mode** – ramps brightness up over 5–30 minutes so the target is reached exactly at the alarm time.
- Uses exact alarms and a foreground service so they fire reliably; alarms are re-registered after reboot, time or time-zone changes, and app updates.

### Tile
- A Wear OS tile mirrors the control screen (wheel, marker, brightness arc, power button) from the last saved state.
- Tap the power button on the tile to toggle the strip without opening the app; tap elsewhere to launch the app.

## How it works

Each command is sent as two 9-byte packets — one in the duoCo format and one in the ELK-BLEDOM alternate format — and the controller ignores whichever it doesn't understand. This lets one app drive both controller families. The protocol is a Kotlin port of an existing desktop Python implementation.

> **Note:** these controllers accept only one BLE connection at a time. If the strip won't connect, close any other app (e.g. a PC or phone controller) that's holding the link. The strip's real state can't be read back over BLE, so the app and tile show what the watch last sent.

## Project layout

```
app/src/main/java/com/example/myled/
├── ble/           BLE GATT client, packet protocol, device matching, packet coalescing
├── ui/            Compose for Wear OS screens: control view, colour wheel, brightness arc, device picker, alarms
├── alarm/         Alarm model, scheduling, boot receiver, foreground "fire" service, notifications
├── tile/          Wear OS tile service and its renderer
├── data/          DataStore-backed settings (last device, colour, brightness, power)
└── presentation/  MainActivity and theme
```

## Requirements

- Wear OS watch running **Android 11 (API 30)** or newer with Bluetooth LE
- A compatible ELK-BLEDOM / duoCo BLE LED strip controller
- Android Studio with the Android SDK (compile SDK 37) to build

## Building

```bash
./gradlew assembleDebug
```

Install on a connected watch or emulator:

```bash
./gradlew installDebug
```

Run the unit tests (protocol, packet coalescing, device matching, alarm scheduling, colour math, wheel geometry):

```bash
./gradlew test
```

## Permissions

| Permission | Why |
| --- | --- |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | Find and talk to the strip (location is not used on API 31+) |
| `ACCESS_FINE_LOCATION` | BLE scanning on API 30 only |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Fire LED alarms on time |
| `RECEIVE_BOOT_COMPLETED` | Restore alarms after a reboot |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep the BLE connection alive while an alarm runs |
| `POST_NOTIFICATIONS` | Show alarm progress and failures |
| `WAKE_LOCK` | Keep the CPU awake during a sunrise fade |
