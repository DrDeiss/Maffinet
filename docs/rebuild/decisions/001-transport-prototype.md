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
