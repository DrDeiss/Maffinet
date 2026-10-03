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
