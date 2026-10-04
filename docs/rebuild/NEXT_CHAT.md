# Автономное продолжение: P01 остаётся in_progress

Работай только в `E:\maffinet android`. Прочитай AGENTS.md, START_HERE/STATE/
ROADMAP, sessions/P01.md (последний report), sessions/P00.md (история),
NEXT_SESSION_PROMPT, product/ARCHITECTURE/research/test-plan/usage-map/
cleanup-ledger и оба ADR. Затем `lab/README.md`, source-lock/license-inventory
и `docs/rebuild/evidence/P01-checks.json`. Выполняй **только продолжение P01**,
не начинай P02 до настоящих exit criteria. Отсутствие среды не позволяет done.

## Git/checkpoint

Previous completed P00: `d812c81ebc55d117d8cb220fedc24605371436a7`, branch
`codex/rebuild-p00`. P01 начинался на этом HEAD, clean tree/index. Baseline
исходной0.3.2 работы — `2d63fc3d6e2e04d2ba8a088f234e19aa2e4e75cd`.
Проверь actual HEAD/status/staged paths и git log по STATE.checkpointCommitSubject:
`feat(lab): scaffold P01 source transport and helper harness`.
STATE.lastCheckpointCommit — уже существующий P00; completionCommitSubject —
historical P00. Будущий hash partial P01 не включался в его собственный commit.
Не reset/clean/откатывать появившуюся работу.

## Сделано, но Android/native ещё не compiled

Opt-in `-Pmaffinet.transportLab=true` добавляет modules `transport-lab`
(отдельный VPN/JNI/relay APK) и `traffic-helper` (selected/control applicationId
flavors). Default :app не подключает lab code. Production UI/service/settings/
user data/custom HEV gitlink/eight binaries не менялись; production switch — P03.

Source lock закрепляет все recursive repos:

- HEV2.14.4 `4d6c334dbfb68a79d1970c2744e62d09f71df12f`.
- Core `4be2e621813ba0315cfacd995bf501bde91d6996`.
- Task-system `8d83bbbf79557138726c8ee5a5fae99cbb978d61`.
- lwIP `07dbf162c718cc78ddedb9e67c6ebd17065eaf13`.
- YAML `efa36117a8646d26d12b58e05bac472d7854a70d`.

Ignored checkout: `.toolchain/rebuild-p01/hev-upstream`. Valid patched output:
`.toolchain/transport-source-p01-ready`, 549 files, manifest SHA256
`4f6c4d25b4bd075a691c453609183db85260ca9714d2c803d5b9d80d6576cc9b`.
Другие transport-source outputs — stale intermediates, сохранены и не используются.
Archive/apply canonical LF; public header aliases materialized in-tree. Verify
rejects stale patches/changed files/lock/license assets. Prepare требует fresh
output, не удаляет существующие. Семь packaged license assets включают original
libyaml authors и 101 verbatim lwIP blocks. Libyaml `2c891fc7a770e8ba2fec34fc6b545c672beb37e6`
— license-only reference. Copyright/LICENSE/NOTICE/provenance сохранить.

Bridge: Java original TUN, native duplicate, accepted/ready/outcome раздельны.
Early STOP сохраняется; timeout удерживает ownership и запрещает restart.
main.c адаптирован из HEV с cleanup; patch добавляет partial gateway guards и
socket/tuple/readiness callbacks. Custom JNI/TV Boolean не заимствован.
TCP tuple app-local=PCB remote, destination=PCB local. В UDP первая callback
не имеет destination; hook в session datagram callback после lwIP update,
перед SOCKS framing каждого packet. JNI copies, UID Unknown/P03 pending;
outgoing UID не app UID. Это source finding, **не Android helper/wire proof**.

Все HEV client fd protected, SOCKS fixed loopback (не bind physical). External
Java relay sockets проходят protect+Network.bindSocket до connect/send, failure
закрывает fd. Device injections protect/bind добавлены. Allowlist строго только
`io.maffinet.lab.helper.selected`; control и own package excluded. Missing selected
helper/start consent/physical network/DNS вызывает отказ, не full-device VPN.
Helper probes: numeric TCP/UDP echo, system DNS отдельно, raw DNS UDP/TCP A/AAAA,
ordinary certificate/name-verified TLS и credential-free HTTPS `/p01`, IPv4/IPv6,
count1..100. Нет CA installation/trust/name bypass/credentials/payload logs.

Lab README содержит fixture server, foreground adb/capture recipe и T01 JSONL
checker. Private JSONL/keys/APKs/logs не коммитить. Checker требует readiness,
original tuple matches, separate UIDs, selected/control dual-stack matrix и
полный event buffer; source/APK/SLO/device gates им не доказываются.
Lab limitations: serial UDP/new socket per datagram, upstream source port может
меняться, general UDP/QUIC/media pending. Fixed helpers/status polling250ms —
experiment, не production supervisor. Обычное physical DNS forwarding не P04
broker. Handover/process/OEM/TV/16KiB/native failures ещё не приняты.

