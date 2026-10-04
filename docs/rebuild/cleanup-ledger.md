# Cleanup ledger P00

Дата: 4 октября 2026 года. Владелец операций — эта сессия P00; исходные
изменения пользователя сохранены в baseline `2d63fc3d6e2e04d2ba8a088f234e19aa2e4e75cd`.
Raw snapshot изменённых файлов проверен по [manifest](evidence/baseline-manifest.json).
Каталоги cache/toolchain, APK, signing material, приватная evidence и устройство
не очищались. Usage map — [отдельный файл](usage-map.md).

| ID | Путь и операция | Основание / replacement | Проверка и восстановление |
|---|---|---|---|
| C01 | Удалён `app/src/main/java/io/maffinet/android/ui/services/ServicesScreen.kt` (73 строки) | Отменённый экран; rg по main/test/androidTest/verification/src/tools/.github нашёл только определение. Нет route в MainActivity или manifest/resource/ProGuard/reflection ссылки. Новый app-first UI P02 | Повторный rg — 0 usages; JVM 154 passed/7 skipped до и после; Android build до/после blocked одинаковым missing SDK. Восстановить единственный файл `git restore --source=2d63fc3 -- <path>` после проверки новых изменений |
| C02 | `PLAN.md` перенесён в `docs/rebuild/archive/PLAN-0.3.2-alpha.md`, корень заменён указателем; пять относительных ссылок адаптированы | Прежний развивавшийся план — историческая evidence, не новые требования. Все входящие ссылки на PLAN сохранены через указатель; пять ссылок `docs/<name>` после переноса изменены на `../../<name>` | После обратной замены только пяти link targets SHA256 совпадает с исходным manifest PLAN; восстановление из baseline или архива; содержимое архива не редактировалось |
| C03 | Создан корневой `AGENTS.md`; README ведёт к rebuild | Прежних применимых AGENTS в E:/ и проекте не найдено; предотвращает продолжение отменённого плана | Paths/links проверены; удалить новый указатель для обратной операции, не трогать user source |
| C04 | Исторические docs/ARCHITECTURE, NETFIX_COMPARISON, NETWORK_RECOVERY_RESEARCH получили статус и ссылку на rebuild | Оставлены по исходным путям: живые относительные ссылки и provenance/evidence. Переезд остальных документов не даёт ценности P00 | Добавлены только status prefixes; исходный текст сохранён; restore отдельного префикса из baseline |

Наличие документационного назначения не означает удаление активного кода.
ServiceCatalog/ServiceProfile/settings/test setters остаются по подтверждённым
usages. ApplicationRouting расположен внутри ServiceProfile.kt и нужен runtime.
YoutubeTab/SettingsTab/InfoTab имеют numeric routes и не являются мёртвыми.
Globals, HEV, native patches, domains и миграции назначены P02/P03/P05/P08/P09
в [usage-map.md](usage-map.md), а не удалены по имени.

P00 не заявляет полную Android-компиляцию после удаления: baseline SDK blocker
сохраняется. Проверки source reachability и чистых models выполнены; первую
доступную Android-сборку P01 проводить до новых production изменений. Если
найдётся скрытая зависимость C01, восстановить один файл, записать evidence и
пересмотреть ledger. Массовый reset/clean не использовать.
