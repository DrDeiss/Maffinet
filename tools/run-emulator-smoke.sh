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
API_LEVEL="${MAFFINET_SMOKE_API_LEVEL:-29}"
IMAGE_TAG="${MAFFINET_SMOKE_IMAGE_TAG:-default}"
IMAGE_ARCH="${MAFFINET_SMOKE_IMAGE_ARCH:-x86_64}"
EMULATOR_PORT="${MAFFINET_SMOKE_EMULATOR_PORT:-5580}"
PREBUILT="${MAFFINET_SMOKE_PREBUILT:-false}"
[[ "$API_LEVEL" =~ ^[0-9]+$ && "$IMAGE_TAG" =~ ^[a-z0-9_]+$ && "$IMAGE_ARCH" =~ ^[a-z0-9_-]+$ ]] || {
  echo 'API level, image tag and architecture must identify an SDK system-image package.' >&2; exit 1;
}
[[ "$EMULATOR_PORT" =~ ^[0-9]+$ ]] && (( EMULATOR_PORT >= 5554 && EMULATOR_PORT <= 5682 && EMULATOR_PORT % 2 == 0 )) || {
  echo 'Choose an unused even emulator port between 5554 and 5682.' >&2; exit 1;
}
[[ "$PREBUILT" == true || "$PREBUILT" == false ]] || { echo 'MAFFINET_SMOKE_PREBUILT must be true or false.' >&2; exit 1; }
IMAGE="system-images;android-$API_LEVEL;$IMAGE_TAG;$IMAGE_ARCH"
RUN_ID="api$API_LEVEL-$IMAGE_TAG-$IMAGE_ARCH"
AVD_NAME="maffinet-smoke-$RUN_ID"
REPORT_DIR="${MAFFINET_SMOKE_REPORT_DIR:-$ROOT_DIR/app/build/reports/emulator-smoke/$RUN_ID}"
WORK_DIR="${MAFFINET_SMOKE_WORK_DIR:-${RUNNER_TEMP:-$ROOT_DIR/.toolchain}/maffinet-emulator-smoke/$RUN_ID-port$EMULATOR_PORT}"
export ANDROID_USER_HOME="$WORK_DIR/android-user"
export ANDROID_AVD_HOME="$WORK_DIR/avd"
export ANDROID_SERIAL="emulator-$EMULATOR_PORT"
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
test -s "$SDK_ROOT/system-images/android-$API_LEVEL/$IMAGE_TAG/$IMAGE_ARCH/package.xml" || {
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
    local screenshot_count=0
    if [[ -d "$REPORT_DIR/screenshots/ui-smoke" ]]; then
      screenshot_count=$(find "$REPORT_DIR/screenshots/ui-smoke" -type f -name '*.png' -size +0c | wc -l)
    fi
    printf 'Captured UI screenshots: %s\n' "$screenshot_count" | tee "$REPORT_DIR/screenshot-summary.txt"
    if (( screenshot_count == 0 )); then
      echo 'No UI test screenshots were collected; the final emulator screen alone is insufficient.' >&2
      cat "$REPORT_DIR/screenshot-pull.log" >&2
      # Preserve an earlier test/boot failure, and fail otherwise successful
      # tests whose required visual evidence could not be exported.
      (( status != 0 )) || status=1
    fi
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
if ! timeout 15s "$EMULATOR" -version > "$REPORT_DIR/emulator-version.txt" 2>&1; then
  cat "$REPORT_DIR/emulator-version.txt" >&2
  ldd "$SDK_ROOT/emulator/qemu/linux-x86_64/qemu-system-x86_64" > "$REPORT_DIR/emulator-loader.txt" 2>&1 || true
  cat "$REPORT_DIR/emulator-loader.txt" >&2
  exit 1
fi
if ! timeout 15s "$EMULATOR" -accel-check > "$REPORT_DIR/acceleration.txt" 2>&1; then
  cat "$REPORT_DIR/acceleration.txt" >&2
  exit 1
fi
if ! printf 'no\n' | timeout 60s "$AVD_MANAGER" create avd --force --name "$AVD_NAME" --package "$IMAGE" --path "$ANDROID_AVD_HOME/$AVD_NAME.avd" --device pixel > "$REPORT_DIR/avd-create.log" 2>&1; then
  cat "$REPORT_DIR/avd-create.log" >&2
  exit 1
fi
timeout 15s "$ADB" start-server
if "$ADB" devices | awk '{print $1}' | grep -Fxq "$ANDROID_SERIAL"; then
  echo "An emulator already occupies $ANDROID_SERIAL; refusing to replace it." >&2
  exit 1
fi
"$EMULATOR" -avd "$AVD_NAME" -port "$EMULATOR_PORT" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -accel on -memory 2048 -cores 2 -camera-back none -camera-front none > "$REPORT_DIR/emulator.log" 2>&1 &
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
ACTUAL_API="$(timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$ACTUAL_API" == "$API_LEVEL" ]] || { echo "Expected API $API_LEVEL, booted API $ACTUAL_API." >&2; exit 1; }
printf 'API=%s\nimage=%s\nserial=%s\n' "$ACTUAL_API" "$IMAGE" "$ANDROID_SERIAL" > "$REPORT_DIR/device.txt"
timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell input keyevent 82
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell settings put global "$setting" 0
done
timeout 10s "$ADB" -s "$ANDROID_SERIAL" logcat -c
cd "$ROOT_DIR"

