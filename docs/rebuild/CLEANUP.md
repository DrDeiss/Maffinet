# Очистка старого Maffinet

P00 выполнил первую проверенную очистку после baseline commit `2d63fc3`.
Фактические операции — [cleanup-ledger.md](cleanup-ledger.md), достижимость и
назначенные этапы — [usage-map.md](usage-map.md). Таблица ниже сохраняет
классификацию; она не разрешает удалять оставшиеся кандидаты без replacement.

## Сохранение исходного состояния

1. Прочитать применимые AGENTS.md, проверить branch/HEAD/status и первоначальные незакоммиченные изменения. Не использовать `reset --hard`, `clean -fd`, массовый restore или удаление исследовательских каталогов.
2. Сохранить восстановимую копию tracked diff и нужных untracked source-файлов. Предпочтительно — отдельная рабочая ветка `codex/rebuild-p00` и проверенный baseline-коммит конкретных файлов. Сначала проверить staged diff и исключить секреты, `.toolchain`, подписи, APK, приватные device logs и аккаунтные screenshots.
3. При запрете записи в Git сохранить локальный snapshot разрешённых файлов и manifest/checksums, отметить ограничение. Не объявлять snapshot Git-коммитом.
4. Сохранить данные миграции и описание старых схем. Не очищать телефон, пользовательские настройки и установленное приложение.

## Классификация кандидатов

| Объект | Действие | Условие |
|---|---|---|
| `ui/services/ServicesScreen.kt` | Удалить отменённый экран | Проверить отсутствие доступного navigation path и зависимых тестов |
| `core/services/ServiceCatalog.kt`, `ServiceProfile.kt`, service-enabled preferences | Заменить/убрать отменённую модель | Сначала удалить callers; отдельно сохранить нужный allowlist helper и миграцию. `ApplicationRouting` сейчас связан с этим пакетом и не должен исчезнуть вместе с ним |
| `MainTab.kt`, `YoutubeTab.kt`, `SettingsTab.kt`, числовые routes MainActivity | Заменить собственной навигацией и экранами | Сейчас часть routes достижима; не удалять как «мёртвый код» без новой замены |
| `data/Actions.kt` и общие mutable globals | Заменить supervisor/repository/ViewModel | Изолировать состояния VPN, Telegram, обновлений и UI; сохранить STOP-инварианты |
| YouTube wizard и default package insertion | Удалить специфический обязательный сценарий | Не ломать импорт пользовательских команд и настройки выбранных приложений |
| Старые service-flags и `wants_youtube_bypass` | Оставить только одноразовый migrator | После миграции новое runtime не читает их как authority |
| `LegacyStrategyAliases`, `{list:youtube/...}`, старые presets | Изолировать в versioned import adapter | Команды пользователя не пропадают; авто-режим не зависит от legacy aliases |
| Current AutomaticAccessController и native patches | Заменять по контрактам | Сохранить доказанные TTL/epoch/streaming/replay guards; не выбросить регрессионные знания |
| HEV prebuilts и JNI rebind scripts | Удалить из активной сборки после source-built replacement | Проверить ABI, TV, stop/restart, UDP/IPv6 и сохранить historical provenance |
| Rust prebuilts Telegram | Заменить воспроизводимой сборкой | Сначала подтвердить Cargo.lock, exports, transitive licenses и runtime |
| Общая 130-domain база | Изолировать в экспертные правила или hints | Не делать её обязательной областью auto; неизвестные приложения поддерживаются без новой записи каталога |
| Hosts import | Разделить domain filter и IP mapping | Не менять семантику старого пользовательского файла молча |
| Старый `PLAN.md`, сравнительные docs, historical screenshots | Архивировать как историю и пометить superseded | Сохранять подтверждения, первичные источники, copyright; не смешивать с новым планом |
| Build outputs, временные XML, downloads | Убирать только проверенные task-owned объекты | Не удалять toolchain, исходники, приватную evidence и чужие файлы по шаблону имени |
| Product strings, assets и updater links | Заменить собственными | Проверить production resources, manifest, links, onboarding и APK, а не только имя файла |
| LICENSE, NOTICE, dependency notices, происхождение кода | Сохранить и уточнить | Не вычищать атрибуцию массовой заменой слова |

## Порядок

В P00 удалить только подтверждённые недостижимые остатки и перенести устаревшую документацию с рабочими ссылками. Если usage существует, записать точную зависимость и назначить замену на P02/P03/P08. «Зачистить всё старое» означает вывести ненужную реализацию из рабочей системы последовательно, сохраняя сборку, данные и проверяемость.

Обязательный отчёт `cleanup-ledger.md`: путь, владелец текущей работы, почему ненужен, usage search, replacement, операция, результат проверки, способ восстановления. Обязательства по чужому авторству не являются мусором.

Перед любым рекурсивным move/delete в Windows разрешить абсолютные target paths и убедиться, что они находятся внутри workspace. Использовать native PowerShell `-LiteralPath`; не передавать вычисленные пути в другой shell для удаления.