## Следующая работа

1. Найди provisioned SDK36/build-tools/CMake3.22.1/NDK29/adb, Linux native host
   и authorized device/emulator. В текущей среде SDK/adb/Android env/local.properties
   отсутствовали, WSL не установлен. **Новые SDK agreements автоматически не
   принимать**. Сначала app baseline и lab compile/lint; исправь ошибки uncompiled
   Gradle/CMake/Java/JNI. Не устанавливать CA/не менять чужие данные.
2. Current workspace commands:

```powershell
$env:JAVA_HOME='E:\maffinet android\.toolchain\jdk\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME='E:\maffinet android\.toolchain\gradle'
py -3.11 tools/verify-transport-source.py .toolchain/transport-source-p01-ready
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pmaffinet.ndkVersion=29.0.14206865 --offline --no-daemon --console=plain
.\gradlew.bat -Pmaffinet.transportLab=true -Pmaffinet.ndkVersion=29.0.14206865 -Pmaffinet.transportSource=.toolchain/transport-source-p01-ready -Pmaffinet.python=C:/Users/Admin/AppData/Local/Programs/Python/Python311/python.exe :transport-lab:assembleDebug :traffic-helper:assembleSelectedDebug :traffic-helper:assembleControlDebug :transport-lab:lintDebug :traffic-helper:lintSelectedDebug :traffic-helper:lintControlDebug --no-daemon --console=plain
```

Fresh host: `prepare-transport-lab.py --fetch` создаёт default
`.toolchain/transport-source` без source property. После edits patches используй
fresh `--output` и matching source property. Не prepare поверх existing output.
Git OpenSSL backend обошёл local schannel credential error без TLS weakening;
recursive fetch script не использует недоступный Git sh submodule helper.

3. Собери четыре ABI from source и три APK. Сохрани private APK hashes/signer/
   native exports/build recipe/license packaging audit. Legacy custom binary
   не заменять stock. APK/signature/keys/toolchain/private logs не коммитить.
4. Provisioned Linux: existing parser/socket/stream/sanitizers и new bridge
   config/init/protect failures, early STOP/native restart/FD ownership fixtures.
   STOP timeout не превращать в Idle; native worker должен быть реально reaped.
5. Authorized lab install/consent: настоящие T01/T02 selected/control TUN,
   original helper/socket/wire tuples, IPv4/IPv6 TCP/TLS/HTTP/UDP и raw/system DNS
   отдельно. Control success должен быть вне TUN events; собственная проба
   Maffinet не замена. Device protect/bind failures запрещают target connect/send.
6. Test-plan SLO: starts30, **native/TUN** stops/restarts100, FD counts, p95
   start/stop, warm overhead100, RSS growth и API26/29/36. TV/full16KiB runtime
   отдельно P09. Measured ADR принимает stack только после evidence. Если HEV
   неприемлем по hooks/измерениям, compare immutable Firestack/gVisor с licenses/
   Android/runtime/RSS/size; environment blocker не доказывает неприемлемость HEV.

## Фактически проверено

JVM161 total:154 passed/7 native parser skipped, failures/errors0. Baseline
Android exit1 SDK location not found до compilation21s, lab build/lint exit1
там же27s, до lab task resolution. Native fixture exit1 requires Linux/C compiler.
Source prepare/apply/integrity/license-assets exit0; legacy eight hashes/four
JNI inverse bindings exit0. Actual Java11 relay host sockets TCP/UDP IPv4/IPv6,
pre-connect/pre-send denial и relay close100 passed. Десять Python DNS/framing/
confinement/synthetic evidence rejection tests passed. Это host-only; synthetic
records не lab evidence. APK/source-built ABI/Android TUN/native100 не исполнены.

## Завершение

Без среды делай независимые fixes/checks, сохраняй P01 in_progress с конкретными
remaining gates. Не повторяй checks десятками, не начинай P02, не уменьшай exit
criteria из-за missing device. Empty selection никогда не весь телефон;
application data/0-RTT не replay, TLS/ECH не ослаблять. Сохранить чужие изменения/
user data. Без новых чатов/subagents, push/publish/merge/main reset.

Дополняй sessions/P01.md отдельной dated continuation, сохраняя историю.
Обнови STATE, evidence, ADR findings/provenance и автономный NEXT_CHAT. Проверь
`py -3.11 tools/verify-rebuild-docs.py`, JSON/links/staged paths/diff и exclusions.
Создай только local commit конкретных files. STATE хранит existing previous hash
и current subject, не будущий hash в самом commit. Финал: actual commit/checkpoint,
checks, remaining gates, short continuation P01 (P02 лишь после всех exits).
