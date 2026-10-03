#!/usr/bin/env bash
# Requires a licensed, already provisioned Linux Android SDK. This script never
# installs SDK packages or answers license prompts. KVM permission changes are
# limited to an ephemeral GitHub Actions runner.
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:?Set ANDROID_HOME to the provisioned Android SDK.}"
ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"
AVD_MANAGER="$SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
IMAGE="system-images;android-29;default;x86_64"
AVD_NAME="maffinet-smoke-api29"
REPORT_DIR="$ROOT_DIR/app/build/reports/emulator-smoke"
WORK_DIR="${RUNNER_TEMP:-$ROOT_DIR/.toolchain}/maffinet-emulator-smoke"
export ANDROID_USER_HOME="$WORK_DIR/android-user"
export ANDROID_AVD_HOME="$WORK_DIR/avd"
export ANDROID_SERIAL="emulator-5580"
EMULATOR_PID=""

mkdir -p "$REPORT_DIR/screenshots" "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
if [[ ! -x "$AVD_MANAGER" ]]; then
  mapfile -t AVD_MANAGERS < <(find "$SDK_ROOT/cmdline-tools" -mindepth 3 -maxdepth 3 -type f -name avdmanager -path '*/bin/avdmanager' | sort -V)
  if (( ${#AVD_MANAGERS[@]} == 0 )); then
    echo 'No installed avdmanager was found.' >&2
    exit 1
  fi
  AVD_MANAGER="${AVD_MANAGERS[-1]}"
fi
for executable in "$ADB" "$EMULATOR" "$AVD_MANAGER"; do
  test -x "$executable" || { echo "Missing SDK executable: $executable" >&2; exit 1; }
done
test -s "$SDK_ROOT/system-images/android-29/default/x86_64/package.xml" || {
  echo "Install $IMAGE with agreements already accepted by its owner." >&2
  exit 1
}

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  if [[ -n "$EMULATOR_PID" ]]; then
    timeout 15s "$ADB" -s "$ANDROID_SERIAL" logcat -d -v threadtime > "$REPORT_DIR/logcat.txt" 2>&1
    timeout 10s "$ADB" -s "$ANDROID_SERIAL" exec-out screencap -p > "$REPORT_DIR/screenshots/final-screen.png" 2> "$REPORT_DIR/screencap.log"
    timeout 20s "$ADB" -s "$ANDROID_SERIAL" pull /sdcard/Android/data/io.maffinet.android/files/ui-smoke "$REPORT_DIR/screenshots/" > "$REPORT_DIR/screenshot-pull.log" 2>&1
    timeout 10s "$ADB" -s "$ANDROID_SERIAL" emu kill >> "$REPORT_DIR/emulator-stop.log" 2>&1
    kill -TERM "$EMULATOR_PID" 2>/dev/null
    for ((attempt=0; attempt<10; attempt++)); do
      kill -0 "$EMULATOR_PID" 2>/dev/null || break
      sleep 1
    done
    kill -KILL "$EMULATOR_PID" 2>/dev/null
    wait "$EMULATOR_PID" 2>/dev/null
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
  test -c /dev/kvm || { echo 'The GitHub runner does not expose KVM.' >&2; exit 1; }
  sudo chmod a+rw /dev/kvm
fi
test -r /dev/kvm && test -w /dev/kvm || {
  echo 'KVM is required; grant access on this Linux test host before running.' >&2
  exit 1
}
timeout 15s "$EMULATOR" -version > "$REPORT_DIR/emulator-version.txt" 2>&1
timeout 15s "$EMULATOR" -accel-check > "$REPORT_DIR/acceleration.txt" 2>&1
printf 'no\n' | timeout 60s "$AVD_MANAGER" create avd --force --name "$AVD_NAME" --package "$IMAGE" --path "$ANDROID_AVD_HOME/$AVD_NAME.avd" --device pixel > "$REPORT_DIR/avd-create.log" 2>&1
timeout 15s "$ADB" start-server
if "$ADB" devices | awk '{print $1}' | grep -Fxq "$ANDROID_SERIAL"; then
  echo "An emulator already occupies $ANDROID_SERIAL; refusing to replace it." >&2
  exit 1
fi
"$EMULATOR" -avd "$AVD_NAME" -port 5580 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -accel on -memory 2048 -cores 2 -camera-back none -camera-front none > "$REPORT_DIR/emulator.log" 2>&1 &
EMULATOR_PID=$!

# Avoid an unbounded adb wait-for-device: each command and the boot loop have
# their own limits, and a crashed emulator immediately fails the job.
BOOT_DEADLINE=$((SECONDS + 360))
BOOTED=false
while (( SECONDS < BOOT_DEADLINE )); do
  kill -0 "$EMULATOR_PID" 2>/dev/null || { echo 'Emulator exited before boot.' >&2; exit 1; }
  REMAINING=$((BOOT_DEADLINE - SECONDS))
  (( REMAINING > 0 )) || break
  COMMAND_LIMIT=$((REMAINING < 5 ? REMAINING : 5))
  if [[ "$(timeout "${COMMAND_LIMIT}s" "$ADB" -s "$ANDROID_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]]; then
    BOOTED=true
    break
  fi
  REMAINING=$((BOOT_DEADLINE - SECONDS))
  (( REMAINING > 0 )) && sleep "$((REMAINING < 3 ? REMAINING : 3))"
done
[[ "$BOOTED" == true ]] || { echo 'Emulator failed to boot within six minutes.' >&2; exit 1; }
timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell input keyevent 82
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell settings put global "$setting" 0
done
timeout 10s "$ADB" -s "$ANDROID_SERIAL" logcat -c
cd "$ROOT_DIR"
timeout --signal=TERM --kill-after=20s 12m bash ./gradlew :app:connectedDebugAndroidTest -Pmaffinet.ndkVersion=29.0.14206865 -Pandroid.testInstrumentationRunnerArguments.maffinet.allowEmulatorVpnConsent=true --no-daemon --console=plain 2>&1 | tee "$REPORT_DIR/instrumentation.log"
