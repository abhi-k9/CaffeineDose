#!/usr/bin/env bash
# Runs CaffeineDose on a connected device or emulator and checks, second by second, that the screen stays on while a
# timer runs: screen state, wake lock, process, foreground service and notification.
#
# Usage: device-test.sh <debug APK> [output directory]
set -euo pipefail

apk=$1
out=${2:-device-test}
pkg=io.github.abhik9.caffeinedose.debug
ns=io.github.abhik9.caffeinedose
tile="$pkg/$ns.tile.AwakeTileService"
receiver="$pkg/$ns.automation.AutomationReceiver"
# Short, so that a screen that is not held turns off quickly.
screen_timeout_s=15
failures=0

mkdir -p "$out"

device() { adb shell "$@" | tr -d '\r'; }

wakefulness() { device dumpsys power | sed -n 's/^ *mWakefulness=//p' | head -n 1; }

wake() {
  device input keyevent KEYCODE_WAKEUP
  device wm dismiss-keyguard || true
  sleep 1
}

automation() { # <action> [duration in seconds]
  local extras=()
  if [ $# -gt 1 ]; then extras=(--el duration "$2"); fi
  device am broadcast -n "$receiver" -a "$ns.action.$1" "${extras[@]}" > /dev/null
}

# Samples the state every second for <seconds>. <expectation> is "held" (a timer runs: the screen must stay on) or
# "free" (no timer: only recorded).
sample() { # <scenario> <seconds> <expectation>
  local scenario=$1 seconds=$2 expectation=$3
  local start now state lock pid foreground notification ok=1 pids=""
  start=$(date +%s)
  while now=$(date +%s); [ $((now - start)) -lt "$seconds" ]; do
    state=$(wakefulness)
    lock=$(device dumpsys power | grep "CaffeineDose:screen" | head -n 1 | sed 's/^ *//' || true)
    pid=$(device pidof "$pkg" || true)
    foreground=$(device dumpsys activity services "$pkg" | grep -c "isForeground=true" || true)
    notification=$(device dumpsys notification | grep -c "pkg=$pkg" || true)
    printf '%-14s t=%3ss screen=%-8s pid=%-6s foreground=%s notification=%s lock=%s\n' \
      "$scenario" $((now - start)) "$state" "${pid:-none}" "$foreground" "$notification" "${lock:-none}" | tee -a "$out/timeline.txt"
    if [ "$expectation" = held ]; then
      if [ "$state" != Awake ] || [ -z "$lock" ] || [ "$foreground" = 0 ]; then ok=0; fi
      pids="$pids ${pid:-none}"
    fi
    sleep 1
  done
  if [ "$expectation" = held ]; then
    if [ "$(echo "$pids" | tr ' ' '\n' | sed '/^$/d' | sort -u | wc -l)" -ne 1 ]; then ok=0; fi
    if [ "$ok" = 1 ]; then
      echo "PASS $scenario: the screen stayed on, held by the service, in the same process" | tee -a "$out/summary.txt"
    else
      echo "FAIL $scenario: see $out/timeline.txt" | tee -a "$out/summary.txt"
      failures=$((failures + 1))
    fi
  fi
}

echo "Device: $(device getprop ro.product.model), Android $(device getprop ro.build.version.release) (API $(device getprop ro.build.version.sdk))" |
  tee "$out/summary.txt"

adb install -r "$apk" > /dev/null
device pm grant "$pkg" android.permission.POST_NOTIFICATIONS
device settings put global stay_on_while_plugged_in 0
device svc power stayon false
device settings put system screen_off_timeout $((screen_timeout_s * 1000))
device locksettings set-disabled true || true
device cmd statusbar add-tile "$tile" || true
device logcat -c

echo "== Power configuration" | tee -a "$out/summary.txt"
device dumpsys power | grep -iE "timeout|attentive|override" | sed 's/^ */  /' | tee -a "$out/summary.txt" || true

# Without timer, the screen turns off after the timeout: checks that the device and this script behave as expected.
wake
sample baseline 35 free

# Started from the app, then the app is left: the user's first scenario.
wake
device am start -W -n "$pkg/$ns.ui.MainActivity" > /dev/null
sleep 2
automation START 300
sleep 1
device input keyevent KEYCODE_HOME
sample app 75 held
device dumpsys power > "$out/dumpsys-power-while-held.txt"
device dumpsys activity services "$pkg" > "$out/dumpsys-service-while-held.txt"
automation STOP
sample app-stopped 40 free

# Started from the Quick Settings tile: the user's second scenario.
wake
device cmd statusbar click-tile "$tile"
sleep 1
device cmd statusbar collapse
sample tile 75 held
device cmd statusbar click-tile "$tile"
device cmd statusbar collapse
sample tile-stopped 40 free

# A timer that ends: the screen is held until then, then turns off after the usual timeout.
wake
device am start -W -n "$pkg/$ns.ui.MainActivity" > /dev/null
sleep 2
automation START 45
sleep 1
device input keyevent KEYCODE_HOME
sample expiring 40 held
sample expired 40 free

device logcat -d -v time > "$out/logcat.txt"
device dumpsys activity exit-info "$pkg" > "$out/exit-info.txt" || true

echo "== App log" | tee -a "$out/summary.txt"
grep -E "AwakeService|ServiceScreenKeeper|DoseActionReceiver|AutomationReceiver|AndroidRuntime|FATAL|$pkg" "$out/logcat.txt" |
  grep -vE "^\s+at " | tail -n 150 | tee -a "$out/summary.txt" || true
echo "== Process exits" | tee -a "$out/summary.txt"
grep -E "ApplicationExitInfo|reason=|description=" "$out/exit-info.txt" | head -n 30 | tee -a "$out/summary.txt" || true

echo "Failures: $failures" | tee -a "$out/summary.txt"
exit $((failures > 0 ? 1 : 0))
