# Этапы переработки Maffinet Android

P00 завершён 4 октября 2026 года. Рабочий scope принят в [product.md](product.md),
исследовательские решения — в [research.md](research.md) и `decisions/`,
проверки/SLO — в [test-plan.md](test-plan.md). Подробные входы и exit ниже
являются рабочим планом; прежние предложения PREP не доказательство реализации.

P01 начат 4 октября и остаётся in_progress. [Отчёт](sessions/P01.md) и
[lab harness](../../lab/README.md) сохраняют source/hooks/helpers и host checks;
SDK/Android/native/real TUN/SLO gates ещё pending. Exit criteria не сокращались.

Каждый новый чат выполняет один следующий этап и сохраняет результат в этой папке. Следующий этап начинается только после проверки exit criteria предыдущего. Размер этапа можно уменьшить в P00/P01, если прототип обнаружит новую зависимость; изменения графа фиксируются, старые результаты не переписываются как будто они были известны заранее.

## Последовательность

| Этап | Результат | Обязательная проверка завершения |
|---|---|---|
| P00 | Baseline, повторный аудит, cleanup ledger, утверждённые research/product/architecture/test планы и checkpoint-протокол | Исходная работа восстановима; очистка подтверждена usages; выбран scope; STATE указывает P01; blockers явно записаны |
| P01 | Рабочий source-built transport prototype и helper APK | Реальный TUN TCP/UDP, start/stop/restart, original tuple hooks, IPv6 baseline; выбор стека оформлен ADR; нет зависимости от недоступного custom HEV source |
| P02 | Самостоятельный application shell и миграция данных | Новый onboarding/app picker/navigation; совместимое обновление; выбор пакетов, домены, DNS, Telegram и команды не теряются; legacy UI не подключён |
| P03 | SessionSupervisor, repositories, flow context и attribution | STOP/consent/revoke/reconfigure/network change/process recreation; API29+ UID через original tuples; unknown корректен; globals не управляют новым runtime |
| P04 | DNS broker UDP/TCP в настоящем пути | A/AAAA/CNAME/EDNS/HTTPS, TCP framing/truncation, negative TTL, private/split DNS, отмена, исходный source address в UDP-ответах; helper APK использует broker |
| P05 | AccessPlanner, bounded local DPI и восстановление | Измеримый first/next request; короткий fallback, network-scoped cache и backoff; никакого replay application data/0-RTT; TLS identity сохраняется |
| P06 | Собственный DoH и согласованные DNS mapping policies | Bootstrap без loop, сертификат, wire responses, cache, A/AAAA/HTTPS coherency и explicit downgrade; внешний Smart DNS только по разрешённой политике |
| P07 | ECH/GREASE, QUIC и dual-stack acceptance | Раздельные fixtures, unknown без ложного exact-host; UDP других протоколов работает; IPv6-only/NAT64; fallback только там, где доказан |
| P08 | Самостоятельный UI результата и инструменты | App-first основные экраны, отдельные engine/evidence статусы, ошибки и retry, accessibility/TV; ручные детали доступны по необходимости; Telegram воспроизводим и независим |
| P09 | Приёмка приложения и удаление активного legacy | Сборка/тесты/lint/native/sanitizers/E2E; телефон, реальная сеть, фон/reconnect, media/API, 16 KiB runtime; актуальная provenance и migration отчёт; неподтверждённые функции помечены |

P01 не строит окончательный UX и не обещает исправление LinkedIn. P04 не выдаёт plaintext DNS forwarding за управление app-owned DoH. P07 может закончиться документированными ограничениями отдельных протоколов, но не выдуманным успехом. P09 не публикует приложение автоматически.

## Документы первой сессии

P00 актуализирует эту папку и добавляет:

