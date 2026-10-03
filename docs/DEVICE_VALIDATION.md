# Alpha device validation

## Completed emulator validation

Source: `30ee86a15a6ed889f719a24b10a2577bcb5d6e6b`.
[CI run 37144128664](https://github.com/DrDeiss/Maffinet/actions/runs/37144128664),
2026-10-03, GitHub Linux runner, stable NDK r29, AOSP API 29 `default;x86_64`
Pixel emulator, portrait 1080×1920, Android three-button navigation.
The test's simulated VPN consent is explicitly restricted to qemu; no physical
device permission flow is inferred from it.

| Check | Recorded result |
| --- | --- |
| Debug, unsigned release and test APKs | All assembled; four native ABIs packaged |
| Standalone JVM/native contracts | 32 passed, zero failures/skips; six actual pinned-C tests, including all 73 preset arguments |
| Android JVM tests | 31 passed, zero failures/skips; includes four alpha/stable/version-tag cases |
| Android instrumentation | Nine passed, zero failures/errors/skips; eight custom checks plus inherited application-ID check |
| Android Lint | Zero errors, 157 warnings, nine hints |
| First launch | Development build opens Maffinet welcome without an upstream notice; welcome survives Activity relaunch and the deferred notice is not marked seen |
| Product navigation/attribution | Four destinations open; debug Home/About/onboarding contain no NetFix/rupleide reminders; release attribution gate compiles enabled |
| Update state | Disabled automatic checks leave About explicitly unchecked; network/no-release/no-APK outcomes remain distinct in source |
| Saved services | YouTube/Instagram choices survive closing and opening a new Activity |
| Saved User domains | Normalization/save/disable survive relaunch; an invalid URL cannot overwrite the saved list |
| Native namespace | Eight shipped binary hashes and four inverse HEV provenance checks pass; x86_64 runtime resolves the new Maffinet JNI class without link/registration errors |
| Native start/stop/retry | Actual Maffinet JNI SOCKS negotiation and Android TUN establish twice; temporary config, socket and TUN descriptors clean up |
| Failed routing/start cancellation | Empty installed routing cleans up native resources and permits retry; immediate STOP invalidates queued startup |
| Universal candidate/restoration | One real native candidate probes every selected profile; candidate resources stop before original VPN restoration |
| Visual evidence | 12 UI PNGs collected and inspected; product and About scroll content stay clear of floating/system navigation |

Original captures of Home, Services, Strategies, About and the domain validation
error are retained in [screenshots](screenshots/README.md). The full 12-image set,
JUnit/Lint reports and emulator/logcat output are in CI artifact
`android-validation` (ID `11280884164`, SHA-256
`916601120da4fe952f88004d6081387bf8cc084e742ec4e0afcc057ec25b2ff5`).
The standalone suite report is artifact `jvm-validation` (ID `11281368133`).
Documentation-only commits after the tested source do not change application code.
Instrumentation ran against debug; release was assembled but its attribution UI
was not executed on the emulator. ARM and x86 binaries pass structural/hash
checks; their device runtime remains unverified.
Visual QA also retains one earlier layout issue: with the keyboard open in the
restored domain editor, its header can scroll under the status bar; the editable
domain field stays readable. This is separate from the attribution change.

Instrumented probes run from the application's excluded UID. The TUN is configured
with a distinct installed test APK UID, but these tests do not generate traffic
from that helper UID. They prove native/TUN lifecycle and candidate probes;
they do not prove full routed application behavior or provider-specific bypass.
Remote HTTP/TLS success is not required for the lifecycle test to pass.

## Physical phone and Android TV acceptance

Record device model, Android version, ABI, provider/network and build commit.
Phone and Android TV checks are distinct. All physical-device rows remain unverified.

| Check | Expected result | Result |
| --- | --- | --- |
| Installation | Maffinet and NetFix packages/data remain separate | Pending |
| First launch | Maffinet branding; notice deferred in debug and shown in release | Pending |
| VPN permission/start/stop | Consent, foreground notice, complete cleanup | Pending |
| YouTube regression | Known working strategy reaches web/media | Pending |
| Service routing | Installed enabled packages enter allowed routing | Pending |
| Manual selections | Explicit app choices combine with service routing | Pending |
| Domain lists | Built-in/service/user domains reach ByeDPI arguments | Pending |
| User list | Add/edit/remove/toggle/import/export persist after restart | Pending |
| Multi-service probes | Results show each selected service and coverage | Pending |
| Auto | Coverage ranks before latency deterministically | Pending |
| DNS/IPv6 | Existing resolver and IPv6 controls work | Pending |
| Network transition | Wi-Fi/mobile switch reconnects a working tunnel | Pending |
| Background/reboot | Foreground service and optional boot start work | Pending |
| Proxy mode | Existing ByeDPI and Telegram proxy controls work | Pending |
| Strategies | Existing imports/exports and legacy placeholders work | Pending |
| TV | Remote focus, navigation, connect and back work | Pending |

HTTP/TLS probe success alone does not certify full Android application behavior.
