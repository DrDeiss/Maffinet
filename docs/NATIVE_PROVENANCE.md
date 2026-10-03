# Native provenance

Imported from rupleide/NetFixMobile commit `19cb13c`. Native binary hashes are in
`native-binaries.sha256`; all eight inherited HEV/Rust ABI files remain unchanged.

| Component | Imported source | License | Build behavior |
| --- | --- | --- | --- |
| ByeDPI | ba532298de7b28cfe854aea83d061369d13ca290; version 17.3 | MIT, hufrea | CMake rebuild |
| HEV tunnel | c26333ae1d9a0e69f1ab567ef0a46094bdfadcf1 unavailable | Declared upstream MIT, hev | Retain prebuilts |
| Telegram Rust proxy | native/tgproxy-rust, crate 1.0.0, Cargo.lock | Inherited Android integration GPLv3; Flowseal MIT | Retain prebuilts |

HEV binaries dynamically register the original class
`com/rupleide/netfix/core/dpibypass/TProxyService` and startup signature
`(Ljava/lang/String;IZ)V`. A tiny Kotlin bridge remains in this package solely
for binary compatibility; the Maffinet namespace/applicationId is independent.
Stock HEV 2.14.4 registers a two-argument start and cannot replace these custom
binaries without reviewing the Android TV behavior.

`rebuildHevTunnel` is explicit and checks the exact source revision, JNI signature,
and configured NDK. Normal builds never invoke it or overwrite shipped HEV files.
Rust uses JNA C exports and is independent of the Kotlin package. ByeDPI JNI
symbols are rebuilt to match the new package.

The default inherited NDK is 30.0.14904198 (beta). CI builds ByeDPI with officially
published stable NDK 29.0.14206865 using `-Pmaffinet.ndkVersion=29.0.14206865`.
This override changes the compiler toolchain only; it never rebuilds the retained
HEV/Rust binaries. CI success does not validate the default beta toolchain.

The unavailable HEV source and unverified Rust binary/source correspondence are
release gates. A working APK using the binaries is not a reproducible source build.
Restore matching sources and third-party notices before public binary distribution.

Focused recovery check on 2026-10-03 found no exact HEV commit in the canonical
heiher repository or the closest dovecoteescapee, wiktorbgu and romanvht forks.
Global GitHub commit/code searches also found no exact SHA. NetFix's initial public
commit [0a246dbb](https://github.com/rupleide/NetFixMobile/commit/0a246dbb70b054667a1293f6220e04a5d5b60e72)
already contains this pin and canonical URL; there is no earlier public submodule
history in that repository. This is a bounded search result, not proof that the
source exists nowhere. Request the exact customized source archive/repository,
recursive submodules and build procedure from the upstream author.
