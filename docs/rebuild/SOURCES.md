# Исследование улучшений Maffinet

Источники проверены 4 октября 2026 года. Ниже отделены описанные авторами возможности от предложений для Maffinet. Это не рейтинг приложений и не подтверждение обхода на сети пользователя.

## Что взять из других подходов

| Решение | Подтверждённая идея | Предложение для Maffinet | Граница применения |
|---|---|---|---|
| [Rethink](https://github.com/celzero/rethink-app) | DNS, flow tracking и разные выходы объединяются в одном Android VPN; UID через ConnectivityService на Android 10+ | Единый рабочий путь, контекст соединения, отдельные политики и результат на приложение | Не переносить firewall-продукт целиком. DNS attribution сложнее обычного TCP tuple и не всегда точна |
| [Intra](https://github.com/Jigsaw-Code/Intra) | Собственный DoH и наблюдение DNS-поведения | Реальный resolver вместо каталога названий; bootstrap и ограниченные проверки транспорта | DoH исправляет часть DNS-проблем, но не меняет IP клиента и не гарантирует обход DPI |
| [PCAPdroid](https://github.com/emanuele-f/PCAPdroid) | Наблюдение сетевого трафика Android без root | Ограниченные локальные метаданные и понятная причина отказа | Не добавлять MITM и автоматический сбор payload; второй VpnService нельзя использовать как параллельный анализатор |
| [ByeDPI](https://github.com/hufrea/byedpi) | Независимый локальный SOCKS-движок с DPI-приёмами | Использовать непосредственно как изолированный engine adapter | Семантика фильтров/retry/UDP зависит от закреплённой ревизии и должна проверяться |
| [HEV](https://github.com/heiher/hev-socks5-tunnel) | Доступный tun2socks, UDP forwarding, документированный mapdns | Сборка из исходников с собственным JNI и flow hooks | Документация upstream не подтверждает свойства нынешних кастомных prebuilt Maffinet |
| [NetFix Windows](https://github.com/rupleide/NetFix) | Автоматизация инструментов, настоящие hosts-моды и DNS физического интерфейса | Сохранить принцип простого запуска и полезное разделение механизмов | Не переносить Windows-службы, BAT, интерфейс и packet-level обещания |

Вывод исследования: наиболее полезно объединить DNS и соединения в одном контролируемом пути, сохранить контекст сети и показывать измеренный результат. Копирование экранов другого инструмента не является необходимой частью этого решения.

## Android API

- [VPN guide](https://developer.android.com/develop/connectivity/vpn): в одном user/profile действует один VpnService. Для разделения трафика доступны allowed/disallowed applications. Выходящие служебные sockets нужно исключать из туннеля. Добавление приложения к списку действующего VPN требует пересоздания интерфейса; план должен учитывать краткий reconnect.
- [ConnectivityManager.getConnectionOwnerUid](https://developer.android.com/reference/android/net/ConnectivityManager#getConnectionOwnerUid(int,%20java.net.InetSocketAddress,%20java.net.InetSocketAddress)): API29+, TCP/UDP, исходные local/remote адреса, доступ активному VPN; возможен INVALID_UID. Это основание для прототипа attribution, а не обещание точного package для каждого события.
- [VpnService.Builder.addDnsServer](https://developer.android.com/reference/android/net/VpnService.Builder#addDnsServer(java.net.InetAddress)): назначение resolver. Вызов не реализует DNS proxy, проверку ответов или recovery.
- [VpnService.protect](https://developer.android.com/reference/android/net/VpnService#protect(int)) и [Network.bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.net.Socket)): контракт исходящих sockets вне собственного туннеля и на выбранной физической сети. Конкретный native bridge необходимо испытать.

Предложение: сохранить minSdk26, но явно объявлять attribution ограниченной на API26–28 и при неизвестном UID. Не повышать minimum только ради красивого per-app статуса без продуктового решения.

## Протоколы

| Источник | Требование к проекту |
|---|---|
| [RFC 2308](https://www.rfc-editor.org/rfc/rfc2308.html) | Различать NXDOMAIN и NODATA, учитывать SOA и отрицательный TTL. Пустой массив IP не заменяет DNS-результат |
| [RFC 8484](https://www.rfc-editor.org/rfc/rfc8484.html) | DoH передаёт DNS wire messages через HTTPS. Нужны bootstrap, валидация сертификата и явная политика downgrade |
| [RFC 9460](https://www.rfc-editor.org/rfc/rfc9460.html) | SVCB/HTTPS задают endpoint и параметры. Нельзя менять A/AAAA отдельно и оставлять несовместимые hints/ECH/ALPN |
| [RFC 9849](https://www.rfc-editor.org/rfc/rfc9849.html) | ECH скрывает внутренний ClientHello; внешнее имя не доказывает inner hostname, GREASE использует похожую форму |
| [RFC 9001](https://www.rfc-editor.org/rfc/rfc9001.html) | QUIC Initial допускает ограниченный анализ по публично выводимым Initial keys; это отдельный parser, не TCP observer |
| [RFC 9114](https://www.rfc-editor.org/rfc/rfc9114.html) | HTTP/3 использует QUIC; fallback после UDP-проблем нужно проверять у конкретного клиента |
| [RFC 8305](https://www.rfc-editor.org/rfc/rfc8305.html) | Планировать семейства адресов и соединения с учётом Happy Eyeballs, без глобального подавления AAAA |

## Приоритет улучшений

1. **Высокий:** воспроизводимый транспорт и тестовый APK, генерирующий настоящий выбранный трафик через TUN. Это снимает зависимость от opaque бинарников и даёт проверяемую основу.
2. **Высокий:** DNS broker UDP/TCP в рабочем пути. Он расширяет область восстановления на ошибки до TLS; это не доказанное исправление ECH-сбоя LinkedIn.
3. **Высокий:** flow context, сетевые поколения и уровни достоверности. Это позволяет отличить состояние туннеля от успеха приложения и не смешивать результаты разных сетей.
4. **Высокий:** короткий подбор на сбое, локальная память успешного метода и отрицательный backoff. Не повторять полный перебор при каждом запуске.
5. **Средний:** собственный DoH с ограниченной политикой bootstrap; поддержка IPv6/NAT64. Нужны протокольные испытания и замеры батареи.
6. **Средний:** диагностируемый QUIC и отдельно испытанная политика TCP fallback. Глобальный запрет UDP443 исключить.
7. **Средний:** декларативные пакеты известных endpoint с version, expiry и provenance. Они ускоряют проверку, но не заменяют поддержку неизвестных приложений.
8. **Позже:** fake-IP как отдельный опыт для сохранения hostname до TLS. Нужны совместимость с ECH/SVCB, cache, shared CDN и dual stack; mapdns не включать как готовое решение.

## Что исключить из первого выпуска

Не включать ML-подбор без обучающих данных, непрерывное сканирование Интернета, обещание обхода любой блокировки, захват всего устройства при пустом выборе, root/Zapret как обязательный режим, Windows-моды как исполняемый формат, массовые внешние DNS-пробы и собственный облачный backend.

Публичный Smart DNS может направлять трафик через инфраструктуру провайдера. Следовательно, «без собственного сервера» не означает «без внешнего посредника». Проверять описание провайдера, включать только явно разрешённые endpoints и не обещать смену региона для произвольного приложения. Локальный DPI не меняет внешний IP; при отсутствии доступного альтернативного пути IP/GeoIP-блокировка может оставаться неустранимой.

## Локальные первичные материалы

- Android upstream сохранён в Git: `upstream/main`, commit `19cb13c19f87a1fe6339acb65e35da2ab4d14a43`.
- Windows checkout: `E:\maffinet\research\NetFix`, commit `f53d458dc85d329a5f4cf25d3222e3fc48686e66`. Web fetch отдельных закреплённых C# файлов не сработал; выводы об их реализации проверены по локальным исходникам.
- Текущее приложение: `app/src/main/java/io/maffinet/android`, `app/src/main/cpp/automatic_access.c`, tracked patches и `verification`.
- Исходные условия использования: `LICENSE`, `NOTICE`, `docs/UPSTREAM_LICENSE_NOTES.md`, `docs/NATIVE_PROVENANCE.md`. Это сохранённые документы и upstream-заявления; настоящее исследование не устанавливает юридическую силу каждой дополнительной формулировки.
- Физические и CI-записи: `docs/ANDROID36_AND_LINKEDIN.md`, `docs/build-info-0.3.2-alpha.json`, `docs/DEVICE_VALIDATION.md`. Их происхождение и ограничение описаны в [аудите](AUDIT.md).

Следующая сессия перепроверяет ревизии, доступность исходников и спорные API-контракты. Изменение внешнего README не должно автоматически менять утверждённые требования Maffinet.

## Проверка P00 и новые уточнения

4 октября 2026 года повторно открыты Android VPN/owner/protect/bind API и
RFC2308/8484/9460/9849/9001/9114/8305. Добавлены
[DNS over TCP RFC7766](https://www.rfc-editor.org/rfc/rfc7766.html),
[EDNS RFC6891](https://www.rfc-editor.org/rfc/rfc6891.html),
[DNS64 RFC6147](https://www.rfc-editor.org/rfc/rfc6147.html) и
[Android radio/update guidance](https://developer.android.com/develop/connectivity/minimize-effect-regular-updates).
Это источники protocol contracts, не свидетельства работы нового broker.

HEV первый candidate pin:
[`4d6c334dbfb68a79d1970c2744e62d09f71df12f`, tag2.14.4](https://github.com/heiher/hev-socks5-tunnel/commit/4d6c334dbfb68a79d1970c2744e62d09f71df12f).
Прочитаны [JNI](https://raw.githubusercontent.com/heiher/hev-socks5-tunnel/2.14.4/src/hev-jni.c)
и [tunnel](https://raw.githubusercontent.com/heiher/hev-socks5-tunnel/2.14.4/src/hev-socks5-tunnel.c):
узкий bridge и callbacks до SOCKS доступны как source. Core gitlink
[`4be2e621813ba0315cfacd995bf501bde91d6996`](https://github.com/heiher/hev-socks5-core/tree/4be2e621813ba0315cfacd995bf501bde91d6996).
Остальные dependency hashes и runtime proof — P01.

[Firestack n2 README](https://raw.githubusercontent.com/celzero/firestack/n2/README.md)
подтверждает Android/gVisor и нестабильный API. Проверена
[MPL2 LICENSE](https://raw.githubusercontent.com/celzero/firestack/main/LICENSE).
Это исследовательский branch, не production pin. Прежний
[Outline standalone README](https://raw.githubusercontent.com/OutlineFoundation/outline-go-tun2socks/master/README.md)
указывает прекращение сопровождения и перенос в outline-apps/Intra: больше не
рассматривать его как поддерживаемый самостоятельный кандидат.

Rethink/Intra/PCAPdroid descriptions повторно просмотрены на первичных repos;
immutable pins для копирования их кода не выбраны, код не импортирован.
API/tag fetch и некоторые HEV session/raw pages вернули cache/access errors;
полный source checkout в P00 не заявлен. Facts/hypotheses/prototypes/cost/acceptance
каждого направления — [research.md](research.md), выбор — [ADR-001](decisions/001-transport-prototype.md).
