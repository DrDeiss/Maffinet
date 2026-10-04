# P01 transport experiment — acceptance pending

This path is independent of `:app`. It is enabled only by
`-Pmaffinet.transportLab=true`. It never loads the inherited HEV/Rust binaries,
uses production settings, or changes the production applicationId. P01 is
**in_progress**: Android compilation, all four native ABIs and real TUN gates
have not run in the current environment.

## Source and build

The reviewed HEV candidate and every recursive gitlink, canonical Git tree,
archive SHA256 and root license SHA256 are in
[source-lock.json](transport/source-lock.json). Source checkouts/exports are
ignored. Git archive and patch application force LF so pins work on Windows and
Linux; public include symlinks are copied from their in-tree target bytes.
The preparation script rejects changed tracked source, unknown dependencies,
changed lock, output outside `.toolchain`, and an existing output. It never
deletes directories or mutates the inherited custom HEV submodule.

On a fresh provisioned host (no automatic SDK license acceptance):

```powershell
py -3.11 tools/prepare-transport-lab.py --fetch
py -3.11 tools/verify-transport-source.py
.\gradlew.bat -Pmaffinet.transportLab=true -Pmaffinet.ndkVersion=29.0.14206865 :transport-lab:assembleDebug :traffic-helper:assembleSelectedDebug :traffic-helper:assembleControlDebug :transport-lab:lintDebug :traffic-helper:lintSelectedDebug :traffic-helper:lintControlDebug --no-daemon --console=plain
```

The current Windows session's verified export is
`.toolchain/transport-source-p01-ready`. Add
`-Pmaffinet.transportSource=.toolchain/transport-source-p01-ready` here. Earlier
ignored experimental outputs are stale and intentionally retained. Specify a
fresh `--output` and the matching property to prepare again.
`-Pmaffinet.python=<absolute Python executable>` resolves CMake's Python on hosts
with a broken Store alias. CMake3.22.1/SDK36/NDK29 must already be provisioned
with appropriate agreements. Four ABI filters are configured; **none built yet**.
The above build attempt stops while configuring `:app`, before lab task
resolution, on `SDK location not found`. These module/NDK recipes are uncompiled.

CMake mirrors the pinned `Android.mk`, `build.mk` and `configs.mk` source/define
sets with quoted paths, accommodating spaces in this workspace. Stock JNI and
main are excluded. [main.c](transport/native/main.c) adapts HEV's main cleanup;
[patch](transport/native/patches/001-lab-hooks.patch) adds lifecycle/tuple/socket
hooks and partial gateway failure guards. Preserve all notices.
[License inventory](transport/license-inventory.json) records seven packaged
assets, including original libyaml authors and a conservative superset of lwIP
source copyright/license blocks. `tools/update-transport-lab-notices.py` can
reproduce these from pinned checkouts; its extra libyaml checkout is a license
reference, not an additional compiled dependency. New Java/bridge/tools are
Maffinet implementation under the project's GPLv3; HEV/lwIP remain attributed.

## Lab path and ownership

Install three debug APKs only on an authorized lab device/emulator. No installation
or device changes have been performed in this session. The package identities are:

- `io.maffinet.lab.transport`: isolated VPN owner, fixed **nonempty** allowlist.
- `io.maffinet.lab.helper.selected`: selected helper, separate UID.
- `io.maffinet.lab.helper.control`: excluded helper, separate UID.

Stop production Maffinet/other VPNs before starting the lab Activity and grant
system consent. It refuses missing selected helper, missing consent, absent DNS,
and a VPN/default network instead of a physical network. IPv4/IPv6 default routes
apply only to the selected helper. Builder DNS is copied from physical link
properties; this is ordinary forwarding, **not P04 broker/recovery**. System
resolver/private-DNS behavior is recorded separately; do not change user policy.

`VpnService → TUN → pinned HEV → loopback numeric SOCKS5 → physical network`.
All HEV client descriptors are protected before connect; the config fixes its
SOCKS destination to loopback, which is deliberately not bound to the physical
network. The Java relay's external TCP/UDP sockets must pass protect and
`Network.bindSocket` before connect/send; failure closes the descriptor. Injection
checkboxes exercise each failure. No remote SOCKS server is a product dependency.
See the [Android protect](https://developer.android.com/reference/android/net/VpnService#protect(java.net.Socket))
and [bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.net.Socket)) contracts.

The native worker duplicates the borrowed TUN fd and closes only its duplicate.
Java retains/closes its original after reap. Start acceptance is separate from
readiness. STOP during initialization is remembered until the event pipe is
ready; the bridge disables quit before HEV closes the pipe. A one-second timeout
returns a failure and retains ownership, refusing restart; it does not claim Idle.
Failure/restart behavior still requires native runtime and T02 evidence.

TCP gateway PCB `remote_*` is application local, `local_*` is destination.
UDP's first tunnel callback has destination unset; lwIP updates it on the next
callback. The hook is in session datagram handling before SOCKS address framing,
for every destination/datagram. JNI copies address bytes and host-order ports.
Orientation is established from source, **not yet checked against Android wire**.
UID remains explicitly Unknown until P03; the outgoing socket UID is never used.