- `product.md`: минимальный сценарий, границы обещаний, критерии самостоятельности и что не входит в MVP.
- `research.md`: факты/гипотезы/выбор, ревизии, ссылки, сравнение transport-кандидатов и unresolved questions.
- `test-plan.md`: matrix лабораторных и физических сценариев, методы, baseline и измеримые SLO.
- `cleanup-ledger.md`: операции и доказательства безопасной очистки.
- `decisions/`: ADR с альтернативами, основаниями и последствиями.
- `sessions/P00.md`: фактически выполненные действия и проверки.
- `NEXT_CHAT.md`: автономный промпт для P01.

Не создавать новые параллельные планы в корне и не дописывать отменённые требования в старый PLAN.md. После P00 корневой указатель/AGENTS.md направляет разработку в актуальную папку. Исторические документы сохраняют явный статус.

## Проверки

Для документационного P00 достаточно инвентаризации, usages, ссылок, JSON-состояния и проверки diff. При удалении Kotlin/native/config или изменении сборки требуются соответствующие build/tests. Не запускать десятки одинаковых проверок ради количества.

Перед очисткой production-кода попытаться выполнить подходящую baseline-проверку на исходном состоянии. Если среда не готова, записать конкретный исходный сбой. После очистки сравнить результаты; прежний сбой не выдавать за новую регрессию, а новый — за свойство baseline.

Базовые команды текущего проекта, которые сначала надо сверить с окружением:

```powershell
.\gradlew.bat -p verification test --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pmaffinet.ndkVersion=29.0.14206865
```

Linux native fixtures, sanitizer и Android instrumentation запускаются на доступном подходящем host. Не считать skipped тесты passed и не принимать новые SDK соглашения автоматически. Зафиксировать ошибки окружения, сохранив возможность независимой работы.

После смены build-system/modules команды корректируются в плане. Число старых тестов не является целью: новые проверки должны покрывать production-контракты. Успешный standalone JVM suite не доказывает работу TUN.

## Протокол фиксации между чатами

В начале: читать `START_HERE.md`, `STATE.json`, `ROADMAP.md`, `NEXT_CHAT.md` при наличии и последний `sessions/*.md`; проверить HEAD/status и исходные незакоммиченные изменения.

Во время: работать только над текущим этапом, сохранять findings и решения. Не создавать дополнительные чаты или агентов без разрешения пользователя/применимых инструкций. При обнаружении препятствия выполнять независимую часть; не переходить к зависимому этапу с недоказанным фундаментом.

В конце:

1. Записать `sessions/<stage>.md`: начало/конец, branch, исходный HEAD, пути, изменение поведения, команды, exit codes, evidence, limitations, unresolved work и следующий шаг.
2. Обновить STATE: `currentStage`, `nextStage`, статусы, `blockingIssues`, последний report и checkpoint. Не отмечать done при проваленном exit criterion.
3. Написать `NEXT_CHAT.md`, достаточный для следующего чата без чтения переписки.
4. Проверить diff и stage только относящихся к этапу файлов. Создать локальный коммит. Не делать push, force push, merge в main или публикацию автоматически.
5. Сообщить пользователю этап, фактический commit, проверки, ограничения и точный промпт продолжения.

STATE внутри самого commit не может содержать hash этого ещё не существующего commit. При необходимости хранить checkpoint предыдущего коммита и subject текущего; после фиксации записать hash отдельным docs checkpoint без бесконечного amend. При следующем запуске сверять `git log` с subject/report, а не доверять тексту слепо.

Если работа прервалась, оставить stage `in_progress` и точное remaining work. Новый чат продолжает этот этап; правило «один чат — один этап» не разрешает пропустить незавершённые критерии.

## Контракты этапов после P00

Основная зависимость линейная P00→P01→P02→P03→P04→P05→P06→P07→P08→P09.
UI shell P02 может иметь compatibility facade к legacy service до P03;
это не позволяет считать lifecycle переписанным в P02. Production switch
HEV требует P03 socket/supervisor acceptance. Все этапы ведут provenance registry
новых/адаптированных файлов. Один этап не выполняет следующий заодно.

### P00 — baseline и решения

