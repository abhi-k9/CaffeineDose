#!/usr/bin/env bash
# Runs CaffeineDose on a connected device or emulator and checks, second by second, that the screen stays on while a
# timer runs (screen state, wake lock, overlay, process, foreground service, notification), and that everything is
# released once it ends. The app's own diagnostics log is collected: debug builds record it by default.
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
  for _ in 1 2 3 4 5; do
    device input keyevent KEYCODE_WAKEUP
    device wm dismiss-keyguard || true
    sleep 1
    if [ "$(wakefulness)" = Awake ]; then return; fi
  done
  echo "Could not wake the device up" >&2
}

# Active wake locks only: the dump also lists the history of the wake locks.
held_lock() {
  device dumpsys power | awk '/Wake Locks: size=/ { f = 1; next } f && /^ *$/ { exit } f' | grep "CaffeineDose:screen" | head -n 1 | sed 's/^ *//'
}

click_tile() {
  # The tile must be visible to be clicked.
  device cmd statusbar expand-settings
  sleep 2
  device cmd statusbar click-tile "$tile"
  sleep 1
  device cmd statusbar collapse
}

automation() { # <action> [duration in seconds]
  local extras=()
  if [ $# -gt 1 ]; then extras=(--el duration "$2"); fi
  device am broadcast -n "$receiver" -a "$ns.action.$1" "${extras[@]}" > /dev/null
}

# Starts a timer from the app, then leaves it, as a user would.
start_from_app() { # <duration in seconds>
  wake
  device am start -W -n "$pkg/$ns.ui.MainActivity" > /dev/null
  sleep 2
  automation START "$1"
  sleep 1
  device input keyevent KEYCODE_HOME
}

verdict() { # <scenario> <ok> <description>
  if [ "$2" = 1 ]; then
    echo "PASS $1: $3" | tee -a "$out/summary.txt"
  else
    echo "FAIL $1: $3, see $out/timeline.txt" | tee -a "$out/summary.txt"
    failures=$((failures + 1))
  fi
}

# Samples the state every second for <seconds>, then checks <expectation>:
# - held: a timer runs, the screen stays on, held by the service, in the same process (and by the overlay with
#   "overlay"),
# - released: the timer ended, nothing is held anymore, and the screen turns off after the usual timeout,
# - none: only recorded.
sample() { # <scenario> <seconds> <expectation> [overlay]
  local scenario=$1 seconds=$2 expectation=$3 with_overlay=${4:-}
  local start now state lock pid foreground notification overlay ok=1 pids="" shown=0
  start=$(date +%s)
  while now=$(date +%s); [ $((now - start)) -lt "$seconds" ]; do
    state=$(wakefulness)
    lock=$(held_lock || true)
    pid=$(device pidof "$pkg" || true)
    foreground=$(device dumpsys activity services "$pkg" | grep -c "isForeground=true" || true)
    notification=$(device dumpsys notification | grep -c "pkg=$pkg" || true)
    overlay=$(device dumpsys window windows | grep -c "u0 CaffeineDose}" || true)
    printf '%-16s t=%3ss screen=%-8s pid=%-6s foreground=%s notification=%s overlay=%s lock=%s\n' \
      "$scenario" $((now - start)) "$state" "${pid:-none}" "$foreground" "$notification" "$overlay" "${lock:-none}" |
      tee -a "$out/timeline.txt"
    if [ "$expectation" = held ]; then
      if [ "$state" != Awake ] || [ -z "$lock" ] || [ "$foreground" = 0 ]; then ok=0; fi
      if [ "$overlay" != 0 ]; then shown=1; fi
      pids="$pids ${pid:-none}"
    fi
    sleep 1
  done
  case $expectation in
    held)
      if [ "$(echo "$pids" | tr ' ' '\n' | sed '/^$/d' | sort -u | wc -l)" -ne 1 ]; then ok=0; fi
      if [ -n "$with_overlay" ] && [ "$shown" = 0 ]; then ok=0; fi
      verdict "$scenario" "$ok" "the screen stayed on, held by the service${with_overlay:+ and the overlay}, in the same process"
      ;;
    released)
      # The last sample: the screen must be off, and nothing held.
      if [ "$state" = Awake ] || [ -n "$lock" ] || [ "$overlay" != 0 ] || [ "$foreground" != 0 ]; then ok=0; fi
      verdict "$scenario" "$ok" "everything was released and the screen turned off"
      ;;
  esac
}

# The emulator reports a completed boot before every system service answers.
for _ in $(seq 1 60); do
  if device service check power | grep -q ": found" && device service check input | grep -q ": found" &&
    device service check window | grep -q ": found"; then
    break
  fi
  sleep 3
done
sleep 10

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

# Without timer, the screen turns off after the timeout: checks that the device and this script behave as expected.
wake
sample baseline 25 none

start_from_app 300
sample app 40 held
device dumpsys power > "$out/dumpsys-power-while-held.txt"
automation STOP
sample app-stopped 25 released

wake
click_tile
sample tile 40 held
click_tile
sample tile-stopped 25 released

# The screen is held until the timer ends, then turns off after the usual timeout.
start_from_app 30
sample expiring 25 held
sample expired 30 released

# With "Display over other apps": the invisible window keeps the screen on too, for devices ignoring the wake lock.
device appops set "$pkg" SYSTEM_ALERT_WINDOW allow
start_from_app 300
sample overlay 30 held overlay
automation STOP
sample overlay-stopped 25 released
device appops set "$pkg" SYSTEM_ALERT_WINDOW default

device logcat -d -v time > "$out/logcat.txt"
device dumpsys activity exit-info "$pkg" > "$out/exit-info.txt" || true
device run-as "$pkg" cat no_backup/diagnostics/diagnostics.log > "$out/diagnostics.log" || true

echo "== Crashes" | tee -a "$out/summary.txt"
grep -A 20 "FATAL EXCEPTION" "$out/logcat.txt" | tee -a "$out/summary.txt" || echo "none" | tee -a "$out/summary.txt"
echo "== Diagnostics log (latest last)" | tee -a "$out/summary.txt"
tail -n 120 "$out/diagnostics.log" | tee -a "$out/summary.txt"

echo "Failures: $failures" | tee -a "$out/summary.txt"
exit $((failures > 0 ? 1 : 0))
