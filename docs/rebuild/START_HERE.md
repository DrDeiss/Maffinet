# Переработка Maffinet Android

P00 завершён 4 октября 2026 года: baseline сохранён, повторный аудит и
исследование выполнены, безопасная очистка записана, продукт/архитектура/этапы
и проверки определены. Новый runtime ещё не реализован. Следующий этап — P01.

Начинай с [STATE.json](STATE.json), [NEXT_CHAT.md](NEXT_CHAT.md),
[ROADMAP.md](ROADMAP.md) и последнего [отчёта P00](sessions/P00.md).
Проверь Git status/HEAD и subject stage checkpoint. Первоначальная полная
инструкция — [NEXT_SESSION_PROMPT.md](NEXT_SESSION_PROMPT.md).

Материалы принятого scope:

- [product.md](product.md): основной путь, MVP, смысл статусов и границы.
- [AUDIT.md](AUDIT.md), [usage-map.md](usage-map.md): факты, наследование,
  callers, migrations и этапы удаления legacy.
- [ARCHITECTURE.md](ARCHITECTURE.md): один supervisor, source-built transport,
  DNS broker, policy и evidence.
- [research.md](research.md), [SOURCES.md](SOURCES.md): факты/гипотезы/опыты,
  цена и первичные источники с HEV candidate pin.
- [ADR-001](decisions/001-transport-prototype.md),
  [ADR-002](decisions/002-evidence-and-policy.md): transport/evidence решения.
- [test-plan.md](test-plan.md), [evidence](evidence/README.md): SLO,
  helper/device matrix и фактически исполненные проверки.
- [CLEANUP.md](CLEANUP.md), [cleanup-ledger.md](cleanup-ledger.md): правила,
  операции и восстановление; [архив PLAN](archive/PLAN-0.3.2-alpha.md).

Baseline commit: `2d63fc3d6e2e04d2ba8a088f234e19aa2e4e75cd`, ветка
`codex/rebuild-p00`. Stage commit определяется по subject STATE/git log,
будущий hash не записывается в содержимое самого коммита. Raw snapshot
изменённых файлов дополнительно проверен по SHA256 manifest.

P00 JVM: 154 passed, 7 skipped native parser. Android build/unit/lint blocked
отсутствующим SDK; Linux fixture требует provisioned Linux host. Device/helper
и новый TUN ещё не испытывались. P01 не объявлять done без настоящего TUN.

Каждый чат завершает один текущий этап, сохраняет session/evidence/STATE/NEXT_CHAT
и локальный commit. Push/publish/merge не выполняются автоматически. Секреты и
signing material остаются локальными; чужие изменения и данные сохраняются.
