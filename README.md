# Maffinet

Maffinet is an Android application for local, selective DPI bypass with explicit
application routing, a separate Telegram proxy, editable hosts and strategy testing.

During UI development, debug builds defer the upstream notice and attribution
using `BuildConfig.SHOW_UPSTREAM_ATTRIBUTION=false`. Release builds set it to
`true` and restore the notice, Home/About marking and author details. LICENSE,
NOTICE and source provenance remain in the repository in both cases.

## Status and requirements

Development version: **0.1.0-alpha**. [PLAN.md](PLAN.md) records implementation
phases and actual checks; [known limitations](docs/KNOWN_LIMITATIONS.md) documents
release gates. Android 8.0/API26 and newer; four inherited native ABIs:
arm64-v8a, armeabi-v7a, x86, x86_64. Application ID `io.maffinet.android` allows
installation alongside NetFix Mobile.

## Interface

Home retains Maffinet's large connection button and exposes Applications,
installed-app selection, Telegram, DNS, Strategy and Hosts. The main navigation
contains Home, Strategies and Settings; the former Services destination is retired.
New verification and captures for this model are pending until the updated CI run.

[Additional screen captures](docs/screenshots/README.md) and
[recorded runtime checks](docs/DEVICE_VALIDATION.md) describe the actual tested scope.

## Features

Applications and Telegram are independent mode choices. The common button starts
or stops the chosen modes: applications only, Telegram only, or both. Home displays
each engine's runtime state, including a partial start or failure. Turning both
choices off leaves an explanation to enable a mode before connecting.

Only installed packages explicitly saved in `selected_apps` enter VPN routing.
Updates preserve that selection. Old service-profile flags neither insert packages
nor change hosts; Maffinet excludes its own package and rejects an empty installed
allowlist. DNS is the existing VPN setting and does not configure the standalone
Telegram proxy.

No accounts, remote VPN servers, backend, telemetry or ML selector are added.
Connectivity probes establish HTTP/TLS reachability rather than guaranteeing every
feature of an application. Auto ranks successful configured target coverage before
latency using deterministic rules.

1. Enable **Applications** and use **Choose applications** on Home to select the
   installed Android apps to route through VPN/ByeDPI. Enable **Telegram** for
   its independent MTProto proxy, with direct access to the existing settings.
2. Open **Hosts** to inspect the built-in General base, edit and save User domains,
   or merge/import and export them with Android's document picker. User can be
   enabled or disabled without discarding its saved domains.
3. Choose the existing **DNS** preset for VPN and a **Strategy**, or run the strategy
   comparison against separately configured HTTP/TLS checking addresses. Results
   are invalidated when hosts, checking addresses or relevant filters change.
4. Use the common button on **Home**, granting VPN consent when Applications is
   enabled. Settings remain locked while engines are requested/running or testing;
   strategy tests coordinate stopping and restoring an active VPN.

General is a fixed base of eight existing curated domains plus the enabled User
extension. App selection and legacy service flags never affect that union.
The base is defined in `core/domains/BuiltInDomainLists.kt`. Legacy named lists
remain read-only aliases solely for old `{list:youtube/instagram/linkedin}` commands.
Advanced retains raw commands, desync, host overrides, DNS, IPv6 and strategy
import/export. `{domains}` and `{list:general}` reference active lists; legacy
`{sni}` remains compatible with the original fake-SNI value. Raw host filtering
requires an explicit Advanced override. [Architecture](docs/ARCHITECTURE.md)
describes the independent modes, hosts and strategy snapshots.

## Networking

The existing transport is preserved:

```text
Android VpnService → TUN → HEV tun2socks → local SOCKS → ByeDPI → Internet
```

Traffic is processed locally. Android VPN consent creates the local tunnel; this
does not hide the public IP address. Routing packages through that tunnel and
matching hostnames in ByeDPI are separate responsibilities. Default selective
mode filters every TCP desync group by observable HTTP/TLS hostnames and forwards
unselected traffic without desync. This native revision ignores host filters for
UDP, so selective mode forwards UDP unchanged. Advanced host override retains
unrestricted legacy behavior. IP-only and encrypted-hostname traffic may not match.

