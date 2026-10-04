# Maffinet — audit and implementation plan

## DNS configuration follow-up — 0.3.1-alpha sources, 2026-10-04

Implemented separate saved/VPN-assigned/physical/Private DNS snapshots, explicit
unknown app DoH/DoT, plain UDP/TCP DNS outcomes, bounded control-domain DNS/HTTPS
checks and Home diagnostics. Network handle, interface addresses, routes and all
DNS assignments scope evidence; stale UI evidence is hidden even when two networks
have identical resolver lists. A preference/assignment mismatch requests reconnect
and never silently changes the saved resolver. See docs/AUTOMATIC_ACCESS.md.

Local standalone verification: 158 cases, 151 passed, zero failures, seven
Linux-native fixture cases skipped. After explicit upload confirmation, the source
commit `f596fe954c5fbafea40f270ec3bc4251f6a4847a` was pushed to the authorized
branch. [CI run 37205002273](https://github.com/DrDeiss/Maffinet/actions/runs/37205002273)
passed: 158 standalone JVM/native-parser and 156 Android JVM tests; 21 automatic
native socket cases, 12 stream cases and four invalid-value checks; focused
ASan/UBSan/leak detection; debug/release/test APK assembly; zero Lint errors,
140 warnings and five hints. API29 passed 25 instrumentation cases with one
Android13+ case skipped; API36 passed all 26 and a separate notification-denied
invocation. Signed APK: `build/apk/Maffinet-0.3.1-alpha-debug.apk`, versionCode4,
same certificate as previous builds. Hash and scope: docs/build-info-0.3.1-alpha.json.
The inherited archive filename in CI was still 0.3.0; the manifest and delivered
filename are 0.3.1. A subsequent packaging-only correction updates archivesName.
Local snapshot tree in `.toolchain/apk-build.git`: `abe84685372f9377a21e121ffcbcc4abebf84bb1`.
Main checkout/index and all prior uncommitted changes remain intact.

OnePlus wireless ADB briefly returned: installed version was 0.2.0-alpha,
Android16 and Private DNS xbox-dns.ru. Set stay_on_while_plugged_in to 0 and
removed /data/local/tmp/maffinet-ui.xml; forward list was empty. Connection then
disappeared before APK installation. Later wireless ADB returned and 0.3.1-alpha
was installed successfully with `adb install -r`; versionCode4/target36 confirmed.
Selected LinkedIn/Chrome, GeoHide, manual strategy and system Private DNS survived.
The phone then showed system UI and Maffinet's exit history recorded REMOVE TASK,
not a crash. Auto network acceptance awaits an unlocked idle device; no success
of Auto/LinkedIn is claimed yet. The old Chrome test tab has not been identified/
closed and should not be confused with user tabs.

DNS interception before TLS, ECH/GREASE, QUIC and IPv6 recovery remain unimplemented;
concrete code/protocol research is in docs/NETWORK_RECOVERY_RESEARCH.md.

## Current requirement — automatic access, 2026-10-04

The user clarified that LinkedIn is a diagnostic example, and the product must
serve new unavailable applications without adding a service toggle each time.
The main interaction is application selection and one connection button.
Only local DPI bypass and public Smart DNS are authorized; a private external
proxy/VPN exit is outside the requested architecture.

Automatic Access supersedes the proposed LinkedIn product switch. Current work:
a short TLS fallback chain for selected applications including unknown hosts;
bounded native observation, network-scoped decisions, direct DNS/body probes,
generic data-based route hints and host-route updates for new connections without
restarting VPN. Plain HTTP application payloads must not be automatically replayed.
Manual settings and General/User remain available in expert mode.
Version 0.3.0-alpha implements this general mechanism. Source
`8266a409f085a6e1050a5d54400ff199cb1de207` passed
[CI run 37199682071](https://github.com/DrDeiss/Maffinet/actions/runs/37199682071):
141 standalone JVM/native-parser tests, 139 Android JVM tests, 21 automatic socket
cases, focused ASan/UBSan/leak checks, APK assembly and API29/API36 runtime checks.
API29 passed 25 instrumentation cases with one Android 13+ notification case
skipped; API36 passed all 26 and a separate denied-notification invocation.
Lint reports zero errors, 140 warnings and five hints. The signed local APK and
its exact hash are in `build/apk/build-info.json`. Physical acceptance of the new
automatic mode remains open after losing ADB connectivity. The earlier manual
LinkedIn success is evidence for one candidate, not proof of the new automatic
mode on that device. See [Automatic Access](docs/AUTOMATIC_ACCESS.md).

## Earlier update — Android compatibility and LinkedIn, 2026-10-04

Physical feedback on 0.1.0-alpha reported Android's old-target warning and failed
LinkedIn access in both its application and browser, while other services worked.
The old targetSdk26 is confirmed and changed to36 (minimum26), version0.2.0-alpha.
Modern notification permission, Tile PendingIntent launch, deferred foreground
recovery and background update handling are implemented. JNA is upgraded from
5.14.0 to the verified official5.19.1 AAR, including its Android16KB fixes.
Auto now preserves the active strategy unless every configured URL passes;
partial results remain visible for manual selection, with failed URLs reported.
The onboarding text now directs users to the actual application picker and
diagnostic reports show the effective native arguments with host contents omitted.

LinkedIn's root domains and their subdomains already match General. On the connected
Android16 phone, both usual Cloudflare endpoints return HTTP200 but stall after
13,781 body bytes. LinkedIn's alternative `gcp-lb.www.linkedin.com` endpoint completes
a certificate-verified response of140,059 bytes and loads the native app and Chrome
feed; a VPN restart and cold app launch also passed. The proposed optional
default-off LinkedIn route was superseded by the generic Automatic Access
controller above; the endpoint remains an initial data hint. These phone results
used a temporary manual diagnostic command.

HTTP probes now require64KiB of body or the complete smaller response, preventing
this observed header-only false success. A paced native TLS buffer was also observed
being transformed twice after an early server response; a bounded patch and native
regressions are being verified. Telegram state ownership/publication is atomic, and
internal status broadcasts explicitly target the app package for modern Android.
At this earlier stage, local verification passed84 JVM tests; six Linux native
checks remained CI-only.
The revised CI builds one app/test APK pair and tests it on API29 andAPI36,
including notification denial onAPI36. Final CI/runtime results are recorded above.
See [investigation notes](docs/ANDROID36_AND_LINKEDIN.md).

## Current update — Hosts and DNS, 2026-10-04

The user requested comparison with NetFix Windows and broader Hosts/DNS support.
The follow-up clarifies DNS scope: prioritize Smart DNS for geo-access. The picker
opens that category, offers real GeoHide RU/EU/US resolver profiles and includes
Bezmezhau, DNS-AI and ASTRACAT alongside Xbox, COMSS, malw and Null's Proxy. General
public/family DNS profiles are kept behind their separate category.
The previous eight-domain base constraint is superseded by a curated 130-domain
General in eight categories. Explicit Android app selection, independent Telegram,
User enable state, legacy placeholders and selective TCP semantics remain intact.

Source changes add validated hosts/domain import from documents and HTTPS, manual
malw/GeoHide source buttons, a centralized provider-verified DNS catalog, compatible
legacy DNS values, custom IPv4 DNS and explicit system Private DNS actions.
[Comparison](docs/NETFIX_COMPARISON.md) and [source/usage policy](docs/HOSTS_AND_DNS.md)
record the implementation and remaining desktop differences. IP hosts mappings,
built-in DoH and automatic source updates remain outside this implementation.

Local validation: 60 production-model/catalog JVM tests passed; six Linux native-fixture
tests skipped. The full malw/GeoHide source snapshots parse without errors. Kotlin
PSI parsed 84 main/instrumentation files without syntax errors. Android assembly
remains unavailable on the local host because its SDK is missing. The exact source
`9efbce486c50c34a3b2a6eed335aa6f7be7f0018` passed
[CI run 37157372931](https://github.com/DrDeiss/Maffinet/actions/runs/37157372931):
debug/unsigned release/test APK assembly, 66 JVM/native, 65 Android JVM and 22
API29 instrumentation tests, without failures/skips. Lint reports zero errors,
136 warnings and five hints. The installable debug APK is saved locally at
`build/apk/Maffinet-smartdns-debug.apk`; its verified v2 signature, source, hashes
and exact validation are recorded in `build/apk/build-info.json`.
CI retains the APK as `maffinet-debug-apk` for seven days. Physical Smart DNS
reachability and device acceptance still require the user's device.

## Current goal — independent Applications, Telegram and Hosts

The user's clarification on 2026-10-03 supersedes the service-catalog product
requirements below. Continue the existing Android engines and Maffinet design;
the product now exposes Applications, explicit installed-app selection, an
independent Telegram proxy, VPN DNS, strategies and hosts. Services is removed
from primary navigation; YouTube/Instagram/LinkedIn switches no longer control
routing, domains or strategy checking addresses.

- One common button starts/stops selected Applications and/or Telegram modes.
  Selection, persisted desired state and actual engine state are separate. STOP,
  recovery, background, boot and watchdog must treat each mode independently.
  Configuration changes are locked while engines/resources or checking are active.
- `selected_apps` is the sole VPN package source. Preserve it through migration;
  check installation, exclude Maffinet, and reject an empty installed allowlist.
- Hosts use the existing parser, store and ByeDPI compiler. General has a fixed
  eight-domain base; enabled User domains extend it. Editing/removal/save/toggle,
  merge import and export remain available. App/service choices never alter hosts.
  Preserve `{domains}`, `{list:…}`, legacy fake SNI and explicit Advanced override.
- Strategy checks use an independent editable HTTP/TLS address list and a fixed
  snapshot of hosts/filters/targets. A changed fingerprint invalidates measured
  history and prevents applying a result from a different configuration.
- Preserve VpnService → TUN → HEV → local SOCKS → ByeDPI, all four ABIs and
  `io.maffinet.android.core.dpibypass.TProxyService`. Preserve DNS, Telegram,
  imports/exports, background features, TV navigation, LICENSE/NOTICE and deferred
  development attribution.

Implementation of this clarification is complete in source. For source
`34ed3a1d13d55ab8c25f21c6042a930dd85842c5`,
[CI run 37153332402](https://github.com/DrDeiss/Maffinet/actions/runs/37153332402)
passed debug/unsigned release/test APK assembly, 43 JVM/native, 42 Android JVM and
22 instrumentation tests, with zero failures/skips. Lint reports zero errors,
135 warnings and five hints. Nineteen original API29 emulator captures were
inspected and saved. Regression checks cover independent mode combinations,
partial/failed starts, notification STOP, pending restart cancellation, app
selection/migration, VPN DNS, hosts/probe independence, stale/deleted history and
boot/watchdog isolation. See [device validation](docs/DEVICE_VALIDATION.md) for
the exact tested scope and physical-device limitations. The previous 32/31/9 run
remains historical baseline evidence.

## Historical audit and superseded implementation plan

The audit, phases and progress below describe the earlier implementation. Their
service-profile routing/domain/testing requirements are retained as history and
must not be used as current product requirements. Native architecture, licensing,
provenance and applicable verification constraints remain in force.

Audit date: 2026-10-03. Upstream: `rupleide/NetFixMobile`, commit `19cb13c19f87a1fe6339acb65e35da2ab4d14a43` (v1.0.3 codebase). Independent fork: https://github.com/DrDeiss/Maffinet. The complete upstream Git history is retained.

## Phase 0: audited architecture

One Android application module, Kotlin/Jetpack Compose, minSdk/targetSdk 26, compileSdk 36, AGP 9.0.1, Gradle wrapper 9.x, CMake 3.22.1, NDK 30.0.14904198. Kotlin Compose plugin 2.0.21 must be checked against AGP's built-in Kotlin during an actual build. No meaningful unit tests or CI exist. This machine initially has no Java, Android SDK or adb on PATH; baseline `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` fails before configuration because JAVA_HOME is unset.

### Entry points and pipeline

`MainActivity.kt` owns splash, onboarding, numeric navigation, update checks, watchdog setup, auto-connect and strategy initialization. `ui/MainTab.kt` owns the VPN permission launcher and connection controls. `ServiceManager.kt` dispatches intents; `LifecycleVpnService.kt` and `ByeDpiVpnService.kt` own foreground service state, notifications, pause/resume, TUN and teardown. BootReceiver, WatchdogReceiver/Worker and the quick-settings tile provide other entry points.

Preserve: Android VpnService → TUN → TProxyService/hev-socks5-tunnel → loopback SOCKS → ByeDpiProxy JNI/ciadpi → Internet. `ByeDpiProxyPreferences.kt` compiles command/UI preferences; `UISettings.kt` contains desync/host options. Telegram has a separate `core/tgproxy` service/controller and Rust JNA library, and must remain available.

### Storage and UI

SharedPreferences `${packageName}_preferences`, a separate hints preference file, local strategy result/customization files, mutable globals in `data/Actions.kt`, Compose state, listeners and broadcasts. Five numeric tabs: Home, Telegram, YouTube strategy wizard, Settings, About. Settings includes app selection, DNS, boot/background/economy options, logging/reset, strategy import/export. Existing TV behavior includes focus requesters, landscape sizing, QR links and SmartTube integration; no Leanback launcher/banner is declared.

### Confirmed technical debt

* `DomainListUtils.getLists` is an empty stub in `DpiBypassUtils.kt`.
* YouTube package defaults are reinserted by both VPN startup and SettingsTab, overriding deselection.
* `wants_youtube_bypass` means VPN enabled, not a YouTube service profile, throughout existing lifecycle code.
* StrategyTester tests YouTube only; fake-SNI `{sni}` is YouTube-specific. `-n` is fake SNI, whereas `-H` filters domains; they cannot be interchanged. Preserve the legacy comma string verbatim: this native revision does not split it into fake-SNI rotation.
* ByeDPI `-H` applies to a desync group. Selective filtering must cover every `-A` group and preserve an unmodified fallback, with tests against actual parser semantics.
* DNS preset is parsed for tun2socks but ignored in VPN builder; start failure cleanup/readiness and status transitions need targeted stabilization.
* ACCESS_NETWORK_STATE and the WatchdogReceiver declaration are missing; onLost clears the reconnect baseline. Actual temp-config filenames are not retained for cleanup.
* Native UDP desync ignores host lists. Selective mode must keep UDP forwarding without hostname-selected UDP desync; unrestricted behavior remains an explicit Advanced override.
* MainActivity callbacks rely on indexes 0–4; retain compatibility when introducing the four product tabs.
* Most UI colors are hardcoded. A theme-only change cannot provide a distinct product design.
* Updater points at NetFix APKs and must move to an independent source or stay disabled.
* At audit time, native C ByeDPI symbols and the bundled HEV JNI class used the old namespace. ByeDPI symbols now rebuild to Maffinet; HEV's equal-width JNI class literal is rebound to Maffinet with inverse-hash verification, preserving the custom three-argument start. The old Kotlin class is removed.

### Licensing and native provenance

Upstream root has NOTICE but no LICENSE. README declares GPL-3.0 and additional terms: rename product/package/assets; exact first-launch notice; persistent `(fork of NetFix Mobile by rupleide)` marking on Home/About; preserve authorship and disclose corresponding source alongside distributed APKs. User states author permission to fork. Preserve the upstream terms verbatim in a dedicated provenance document, include GPL text and original NOTICE, and clearly distinguish independent changes. Do not invent an upstream license exception or claim complete native source reproducibility.

ByeDPI submodule is available at `ba532298de7b28cfe854aea83d061369d13ca290`. HEV submodule pins `c26333ae1d9a0e69f1ab567ef0a46094bdfadcf1`, unavailable from the configured heiher repository; bundled four-ABI libraries are present. Default builds preserve HEV engine bytes with the audited Maffinet JNI class binding; rebuilding HEV must be explicit and requires recovering the matching custom source. Audit and retain dependency license texts/notices. Public binary release remains gated on source/provenance completeness.

## Execution plan and change ownership

### Phase 1 — clean fork and identity

Use `io.maffinet.android` for namespace/applicationId and Kotlin sources, including the HEV bridge. Update JNI C bindings, action names, FileProvider authority, root project and APK names. Create a distinct dark-first mint/navy visual system and vector M icon; replace old marketing assets/copy, add independent About and required first-launch notice. Preserve all existing operating controls and advanced workflows. Add README, LICENSE, upstream terms and dependency notices. Commit independently after build/test attempt.

### Phase 2 — Domain Lists

Add pure parsing/normalization/merging and a repository for General, User and service lists. Local settings/storage layer owns schema version and keys. Implement getLists compatibility adapter. A tested centralized argv compiler handles `{domains}`, `{list:ID/name}`, legacy `{sni}`, multi-group whitelist and user-controlled advanced host modes. User input rejects URLs, ports, arbitrary shell arguments, invalid/empty labels; supports comments and IDN normalization. Persist domains/enabled state atomically. Commit with parsing/merge/compiler/storage tests.

### Phase 3 — service profiles

Add data-driven catalog containing YouTube, Instagram and LinkedIn packages/domains/test URLs/default selection. LinkedIn `com.linkedin.android` was verified in Google Play: https://play.google.com/store/apps/details?id=com.linkedin.android. Application routing unions installed enabled-profile packages with explicitly selected manual apps; domain selection stays independent of routing and strategy selection. Remove forced YouTube insertion; never fall back to full-device routing when nothing is selected/installed. Commit with catalog/routing tests.

### Phase 4 — product UI

Four primary destinations: Home, Services, Strategies, Settings. Home has large connection control, enabled service summary, strategy and truthful connectivity status. Retain existing connection logic and map legacy callback destinations. Services has generic toggles and profile details. Settings → Domain lists → User domains offers validated multiline edit/add/remove, enable, Storage Access Framework import/export. Settings → Advanced retains existing app/DNS/Telegram/strategy controls and adds raw command, host mode, IPv6 and desync access. Keep business logic out of SettingsTab. Preserve focus/landscape behavior. Commit with build and UI inspection.

### Phase 5 — universal testing and Auto

Probe configured targets for each enabled profile through the same local SOCKS/ByeDPI candidate. Record per-service results, elapsed time and errors, explicitly describing these as HTTP/TLS connectivity checks rather than full app availability. Score deterministically by passed services descending, latency ascending, stable candidate order; no AI. Empty selection must not silently test YouTube. Restore running VPN safely on completion/cancellation/failure. Keep legacy strategy JSON compatibility and fake-SNI expansion. Expose result matrix and retain strategy management/import/export. Commit with scoring/probe-result tests.

### Phase 6 — stabilization

Compile native ByeDPI and all Kotlin; run meaningful JVM tests and lint. Review startup/stop/pause/resume/foreground notifications/permission and reconnect handling. Fix only localized confirmed bugs while preserving pipeline. Record actual checks and device-only pending checks separately: real VPN consent/start/stop, Wi-Fi/mobile switching, background/boot, installed-app routing, host filtering, YouTube/media regression, Instagram, LinkedIn, DNS, IPv6 and TV remote operation. No device result is inferred from unit tests. Produce debug alpha APK only if build succeeds; do not call it a tested release before device checks and native source obligations are satisfied.

## Files expected to change

Identity: Gradle settings/app build, manifest/resources, main/test Kotlin package paths, JNI C symbols. Existing integration: DpiBypassUtils, ByeDpiProxyPreferences, ByeDpiVpnService, StrategyTester/StrategyTestManager, MainActivity/MainTab/SettingsTab/InfoTab/OnboardingFlow, theme/shared components and updater. New code: `core/domains`, `core/services`, `core/strategy`, `data/settings`, `data/domains`, `ui/home`, `ui/services`, `ui/settings`, `ui/strategies`; unit tests mirror business responsibilities, not VpnService internals. Existing Telegram/native transport modules remain intact.

## Verification and risks

At each phase run `:app:assembleDebug :app:testDebugUnitTest`; record environment failures accurately and rerun after toolchain repair. Required final checks also include manifest/applicationId/authority isolation, Maffinet JNI bridge/binary binding, inverse-verified HEV namespace delta and unchanged Rust hashes, domain argv correctness for multiple groups, app-empty routing behavior, deterministic matrix scoring, source/license provenance and stale upstream updater removal. Real provider bypass success depends on network/device and cannot be promised from HTTP-only probes. Existing targetSdk 26, missing exact HEV source, broad package visibility, JNI global proxy singleton and VPN lifecycle races remain explicit risks; no broad target-SDK/network rewrite in this fork.

## Progress

- Phase 0: complete; baseline build blocked by absent Java. GitHub fork created and audit recorded before modifying application code.
- Phase 1: complete for independent package/JNI identity, mint/navy/vector branding and required legal UI. Initial XML/reference/whitespace checks passed; the integrated Android build subsequently passed in CI. Local Google SDK/SDK Preview license acceptance was requested explicitly and remains pending.
- Phase 2: complete. Domain parsing/normalization/merging, versioned atomic User storage, General/User/service lists, live compatibility adapter and structural ByeDPI argv compiler implemented. Fifteen focused Phase 2 tests passed in the standalone production-source JVM suite (20 total including five scoring tests being developed). Every strategy group is host-filtered with TCP constraints and an unmodified fallback; legacy fake-SNI/advanced host override are preserved. Full local APK build still waits on Google agreements; CI validation uses a stable NDK override under the runner's existing SDK agreement.
- Phase 3: complete. Centralized YouTube/Instagram/LinkedIn profiles, generic domain selection, enabled-profile plus manual application routing, own-package exclusion and empty-installed-allowlist rejection implemented. Six focused catalog/routing tests pass (26 production-source JVM tests total). The integrated Android build passed in CI; physical-device connectivity remains pending.
- Phase 4: complete in source. Four primary destinations, clean Home, generic service controls/details, validated local domain editor with SAF import/export, Advanced controls and retained legacy Telegram/TV/strategy management routes. Android compilation and AOSP API 29 portrait UI checks passed in CI. Real screenshots cover onboarding, the four destinations, About, persisted service choices and domain validation. The scroll viewport and dark system navigation keep product controls clear of the bottom bar. Physical phone/TV visual acceptance remains pending. Matrix presentation is completed alongside Phase 5.
- Phase 5: complete in source. HTTP/TLS probes cover every selected profile target through each production-filtered ByeDPI candidate, with a fixed per-run snapshot, persistent per-service matrix and deterministic coverage/latency/preset-order scoring. Five focused scorer tests pass. Best command is saved before VPN restoration; user STOP/PAUSE, cancellation, deferred starts and stuck native workers are coordinated explicitly. VPN JNI is hosted on a tracked thread with listener readiness and bounded cleanup; transport/native engines are preserved. Full integrated Android compilation passed in CI.
- Phase 6: code stabilization and runtime smoke checks implemented; physical device acceptance remains open. Startup waits for the SOCKS listener; failures/destruction close TUN/config/native resources; DNS presets reach the VPN builder; physical-network recovery retains lost-network state; scan/reconnect/start/stop operations share gates and generation tokens. Watchdog permissions/component and VPN-specific state are fixed without replacing transport engines. [CI run 37144128664](https://github.com/DrDeiss/Maffinet/actions/runs/37144128664) passed for source `30ee86a15a6ed889f719a24b10a2577bcb5d6e6b`: 32 standalone JVM tests, 31 Android JVM tests, nine Android instrumentation tests, debug/unsigned release APK assembly and Lint with zero errors. Every suite completed without failures or skips. Real phone/TV acceptance checks remain pending in docs/DEVICE_VALIDATION.md.

### Verification scope

- Continued Phase 6: AOSP API 29 debug instrumentation passed for the four UI destinations, deferred upstream reminders, persisted service/domain edits, real Maffinet JNI SOCKS/TUN start/stop/retry, startup cancellation and universal-test VPN restoration. Nine tests ran with zero failures/errors/skips, including eight custom smoke checks and the inherited application-ID check. Android Lint is an error gate. Six executed production-native fixture tests cover filtering and parse all 73 unchanged inherited presets after the actual selective argument compiler. All 12 collected UI PNGs were inspected, including the clean development About viewport and truthful unchecked-update status. Selected original captures are retained in docs/screenshots; complete reports/logs/PNG evidence are in the CI artifact. Release attribution was compiled, not runtime-tested in this run.

- Local Windows: 26 JVM model tests passed; six Linux native fixture tests explicitly skipped. Syntax inspection passed for 82 Kotlin application/unit/instrumentation files. Android SDK agreement acceptance remains pending locally; no agreement was accepted by project automation.
- GitHub CI: stable NDK r29 compiler override; all four native ABIs packaged in debug and unsigned release builds. Production native HTTP/TLS host selection, retry groups, TCP/IPv4 constraints and unchanged UDP forwarding were exercised successfully. Eight shipped binary hashes, four Maffinet HEV JNI contracts and inverse original hashes are verified. Lint reports zero errors, 157 warnings and nine hints; inherited target SDK and JNA page alignment remain documented limitations. Test reports/logs/PNG evidence are uploaded, with no public APK release.
- Final localized fixes: the update-install receiver is private; About uses the same reserved scroll viewport as product screens. Update checks distinguish available/up-to-date/no-release/no-APK/failure states, respect disabled automatic checks, close HTTP resources and propagate cancellation. Four new Android JVM tests covering alpha/stable, numeric ordering and malformed tags passed in the final CI. The Activity smoke test confirms disabled automatic checks leave About explicitly unchecked.
- Release limitations: physical-device VPN/bypass/media/background/TV behavior and traffic generated by a separately routed helper app remain unverified. Exact customized HEV source was not found in checked public provenance; retained binaries and JNI compatibility are documented. Source/provenance and physical-device gates prevent claiming a verified alpha release.

### User-directed namespace independence and development UI

- Removed the last original Kotlin namespace: HEV now registers `io.maffinet.android.core.dpibypass.TProxyService` on all four ABIs. A deterministic 48-byte class-literal adaptation preserves every other native byte, proven by inverse original hashes; original/target records are in docs/native-jni-rebind.json. The bridge, NDK package macro and keep rule use Maffinet. Exact customized sources remain a separate release gate.
- Per the user's instruction, debug UI defers upstream reminders with `SHOW_UPSTREAM_ATTRIBUTION=false`. Release sets the flag true, preserving first-launch notice, Home/About marking and author cards for the final UI. LICENSE/NOTICE/provenance remain intact.
- Renamed 13 inherited component/service Kotlin filenames to Maffinet, removing old generated file-facade names. The source tree has no original application package or original product filenames.
- CI now assembles debug and unsigned release variants. Updated UI smoke assertions check clean development Home/About/onboarding and preserve release-branch checks. Run 37144128664 passed all 32/31/9 tests without skips, with no JNI linkage errors in logcat; the new class ran on x86_64. Twelve fresh captures document the development UI. The release gate was compiled but not runtime-tested; other ABI/device checks remain pending.
