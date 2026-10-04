# Usage, миграции и происхождение: P00

Проверено после baseline `2d63fc3` до удаления одного недостижимого экрана.
Команды: `rg -n 'ServicesScreen|ServiceCatalog|ServiceProfile|ApplicationRouting'
app/src verification`, поиска routes в MainActivity, manifest/resources/ProGuard,
а также `git show upstream/main:<path>`. Usages — фактические зависимости;
сходство после namespace normalization не доказывает самостоятельное авторство.

| Область | Текущий путь / callers | Происхождение и план |
|---|---|---|
| Activity/UI | MainActivity numeric 0→MainTab, 1→TgProxyTab, 2→StrategiesScreen, 3→SettingsScreen, 4→InfoTab, 6→YoutubeTab, 7→SettingsTab, 8→AdvancedScreen, 9→DomainListsScreen | Upstream Activity имеет numeric tabs Main/Tg/Youtube/Settings/Info. Собственная замена P02, product tools/accessibility P08; legacy пути отключаются до удаления |
| Отменённый ServicesScreen | Только определение в `ui/services/ServicesScreen.kt`; route/import/manifest/test/reflection callers не найдены | Удалён P00, восстановим из baseline; не переносить в новый UI |
| ServiceProfile/ApplicationRouting | Оба в `core/services/ServiceProfile.kt`. Routing вызывают ConnectionCoordinator и ByeDpiVpnService. ServiceCatalog вызывают settings и тесты | Весь пакет не удалять. Новый app picker/repository P02, routing contract P03; отделить helpers, только потом удалить каталог |
| Settings schema2 | `data/settings/MaffinetSettingsRepository.kt`: SharedPreferences `<applicationId>_preferences`, миграция legacy flags once; desired states independent | P02 migrator+backup/report, P03 новые runtime repositories; `maffinet_enabled_services` пока в settings/test setters, не deleting данных |
| Globals и receivers | `data/Actions.kt`, ServiceManager/ConnectionCoordinator, BootReceiver/Watchdog/Tile, MainTab и старые tools | Upstream application conventions сохраняются. P03 serializer supervisor/repositories; P02 временный compatibility facade допустим с явным ledger |
| Domain filter | DomainParser/import → UserDomainRepository → FileUserDomainStore schema1; compiler/aliases и старые tools используют built-ins | Regression contracts сохраняются. P02 migration, P05 ручной adapter, P06 отдельный mapping; 130-domain list не runtime authority нового auto |
| DNS diagnostics | Builder DNS; AutomaticAccessController DNS probes и DnsConfigurationMonitor | Нет broker выбранного traffic. P04 wire broker, P06 DoH; старые probe contracts — evidence, не DNS implementation |
| Native access | prepared pinned ByeDPI + patches → native-lib.c → ByeDpiProxy → AutomaticAccessController | Прямой собственный engine adapter P05; сохранить fixture/replay/epoch знания. Не выдавать изученный код за clean-room |
| HEV/JNI | TProxyService + custom `.so`, JNI binding/verify scripts; app/build.gradle/sourceSets | P01 отдельный source-built prototype, production switch после P03 integration gates; prebuilts убрать из активной сборки P09 |
| Telegram | TgProxyController/Service, Rust JNA ABI, Cargo.toml/lock, четыре prebuilts | Android integration inherited; P08 собственный controller/adapter и Rust source build, P09 убрать shipped opaque libs при доказанной замене |
| CI/tests | `.github/workflows/android.yml`, verification pure models, parser/socket/stream fixtures, API29/36 instrumentation | Сохраняются. P01 добавить helper TUN suite; API26 и 16 KiB runtime в новом графе, old counts не exit criterion |
| Лицензии | LICENSE GPLv3, NOTICE, licenses/NetFixMobile-NOTICE.txt, UPSTREAM_LICENSE_NOTES и NATIVE_PROVENANCE | Сохраняются. NOTICE упоминает JNA5.14, active dependency5.19.1: фактическая актуализация P08/P09 license/SBOM gate, без удаления авторства |

## Миграционные данные

P02 обязан экспортировать local backup до записи новой схемы, проверить
целостность и делать idempotent import с отчётом. Пакеты — `selected_apps`;
режимы — `maffinet_applications_enabled/requested`,
`maffinet_telegram_enabled/requested`; legacy — `service_enabled`,
`wants_youtube_bypass`, `telegram_proxy_enabled_by_user`.

Доменный файл имеет header `# Maffinet user domains; schema=1` и атомарную
замену. Его фактическое имя/директория определяется Android domain repository
при реализации migrator; не подставлять выдуманный storage path.
DNS, `byedpi_*`, custom strategy argv/probe targets, IPv6 preferences и
Telegram `secret_key`, `tgproxy_*`, `autostart` сохраняются. Unknown/future
version не перезаписывать. Backup с Telegram secret не экспортировать в Git/log.

## Как доказать удаление application layer

В P02/P03/P08 вести registry новых файлов: автор новой реализации, использованный
source, copied/adapted/contract-only, лицензия и заменённый legacy path.
В P09 сравнить поставляемые классы/ресурсы, dependencies, manifest entry points
и references с upstream `19cb13c19f87a1fe6339acb65e35da2ab4d14a43`.
Ни новая namespace, ни малый процент текстового совпадения сами по себе не exit.
Если derived участок остаётся, attribution и статус derived остаются тоже.