# adb exits successfully even when AndroidJUnitRunner reports a failed test.
# Check its final JUnit result and preserve per-test XML alongside the raw log.
verify_instrumentation() {
  python3 - "$REPORT_DIR/$1.log" "$REPORT_DIR/$1-results.xml" "$API_LEVEL" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

log_file, xml_file = map(Path, sys.argv[1:3])
api_level = int(sys.argv[3])
log = log_file.read_text(encoding="utf-8", errors="replace")
suite = ET.Element("testsuite", name=log_file.stem)
status = {}
last_key = None
failed = 0
skipped = 0
for line in log.splitlines():
    if line.startswith("INSTRUMENTATION_STATUS: "):
        field = line.removeprefix("INSTRUMENTATION_STATUS: ")
        key, separator, value = field.partition("=")
        if separator:
            status[key] = value
            last_key = key
    elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
        code = int(line.rsplit(": ", 1)[1])
        if code <= 0 and "test" in status:
            case = ET.SubElement(suite, "testcase", name=status["test"], classname=status.get("class", "AndroidJUnitRunner"))
            if code in (-1, -2):
                failed += 1
                ET.SubElement(case, "failure", message="Instrumentation test failed").text = status.get("stack", status.get("stream", ""))
            elif code in (-3, -4):
                skipped += 1
                ET.SubElement(case, "skipped").text = status.get("stack", status.get("stream", ""))
        status = {}
        last_key = None
    elif line.startswith("INSTRUMENTATION_"):
        last_key = None
    elif last_key in ("stack", "stream"):
        status[last_key] += "\n" + line
completed = re.search(r"^\s*OK \(([1-9][0-9]*) tests?\)", log, re.MULTILINE)
runner_failed = re.search(r"^INSTRUMENTATION_(?:FAILED|ABORTED):|^FAILURES!!!|^INSTRUMENTATION_STATUS_CODE: -(?:1|2)\s*$", log, re.MULTILINE)
if not completed or runner_failed:
    if not failed:
        failed = 1
        case = ET.SubElement(suite, "testcase", name="runnerCompletion", classname="AndroidJUnitRunner")
        ET.SubElement(case, "failure", message="Runner did not finish successfully").text = log[-12000:]
required = ["modernForegroundServicesWorkWithDeniedOrGrantedNotifications"] if log_file.stem.endswith("notifications-denied") else ["nativeSocksAndTunStartStopAndStartAgain"]
if api_level >= 33 and not log_file.stem.endswith("notifications-denied"):
    required.append("modernForegroundServicesWorkWithDeniedOrGrantedNotifications")
for name in required:
    matches = [case for case in suite if case.get("name") == name and case.get("classname") == "io.maffinet.android.network.NativeVpnLifecycleSmokeTest"]
    if not matches or any(len(case) for case in matches):
        failed += 1
        case = ET.SubElement(suite, "testcase", name="requiredLifecycleCheck", classname="AndroidJUnitRunner")
        ET.SubElement(case, "failure", message="Required lifecycle check did not pass").text = name
suite.set("tests", str(len(suite)))
suite.set("failures", str(failed))
suite.set("skipped", str(skipped))
ET.ElementTree(suite).write(xml_file, encoding="utf-8", xml_declaration=True)
if failed:
    sys.exit("Instrumentation failed; see " + str(log_file))
print(f"{log_file.stem}: {completed.group(1)} tests, {skipped} skipped; result XML: {xml_file}")
PY
}

