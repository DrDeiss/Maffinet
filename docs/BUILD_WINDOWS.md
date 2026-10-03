# Portable Windows setup

Run `py -3 tools/stage-toolchain.py` in the repository. It downloads official
Microsoft/Google packages, verifies checksums, installs an isolated JDK and
stages command-line tools without accepting agreements or changing system PATH.

Review `.toolchain/licenses-to-review/android-sdk-license.txt` and
`.toolchain/licenses-to-review/android-sdk-preview-license.txt`. The package plan
with exact versions, URLs and checksums is `.toolchain/android-package-plan.json`.
SDK Platform 36, Build Tools 36.0.0, CMake 3.22.1 and command-line tools use the
first agreement. NDK 30.0.14904198 is r30 beta1 and requires the preview agreement.

After accepting both agreements, extract the staged command-line ZIP so its `bin`
folder is `.toolchain/android-sdk/cmdline-tools/latest/bin`. Set process-local
`JAVA_HOME` to the JDK directory under `.toolchain/jdk`, `ANDROID_HOME` to
`.toolchain/android-sdk`, and `GRADLE_USER_HOME` to `.toolchain/gradle`.

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$env:ANDROID_HOME --licenses
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$env:ANDROID_HOME --channel=3 'platforms;android-36' 'build-tools;36.0.0' 'ndk;30.0.14904198' 'cmake;3.22.1'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
```

Only the user should answer agreement prompts. Keep toolchains, local.properties,
and signing keys ignored. Fetch only ByeDPI with
`git submodule update --init app/src/main/cpp/byedpi`; the custom HEV pin is unavailable.
