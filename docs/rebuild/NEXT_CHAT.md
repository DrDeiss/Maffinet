# Автономное продолжение: P01 остаётся in_progress

## Checkpoint перед отправкой на GitHub: 10 октября 2026 года

По прямому запросу пользователя подготовлена последняя локальная версия для
отправки в `DrDeiss/Maffinet` и fast-forward `main`. Предыдущий checkpoint —
`2dee5a3`, новый subject — `fix(lab): retain relay ownership until STOP completes`.
Сверяй фактические remote refs и status; ниже сохранён handoff от 4 октября.
P01 по-прежнему `in_progress`, P02 pending; отправка кода не закрывает exit criteria.

Пять ранее незакоммиченных lab files сохранены: relay STOP подтверждает завершение
listener/workers/sockets в пределах одного общего бюджета ожидания; при timeout
service удерживает relay/TUN, запрашивает native STOP и запрещает restart.
Повторный STOP может завершить reap. Host relay TCP/UDP IPv4/IPv6, denial,
100 STOP cycles, active TCP/UDP STOP, retained worker/later reap/interruption
прошли повторно. Java admission/helper validation и 18 Python tests также passed.
Android compilation/device/native lifecycle в этой сессии не выполнялись.
SDK inventory ниже исторический: lab README отмечает более позднее provisioning;
перепроверь фактические paths/packages/agreements, не принимай новые соглашения.
Проверки с сокетами/дочерними процессами прошли вне ограниченного sandbox;
зависшие sandbox attempts завершены, они не считаются passed.


Работай только в `E:\maffinet android`. Прочитай AGENTS.md, START_HERE/STATE/
ROADMAP, sessions/P01.md целиком (initial scaffold + dated continuation),
sessions/P00.md (история), NEXT_SESSION_PROMPT, product/ARCHITECTURE/research/
test-plan/usage-map/cleanup-ledger и оба ADR. Затем lab/README.md,
source-lock/license-inventory и evidence/P01-checks.json, включая continuations.
Выполняй только **P01**, без P02 до реальных exit criteria. Missing environment
не позволяет done. Без других чатов/subagents, push/publish/merge.

## Git и точка продолжения

Branch `codex/rebuild-p00`; baseline `2d63fc3…`, completed P00 `d812c81…`,
initial P01 scaffold `56da2f39f8efbfd33686f4b91ceab4c8241739e0`.
Continuation начиналась на этом HEAD с чистым tree/index.
STATE.lastCheckpointCommit хранит существующий initial P01; текущую фиксацию
сверяй по STATE.checkpointCommitSubject и dated sessions/P01.md continuation:
`fix(lab): fence P01 starts and validate helper evidence`.
Будущий hash не записан в содержимое его commit. completionCommitSubject —
historical P00. Проверь actual HEAD/status/staged paths/git log; не reset/clean/
откатывать появившуюся работу.

## Готовый experiment, без Android/native compilation

Opt-in `-Pmaffinet.transportLab=true`: `transport-lab` (отдельный VPN/JNI/numeric
relay APK) и `traffic-helper` selected/control flavors. Default app не подключает
lab. Production UI/service/settings/data/custom HEV gitlink/eight binaries
не менялись; production transport switch принадлежит P03.

Пять pinned compiled repositories:

- HEV2.14.4 `4d6c334dbfb68a79d1970c2744e62d09f71df12f`.
- Core `4be2e621813ba0315cfacd995bf501bde91d6996`.
- Task-system `8d83bbbf79557138726c8ee5a5fae99cbb978d61`.
- lwIP `07dbf162c718cc78ddedb9e67c6ebd17065eaf13`.
- YAML `efa36117a8646d26d12b58e05bac472d7854a70d`.

