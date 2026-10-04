# Исследование и выбор P00

Проверено 4 октября 2026 года. Локальный baseline:
`2d63fc3d6e2e04d2ba8a088f234e19aa2e4e75cd`. Facts и гипотезы разделены:
успешный исходный JVM suite не подтверждает новый TUN, батарею или обход.
Общая библиография — [SOURCES.md](SOURCES.md).

## Транспорт

| Кандидат и revision | Установленный факт | Цена для Maffinet | Решение |
|---|---|---|---|
| Upstream HEV 2.14.4, `4d6c334dbfb68a79d1970c2744e62d09f71df12f` | C/lwIP, TCP/UDP и dual stack; Android JNI принимает config+fd, без TV Boolean | Собственный bridge, original tuple hooks, socket factory, dependency/license pins, STOP/TV/16 KiB тесты | Первый кандидат P01 |
| Firestack, branch `n2`, README просмотрен 04.10; immutable pin ещё не выбран | gVisor/netstack, Android TCP/UDP/DNS; MPL-2.0; API авторы считают нестабильным | Go runtime, gomobile, больший scope и интеграция; размер/RSS пока неизвестны | Запасной кандидат при неудовлетворительном HEV hook |
| OutlineFoundation/outline-go-tun2socks, master README просмотрен 04.10 | Репозиторий больше не сопровождается, исходники перенесены в outline-apps/Intra | Нельзя принять старый standalone repo как поддерживаемую основу | Использовать современный source location только для сравнения в P01 |
| Нынешний custom HEV `c26333ae…` | Исходник недоступен; проверен JNI rebind восьми shipped бинарников | Нельзя доказать source build из его gitlink | Только восстановимый legacy baseline |

