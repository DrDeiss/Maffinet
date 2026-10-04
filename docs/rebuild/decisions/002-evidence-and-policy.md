# ADR-002: evidence и допустимые политики

Статус: принято. Дата: 4 октября 2026 года.

Engine runtime, DNS/transport probe, observed forwarding и real app scenario
хранятся отдельно. UI использует freshness/session/network generation и owner
confidence. HTTP200 независимой пробы не повышается до «приложение работает».
API26–28/INVALID_UID/shared UID/скрытый hostname имеют честные Unknown/group
состояния. Контекст берётся до SOCKS; UID собственного outgoing socket исключён
из attribution приложения.

Default DNS MVP — физический resolver с сохранением private/split policy.
External resolver recovery и Smart DNS endpoints применяются только разрешённой
политикой. NXDOMAIN не достаточен для её смены. Broker сначала сохраняет wire,
потом cache/recovery, затем DoH/mapping с coherency A/AAAA/HTTPS/SVCB.

Безопасный base path не ожидает полный host research. Fallback ограничен
дедлайном/concurrency/backoff; network changes и STOP отзывают право publish.
Разделяются first safe handshake и следующий flow; application data/POST/0-RTT
не повторяются. UDP443/AAAA глобально не запрещаются; ECH/TLS не ослабляются.

Следствия: часть сценариев останется limited, а статусы будут менее категоричны.
Проверки — [test-plan](../test-plan.md); пользовательский смысл —
[product](../product.md). Достоверность имеет приоритет перед зелёным статусом.