Вход: PREP и dirty source0.3.2. Изменения: docs/rebuild, root pointers, один
недостижимый экран. Выход: проверенный baseline commit/raw snapshot, audit,
usage/cleanup ledger, product/research/test/ADR, самостоятельный NEXT_CHAT.
Проверки: usages, baseline и post-cleanup JVM/build attempts, native hashes и
patch apply, JSON/links/diff. Риск: старый SDK/native/device gate скрыт старым CI.
Exit: исходная работа восстановима, очистка обоснована, командами установлены
исходные environment blockers, граф/criteria записаны, текущая фиксация локальна.
Missing SDK не считается успешной Android-сборкой и переносится как P01 gate.

### P01 — транспорт и helper

Вход: baseline `2d63fc3`, ADR-001, provisioned SDK/NDK/Linux/device или emulator.
Изменения: новый source-built transport adapter/test path, pinned source/build
scripts/licenses, helper variants/harness, CI test tasks при необходимости.
Нынешний custom source gitlink/prebuilt не перезаписывать stock binary.
Выход: четыре ABI from source; helper APK отдельного UID; скопированные
original tuples до SOCKS; минимальный socket hook; transport decision ADR.
Проверки: T01/T02, TCP/TLS/UDP/IPv6 selected/nonselected, protect/bind failure,
30 starts/100 stops и RSS/latency, source dependency pins. Риски: pcb tuple
orientation, fd lifecycle, TV custom behavior, gVisor cost. Exit: настоящий
Android TUN прототип, helper артефакты/source hashes и lab evidence, выбранный
stack с limit/16KiB pending plan. Host-only fixtures не закрывают exit.
При отсутствии среды оставить P01 in_progress, не переходить к P02.

### P02 — свой shell и миграция

Вход: принят transport P01, inventory schema2/schema1 и legacy keys в usage map.
Изменения: typed navigation, app picker/onboarding, ViewModel/StateFlow,
versioned settings/domain/command import; MainActivity production routes.
Выход: выбранный пакет — основной объект, новый UI не вызывает legacy composables;
local backup/report и совместимое обновление appId/signer. Старый engine допустим
через временный facade, помеченный в ledger. Проверки: T03, empty/uninstalled,
update0/1/2/future/corrupt/idempotent, own package excluded, UI реакция/доступность.
Риски: скрытые preference defaults/Telegram secret, недоступный compatible signer.
Exit: собственные navigation/app picker, no data loss fixtures, disabled old
routes, build/unit/instrumentation actual; недоказанный signed update остаётся gate.

### P03 — supervisor и flow ownership

Вход: shell/repositories P02, original tuple/socket hooks P01.
Изменения: один SessionSupervisor/VpnService, native FD factory protect+bind,
typed desired/runtime/generations, receivers/tile, flow events/owner confidence.
Выход: production source transport integration, serialized stop/reconfigure,
event-driven networks, globals/числовые callbacks не runtime authority.
Проверки: T02/T04/T05/T06, API26/29/36 helper UIDs, consent/process/background,
STOP race/stale publish/fd counts. Риски: shared/system resolver UID, OEM lifecycle,
network loss during bind. Exit: resources и generations закрываются по контракту,
unknown честен, own socket UID не приписан helper, old globals отключены от runtime.

### P04 — видимый DNS в рабочем пути

Вход: один supervisor, real TUN и ownership hooks P03.
Изменения: virtual DNS address handling TCP/UDP до SOCKS, protected upstream,
wire forwarding затем bounded cache/recovery policy. Выход: visible query/outcome
и корректные wire responses; host recovery не зависит от появившегося TLS.
Проверки: T07/T08, DNS/source address/ID/question matching, EDNS/DO/AD/unknown RR,
TC→TCP, negative TTL, STOP/network generation. Риски: netd attribution, private DNS,
TCP framing, cache poisoning/malformed. Exit: helper выбранный traffic действительно
проходит broker, split policy intact, encrypted DNS marked unknown; tests и build.
Собственный DoH здесь ещё не заявляется.