Ignored checkout `.toolchain/rebuild-p01/hev-upstream`; verified output
`.toolchain/transport-source-p01-ready`,549 files, manifest SHA256
`4f6c4d25b4bd075a691c453609183db85260ca9714d2c803d5b9d80d6576cc9b`.
Other outputs — retained stale intermediates. Archive/apply canonical LF,
include aliases materialized in-tree. Prepare требует fresh output, не удаляет
existing. Source checks не отключаются `python -O`. Семь packaged license assets
сохраняют libyaml authors и101 verbatim lwIP blocks. Libyaml `2c891fc7…` —
license-only reference. LICENSE/NOTICE/provenance/lock/patches сохранить.

Bridge: Java original TUN, native duplicate; accepted/ready/outcome раздельны.
Early STOP сохраняется; timeout удерживает ownership и запрещает restart до
reap. Cleanup/partial gateway guards/socket/tuple callbacks добавлены, native
runtime/failure/sanitizers не исполнялись. TCP app-local=PCB remote,
destination=PCB local. UDP hook — session datagram callback после lwIP update,
перед SOCKS framing. JNI copies, UID Unknown/P03; outgoing UID не helper UID.
Source orientation ещё не Android helper/wire proof.

HEV sockets protected before loopback connect; Java external relay sockets
protect+physical Network.bindSocket before connect/send, failure закрывает fd.
Fixed nonempty allowlist только `io.maffinet.lab.helper.selected`; control/own
excluded. Missing helper/consent/physical network/DNS — hard failure, не whole
phone. Serial UDP/new socket per datagram ограничен echo/DNS fixture;
general QUIC/media pending. Poll250ms/fixed helpers — experiment, не supervisor.

## Continuation fixes и новый capture contract

`LabStartTickets` отменяет все START, принятые до STOP/revoke. Cleanup не
восстанавливает old ticket; destroy terminal; explicit START после STOP может
исполняться после cleanup. Duplicate START при owned session ignored.
Foreground notification повторяется при actual queued restart после STOP.
Host test покрывает admission gate, service/foreground/native behavior pending.

Helper schemaVersion2 / `ProbeValidation`:

- Numeric IPv4 — four decimal octets; IPv6 — unscoped hex literal с colon.
  Hex-looking hostname (`face`), shortened IPv4 и invalid protocol отвергаются
  до resolution/target socket.
- TLS certificate+original hostname validation сохранена. HTTP — credential-free
  GET `/p01`, Content-Length и complete21-byte `maffinet-p01-fixture\n` body
  lab server. Bare200/short/duplicate length/chunked/другая body — failed.
- Raw DNS — exact single compressed A/AAAA controlled fixture answer, matched
  transaction/question/type/class/counts/frame/length. Это не general CNAME/EDNS
  parser и не P04 broker. Numeric TCP/UDP echo и system DNS отдельно.

Checker требует current schema/protocol metadata, readiness, complete buffer/
generation/time, selected original tuples и отсутствие control tuples. Нужны
DNS qtype1 **и** qtype28 для UDP/TCP на IPv4 **и** IPv6 endpoints обоих helpers,
TCP/UDP/TLS/HTTPS и system DNS. Capture final transport snapshot **после всех
completed probes**; old/early snapshot/event loss reject. Helper scenario
overwrites private JSONL — capture/combine между runs; transport ring очищается
на restart. `python -O` не отключает reject checks. Synthetic unit fixtures не
lab evidence; checker не сертифицирует APK/source/native/SLO/device.
lab/README.md содержит foreground adb/endpoint/capture recipe и limitations.

## Следующая работа

1. Найди provisioned SDK36/build-tools36/CMake3.22.1/NDK29/adb с существующими
   agreements, Linux+C compiler и authorized lab device/emulator. SDK paths/
   config/local.properties отсутствовали, adb/cc/clang/gcc/CMake/ninja/docker
   не в PATH, WSL installation required. Command-line tools ZIP в downloads
   не означает provisioned SDK. **Не принимать новые SDK agreements автоматически.**
2. Сначала production baseline assemble/unit/lint, затем lab compile/lint.
   Исправь ошибки Gradle/CMake/Java/JNI. Device T02: START1/START2→STOP→explicit
   START, native init STOP, revoke/destroy, duplicate START, foreground ordering.

