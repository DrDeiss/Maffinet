# Alpha device validation

## Completed emulator validation

Source: `256dfaee5559eaf827e3228805d57653257bc56e`.
[CI run 37141382387](https://github.com/DrDeiss/Maffinet/actions/runs/37141382387),
2026-10-03, GitHub Linux runner, stable NDK r29, AOSP API 29 `default;x86_64`
Pixel emulator, portrait 1080×1920, Android three-button navigation.
The test's simulated VPN consent is explicitly restricted to qemu; no physical
device permission flow is inferred from it.

| Check | Recorded result |
| --- | --- |
| Debug APK and test APK | Both assembled; four native ABIs packaged |
| Standalone JVM/native contracts | 32 passed, zero failures/skips; six actual pinned-C tests, including all 73 preset arguments |
| Android JVM tests | 31 passed, zero failures/skips; includes four alpha/stable/version-tag cases |
| Android instrumentation | Nine passed, zero failures/errors/skips; eight custom checks plus inherited application-ID check |
| Android Lint | Zero errors, 157 warnings, nine hints |
| First launch | Required author notice appears; acknowledgement survives Activity relaunch |
| Product navigation/attribution | Four destinations open; Home and About contain the required fork marking |
| Update state | Disabled automatic checks leave About explicitly unchecked; network/no-release/no-APK outcomes remain distinct in source |
| Saved services | YouTube/Instagram choices survive closing and opening a new Activity |
| Saved User domains | Normalization/save/disable survive relaunch; an invalid URL cannot overwrite the saved list |
| Native start/stop/retry | Actual JNI SOCKS negotiation and Android TUN establish twice; temporary config, socket and TUN descriptors clean up |
| Failed routing/start cancellation | Empty installed routing cleans up native resources and permits retry; immediate STOP invalidates queued startup |
| Universal candidate/restoration | One real native candidate probes every selected profile; candidate resources stop before original VPN restoration |
| Visual evidence | 12 UI PNGs collected and inspected; product and About scroll content stay clear of floating/system navigation |

Original captures of Home, Services, Strategies, About and the domain validation
error are retained in [screenshots](screenshots/README.md). The full 12-image set,
JUnit/Lint reports and emulator/logcat output are in CI artifact
`android-validation` (ID `11280812005`, SHA-256
`e8508be5862f77460b6c0937e0946b2d4a51c4e72db401b5cdd86cbea11a83d5`).
The standalone suite report is artifact `jvm-validation` (ID `11280716703`).
Documentation-only commits after the tested source do not change application code.

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
| First launch | Maffinet branding and fork notice appear | Pending |
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
