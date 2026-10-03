# Known limitations and release gates

This is an alpha development checkout. PLAN.md records completed phases and
actual validation. Product intent does not imply successful device tests.

- **Missing custom HEV source:** gitlink `c26333ae1d9a0e69f1ab567ef0a46094bdfadcf1`
  cannot be fetched from its declared origin. Builds retain the original binaries.
  Recover the matching customized source before publishing a complete source release.
- **Rust provenance:** source, Cargo.lock and binaries were imported together, but
  no inherited build script or attestation proves binary/source correspondence.
  Resolve that and transitive native dependency notices before distribution.
- **Device verification:** builds/JVM tests do not prove VPN lifecycle, reconnect,
  Wi-Fi/mobile transitions, OEM background behavior, or Android TV navigation.
- **Connectivity checks:** HTTP/TLS reachability does not prove authentication,
  media delivery or every feature of a service application. Service domains are
  initial values and require maintenance.
- **Filtering:** observable supported hostnames can match ByeDPI domain lists;
  IP-only, encrypted-hostname and some UDP traffic may not. Package routing and
  domain filtering have different scopes.
- **UDP:** this pinned native revision does not apply host filters to UDP desync.
  Selective mode therefore forwards UDP unchanged and confines desync to TCP.
  Advanced host override preserves unrestricted legacy settings explicitly.
- **Empty routing:** Maffinet refuses a tunnel with no installed selected packages
  rather than capturing the entire device. Install an enabled profile application
  or choose an installed application manually for custom domains.
- **Probe history:** matrices show saved HTTP/TLS results for the tested selection,
  not continuous availability. HTTP protection/rate limits can cause false failures.
- **Native termination:** a worker that still runs after bounded stop/force-close
  blocks another singleton start. Restart the application process before retrying;
  activity recreation alone cannot reset a native worker.
- **Publication:** targetSdk remains the inherited 26. Store publication needs a
  separate target SDK migration and permission/foreground-service validation.
  Release signing is unconfigured; use a private maintainer key outside Git.
- **SDK agreements:** portable setup stages Android SDK and Preview agreements
  without accepting them. CI uses stable NDK r29 with the runner's existing standard
  agreement; that validates a compiler override, not the default r30 beta toolchain.

Use [the device checklist](DEVICE_VALIDATION.md) before calling a build ready.