## Build and tests

Required: JDK17+, SDK Platform36, Build Tools36.0.0, NDK30.0.14904198 (r30 beta1),
CMake3.22.1. The checksum-pinned Gradle9.1.0 wrapper is included.

```sh
git submodule update --init app/src/main/cpp/byedpi
sdkmanager --channel=3 "platforms;android-36" "build-tools;36.0.0" "ndk;30.0.14904198" "cmake;3.22.1"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Set JAVA_HOME and ANDROID_HOME, or sdk.dir in ignored local.properties. Review
and accept relevant Android SDK agreements yourself before package installation;
the beta NDK additionally requires the Android SDK Preview License Agreement.
No project script accepts agreements for you.

On Windows use gradlew.bat. `py -3 tools/stage-toolchain.py` stages verified
Microsoft/Google tools and agreements in ignored .toolchain without accepting
terms. See [portable Windows setup](docs/BUILD_WINDOWS.md).

Fetch only the available ByeDPI submodule. The inherited HEV commit is unavailable,
so recursive initialization fails. Normal builds use the audited Maffinet JNI
binding for HEV and preserve the Rust binaries. The bridge belongs to
`io.maffinet.android.core.dpibypass`; no original application namespace is required.
[Native provenance](docs/NATIVE_PROVENANCE.md)
records exact source pins and binary hashes. The optional `:app:rebuildHevTunnel`
requires the recovered matching customized source; stock HEV has a different ABI.
`python3 tools/rebind_hev_jni.py --check` proves the four HEV files differ from
their recorded originals only in the fixed-width JNI class name. `--apply`
reproduces that binding from recognized originals and refuses unknown binaries.

CI builds/tests with stable NDK29.0.14206865 and the runner's existing standard
SDK agreement; -Pmaffinet.ndkVersion supplies that compiler override. Debug APKs appear in app/build/outputs/apk/debug.
Release signing is unconfigured; use a private maintainer key outside source
control. Unit tests cover parsing/merging, explicit routing, mode choices, arguments, scoring and
persistence. `./gradlew -p verification test` runs pure production-source JVM tests
without Android SDK. Linux CI also compiles unchanged pinned ByeDPI for actual
host/protocol/retry/UDP contract checks; see [verification](verification/README.md).
CI assembles debug, unsigned release and instrumentation APKs, runs Android Lint
with errors fatal and executes UI/native VPN smoke tests on an AOSP API29 x86_64
emulator. It collects
reports and screen captures. A licensed Linux SDK host can run the same checks
with `bash tools/run-emulator-smoke.sh`; the script requires a provisioned AOSP
API29 image, emulator, platform tools and KVM. Its simulated VPN consent is
restricted to an explicitly opted-in qemu test environment. The tests check native
SOCKS/TUN lifecycle and configured-target probes, while routed helper-app traffic,
provider bypass, media and physical phone/TV acceptance remain separate checks.
Use [device validation](docs/DEVICE_VALIDATION.md)
for VPN lifecycle, background, network switching and Android TV checks.

## License and attribution

Maffinet uses parts of [rupleide/NetFixMobile](https://github.com/rupleide/NetFixMobile)
and is an independent development with its own identity, interface and selective
DPI bypass architecture. It is not an official NetFix product. Required release
attribution: **(fork of NetFix Mobile by rupleide)**.

Upstream README declares GPL-3.0 with additional terms; its imported checkout has
no standalone LICENSE. [LICENSE](LICENSE) supplies standard GPLv3 text, while
[upstream licensing notes](docs/UPSTREAM_LICENSE_NOTES.md) preserve the exact
asserted additional terms. [NOTICE](NOTICE) and [licenses](licenses) preserve
attribution, original NetFix notice and available third-party license texts.

NetFix Mobile ©2024–2026 rupleide; ByeByeDPI by romanvht; original Android
integration by dovecoteescapee; ByeDPI by hufrea; HEV tunnel by hev; Telegram
Android integration by amurcanov based on Flowseal/tg-ws-proxy. Maffinet does not
claim upstream trademarks or code ownership. Recover matching native sources
before public binary distribution and publish corresponding Maffinet source
changes alongside any permitted release.
