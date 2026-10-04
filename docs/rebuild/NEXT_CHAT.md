# Автономное продолжение: P01

Работай в `E:\maffinet android`. Прочитай корневой AGENTS.md и
`docs/rebuild/START_HERE.md`, `STATE.json`, `ROADMAP.md`, `sessions/P00.md`,
`product.md`, `ARCHITECTURE.md`, `research.md`, `test-plan.md`, `usage-map.md`,
`cleanup-ledger.md`, оба ADR в `decisions/` и первоначальный
`NEXT_SESSION_PROMPT.md`. Выполни только текущий in_progress либо следующий
pending этап. Если STATE соответствует сохранённому результату, это P01.

## Точка продолжения

P00 завершён 4 октября 2026 года на `codex/rebuild-p00`. Начальный HEAD
`0d1cf68054120f2248f038fbbb3af9fe448589a0`; исходная работа0.3.2-alpha
сохранена baseline commit `2d63fc3d6e2e04d2ba8a088f234e19aa2e4e75cd`.
Проверь HEAD/status и `git log` с subject
`docs(rebuild): complete P00 audit cleanup and implementation plan`.
STATE.lastCheckpointCommit указывает на уже созданный baseline, а
completionCommitSubject — на фиксацию P00. Не откатывай появившуюся работу.

Удалён только недостижимый ServicesScreen; legacy ServiceProfile.kt содержит
ApplicationRouting с runtime callers и сохранён. PLAN0.3.2 архивирован.
Reachable old UI/services/native/migration ещё ждут замен P02/P03/P05/P08/P09.
Продукт принят: выбрал установленные apps → consent → включил → наблюдаемый
результат. Нет обязательного собственного сервера/root/account, весь телефон
при пустом выборе никогда не включается. TLS/ECH не ослаблять, application
data/POST/0-RTT не replay. Telegram независим.

## P01: конкретная работа

1. Проверь toolchain/source/SDK/device. Portable JDK и Gradle-cache доступны;
   команды в test-plan. В P00 Android SDK/adb отсутствовали, WSL не установлен.
   Найди provisioned среду/host и уже принятые SDK agreements. Новые соглашения
   автоматически не принимай. До production edits запусти первую доступную
   Android-сборку после C01 cleanup; исходные failures сохранены в logs.
2. Первый prototype — upstream HEV2.14.4, commit
   `4d6c334dbfb68a79d1970c2744e62d09f71df12f`. Получи source и все recursive
   gitlinks, запиши hashes/license inventory/build recipe. Core pin:
   `4be2e621813ba0315cfacd995bf501bde91d6996`; остальные проверь при fetch.
   Не меняй нынешний custom HEV gitlink/binaries по похожему имени.
3. Создай собственный bridge/test path с явным владением TUN fd и
   start/stop/error outcomes. Stock JNI имеет config+fd, без TV Boolean;
   custom TV semantics исследуй отдельно. Hooks всех external fd защищают
   и привязывают к current physical network до connect. Ошибка protect/bind
   не допускает внешний connect.
4. Копируй original TCP/UDP local/remote tuple и family до SOCKS translation.
   Проверяй orientation по helper socket/wire. App UID не заменять UID своего
   upstream socket. P01 доказывает tuple hook; final UID/Unknown/shared policy P03.
5. Добавь helper APK с другим applicationId/UID, два variants для selected и
   nonselected control. DNS raw UDP/TCP и system resolver, numeric TCP,
   verified TLS/credential-free HTTP, UDP echo, IPv6. Real allowlist → TUN →
   source transport → physical network обязателен; проба процесса Maffinet
   его не заменяет. Не удаляй данные чужого устройства; соблюдай разрешения
   на установку APK/физические действия.
6. Собери четыре ABI from source; исполни T01/T02 selected/nonselected,
   start/stop/restart, FD counts, IPv6 и SLO measurements. Добавь repeatable
   harness и actual Gradle/CI commands. APK/signing/private logs не коммитить.
   Full runtime16KiB/TV остаётся P09 gate, lab bridge proof его не подменяет.
7. Прими stack ADR по измерениям. При неприемлемых HEV hooks сравни
   source-built Firestack/gVisor с immutable pin, MPL/transitive licenses,
   API/build/Go runtime/RSS/размером. Старый standalone Outline repo не
   сопровождается; modern source location в research. Новый TCP/IP stack
   с нуля не писать без отдельного обоснования.

## Доказательства и завершение

P00 suite реально исполнил 161 cases: 154 passed, 7 native parser skipped,
0 failures/errors. До и после cleanup Android assemble/unit/lint exit1
`SDK location not found`. Native hash/inverse JNI check и ByeDPI patch prep
exit0; Linux fixture exit1: требует Linux/C compiler. Device/helper/ECH/QUIC/
NAT64/energy pending; старый OnePlus LinkedIn результат — история.

P01 done только с source-built transport+helper и настоящим Android TUN lab
evidence по ROADMAP. Если SDK/Linux/device недоступны, делай source/hooks/helper/
fixtures/harness и независимые проверки, оставь P01 in_progress с конкретным
remaining gate и промптом продолжения P01. Не перескакивай к P02.

В конце сохрани `sessions/P01.md`, команды/exit codes/evidence/limitations,
обнови STATE и NEXT_CHAT, проверь diff/links/JSON (есть
`py -3.11 tools/verify-rebuild-docs.py`), stage только конкретные files и
создай локальный commit. Git write может требовать managed escalation;
локальные commits уже разрешены. Без push/publish/merge/main reset.
Сообщи этап, ссылки, фактический hash, проверки и оставшиеся gates.