```powershell
$env:JAVA_HOME='E:\maffinet android\.toolchain\jdk\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME='E:\maffinet android\.toolchain\gradle'
py -3.11 tools/verify-transport-source.py .toolchain/transport-source-p01-ready
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pmaffinet.ndkVersion=29.0.14206865 --offline --no-daemon --console=plain
.\gradlew.bat -Pmaffinet.transportLab=true -Pmaffinet.ndkVersion=29.0.14206865 -Pmaffinet.transportSource=.toolchain/transport-source-p01-ready -Pmaffinet.python=C:/Users/Admin/AppData/Local/Programs/Python/Python311/python.exe :transport-lab:assembleDebug :traffic-helper:assembleSelectedDebug :traffic-helper:assembleControlDebug :transport-lab:lintDebug :traffic-helper:lintSelectedDebug :traffic-helper:lintControlDebug --offline --no-daemon --console=plain
```

Fresh host: `prepare-transport-lab.py --fetch` создаёт default
`.toolchain/transport-source`. После patch edits — fresh output/matching property.
Git OpenSSL обходил schannel local error без ослабления TLS; pinned fetch
обходит broken Git sh submodule helper. Не устанавливать CA/не менять user DNS
policy/данные. SDK installation/agreements/device changes не выполнялись.

3. Four ABI from source и три APK. Private hashes/signer/exports/source-artifact/
   license packaging audit; legacy custom binary не заменять stock. APKs/keys/
   signing/toolchain/private logs не коммитить.
4. Linux: existing parser/socket/stream/sanitizers и bridge config/init/protect
   failures, early STOP/native restart/FD ownership. Worker реально reaped;
   timeout не Idle и restart не разрешён до reap.
5. Authorized install/consent: real selected/control T01/T02 API26/29/36,
   helper/socket/wire tuples, IPv4/IPv6 TCP/TLS/HTTP/UDP и raw/system DNS отдельно.
   Wrong-name/untrusted TLS must fail. Protect/bind injections требуют отсутствия
   target-side connect/receive; host seams не заменяют device evidence.
6. SLO: starts30, native/TUN stops/restarts100, FD/RSSgrowth, p95 start/stop,
   warm overhead≥100 samples/family. TV/full16KiB runtime — P09. Adopt stack ADR
   лишь после actual evidence; environment blocker не rejects HEV.

## Исполнено и ограничения

Initial checkpoint JVM161/154 passed/7 skipped и relay IPv4/IPv6/denial/close100
в continuation не rerun: historical host checks, не новые Android outcomes.
Continuation Java11 actual gate/validation passed,55 invalid inputs rejected;
Python18 tests passed (optimized reject included); source549/patch1/licenses7
unchanged. Baseline Android exit1 SDK before compilation24s; lab exit1 same
configuration blocker31s. Linux fixture exit1 requires Linux/C compiler.
Это host build times, не SLO. First Java test fixture length20 mistake исправлен
до21 до final pass; не Android regression.

Нет source-built ABI/APK/TUN/native lifecycle/FD/RSS/latency/wire/physical app
acceptance. Host gate не доказывает atomic production STOP publication/service
destruction/foreground behavior. Handover/process/OEM/TV/16KiB pending. HEV
candidate only. Без среды делай independent P01 fixes/checks, сохрани stage
in_progress с конкретными blockers, не начинай P02/не снижай criteria.

## Завершение

Дополняй sessions/P01.md dated continuation, сохраняй историю. Обнови STATE,
evidence/limitations/ADR/provenance и standalone NEXT_CHAT. Выполни
`py -3.11 tools/verify-rebuild-docs.py`, JSON/links/staged paths/diff/exclusions.
Stage конкретные source/doc paths; local commit, без future hash в содержимом.
Сохраняй чужую работу/данные; empty allowlist не весь телефон, TLS/ECH не
ослаблять, application data/0-RTT не replay. Финал: actual commit/checks/remaining
gates и prompt «Прочитай docs/rebuild/NEXT_CHAT.md и продолжи P01 по STATE.json».
