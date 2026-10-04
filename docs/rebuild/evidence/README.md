# Evidence P00

- [baseline-manifest.json](baseline-manifest.json): 121 первоначально изменённый
  или untracked файл, длина и SHA256 до cleanup, verified local raw ZIP.
  Исходный status без новых evidence имел 85 верхнеуровневых записей, 121 при
  раскрытии untracked каталогов. Отличие от PREP84 — папка docs/rebuild.
- [checks.json](checks.json): фактические команды/exit codes/ограничения.
- [P00-baseline-jvm.log](P00-baseline-jvm.log) и
  [P00-post-cleanup-jvm.log](P00-post-cleanup-jvm.log): принудительное исполнение
  pure models, 154 passed и 7 skipped native parser, 0 failures/errors.
- [P00-baseline-android.log](P00-baseline-android.log) и
  [P00-post-cleanup-android.log](P00-post-cleanup-android.log): SDK отсутствует,
  assemble/unit/lint остановлены до компиляции.
- [doc-validation.json](doc-validation.json): paths/links/JSON/fences,
  baseline archive и post-cleanup XML totals, проверенные этим этапом.
- [P00-native-fixture.log](P00-native-fixture.log): фактический отказ запуска
  Linux fixture на Windows до компиляции; native runtime не проверен.

Это local build/model evidence, не device acceptance. Raw ZIP находится
в ignored `.toolchain/rebuild-p00/baseline.zip`; его hash в manifest.
Baseline Git commit `2d63fc3d6e2e04d2ba8a088f234e19aa2e4e75cd`
сохраняет весь source/docs, а существующий HEAD — остальные tracked файлы.
Игнорируемые toolchain/APK/signing/private logs не копировались в коммит
и не удалялись. Native patches содержат исходные whitespace context lines;
они не исправлялись ради diff --check, чтобы сохранить apply-контракт.

В архиве PLAN адаптированы пять относительных link targets. Обратная замена
восстанавливает исходный SHA256; validator дополнительно сравнивает historical
content с Git baseline с допустимой LF/CRLF нормализацией для будущих checkout.

## P01

[P01-checks.json](P01-checks.json) сохраняет initial scaffold checks и отдельный
continuation ledger на HEAD `56da2f3`: Android baseline/lab SDK failures,
Linux fixture blocker, Java admission/helper validation, Python18 rejection
contracts и source/license integrity. Raw continuation logs/environment JSON
с SHA256 остаются ignored `.toolchain/rebuild-p01/continuation-*`; это host
evidence, без private network/device captures. [Session](../sessions/P01.md)
фиксирует границы. JVM154/7 и relay100 не повторялись и остаются historical.

Публичные Gradle logs сохраняют все строки с удалением trailing spaces в
стандартном сообщении Gradle daemon. Raw copies в ignored
`.toolchain/rebuild-p00/raw-logs/`; raw/saved SHA256 записаны в checks.json.