check_denied_notifications() {
  (( API_LEVEL >= 33 )) || return 0
  # Revoking a runtime permission can kill the target process. Do it only
  # between runner invocations, then check real FGS/VPN lifecycle with denial.
  timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell pm revoke io.maffinet.android android.permission.POST_NOTIFICATIONS
  timeout --signal=TERM --kill-after=20s 2m "$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -r \
    -e maffinet.allowEmulatorVpnConsent true \
    -e maffinet.expectedNotificationPermission denied \
    -e class io.maffinet.android.network.NativeVpnLifecycleSmokeTest#modernForegroundServicesWorkWithDeniedOrGrantedNotifications \
    io.maffinet.android.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee "$REPORT_DIR/instrumentation-notifications-denied.log"
  verify_instrumentation instrumentation-notifications-denied
  timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell pm grant io.maffinet.android android.permission.POST_NOTIFICATIONS
}

if [[ "$PREBUILT" == true ]]; then
  mapfile -t APP_APKS < <(find "$ROOT_DIR/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | sort)
  mapfile -t TEST_APKS < <(find "$ROOT_DIR/app/build/outputs/apk/androidTest/debug" -maxdepth 1 -type f -name '*.apk' | sort)
  (( ${#APP_APKS[@]} == 1 && ${#TEST_APKS[@]} == 1 )) || { echo 'Expected exactly one debug app APK and one test APK from the build job.' >&2; exit 1; }
  sha256sum "${APP_APKS[0]}" "${TEST_APKS[0]}" > "$REPORT_DIR/apk-sha256.txt"
  timeout 120s "$ADB" -s "$ANDROID_SERIAL" install -r "${APP_APKS[0]}"
  timeout 120s "$ADB" -s "$ANDROID_SERIAL" install -r "${TEST_APKS[0]}"
  timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell dumpsys package io.maffinet.android > "$REPORT_DIR/installed-app.txt"
  if (( API_LEVEL >= 33 )); then
    # Avoid a system permission dialog during existing UI smoke. Permission
    # denial is exercised in a separate invocation below, outside the target UID.
    timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell pm grant io.maffinet.android android.permission.POST_NOTIFICATIONS
  fi
  timeout --signal=TERM --kill-after=20s 12m "$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -r \
    -e maffinet.allowEmulatorVpnConsent true \
    -e maffinet.expectedNotificationPermission granted \
    io.maffinet.android.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee "$REPORT_DIR/instrumentation.log"
  verify_instrumentation instrumentation
  check_denied_notifications
  exit 0
fi

if (( API_LEVEL >= 33 )); then
  # connectedDebugAndroidTest installs the app immediately before testing.
  # Preinstall it so notification permission is already granted at first launch.
  timeout --signal=TERM --kill-after=20s 12m bash ./gradlew :app:assembleDebug -Pmaffinet.ndkVersion=29.0.14206865 --no-daemon --console=plain 2>&1 | tee "$REPORT_DIR/manual-build.log"
  mapfile -t MANUAL_APP_APKS < <(find "$ROOT_DIR/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | sort)
  (( ${#MANUAL_APP_APKS[@]} == 1 )) || { echo 'Expected exactly one assembled debug app APK.' >&2; exit 1; }
  timeout 120s "$ADB" -s "$ANDROID_SERIAL" install -r "${MANUAL_APP_APKS[0]}"
  timeout 10s "$ADB" -s "$ANDROID_SERIAL" shell pm grant io.maffinet.android android.permission.POST_NOTIFICATIONS
fi

# AGP 9.0.1 marks this retention option stable and wires it to keepInstalledApks.
# Retain APKs only on this isolated test emulator until screenshots are pulled.
# AndroidX uses the same option for screenshot outputs:
# https://android.googlesource.com/platform/frameworks/support/+/eb74b1a4b3527414639994574a201f125aff90f5
timeout --signal=TERM --kill-after=20s 12m bash ./gradlew :app:connectedDebugAndroidTest -Pmaffinet.ndkVersion=29.0.14206865 -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.maffinet.allowEmulatorVpnConsent=true -Pandroid.testInstrumentationRunnerArguments.maffinet.expectedNotificationPermission=granted --no-daemon --console=plain 2>&1 | tee "$REPORT_DIR/instrumentation.log"
check_denied_notifications
