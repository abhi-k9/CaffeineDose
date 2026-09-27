# <img src="fastlane/metadata/android/en-US/images/icon.png" alt="" width="48" align="top"> CaffeineDose

[![CI](https://github.com/abhi-k9/CaffeineDose/actions/workflows/ci.yml/badge.svg)](https://github.com/abhi-k9/CaffeineDose/actions/workflows/ci.yml)

CaffeineDose keeps your screen on for a set amount of time, then lets it turn off normally again, without changing your
screen timeout setting.

It is a sibling of [ZzzTimer](https://github.com/abhi-k9/ZzzTimer), and shares its structure: where ZzzTimer acts when
the timer *ends*, CaffeineDose holds the screen *while* the timer runs (see [NOTICE](NOTICE)).

Download the APK from the [latest release](https://github.com/abhi-k9/CaffeineDose/releases/latest), and see
[Verifying a download](#verifying-a-download).

## Usage

1. Add the CaffeineDose tile to the Quick Settings panel.
2. Tap the tile to keep the screen on for the default duration, tap it again to stop.
3. Extend, reduce or stop the timer from its notification. Dismissing the notification (possible since Android 14) also
   stops the timer, and so does turning the screen off (e.g. with the power button), unless disabled in the settings.
4. Open the app (launcher icon, notification tap, or long press on the tile) to:
   - keep the screen on for an exact duration, or pick a preset,
   - configure the default duration and the `+` / `−` steps,
   - choose whether turning the screen off stops the timer,
   - choose the theme (System, Light, Dark) and Material You dynamic colors,
   - allow or forbid other apps to control the timer,
   - share diagnostics when a timer stops unexpectedly (see below).

While the screen is kept on, the device doesn't lock automatically either: stop the timer, or turn the screen off,
before leaving the device unattended.

## Diagnostics

Some manufacturers' power management stops apps in ways stock Android doesn't. *Share diagnostics*, in the app, shares
a short report to help understand it: the relevant settings, why Android last stopped the app
([`ApplicationExitInfo`](https://developer.android.com/reference/android/app/ApplicationExitInfo)), and the latest
events (timer starts and stops and their source, the service lifecycle, and a heartbeat every 15 seconds while a timer
runs). It is kept on the device only, see the [privacy policy](PRIVACY.md).

The emulator [device test](.github/workflows/device-test.yml) runs the app on stock Android 14 and 16, and checks second
by second that the screen stays on while a timer runs.

## Automation

Tools like [Tasker](https://tasker.joaoapps.com/) or `adb` can control the timer with explicit broadcasts.
This can be turned off in the app settings.

| Action                                           | Effect                                                         |
|--------------------------------------------------|----------------------------------------------------------------|
| `io.github.abhik9.caffeinedose.action.START`     | Starts a timer: of `duration` seconds, or the default duration |
| `io.github.abhik9.caffeinedose.action.UPDATE`    | Adds `duration` seconds (can be negative) to the running timer |
| `io.github.abhik9.caffeinedose.action.INCREMENT` | Extends the running timer by the configured step               |
| `io.github.abhik9.caffeinedose.action.DECREMENT` | Reduces the running timer by the configured step               |
| `io.github.abhik9.caffeinedose.action.TOGGLE`    | Starts the default timer, or stops the running one             |
| `io.github.abhik9.caffeinedose.action.STOP`      | Stops the running timer                                        |

`duration` is a `long` or `int` extra, in seconds, capped to 24 hours. For example:

```bash
# Keep the screen on for 10 minutes
adb shell am broadcast -n io.github.abhik9.caffeinedose/.automation.AutomationReceiver \
  -a io.github.abhik9.caffeinedose.action.START --el duration 600

# Remove 1 minute
adb shell am broadcast -n io.github.abhik9.caffeinedose/.automation.AutomationReceiver \
  -a io.github.abhik9.caffeinedose.action.UPDATE --el duration -60

# Stop
adb shell am broadcast -n io.github.abhik9.caffeinedose/.automation.AutomationReceiver \
  -a io.github.abhik9.caffeinedose.action.STOP
```

Debug builds use the `io.github.abhik9.caffeinedose.debug` package: adjust the component name (`-n`) accordingly.

Since Android 12, apps can't start a foreground service from the background: starting a timer from automation while the
app is in the background may be refused (a toast says so). Turning off battery optimization for CaffeineDose
(Settings → Apps → CaffeineDose → Battery → Unrestricted) lifts this restriction. Changing or stopping a running timer
always works.

## How it works

`FLAG_KEEP_SCREEN_ON`, the recommended way to keep the screen on, only works while one of the app's own windows is
visible. To keep the screen on over other apps, a foreground service holds a (deprecated, but still supported)
[`SCREEN_BRIGHT_WAKE_LOCK`](https://developer.android.com/reference/android/os/PowerManager#SCREEN_BRIGHT_WAKE_LOCK).
No foreground service type describes this use, so the service uses the
[`specialUse`](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use) type,
which has no time limit.

The service is the source of truth of the timer: the timer exists only while the service holds the wake lock, both live
in memory in the same process, and nothing is persisted. If the process dies, the wake lock is released with it: the
screen can never stay on by accident, and there is nothing to clean up after a crash or a reboot.

The end of the timer is tracked on the monotonic `elapsedRealtime` clock, so changing the time or the time zone doesn't
affect a running timer. The wake lock is acquired with a timeout matching the remaining time, as a safety net. When the
timer ends, the lock is released with
[`ON_AFTER_RELEASE`](https://developer.android.com/reference/android/os/PowerManager#ON_AFTER_RELEASE): the screen then
turns off after the usual screen timeout, instead of right away.

The Quick Settings tile starts the service directly. On devices that don't allow a tile to start a foreground service,
it falls back to an invisible, non-exported activity that starts it from the foreground.

Since Android 16 QPR2, the notification is displayed as a
[Live Update](https://developer.android.com/develop/ui/views/notifications/live-update).

## Project structure

| Module  | Content                                                                                                        |
|---------|----------------------------------------------------------------------------------------------------------------|
| `:core` | Pure Kotlin: the timer logic (`AwakeTimer`), settings and time helpers. Unit tested on the JVM.                |
| `:app`  | Android adapters (foreground service and wake lock, notification, tile, receivers) and the Jetpack Compose UI. |

`:core` defines small interfaces (`ScreenKeeper`, `DeviceClock`) that `:app` implements with the platform APIs.

## Building

Requirements: JDK 17 or newer, and the Android SDK (API 37).

```bash
./gradlew assembleDebug    # debug APK
./gradlew :core:test       # unit tests
./gradlew lint             # Android lint (warnings are errors)
```

Release builds are minified. They are signed only when a signing configuration is provided, as Gradle properties
(e.g. in `~/.gradle/gradle.properties`) or environment variables — never commit keystores or passwords:

| Gradle property                  | Environment variable                 |
|----------------------------------|--------------------------------------|
| `caffeinedose.signing.storeFile`     | `CAFFEINEDOSE_SIGNING_STORE_FILE`        |
| `caffeinedose.signing.storePassword` | `CAFFEINEDOSE_SIGNING_STORE_PASSWORD`    |
| `caffeinedose.signing.keyAlias`      | `CAFFEINEDOSE_SIGNING_KEY_ALIAS`         |
| `caffeinedose.signing.keyPassword`   | `CAFFEINEDOSE_SIGNING_KEY_PASSWORD`      |

## Releasing

1. Bump the version in `app/build.gradle.kts` on `main`, and add its changelog as
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (`./gradlew -q :app:printVersionCode` prints the
   version code). CI checks that it exists, and the release notes start with it.
2. Run the [release workflow](.github/workflows/release.yml) on `main` (Actions → Release → Run workflow), which creates
   the `vX.Y.Z` tag. Alternatively, push that tag yourself: it must point to a commit of `main`.

The workflow builds the app from scratch (no shared build cache), runs the tests and lint, signs the APK, checks that it
is signed with the expected certificate, attests its build provenance, and publishes it with its SHA-256 checksum as a
GitHub Release. It runs in the `release` environment: protection rules such as required reviewers can be added to it in
Settings → Environments.

It requires these repository (or `release` environment) secrets, in Settings → Secrets and variables → Actions:

| Secret                                 | Value                                |
|----------------------------------------|--------------------------------------|
| `CAFFEINEDOSE_SIGNING_KEYSTORE_BASE64` | The release keystore, base64 encoded |
| `CAFFEINEDOSE_SIGNING_STORE_PASSWORD`  | The keystore password                |
| `CAFFEINEDOSE_SIGNING_KEY_ALIAS`       | The key alias                        |
| `CAFFEINEDOSE_SIGNING_KEY_PASSWORD`    | The key password                     |

It also requires the `CAFFEINEDOSE_SIGNING_CERT_SHA256` repository (or `release` environment) **variable**: the SHA-256
digest of the signing certificate, as printed by `apksigner verify --print-certs` or `keytool -list -v`. The workflow
checks it before building, and refuses to publish an APK signed with any other key.

A keystore can be created once with `keytool`, or the ZzzTimer one reused. Keep it and its passwords safe: every future
update must be signed with the same key.

```bash
keytool -genkeypair -keystore caffeinedose-release.jks -alias caffeinedose -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 caffeinedose-release.jks   # value of CAFFEINEDOSE_SIGNING_KEYSTORE_BASE64 (macOS: base64 -i caffeinedose-release.jks)
```

### Verifying a download

- Its signing certificate can be checked with `apksigner verify --print-certs`, AppVerifier or Obtainium, against the
  SHA-256 digest listed in the release notes.
- It was built by this repository's release workflow:
  ```bash
  gh attestation verify CaffeineDose-vX.Y.Z.apk --repo abhi-k9/CaffeineDose
  ```
- Its SHA-256 checksum is published next to it: `sha256sum --check CaffeineDose-vX.Y.Z.apk.sha256`

## Privacy

CaffeineDose has no Internet permission and collects no data, see the [privacy policy](PRIVACY.md).

## License

    Copyright 2020 Simon Marquis
    Copyright 2026 abhi-k9

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
