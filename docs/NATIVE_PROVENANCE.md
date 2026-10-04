# Native provenance

Imported from rupleide/NetFixMobile commit `19cb13c`. Native binary hashes are in
`native-binaries.sha256`. The four Rust files remain unchanged. The four HEV
files have a deterministic JNI class-name rebind recorded in
`native-jni-rebind.json`, including original/target hashes and byte offsets.

| Component | Imported source | License | Build behavior |
| --- | --- | --- | --- |
| ByeDPI | ba532298de7b28cfe854aea83d061369d13ca290; version 17.3 + tracked Maffinet patch | MIT, hufrea | CMake rebuild from prepared sources |
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

### Pinned ByeDPI preparation and streaming regression

The ByeDPI gitlink stays at `ba532298de7b28cfe854aea83d061369d13ca290`.
`tools/prepare-byedpi.py` reads committed C/header files from that exact pin into
a generated build directory, checks and applies the tracked patches under
`app/src/main/cpp/patches`, and prints their SHA-256 hashes. It neither changes
nor resets the submodule checkout. CMake and `tools/build-native-contract.py`
both use this preparation, so APK and Linux fixtures compile the same sources.
Preparing/building ByeDPI requires host Python 3 and Git, in addition to the
configured C/NDK toolchain.

`paced-tls-buffer.patch` retains parsing metadata and a transformation marker
with each pending TCP buffer. A retry after pacing, EAGAIN or a short write does
not insert TLS headers into the same buffer twice. An early server response
defers resetting client round/part progress until its pending bytes are sent;
the next client exchange then advances beyond `-R1`. The patch also skips empty
cuts and accounts for actual partial send lengths. This addresses the repeated
TLS-header insertion observed in the physical-phone log; it does not implement
ClientHello reassembly or claim that every LinkedIn/network failure has the
same cause.

Two additional native options support scoped manual diagnostic commands:
`--group-pacing=20` enables pacing with a 20 ms timer for the current group only
(valid interval 1–60000 ms), and `--group-redirect=tcp://IP:443` sets that group's
destination without enabling global delayed connect. Host/protocol filters on
the selected redirect group still require reading client bytes/SNI before
connecting. Groups outside its port/protocol scope retain their connection
behavior. Existing `-Z`, `-W` and `-C` keep their global semantics; other group
filters, parts, redirect destinations and round limits are not inherited across
`-A` boundaries.

The Linux regression includes real loopback TCP sockets and production tunnel,
send/receive, reconnect and timer callbacks. It replays the early-response
ordering, injects EAGAIN/short writes only into the outbound socket, checks exact
TLS-record payload bytes for large/partial ClientHello buffers, and checks
encrypted follow-up data, zero cuts, raw fallback and server-first normal groups.
Parser checks cover scoped options and preservation of legacy global flags.
These hermetic checks validate forwarding/state behavior, not an external TLS
handshake or a DPI bypass in a particular network. Local patch preparation and
Python syntax checks passed on Windows. CI run
[37196163868](https://github.com/DrDeiss/Maffinet/actions/runs/37196163868),
commit `6e766b9a66d5e5ed4f25b33f0e087ca2416dfee1`, passed the 12 streaming
cases and four invalid-value parser checks, including pacing after a short write.

### Automatic Access source additions

`runtime-auto-access.patch` adds the opt-in `--auto-access` path; the separately
tracked `automatic_access.c/.h` module is copied into the generated source tree.
The APK and Linux fixtures use this same preparation; the ByeDPI gitlink remains
unchanged. Commands without `--auto-access` retain the manual cache/retry behavior.

TCP 443 waits for a complete bounded ClientHello (16 KiB maximum, two seconds),
then snapshots an exact hostname/port route for that connection. A mutex protects
128 numeric public IPv4 routes and the active epoch. TTL uses `CLOCK_BOOTTIME`
on Linux/Android, so routes expire during deep sleep; other platforms use the
monotonic fallback. Java observation runs outside the route mutex. Epoch changes
clear routes, stale epoch writes fail, and existing connections keep their
destination. This feature does not change SNI, certificates or established TLS.

Learning and hot routes exclude private/special original destinations and ECH
ClientHello extensions, including conservative GREASE suppression. Private TCP
443 destinations pass through without automatic desynchronization. Other ports
connect immediately. TLS/server-first protocols on port 443 may incur the bounded
ClientHello wait; missing DNS/SNI and ECH-hidden names cannot be recovered by
this observer. The inherited strategy cache keyed only by destination IP is
disabled in automatic mode to avoid sharing a result between CDN hostnames.

A loopback SOCKS5 greeting containing both methods `0x00` and private marker
`0x80` receives the ordinary `05 00` answer, but bypasses hot routes and learning
for that probe connection. Normal desynchronization remains active. Non-loopback
peers cannot activate this probe marker. Only preserved TLS handshake/CCS bytes
may reconnect after sending client bytes; plaintext requests and TLS application
records, including 0-RTT, never replay. After sending a complete replayable Hello,
two seconds without any peer TLS bytes advances the fallback chain. First peer
data or later client application bytes cancel this automatic response timer.

The 21 automatic Linux map/SOCKS/socket cases cover unknown host routing, a
second hostname on the same IP, probe bypass, partial/multiple TLS records,
bounded time/size, other-port server-first behavior, epoch/TTL and public-address
guards, ECH, absent/duplicate SOCKS replies, safe Hello retries, real server EOF
and ACKed-but-silent TLS, and cancellation/no replay after application bytes.
CI run [37198262561](https://github.com/DrDeiss/Maffinet/actions/runs/37198262561),
commit `7659adf6ea0a40e960d846ca645f4d0165c12960`, passed the first 19 automatic
cases, 12 streaming cases, four invalid-value checks and seven native-parser JVM
tests, including the actual production automatic argument chain. CI run
[37198766246](https://github.com/DrDeiss/Maffinet/actions/runs/37198766246),
commit `020173eaaa10baffa40a7dba38cae6114837f551`, passed all 21 automatic cases
and 141 JVM/native-parser tests. The additional
pooled-buffer case sends an 8 KiB Hello through reused 4 KiB buffers, pacing,
route lookup and fallback. Automatic collection/replay explicitly checks actual
buffer capacity and grows it without losing queued content or the allocation on
ENOMEM. A further partial-response case forwards
the first server TLS bytes in two reads without a false `-As` retry. Automatic
mode does not classify a first read shorter than the six-byte ServerHello prefix
as a TLS failure; forwarding that partial response releases the saved Hello and
prevents later replay of a connection which has already returned peer bytes.
The runner also builds a separately instrumented production fixture and executes
the pooled-buffer case with ASan, UBSan and leak detection. This check, all 21
automatic cases, 12 streaming cases, four invalid-value checks and all 141
JVM/native-parser tests passed for `8266a409f085a6e1050a5d54400ff199cb1de207` in
[CI run 37199682071](https://github.com/DrDeiss/Maffinet/actions/runs/37199682071).

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