The numeric relay is intentionally small: 16 native sessions, at most 32 workers,
8s socket timeout, no name resolution/DPI/retry. UDP processes one outstanding
datagram at a time, creating an upstream socket per response, so its physical
source port can change and throughput is limited. It is a fixture for controlled
echo/DNS; arbitrary media/QUIC/large datagrams are not accepted capabilities.
It is not the future production relay. TV custom Boolean semantics are not
implemented. The service polls lab status at 250ms; P03 must replace this with
the production supervisor/network events. Network handover, process death and
OEM/background handling are pending. Static lab display state is confined to
this APK; it is not a production runtime authority.

## Endpoints and helper probes

Run the explicit fixture on a reachable lab host, once per desired address family:

```powershell
py -3.11 tools/transport-lab-server.py --host <numeric-lab-address>
```

Ports: TCP echo15001, UDP echo15002, raw DNS UDP/TCP1053. DNS returns controlled
A/AAAA answers with matched transaction/question. For TLS/HTTPS add `--cert`,
`--key`, `--tls-port` for an operator-provided certificate trusted normally by
the helper; no CA installation, trust bypass, hostname bypass or credentials.
Use the original certificate hostname in helper `name`; the connection uses
the chosen numeric IP, ordinary certificate validation and HTTPS name checking.
Wrong-name/untrusted tests must fail. HTTP makes only credential-free `GET /p01`
and stores status/byte count/hash, never payload. Success is only that probe.

The helper UI selects numeric TCP echo, UDP echo, TLS, HTTPS, raw UDP/TCP DNS
or system resolver. Raw DNS supports A (`qtype=1`) and AAAA (`qtype=28`);
truncation/rcode/mismatched question is a failure, with no recovery. The system
resolver's blocking timeout belongs to the OS, not the socket8s budget. Encrypted
DNS and resolver attribution are not inferred from a system lookup success.

After explicit install/consent, adb may drive the foreground Activities (the
service is not exported). These commands are a **recipe, not executed evidence**:

```powershell
adb shell am start -n io.maffinet.lab.transport/.LabActivity --es command START
adb shell am start -n io.maffinet.lab.helper.selected/io.maffinet.lab.helper.ProbeActivity --ez run true --es protocol tcp --es ip <numeric-lab-address> --ei port 15001 --ei count 1
adb shell am start -n io.maffinet.lab.helper.control/io.maffinet.lab.helper.ProbeActivity --ez run true --es protocol udp --es ip <numeric-lab-address> --ei port 15002 --ei count 1
adb shell am start -n io.maffinet.lab.transport/.LabActivity --es command STOP
```

Set `protocol` to `tls`/`https` and pass `--es name <original-hostname>` at
TLS15443. DNS uses `dns-udp`/`dns-tcp`, DNS1053, `name`, `qtype`.
System DNS uses `system-dns`/`name`. `count=100` runs sequential warm probes.
Each scenario overwrites its helper's private `files/p01-results.jsonl`; capture
and combine completed runs manually before starting the next scenario. Private
transport `files/p01-events.jsonl` is snapshotted during readiness/monitor/STOP.
Each new native generation clears its event ring; capture it before restart.
512-event cap/dropped count is explicit; limit each evidence session accordingly.

Copy private files using authorized `adb exec-out run-as <package> cat files/<file>`
into ignored `.toolchain/rebuild-p01` paths. Preserve source HEAD/dirty tree,
APK hashes/signer/ABI, Android API/page size, network/Private DNS, physical endpoint,
allowlist, helper UID, expected tuple, cold/warm and timing alongside results.
Do not commit private IPs/names, APKs, keys, payload or raw logs.

```powershell
py -3.11 tools/check-transport-lab-run.py --selected <private-selected-jsonl> --control <private-control-jsonl> --events <private-events-jsonl>
```

The checker requires passed TCP/UDP/TLS/HTTPS/raw-DNS in both families plus system
DNS, distinct helper UIDs, native readiness, complete event buffer/generation,
selected tuples during the probe and absence of control tuples. It checks only
this JSONL T01 subset. It does not certify APK provenance, native worker/FD
cleanup, SLO, device network or application access. Do not feed synthetic unit
fixtures as lab evidence. Failure injections need separate expected-failure
reports and must show no target-side receive/connect.

## Host checks and remaining gates

```powershell
py -3.11 tools/test-transport-relay.py
py -3.11 tools/test-transport-lab-tools.py
```

The first compiles the actual relay Java11 code with host fault seams, exercises
real TCP/UDP IPv4/IPv6 sockets, pre-connect/pre-send rejection and 100 relay
close cycles. It proves neither Android binding nor native/TUN lifecycle. The
second uses synthetic evidence for rejection paths and fixture DNS/framing.
It proves no Android network result.

Remaining P01 gates: first Android/NDK compilation and lint; four ABI artifacts,
source/artifact/license hashes; native/socket/stream/sanitizers on provisioned
Linux; real helper TUN/wire original tuples and selected/control matrix on
API26/29/36; native early STOP/init-failure/restart100; start30/latency/FD/RSS SLO;
protect/bind injection on device; measured transport ADR. TV/16KiB full runtime
remain P09 gates. No P02 or production switch until P01 exit criteria pass.
