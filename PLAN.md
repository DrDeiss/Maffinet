# Maffinet — audit and implementation plan

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
