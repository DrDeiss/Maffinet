# Native provenance

Imported from rupleide/NetFixMobile commit `19cb13c`. Native binary hashes are in
`native-binaries.sha256`. The four Rust files remain unchanged. The four HEV
files have a deterministic JNI class-name rebind recorded in
`native-jni-rebind.json`, including original/target hashes and byte offsets.

| Component | Imported source | License | Build behavior |
| --- | --- | --- | --- |
| ByeDPI | ba532298de7b28cfe854aea83d061369d13ca290; version 17.3 | MIT, hufrea | CMake rebuild |
| HEV tunnel | c26333ae1d9a0e69f1ab567ef0a46094bdfadcf1 unavailable | Declared upstream MIT, hev | Preserve engine bytes; rebind JNI to Maffinet |
| Telegram Rust proxy | native/tgproxy-rust, crate 1.0.0, Cargo.lock | Inherited Android integration GPLv3; Flowseal MIT | Retain prebuilts |

HEV binaries dynamically register
`io/maffinet/android/core/dpibypass/TProxyService` and startup signature
`(Ljava/lang/String;IZ)V`. The Kotlin bridge now belongs to that Maffinet package;
the old application class is removed, with no runtime alias or fallback.
Stock HEV 2.14.4 registers a two-argument start and cannot replace these custom
binaries without reviewing the Android TV behavior.

## Reproducible JNI namespace binding

The imported internal class name and the Maffinet name are both 48 ASCII bytes.
Each original ELF contains exactly one NUL-bounded class literal in `.rodata`,
whose section flags contain neither WRITE nor EXECINSTR. Its inherited load
segment is RX; this adaptation preserves those permissions.
`tools/rebind_hev_jni.py --apply` replaces only those
48 bytes, without shifting ELF sections, relocation addresses, alignment or
instructions. All four inputs are validated against their original SHA-256
before any file changes; already-bound targets are accepted idempotently.
The original GNU build-id also remains unchanged; identify the adapted artifact
by its recorded whole-file SHA-256, rather than treating it as a source rebuild.

`--check` and `tools/verify-native-binaries.py` require the new class and the
three original native method contracts. Restoring the old 48 bytes in memory
must reproduce the exact original whole-file hash. This inverse check proves
every other byte remains unchanged. The manifest records both original and
shipped hashes, rather than presenting modified HEV files as unchanged.
The original bytes remain recoverable in the imported Git history. Rust hashes
are unaffected. The JNI make configuration and ProGuard keep rule use Maffinet.
Inherited diagnostic source-file paths remain embedded in the ELF files; those
paths are build metadata, not Java class lookups or application dependencies.

This is an audited binary namespace adaptation, not a HEV source rebuild or a
claim of complete native reproducibility. It removes the original application's
runtime namespace dependency while preserving the customized tunnel behavior.
JNI class lookup/registration follows the
[Android NDK JNI guidance](https://github.com/android/ndk/wiki/JNI).

## Source rebuild and release gates

`rebuildHevTunnel` is explicit and checks the exact source revision, JNI signature,
and configured NDK. Normal builds never invoke it or overwrite shipped HEV files.
Rust uses JNA C exports and is independent of the Kotlin package. ByeDPI JNI
symbols are rebuilt to match the new package.

The default inherited NDK is 30.0.14904198 (beta). CI builds ByeDPI with officially
published stable NDK 29.0.14206865 using `-Pmaffinet.ndkVersion=29.0.14206865`.
This override changes the compiler toolchain only; it never rebuilds the retained
HEV engine/Rust binaries. CI success does not validate the default beta toolchain.

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
