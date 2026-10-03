# Maffinet

Maffinet is an independent project inspired by NetFix Mobile and uses parts of
its open-source codebase. Upstream: [rupleide/NetFixMobile](https://github.com/rupleide/NetFixMobile).
Maffinet is an independent development with its own identity, interface and
selective DPI bypass architecture. It is not an official NetFix product.

Attribution: **(fork of NetFix Mobile by rupleide)**.

## Status and requirements

Development version: **0.1.0-alpha**. [PLAN.md](PLAN.md) records implementation
phases and actual checks; [known limitations](docs/KNOWN_LIMITATIONS.md) documents
release gates. Android 8.0/API26 and newer; four inherited native ABIs:
arm64-v8a, armeabi-v7a, x86, x86_64. Application ID `io.maffinet.android` allows
installation alongside NetFix Mobile.

## Features

Choose services while separate models handle Android package routing, domain
lists and ByeDPI strategy selection. Initial profiles: YouTube, Instagram and
LinkedIn. Home, Services, Strategies and Settings expose normal controls;
advanced network controls and the inherited standalone Telegram proxy remain
available separately. Local user-domain editing, import/export and multi-service
HTTP/TLS checks are implemented. YouTube is enabled initially to preserve the
upstream default; Instagram and LinkedIn can be enabled in Services.

No accounts, remote VPN servers, backend, telemetry or ML selector are added.
Connectivity probes establish HTTP/TLS reachability rather than guaranteeing every
feature of a service application. Auto ranks successful service coverage before
latency using deterministic rules.

1. Enable the required profiles in **Services**. Their installed Android packages
   combine with manual choices in **Settings → Приложение и подключение**.
2. Add local domains in **Settings → Domain lists**, validate and save; enable
   the User list. Import/export uses Android's document picker.
3. Run **Strategies → Auto** to compare selected services, or choose an existing
   strategy manually. The matrix records the last check and individual HTTP errors.
4. Connect from **Home** after granting Android VPN permission. Configuration
   changes require stopped VPN resources; tests stop and restore an active VPN.

General is the union of enabled service and User domains. New profiles belong in
`core/services/ServiceCatalog.kt`; routing, lists, UI and probes consume that data.
Advanced retains raw commands, desync, host overrides, DNS, IPv6 and strategy
import/export. `{domains}` and `{list:general}` reference active lists; legacy
`{sni}` remains compatible with the original fake-SNI value.

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
so recursive initialization fails. Normal builds preserve the HEV/Rust binaries
and original JNI compatibility bridge. [Native provenance](docs/NATIVE_PROVENANCE.md)
records exact source pins and binary hashes. The optional `:app:rebuildHevTunnel`
requires the recovered matching customized source; stock HEV has a different ABI.

CI builds/tests with stable NDK29.0.14206865 and the runner's existing standard
SDK agreement; -Pmaffinet.ndkVersion supplies that compiler override. Debug APKs appear in app/build/outputs/apk/debug.
Release signing is unconfigured; use a private maintainer key outside source
control. Unit tests cover parsing/merging, profiles, arguments, scoring and
persistence. `./gradlew -p verification test` runs pure production-source JVM tests
without Android SDK. Linux CI also compiles unchanged pinned ByeDPI for actual
host/protocol/retry/UDP contract checks; see [verification](verification/README.md).
CI also assembles instrumentation tests, runs Android Lint with errors fatal and
executes UI/native VPN smoke tests on an AOSP API29 x86_64 emulator. It collects
reports and screen captures. A licensed Linux SDK host can run the same checks
with `bash tools/run-emulator-smoke.sh`; the script requires a provisioned AOSP
API29 image, emulator, platform tools and KVM. Its simulated VPN consent is
restricted to an explicitly opted-in qemu test environment. The tests check native
SOCKS/TUN lifecycle and selected-service probes, while routed helper-app traffic,
provider bypass, media and physical phone/TV acceptance remain separate checks.
Use [device validation](docs/DEVICE_VALIDATION.md)
for VPN lifecycle, background, network switching and Android TV checks.

## License and attribution

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
