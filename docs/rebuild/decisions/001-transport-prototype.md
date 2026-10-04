# ADR-001: source-built HEV как первый прототип

Статус: принято для исследования P01, production stack ещё не принят.
Дата: 4 октября 2026 года. Основания: [research](../research.md),
[baseline](../evidence/baseline-manifest.json), [test-plan](../test-plan.md).

Существующий HEV имеет custom JNI/TV ABI и недоступный source pin. Его нельзя
заменять stock `.so` без собственной интеграции. P01 начинает с upstream
HEV2.14.4 `4d6c334dbfb68a79d1970c2744e62d09f71df12f`, рекурсивно фиксирует
gitlinks, лицензии и команды сборки. Использовать отдельный bridge/test service,
не переписывать текущие prebuilts.

Причина выбора: C stack уже соответствует локальному SOCKS/DPI пути;
опубликованный tunnel source содержит TCP/UDP callbacks до SOCKS translation.
Гипотеза original tuple hook проверяется helper APK до утверждения stack.

Обязательные gates: четыре ABI from source, FD ownership и native STOP/restart,
TCP/TLS/UDP/IPv6 через настоящий выбранный TUN, исключённый helper control,
копирование tuple до translation, protect/bind failure handling и измерение
RSS/CPU/latency. Source pin сам по себе не означает reproducible build или
16 KiB runtime acceptance.

Если hook требует вмешательства в TCP semantics или не проходит UDP/IPv6/stop,
сравнить Firestack/gVisor при закреплённом source. Измерить Android API support,
Go runtime/размер/память/лицензии и API stability. Старый standalone Outline repo
не брать: он сообщает о прекращении сопровождения. Новый stack с нуля исключён
без отдельного решения и измеренной необходимости.

Результат P01 — ADR принятого транспорта с фактическими pins/evidence,
не декларация в P00. Если среды нет, P01 остаётся in_progress с точным gate.

## Findings P01, 4 октября 2026 года

Все пять source repositories получены: [immutable lock](../../../lab/transport/source-lock.json)
содержит recursive gitlinks, Git trees, canonical archive и license hashes.
Кастомный gitlink/prebuilts не изменены. Изолированный
[bridge/test path](../../../lab/README.md) включается отдельным Gradle property.

TCP gateway PCB ориентирован destination→app; JNI копирует remote как
application local до SOCKS translation. UDP первое создание PCB ещё не имеет
destination: hook перенесён в datagram callback после обновления адресов lwIP,
перед SOCKS framing, для каждого destination. Это source finding; helper/wire
orientation acceptance ещё отсутствует. UID остаётся Unknown/P03 pending.

Stock quit ждёт event fd бесконечно. Bridge сохраняет early STOP, вызывает quit
только в ready interval, сохраняет ownership при timeout и запрещает новый
worker до reap. Main wrapper освобождает приобретённые logger/task resources;
patch защищает partial gateway allocation failures. Native execution и
failure/restart/FD acceptance не исполнялись из-за отсутствующей среды.

Host relay прошёл TCP/UDP IPv4/IPv6, pre-connect/pre-send denial и 100 close
cycles. SDK baseline/lab build blocked до compilation; native fixtures требуют
Linux/C compiler. Нет размера/RSS/latency/четырёх ABIs/APK/TUN результата.
**HEV остаётся кандидатом; production stack не принят.** Сравнение Firestack
не запускается по одному environment blocker: неприемлемость HEV не измерена.
TV Boolean semantics и full16KiB acceptance остаются отдельными gates.
