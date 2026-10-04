# Этапы переработки Maffinet Android

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
