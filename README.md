# <img src="fastlane/metadata/android/en-US/images/icon.png" alt="" width="48" align="top"> CaffeineDose

[![CI](https://github.com/abhi-k9/CaffeineDose/actions/workflows/ci.yml/badge.svg)](https://github.com/abhi-k9/CaffeineDose/actions/workflows/ci.yml)

CaffeineDose keeps your screen on for a set amount of time, then lets it turn off normally again, without changing your
screen timeout setting.

Download it from the [latest release](https://github.com/abhi-k9/CaffeineDose/releases/latest) (see
[verifying a download](#verifying-a-download)). It is a sibling of [ZzzTimer](https://github.com/abhi-k9/ZzzTimer): where
ZzzTimer acts when the timer *ends*, CaffeineDose holds the screen *while* it runs (see [NOTICE](NOTICE)).

## Usage

- **Quick Settings tile**: tap to keep the screen on for the default duration, tap again to stop.
- **Home screen widget**: the same as the tile, from the home screen. 1×1 by default, it shows more once resized.
- **Tap again for longer** (optional): each tap on the tile or the widget starts the next, longer step of your choice
  (15 min → 1 h → 4 h by default, possibly "while charging"), and a tap after the last one stops.
- **Notification**: extend, reduce or stop the timer. Dismissing it (possible since Android 14) stops the timer, and so
  does turning the screen off (e.g. with the power button), unless disabled in the settings.
- **App** (launcher, notification tap, or long press on the tile): keep the screen on for any duration, configure the
  default duration and the `+` / `−` steps, the behavior, the theme, automation and diagnostics.

**While charging**: keep the screen on until the charger is unplugged (for at most 24 hours), from the app, with
automation, or as a step of the tile and widget.

**Battery** (optional, in the app settings): let the screen turn off once the battery drops to a level of your choice
(15% by default), or when Battery Saver turns on. Neither applies while the phone is charging.

While the screen is kept on, the device doesn't lock automatically either: stop the timer, or turn the screen off,
before leaving it unattended.

On some phones, e.g. Samsung, the screen turns off anyway unless *Keep the screen on over other apps* is allowed in the
app (see [how it works](#how-it-works)).

## Automation

Tools like [Tasker](https://tasker.joaoapps.com/) or `adb` can control the timer with explicit broadcasts to
`io.github.abhik9.caffeinedose/.automation.AutomationReceiver` (`io.github.abhik9.caffeinedose.debug/…` for debug
builds). This can be turned off in the app settings.

| Action (`io.github.abhik9.caffeinedose.action.…`) | Effect                                                         |
|---------------------------------------------------|----------------------------------------------------------------|
| `START`                                           | Keeps the screen on for `duration` seconds, or the default     |
| `START_WHILE_CHARGING`                            | Keeps the screen on until the charger is unplugged             |
| `UPDATE`                                          | Adds `duration` seconds (can be negative) to the running timer |
| `INCREMENT` / `DECREMENT`                         | Extends / reduces the running timer by the configured step     |
| `TOGGLE`                                          | Starts the default timer, or stops the running one             |
| `STOP`                                            | Stops the running timer                                        |

`duration` is a `long` or `int` extra, capped to 24 hours:

```bash
adb shell am broadcast -n io.github.abhik9.caffeinedose/.automation.AutomationReceiver \
  -a io.github.abhik9.caffeinedose.action.START --el duration 600
```

Since Android 12, starting a timer while the app is in the background may be refused (a toast says so): turning off
battery optimization for CaffeineDose lifts this restriction. Changing or stopping a running timer always works.

## Diagnostics

To investigate an issue, turn on **Record diagnostics** in the app settings, reproduce it, then **Export log** to a file.
The log records what the app does and why (timer operations and where they come from, the service, wake lock and
overlay, the screen turning on and off, a heartbeat while a timer runs, permissions, crashes), and the export starts with
a snapshot of the app and device state. It is off by default (on in debug builds), capped at about 512 KB, and never
leaves the device unless exported (see the [privacy policy](PRIVACY.md)).

## How it works

- `FLAG_KEEP_SCREEN_ON` only works while one of the app's windows is visible. To keep the screen on over other apps, a
  foreground service ([`specialUse`](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
  type, without time limit) holds a deprecated but still supported
  [`SCREEN_BRIGHT_WAKE_LOCK`](https://developer.android.com/reference/android/os/PowerManager#SCREEN_BRIGHT_WAKE_LOCK).
- Some devices ignore that wake lock while the app isn't visible (seen on a Samsung Galaxy S24 Ultra with Android 16).
  With the optional *Display over other apps* permission, the service also shows an invisible, untouchable 1×1 window
  with [`FLAG_KEEP_SCREEN_ON`](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON),
  which the window manager always honors.
- The service is the timer's source of truth: the timer, the wake lock and the window only live in its process's memory.
  Nothing is persisted, so the screen can never stay on by accident after a crash or a reboot.
- The deadline is tracked on the monotonic `elapsedRealtime` clock: changing the time or time zone doesn't affect it.
  The wake lock times out at the deadline as a safety net, and is released with
  [`ON_AFTER_RELEASE`](https://developer.android.com/reference/android/os/PowerManager#ON_AFTER_RELEASE): the screen then
  turns off after the usual timeout, instead of right away.
- Where the tile or the widget isn't allowed to start a foreground service, it starts it from an invisible activity
  instead.

The `:core` module holds the logic in pure Kotlin, unit tested on the JVM: `AwakeTimer`, the settings, durations and the
automation API. It defines small interfaces (`ScreenKeeper`, `DeviceClock`, `EventLog`) that `:app` implements with the
platform APIs, next to the Jetpack Compose UI.

## Building

Requires JDK 17+ and the Android SDK (API 37). `./gradlew assembleDebug test lint` builds, tests and lints (lint and
Kotlin warnings are errors). CI does the same, plus [ktlint](https://pinterest.github.io/ktlint/), on pushes to `main` and
pull requests that change more than documentation, and [zizmor](https://docs.zizmor.sh/) when workflows change; other
branches are checked on demand (Actions → CI → Run workflow). The [device test](.github/workflows/device-test.yml) (Actions → Device
test → Run workflow) checks on an emulator, second by second, that the screen really stays on, then is released;
[its script](.github/scripts/device-test.sh) also runs against a connected device.

Signing release builds and publishing releases are described in [RELEASING.md](RELEASING.md).

## Verifying a download

- Signing certificate (SHA-256), checked by `apksigner verify --print-certs`, AppVerifier or Obtainium:
  `60:C2:8A:EE:88:92:3D:9C:68:96:E0:DE:BE:AE:30:AF:00:F2:9E:FA:94:90:E9:18:1E:2F:82:11:A1:84:8F:34`
- Build provenance: `gh attestation verify CaffeineDose-vX.Y.Z.apk --repo abhi-k9/CaffeineDose`
- Checksum: `sha256sum --check CaffeineDose-vX.Y.Z.apk.sha256`

## License

[Apache License 2.0](LICENSE). No Internet permission, no data collected: see the [privacy policy](PRIVACY.md).
