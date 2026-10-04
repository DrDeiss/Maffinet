# Проверки и SLO: P00

Дата: 4 октября 2026 года. Эти gates применяются к новой реализации.
Исторические CI/APK результаты не подменяют проверки текущего source.

## Фактический baseline

Исходный HEAD `0d1cf68054120f2248f038fbbb3af9fe448589a0`, checkpoint исходных
изменений `2d63fc3d6e2e04d2ba8a088f234e19aa2e4e75cd`, версия 0.3.2-alpha.
Portable JDK21.0.12.1+1 и Gradle9.1.0 найдены в `.toolchain`.
Системная команда `python` — неработающий Store alias; используем `py -3.11`.
ANDROID_HOME/ANDROID_SDK_ROOT/local.properties отсутствуют; SDK в стандартном
каталоге не найден, adb отсутствует, WSL не установлен.

| Проверка | До очистки | После очистки | Что доказывает |
|---|---|---|---|
| verification test, offline, rerun-tasks | 161 total, 154 passed, 7 skipped, 0 failures/errors | Те же 161/154/7/0 | Production pure models. 7 native parser cases требуют Linux executable |
| assembleDebug/testDebugUnitTest/lintDebug, NDK29 override | Exit1: SDK location not found, до компиляции | Exit1: тот же SDK blocker | Сборка и lint не верифицированы |
| verify-native-binaries.py | Exit0: 8 hashes, 4 HEV inverse JNI bindings | Native production не менялся | Проверка shipped byte provenance, не source rebuild |
| prepare-byedpi.py | Exit0, оба patches применены к exact source pin | Native production не менялся | Доступность pinned ByeDPI и apply-check, не native runtime |
| Physical/emulator/helper | Не выполнялось | Не выполнялось | Нет нового device evidence |

Полные Gradle logs — в [evidence](evidence/README.md); компактный command ledger
— [checks.json](evidence/checks.json). Baseline durations JVM77s и Android31s
описывают host-проверки, не latency продукта. Device latency/CPU/energy пока
не измерены. Исторические 547ms анонимного GET не выбираются как product SLO.

## Цели после baseline

Следующие SLO приняты как проектные бюджеты после baseline P00. Они подлежат
проверке P01/P03/P05 и изменению только ADR с измерениями. Это не достигнутые
показатели и не копия старого 25s host budget. Без устройства физические
latency/energy SLO нельзя объявить пройденными.

| Метрика | Цель и способ измерения | Gate |
|---|---|---|
| Consent accepted → Running | p95≤2s, 30 cold starts на каждом lab API, разрешение вне timer | P01/P03 |
| STOP admission | ≤100ms от принятого STOP до invalidation generations; 0 publish старой policy после | P03/P05 deterministic race tests |
| STOP resources | p95≤1s; нет native worker/external fd/TUN, 100 cycles; если не закрылись — Failed, не Idle | P01/P03 |
| Warm plain forwarding overhead | p95≤100ms сверх контрольного no-VPN пути, ≥100 соединений одного lab endpoint/семейства | P01/P05, без DPI/recovery, одинаковая сеть |
| Visible DNS forwarding overhead | p95≤50ms сверх физического resolver lab; ≥100 A/AAAA запросов, cache off | P04 |
| Recovery | total deadline≤8s, ≤3 попыток метода/endpoint на ключ, ≤2 active jobs, ≤64 queued keys; overflow не блокирует base flow | P05 fixtures и helper |
| Negative backoff | ≥60s на failed key при той же network/policy; явный retry может отменить; новый generation не наследует stale success | P05 |
| Memory | Native RSS growth≤5MiB после 100 start/stop cycles; bounded maps/queues; steady memory измерить 30min | P01/P05; абсолютный RSS budget выбрать по двум stack measurements |
| Idle work | 0 synthetic public probes без трафика/explicit action в 30min; CPU process average<1% одного ядра в screen-off lab | P03/P05, физическая energy отдельно |
| UI reaction | p95≤250ms event→render состояния; main thread не делает socket/native join | P02/P08 instrumentation |

Energy протокол: на одном физическом телефоне 30min screen-off и 15min helper
fixed traffic, no-VPN/старый/manual/новый режимы, одинаковые сеть/температура,
минимум три повторения; сохранить CPU/wakeup/radio bytes/battery counters и
дельты. Процент battery/hour не утверждать без baseline noise; результаты и
реалистичный absolute power budget назначить в P09. Нельзя выдавать host build
time за измерение расхода батареи.

## Helper APK и свидетельства

Отдельный applicationId, UID и подпись helper; не instrumentation-клиент
в процессе Maffinet. Два установленных helper variants нужны для одновременно
выбранного/невыбранного трафика. У helper INTERNET и explicit сценарии:
system DNS, raw UDP53/TCP53 wire, numeric TCP, TLS original hostname с обычной
проверкой сертификата, UDP echo, IPv6, credential-free HTTP, по возможности QUIC.
Raw/system DNS results записываются раздельно: системный resolver может скрывать
или объединять запросы. Несколько выбранных UIDs проверяются одновременно.

Стабильный lab endpoint с управляемыми reset/silence/DNS/IPv6 поведениями
предпочтителен для детерминизма; локальный test server не становится обязательной
продуктовой внешней инфраструктурой. Public endpoints только по назначению
конкретного теста, без перебора providers. TLS fixture использует проверяемое
имя/сертификат helper, не CA в чужом приложении.

