# Alpha device validation

## Completed independent-mode validation

Source: `34ed3a1d13d55ab8c25f21c6042a930dd85842c5`.
[CI run 37153332402](https://github.com/DrDeiss/Maffinet/actions/runs/37153332402),
2026-10-03 UTC / 2026-10-04 Moscow, GitHub Linux runner, stable NDK r29,
AOSP API29 `default;x86_64` Pixel emulator, portrait 1080×1920, three-button
navigation. Applications/Telegram, explicit routing and independent hosts/targets
supersede the historical service-catalog model below.

| Check | Recorded result |
| --- | --- |
| Debug, unsigned release and test APKs | All assembled; four native ABIs packaged |
| Standalone JVM/native | 43 passed, zero failures/skips; six actual C-parser contracts including all 73 preset commands |
| Android JVM | 42 passed, zero failures/skips |
| Android instrumentation | 22 passed, zero failures/errors/skips: 11 native lifecycle, six UI, three probe/history, one migration and inherited application-ID check |
| Android Lint | Zero errors, 135 warnings, five hints; warnings remain release work, not a clean-lint claim |
| Mode combinations | Actual Applications only, Telegram only and both; neither selected rejects start; empty installed allowlist never creates a device-wide VPN |
| Partial failures/retry | Invalid app selection still permits Telegram; occupied Telegram port reports failure without stopping VPN; stop/retry cleans up native resources |
| STOP/restart | Native SOCKS/TUN establish repeatedly; common STOP cancels pending VPN restart; actual Telegram notification STOP PendingIntent closes the listener and permits retry |
| Telegram UI | Home shows actual running/stopped listener states; choices/configuration lock while running; notification has real proxy status and only supported STOP action |
| Routing/migration | All four inherited choice combinations migrate once; manual selection and DNS survive; later service flags never insert packages; own app excluded |
| Direct app/DNS controls | Actual app selection/deselection/reselection via picker; preset selection survives Activity relaunch; native VPN LinkProperties contain selected DNS before/after restart |
| Hosts | General/User independent of application/service choices; normalized edits, disabled state and invalid-save protection survive relaunch; repository merge/import/export round trip and native host-filter contracts pass |
| Strategy probes/history | Independent addresses persist; one real candidate uses current compiled hosts and restores VPN after testing/cancellation; hosts/URL/override invalidate history; deleted commands cannot return or be applied, explicit reimport keeps no old measurements |
| Recovery policy | Saved per-mode recovery/foreground service and simulated boot/watchdog entry points tested; boot with autostart off clears old session requests so later watchdog cannot revive them; economy choice retained |
| Branding/navigation | Home/Strategies/Settings with direct Hosts, app picker, DNS and Telegram settings; debug Home/About/welcome have no upstream reminders; release attribution branch assembles |
| Native contracts | Eight shipped binary hashes and four inverse HEV provenance checks pass; x86_64 runtime resolves the Maffinet JNI class; no fatal/JNI linkage errors found in recorded logcat |
| Visual evidence | 19 original UIAutomation PNGs inspected; compact app/DNS lists have usable scrolling; Home state, General list, saved User data and validation error captured |

Reports/logs and all 19 captures are in `android-validation` artifact
`11284907595` (ZIP SHA-256
`cfdae66f48883d6bb2e777aceda6c37a2fd18aff7acb5b27b66995f91c291751`).
The standalone report is `jvm-validation` artifact `11284912100`. Original PNGs
are saved in [screenshots](screenshots/README.md), with per-file hashes.
Documentation-only commits after this source do not change tested application code.

Local Windows verification passed 37 JVM tests; six Linux C-fixture tests were
explicitly skipped locally and executed successfully in CI. Syntax inspection
covered 92 Kotlin files; native hash/JNI checks passed. Local Android Gradle tasks
could not configure because the SDK is absent; Android assembly/tests/lint above
were executed on the provisioned CI runner.

The emulator-only VPN-consent shortcut is restricted to qemu; actual physical
consent is unverified. Probes run from Maffinet's excluded UID. A distinct installed
test APK UID enters the TUN allowlist, but these tests do not generate routed
traffic from that helper. Remote HTTP success is not required by lifecycle tests;
provider bypass, authentication/media and complete selected-app traffic remain
unverified. Document-picker SAF interaction and external Telegram deep-link use
were not automated; their existing flows are retained and repository round trips
were checked. Boot/watchdog checks invoke real components under simulated policy,
not a physical reboot or OEM process-kill campaign. Instrumentation uses debug;
release attribution UI and other ABI device runtimes are unverified. TV, landscape,
large fonts, other Android versions and 16KB-page environments need separate checks.
Scrolling captures may show a neighbouring card at the viewport edge; User editing
can show the keyboard. Phone/TV acceptance below remains open.

## Historical completed emulator baseline

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

The historical Services capture is retained in [screenshots](screenshots/README.md);
stable screenshot filenames now show the clarified model. The full original 12-image set,
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
| Mode combinations | Applications only, Telegram only, both; both off explains requirement | Pending |
| Partial startup/errors | One failed mode does not stop the other; each runtime state is truthful | Pending |
| Explicit routing | Only installed `selected_apps` packages enter allowlist; own app excluded; empty selection rejects VPN | Pending |
| Saved app choices | Update/restart retains choices; service flags never insert packages | Pending |
| Hosts independence | Fixed General plus enabled User reach ByeDPI; app/service changes leave hosts unchanged | Pending |
| User list | Add/edit/remove/toggle/import/export persist after restart | Pending |
| Independent probes | Results show configured checking addresses; changed hosts/targets invalidate history | Pending |
| Auto | Coverage ranks before latency deterministically | Pending |
| DNS/IPv6 | Existing VPN resolver and IPv6 controls work; DNS does not configure Telegram proxy | Pending |
| Network transition | Wi-Fi/mobile switch reconnects a working tunnel | Pending |
| Background/reboot/watchdog | Recover only each requested mode; STOP does not resurrect either engine | Pending |
| Repeat/independent STOP | Repeated native starts clean up; engine-specific STOP leaves the other mode intact | Pending |
| Telegram only | Existing MTProto settings/connectivity work with Applications disabled | Pending |
| Strategies | Existing imports/exports and legacy placeholders work | Pending |
| TV | Remote focus, navigation, connect and back work | Pending |

HTTP/TLS probe success alone does not certify full Android application behavior.
