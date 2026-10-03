# SDK-independent verification

Run `gradlew.bat -p verification test --no-daemon --console=plain` (Windows) or
`./gradlew -p verification test` (Linux/macOS), with JDK17+ available.

This independent Kotlin/JVM build compiles the production pure domain/profile/
argument/scoring models and their focused JUnit tests directly from app sources.
It excludes Android repositories, UI and transport. No Android SDK packages or
SDK license acceptance are required. Reports appear under
`verification/build/reports/tests/test`.

Passing these tests validates those models and does not substitute for an Android
APK build, instrumented tests, native ABI loading, or physical-device checks.

On Linux, fetch the pinned ByeDPI submodule and run
`python3 tools/build-native-contract.py`, then pass
`-Pmaffinet.nativeFixture="$PWD/verification/build/native/native-contract"` to the
verification command. Five additional contract tests feed the production argument
compiler into the real pinned native parser/group selector. They exercise HTTP/TLS
hostname boundaries, retry groups, protocol narrowing, and UDP unchanged forwarding
through a local socketpair. No external connections or Android SDK are involved.
Without that fixture these tests explicitly skip; ordinary JVM tests still run.