Каждый run сохраняет source HEAD/tree, APK SHA256, signer fingerprint, ABI,
устройство/API/page size, network/Private DNS, allowlist revision, generation,
protocol/family, original tuple, helper UID и confidence, cold/warm, result/time,
expected/observed. Метаданные обезличивать; секреты/аккаунты/payload не писать.
В nonselected control требуется одновременно helper success по physical path
и отсутствие его flows в TUN events; один успешный GET не доказывает exclusion.

## Матрица обязательных gates

| ID / этап | Сценарий | Acceptance |
|---|---|---|
| T01/P01 | TCP/TLS/UDP, IPv4/IPv6; selected и nonselected helpers | Bidirectional bytes/validated response через real TUN; original tuple до SOCKS совпадает helper socket; контроль outside TUN |
| T02/P01/P03 | 100 STOP/restart, failure start, native blocked I/O, fd ownership | Без hang/double close/leaks; generations monotonic; late callbacks dropped |
| T03/P02 | Update schema0/1/2, empty/uninstalled packages, future/invalid settings, domains/DNS/argv/Telegram | Backup/report, idempotent import, no silent loss; сохранён appId/совместимый signer; unknown version отказ без overwrite |
| T04/P03 | Consent deny/revoke во время start/recovery, STOP в разных точках, Activity recreation, process death | Один supervisor/TUN; STOP не отменяется receiver; restored desired state по явной политике |
| T05/P03/P05 | Wi-Fi→mobile→Wi-Fi, offline, изменённые resolver/link properties | protect/bind до connect; старый generation не публикует cache/routes; resources old network закрыты |
| T06/P03 | API26/29/36, shared UID/INVALID_UID/owner race | Unknown допускается; owner lookup только original app TCP/UDP, не outgoing UID; shared group без ложного package |
| T07/P04 | UDP/TCP DNS A/AAAA/CNAME/HTTPS/SVCB, EDNS/DO/AD, unknown RR, NXDOMAIN/NODATA/SERVFAIL/REFUSED, malformed/TC | Preserved wire/question/ID и UDP source address; correct framing/timeout/negative TTL; matched source+question+transaction; cache bounded |
| T08/P04/P06 | Private/split DNS, system Private DNS strict/opportunistic, app-owned DoH/DoT/DoQ, resolver bootstrap | Не обходить пользовательскую policy; encrypted hostname Unknown; no loop/downgrade без разрешения |
| T09/P05 | First cold safe Hello vs next/warm flow, silent/reset/partial peer/TLS app-data/0-RTT | Измерять отдельно; ни один application byte не повторён; stale decisions suppressed; no 25s queue stall |
| T10/P05/P06 | Wrong cert/name/untrusted endpoint, HTTP403/451/API failures, DoH unavailable | TLS validation intact; probe result не app acceptance; coherent family/HTTPS records; явный decline |
| T11/P07 | Genuine ECH/GREASE, cached HTTPS/ECH configs, absent SNI/shared CDN | Outer не inner, exact-host unknown корректно; запрет unsafe mapping; отдельные реальный и имитированный cases |
| T12/P07 | HTTP/3-only/dual клиент, non443 UDP, loss/reorder/MTU | Native UDP works; результат QUIC отдельно; никаких глобальных UDP bans |
| T13/P07 | IPv6-only/NAT64/DNS64/Happy Eyeballs, IPv4 dead, network prefix change | Forward/recovery family aware; AAAA не подавляется; stale synthesis/cache invalidated |
| T14/P08/P09 | Telegram only/VPN only/both, independent STOP, proxy secret/reconnect/link action | Состояния независимы; no automatic install into чужой Telegram; Rust source/export/license correspondence |
| T15/P08/P09 | TV D-pad, font scale/TalkBack, notifications denied, boot/background/OEM power | Собственный UI usable; lifecycle без duplicate worker; reason/actions понятны |
| T16/P09 | Все четыре ABI, APK native alignment и 16 KiB device/emulator runtime | Source-built libs; actual page size/run evidence, не только ELF alignment |
| T17/P09 | Реальные приложения: cold/warm feed/API/media на Wi-Fi/mobile | Сценарий пользователя с metadata/source/сетью; credentials остаются у него, GET homepage не замена |

API26/29/36 lab gates не покрываются одними прежними API29/36 smoke tests.
Device gate T17, energy, OEM/TV и 16 KiB runtime пока pending. При отсутствии
среды реализовать fixtures/helper/harness, оставить dependent stage in_progress,
не переносить непроверенный фундамент дальше в виде done.

## Команды

В PowerShell перед Gradle использовать найденные portable JDK/cache:

```powershell
$env:JAVA_HOME='E:\maffinet android\.toolchain\jdk\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME='E:\maffinet android\.toolchain\gradle'
.\gradlew.bat -p verification test --offline --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pmaffinet.ndkVersion=29.0.14206865 --offline --no-daemon --console=plain
py -3.11 tools/verify-native-binaries.py
py -3.11 tools/prepare-byedpi.py --output .toolchain/rebuild-p00/byedpi-prepared
py -3.11 tools/verify-rebuild-docs.py
```

Не повторять prepare в существующий patched output без свежего каталога:
инструмент извлекает pinned C/H и заново применяет patches. Linux на
provisioned host: `python3 tools/build-native-contract.py`, затем verification
с `-Pmaffinet.nativeFixture=<absolute executable>`; это закрывает skipped parser
и socket/stream/sanitizer cases. Android commands требуют provisioned SDK36,
build-tools36, CMake3.22.1, NDK29 и существующие SDK agreements.
После P01 добавить actual module/helper tasks в этот файл и CI; выдуманные
имена задач не считать проверкой. Проведение нового CI run/publish в P00 нет.