### P05 — AccessPlanner и DPI

Вход: real DNS/flow outcomes P04, source ByeDPI pin и legacy fixture contracts.
Изменения: новый bounded planner, прямой ByeDPI adapter/socket factory, family-aware
decisions, network memory/backoff, credentials-free probes. Выход: автоматический
короткий fallback по сбою, без каталога обязательных ручных стратегий.
Проверки: T09/T10, safe first handshake и next flow отдельно, replay guards,
deadline8s/queue64/concurrency2, TLS identity и overflow base path.
Риски: held first Hello, приложение не retry, stale endpoint, stream partial writes.
Exit: production planner с наблюдаемым trigger и scoped decisions, no application
data/0RTT replay, SLO fixtures+helper, HTTP probe не app claim. Limited first-flow
результат допустим лишь с явной поддержанной областью и evidence.

### P06 — DoH и mapping

Вход: broker/policy P04/P05. Изменения: wire DoH transport, bootstrap/network
sockets, explicit provider policy, bounded pooling/cache, mapping rules.
Выход: проверенный resolver и coherency A/AAAA/CNAME/HTTPS/SVCB с provenance/expiry.
Проверки: T08/T10, wrong cert/name, bootstrap no loop, DNSSEC flags, hints/ECH/ALPN,
failure/downgrade/STOP. Риски: private/filtering bypass, incompatible endpoint,
external provider proxy. Exit: helper пользуется реальным transport, mapping
ограничен protocol/family/port и разрешением; no silent downgrade/cross-provider mix.

### P07 — protocol acceptance

Вход: family-aware planner/DNS P05/P06. Изменения: QUIC metadata/parser лишь если
полезен и bounded, protocol-specific policy, IPv6/NAT64 fixes; не глобальные bans.
Выход: capability matrix ECH/GREASE/QUIC/IPv6 со supported/limited/unknown.
Проверки: T11/T12/T13, настоящие ECH и GREASE раздельно, HTTP3-only/dual,
non443 UDP, DNS64/Happy Eyeballs и сеть IPv6-only/NAT64. Риски: внешнее имя,
cached DNS/SVCB, unknown protocols/MTU. Exit: unchanged forwarding проверен,
ограничения имеют fixtures/evidence и видны UI; неподтверждённый recovery не marked
supported. Реального NAT64/QUIC success нельзя вывести из обычного TCP GET.

### P08 — результат, инструменты и Telegram

Вход: capability/evidence model P07, shell P02. Изменения: собственные product
screens/text/assets, diagnostics actions, new Telegram controller и source build,
expert legacy import facade removal. Выход: UX выбора приложений без обязательного
DNS/argv выбора, независимый MTProto tool с действиями подключения; provenance registry.
Проверки: T14/T15, accessibility/TV/font scale, status freshness/confidence,
Rust Cargo.lock/exports/licenses и build ABIs, migration no loss.
Риски: false-green status, connection link/secret, signer compatibility.
Exit: legacy UI/globals отключены, тексты собственной identity, лицензии сохранены,
Telegram исходники реально собираются и не зависит от VPN desired state.

### P09 — приёмка и вывод legacy

Вход: P01–P08 evidence и pending device/compatibility gates.
Изменения: удалить active unreachable legacy/prebuilts только после replacements,
обновить license/SBOM/provenance/README/commands и final cleanup ledger.
Выход: locally reviewable release candidate, миграционный отчёт и честная matrix.
Проверки: весь test-plan, app assemble/unit/lint/native/sanitizer/instrumentation,
T16/T17/energy/OEM/TV/compatible update на физических устройствах/сети.
Риски: реальный клиент/API/account/GeoIP, 16KiB/ABI, непройденный device gate.
Exit: все обязательные gates исполнены или scope формально сокращён с ADR и
исключением обещанной функции; отсутствие устройства оставляет P09 in_progress.
Активный derived code с обязательной attribution не стирать из notices.
Публикация/push/merge не выполняются автоматически.