Подтверждения: [HEV release/pin](https://github.com/heiher/hev-socks5-tunnel/commit/4d6c334dbfb68a79d1970c2744e62d09f71df12f),
[JNI 2.14.4](https://raw.githubusercontent.com/heiher/hev-socks5-tunnel/2.14.4/src/hev-jni.c),
[tunnel source](https://raw.githubusercontent.com/heiher/hev-socks5-tunnel/2.14.4/src/hev-socks5-tunnel.c),
[Firestack n2](https://raw.githubusercontent.com/celzero/firestack/n2/README.md),
[Firestack LICENSE](https://raw.githubusercontent.com/celzero/firestack/main/LICENSE),
[Outline migration notice](https://raw.githubusercontent.com/OutlineFoundation/outline-go-tun2socks/master/README.md).

В HEV `tcp_accept_handler` получает lwIP pcb до создания SOCKS session;
`udp_recv_handler` создаёт session с pcb до forwarding. Это обоснованные
кандидаты для копирования original tuple, а не готовый проверенный UID hook.
`src/core` закреплён на `4be2e621813ba0315cfacd995bf501bde91d6996`.
Остальные gitlinks перечислены в [.gitmodules](https://raw.githubusercontent.com/heiher/hev-socks5-tunnel/2.14.4/.gitmodules):
lwIP, hev-task-system, yaml. Их полный hash и transitive licenses требуется
сохранить при fetch P01. Source retrieval отдельных session-файлов и GitHub
API tag через web tool вернул cache/access errors; из этого не выводится
отсутствие исходников. Полного source checkout/сборки HEV в P00 нет.

Порядок P01: pin source/dependencies → отдельный test adapter и helper →
original tuples → TCP/UDP/IPv6/stop/restart измерения → окончательный ADR.
HEV остаётся кандидатом, пока эти gates не пройдены. Собственный TCP/IP stack
с нуля не выбирается. Подробное решение — [ADR-001](decisions/001-transport-prototype.md).

## Факты, гипотезы, прототипы

| Область | Факт / источник | Гипотеза и минимальный опыт | Цена и acceptance |
|---|---|---|---|
| DNS до TLS | Builder задаёт resolver; текущий observer ждёт SNI. [Android VPN](https://developer.android.com/develop/connectivity/vpn), локальные createBuilder/parseImport | Перехват видимого UDP/TCP53 до SOCKS вернёт usable ответ при разрешённом resolver recovery | P04: виртуальный DNS destination, wire-preserving ответы с original source address; NXDOMAIN/SERVFAIL/timeout/TC отдельно |
| DNS wire | [RFC2308](https://www.rfc-editor.org/rfc/rfc2308.html), [RFC7766](https://www.rfc-editor.org/rfc/rfc7766.html), [RFC6891](https://www.rfc-editor.org/rfc/rfc6891.html) | Forward unknown RR без пересборки; кэш вначале выключен, затем bounded TTL cache | TCP framing/несколько сообщений, CNAME/EDNS/negative SOA TTL; malformed не обновляет cache; DNS IDs matching плюс generation |
| Собственный DoH | [RFC8484](https://www.rfc-editor.org/rfc/rfc8484.html) описывает wire DNS через HTTPS | Отдельный resolver transport исправляет часть ошибок DNS, а не все блокировки | P06: bootstrap IP вне VPN, исходный TLS hostname, сертификат, явный downgrade; server/account не нужны |
| Скрытый DNS | Зашифрованные app-owned transports не отдают QNAME passive observer | Метаданные IP/flow остаются доступны, hostname Unknown | P04/P07: DoH/DoT/DoQ/Private DNS fixtures; не перенаправлять 853 как DNS53, не менять системную настройку |
| ECH/GREASE | [RFC9849](https://www.rfc-editor.org/rfc/rfc9849.html); `0xfe0d` есть и у GREASE, outer не доказывает inner | DNS hints могут помочь только при достоверном контексте; простого extension-based различения нет | P07: два реальных клиента + stale/cache/shared-CDN cases; никакого снятия TLS защиты или exact-host по outer |
| UID/tuple | [getConnectionOwnerUid API29](https://developer.android.com/reference/android/net/ConnectivityManager#getConnectionOwnerUid(int,%20java.net.InetSocketAddress,%20java.net.InetSocketAddress)): TCP/UDP, INVALID_UID, SecurityException | Копировать исходные адреса/порты до SOCKS и query активного VPN | P01 tuple proof, P03 UID acceptance на двух helper UIDs; API26–28 Unknown; shared UID группа, DNS attribution отдельная |
| Network/socket | [protect](https://developer.android.com/reference/android/net/VpnService#protect(int)), [bindSocket](https://developer.android.com/reference/android/net/Network#bindSocket(java.io.FileDescriptor)) | Единая factory защищает/привязывает каждый external fd до connect; generation отменяет результат | P01 hook, P03 Wi-Fi/mobile/loss/revoke; ошибка protect/bind запрещает connect; sockets закрываются на STOP |
| Первый запрос | Нынешний маршрут обычно влияет на следующий flow; bounded native retry сохраняет только Hello до peer bytes/application data | Сначала безопасный base forwarding; P05 отдельно проверяет handshake recovery и next-flow learning | Прототип: silent/reset/early peer/TLS app-data/0-RTT; отсутствие replay application bytes; не обещать исправление первого NXDOMAIN поздним TLS |
| QUIC | [RFC9001](https://www.rfc-editor.org/rfc/rfc9001.html), [RFC9114](https://www.rfc-editor.org/rfc/rfc9114.html) | Unchanged UDP forwarding первым; parser Initial отдельно, fallback зависит от клиента | P07: HTTP/3-only и dual protocol клиент, non443 UDP, reorder/loss/MTU; TCP success не UDP evidence |
| IPv6/NAT64 | [RFC8305](https://www.rfc-editor.org/rfc/rfc8305.html), [RFC6147](https://www.rfc-editor.org/rfc/rfc6147.html) | Family-aware outcomes и network-scoped DNS64 сохранят доступ IPv6-only | P01 IPv6 transport, P04 AAAA, P07 NAT64 lab; проверить address synthesis и срок network context; не suppress AAAA |
| Energy/bounds | [Android radio/update guidance](https://developer.android.com/develop/connectivity/minimize-effect-regular-updates); старый controller poll 500ms | Event-driven network updates и работа только на сбое уменьшат лишние wakeups | P03/P05: idle/screen-off 30min, CPU/RSS/radio bytes; stop полностью закрывает workers; см. SLO |
| TLS identity | Текущий GenericHttpsProbe использует original hostname и проверку peer; TCP connected недостаточно | Сохранить identity contract у нового endpoint/probe adapter | P05/P06: wrong-name/untrusted cert rejection, pinning у реального приложения; приложение само проверяет TLS без Maffinet MITM |

NXDOMAIN не доказывает цензуру. Private/split/filtering политика получает
приоритет; default MVP использует resolver физической сети, внешний recovery
только разрешённой политикой. Согласованность A/AAAA/CNAME/HTTPS/SVCB и hints
задаётся одним provenance/expiry; не смешивать ответы разных providers.
Основание endpoint records — [RFC9460](https://www.rfc-editor.org/rfc/rfc9460.html).

## Идеи внешних проектов

[Rethink](https://github.com/celzero/rethink-app) полезен как пример связки
app policy и единого сетевого пути; [Firestack](https://github.com/celzero/firestack)
показывает доступный альтернативный stack с DNS. Не переносить всю
firewall/WireGuard-модель. Immutable pin этих приложений не выбран: изучено
описание, их код в новый слой не скопирован.

[Intra README](https://raw.githubusercontent.com/Jigsaw-Code/Intra/master/README.md)
подтверждает DoH и наблюдения запросов/производительности. Берём идею реального
resolver в data path; не приписываем ему способность исправить GeoIP/DPI.
[PCAPdroid README](https://raw.githubusercontent.com/emanuele-f/PCAPdroid/master/README.md)
подтверждает локальный захват без root через VPN и connection metadata.
Берём ограниченные metadata; не включаем payload/PCAP/MITM или второй VPN.
Для заимствования кода понадобятся отдельные source pins и license audit.

## Неподтверждённые вопросы и следующий опыт

Original tuple orientation, protect/bind внутри HEV/SOCKS/ByeDPI, fd ownership,
TV Boolean семантика, 16 KiB runtime и воспроизводимая Rust сборка остаются
gates P01/P08/P09. Размер/RSS/CPU кандидатов не измерен. Нет физического
устройства или provisioned Android/Linux host в этой сессии: SDK отсутствует,
WSL не установлен, adb не найден. P01 не может закрыть TUN acceptance только
документами или host-only socket tests.
