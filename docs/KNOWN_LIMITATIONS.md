# Known limitations and release gates

This is an alpha development checkout. PLAN.md records completed phases and
actual validation. Product intent does not imply successful device tests.

The clarified Applications/Telegram/Hosts model supersedes service profiles.
[CI run 37153332402](https://github.com/DrDeiss/Maffinet/actions/runs/37153332402)
passed the updated integrated checks and produced 19 inspected emulator captures.
See [the recorded scope](DEVICE_VALIDATION.md); physical acceptance remains open.

- **Missing custom HEV source:** gitlink `c26333ae1d9a0e69f1ab567ef0a46094bdfadcf1`
  cannot be fetched from its declared origin. Builds retain the engine bytes with
  a verified 48-byte JNI class-name rebind to Maffinet; this is not a source rebuild.
  Recover the matching customized source before publishing a complete source release.
- **Rust provenance:** source, Cargo.lock and binaries were imported together, but
  no inherited build script or attestation proves binary/source correspondence.
  Resolve that and transitive native dependency notices before distribution.
- **Device verification:** builds/JVM tests do not prove VPN lifecycle, reconnect,
  Wi-Fi/mobile transitions, OEM background behavior, or Android TV navigation.
  AOSP API29 x86_64 instrumentation covers native SOCKS/TUN start/stop/retry and
  restoration after testing; it does not certify routed helper-app traffic or
  equivalent behavior on physical devices and other Android versions.
- **Native page alignment:** JNA is updated to official5.19.1; its packaged
  libraries have16KB-aligned LOAD segments and RELRO boundaries. This removes the
  confirmed inherited JNA5.14.0 issue. Full16KB runtime compatibility is still
  unverified for the complete APK; LOAD alignment alone does not certify all
  dependencies. The retained HEV binaries have16KB-aligned LOAD segments on all
  four ABIs.
- **Connectivity checks:** HTTP/TLS reachability does not prove authentication,
  media delivery or every feature of an application. The General base and explicit
  checking addresses are initial values and require maintenance.
- **Filtering:** observable supported hostnames can match ByeDPI domain lists;
  IP-only, encrypted-hostname and some UDP traffic may not. Package routing and
  domain filtering have different scopes.
- **Hosts sources:** imports extract destination domain names into the DPI filter.
  Desktop hosts IP mappings are not applied; Smart DNS or a future local DNS
  handler is needed for that behavior. HTTPS sources are refreshed manually and
  merged into User; deleted upstream domains are not automatically removed.
- **UDP:** this pinned native revision does not apply host filters to UDP desync.
  Selective mode therefore forwards UDP unchanged and confines desync to TCP.
  Advanced host override preserves unrestricted legacy settings explicitly.
- **Empty routing:** Maffinet refuses a tunnel with no installed selected packages
  rather than capturing the entire device. Select at least one installed Android
  application explicitly; legacy service flags do not populate this selection.
- **Probe history:** matrices show saved HTTP/TLS results for the tested hosts and
  independently configured checking addresses, not continuous availability.
  Changing that configuration invalidates measured history. HTTP protection/rate
  limits can cause false failures.
- **Independent Telegram/DNS:** the standalone Telegram proxy and VPN have
  separate runtime/desired states; all three combinations and failed partial starts
  need physical-device/background verification. VPN DNS does not configure the
  separate Telegram proxy.
- **Native termination:** a worker that still runs after bounded stop/force-close
  blocks another singleton start. Restart the application process before retrying;
  activity recreation alone cannot reset a native worker.
- **Publication:** targetSdk is36; minimum SDK remains26. The migration includes
  notification permission and foreground-service handling, with API29/API36 CI
  validation tracked separately from physical-device acceptance.
  Release signing is unconfigured; use a private maintainer key outside Git.
- **SDK agreements:** portable setup stages Android SDK and Preview agreements
  without accepting them. CI uses stable NDK r29 with the runner's existing standard
  agreement; that validates a compiler override, not the default r30 beta toolchain.

Use [the device checklist](DEVICE_VALIDATION.md) before calling a build ready.
