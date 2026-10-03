# Maffinet Android и NetFix Windows

Дата проверки: 4 октября 2026 года. Таблица фиксирует состояние Android **до расширения Hosts и DNS в этом изменении**.

## Основание сравнения

Последний опубликованный [NetFix Windows — v1.2.0](https://github.com/rupleide/NetFix/releases/tag/v1.2.0).
Живой `git ls-remote` подтвердил одинаковый commit у `HEAD`, `main` и тега `v1.2.0`:
[`f53d458dc85d329a5f4cf25d3222e3fc48686e66`](https://github.com/rupleide/NetFix/commit/f53d458dc85d329a5f4cf25d3222e3fc48686e66),
20 сентября 2026 года. Исходники локального исследовательского checkout совпадают с этим commit.
Данные DNS-провайдеров следует проверять по их документации отдельно: запись в каталоге NetFix сама по себе не подтверждает актуальность IP или поддержку шифрованного транспорта.

## Что уже есть и чего не хватает

| Возможность | NetFix Windows v1.2.0 | Maffinet Android до изменения |
|---|---|---|
| Обход DPI | Zapret/WinDivert, BAT-конфигурации, GameFilter, IPset | ByeDPI через Android VpnService и HEV tun2socks; совместимость с BAT/WinDivert отсутствует |
| Выбор приложений | Общий перехват Windows-трафика; анализ соединений по PID | Явный список установленных Android-приложений; Maffinet исключается из собственного VPN |
| Telegram отдельно | TgWsProxy как отдельная Windows-служба | Отдельный локальный Rust MTProto-прокси, независимый от VPN |
| Подбор стратегии | Проверка конфигураций Zapret, избранное, переименование, удаление | Проверка ByeDPI через SOCKS, сохранение результатов, избранное; проверяемые HTTP/TLS-адреса настраиваются отдельно |
| Листы доменов | Отдельные активируемые моды, загрузка по URL, текстовый редактор, импорт/экспорт ZIP | Фиксированный General из 8 доменов и включаемое User-дополнение; редактирование, импорт/экспорт текстового файла |
| Настоящий hosts | IP → hostname в системном Windows hosts; применение нескольких модов, редактор, пересоздание файла | Только hostname-фильтр ByeDPI `-H`; IP → hostname не применяется к DNS |
| Автоисточники hosts | `dns.malw.link` и GeoHide; скачивание при открытии раздела/применении, дата источника | Нет сетевых источников и автоматического обновления |
| База для пересоздания hosts | 31 Telegram + 5 GitHub + 200 Discord Media hostname/IP записей | 4 YouTube + 2 Instagram + 2 LinkedIn hostname-фильтра |
| Встроенный DNS-каталог | 5 провайдеров: Xbox-DNS, Cloudflare, Google, Yandex, AdGuard; плюс DHCP | 8 подписанных вариантов плюс стандартный; Xbox, Supercell, NullsProxy и GeoHide ошибочно используют одинаковые IP |
| Пользовательский DNS | Имя, основной/резервный IP, произвольный DoH URL | Нет |
| Шифрованный DNS | Настройка системного Windows DoH, если ОС поддерживает командлет | Пресеты вызывают `VpnService.Builder.addDnsServer`; собственного DoH/DoT-клиента нет |
| Область действия DNS | Активный физический сетевой интерфейс Windows | Выбранные приложения в VPN; отдельный Telegram-прокси этим не настраивается |
| Диагностика | DC1–DC5 Telegram, скорость/пинг, PID/socket/adapter/DNS цепочка, ETW | Проверка стратегий, журналы и отладочный экспорт; аналога Windows ETW/PID цепочки нет |
| Восстановление | Ремонт Windows-служб, античит watchdog, резервные компоненты | Boot/foreground-service/watchdog, плитка быстрого подключения; требуется отдельная проверка поведения на физических устройствах |

Источники реализации: [DNS-каталог и применение](https://github.com/rupleide/NetFix/blob/f53d458dc85d329a5f4cf25d3222e3fc48686e66/Services/DnsManagerService.cs),
[автоисточники](https://github.com/rupleide/NetFix/blob/f53d458dc85d329a5f4cf25d3222e3fc48686e66/Services/Mods/AutoHostsModService.cs),
[применение list/hosts модов](https://github.com/rupleide/NetFix/blob/f53d458dc85d329a5f4cf25d3222e3fc48686e66/Services/Mods/ModActivator.cs),
[загрузка листа по URL](https://github.com/rupleide/NetFix/blob/f53d458dc85d329a5f4cf25d3222e3fc48686e66/Services/Mods/DomainListImporter.cs),
[пользовательский интерфейс и пересоздание hosts](https://github.com/rupleide/NetFix/blob/f53d458dc85d329a5f4cf25d3222e3fc48686e66/MainWindow.xaml.cs).
Android: `BuiltInDomainLists.kt`, `DomainParser.kt`, `DomainListRepository.kt`, `ByeDpiArgumentCompiler.kt`, `ByeDpiVpnService.kt`, `DnsPresets.kt` и [архитектура](ARCHITECTURE.md).

## Почему расширение Hosts требует двух разных механизмов

Android Hosts сейчас определяет, к каким TLS/HTTP hostname применять DPI-стратегию. Запись `claude.ai` в таком фильтре не направляет запрос на сервер GeoHide и не меняет исходящий внешний IP.

Windows hosts-моды содержат пары IP/hostname. Например, Smart DNS и SNI Proxy отвечают IP специального прокси: сервис видит IP этого прокси. [dns.malw.link описывает этот механизм](https://github.com/ImMALWARE/dns.malw.link#как-это-работает), а также ограничение: такой DNS сам по себе не устраняет SNI-блокировку провайдера.
Следовательно, извлечение hostname из hosts-файла полезно для расширения фильтра, но не переносит функцию DNS-подмены. Полная совместимость требует отдельного локального DNS-обработчика с A/AAAA-ответами и правилами конфликтов, либо выбора соответствующего Smart DNS.

Проверенные snapshot исходников, используемых авто-модами Windows:

| Источник | Commit | Строки IP/hostname без комментариев | Уникальные hostname без блокирующих/loopback адресов |
|---|---|---:|---:|
| [dns.malw.link hosts](https://github.com/ImMALWARE/dns.malw.link/blob/bf91eb9f3e971d0943a27fc9ac0a234e6c5fe9db/hosts) | `bf91eb9f3e971d0943a27fc9ac0a234e6c5fe9db`, 29.08.2026 | 26 777 | 199 |
| [GeoHide hosts](https://github.com/Internet-Helper/GeoHideDNS/blob/dd9ed6a37b5a2dbdf82881fda57cccd929107570/hosts/hosts) | `dd9ed6a37b5a2dbdf82881fda57cccd929107570`, 29.09.2026 | 3 968 | 1 003 |

Счётчики воспроизводимо получены из файлов этих commit: пустые строки/комментарии исключены; для последнего столбца исключены `0.0.0.0`, `127.0.0.1`, `::`, `::1`, затем hostname дедуплицированы. Количество строк не равно количеству сервисов или правилам фильтра: один hostname может иметь несколько IP, а большая часть malw-файла — блокирующие записи.

GeoHide подтверждает hostname для Anthropic/Claude, Gemini/Bard, Copilot, Grok, JetBrains, Notion, отдельных Xbox Live и Supercell endpoint. Это источник выбора имён, а не полный сетевой allowlist приложений. Для GitHub/GitLab/Hugging Face, Perplexity, Steam, PlayStation и Epic полезно дополнительно сверять собственную документацию сервисов. При расширении базы не следует добавлять каждый домен из многотысячного hosts-файла по умолчанию.

Листы Zapret тоже отличаются по семантике: [Flowseal list-general](https://github.com/Flowseal/zapret-discord-youtube/blob/main/lists/list-general.txt) содержит записи с `^` для точного совпадения. Удаление маркера и перенос в суффиксный hostname-фильтр может расширить область действия. `list-google.txt` также содержит адреса Google, которые шире задачи разблокировки YouTube. Эти правила нужно адаптировать к ByeDPI осознанно.

## Что изменено в Maffinet после сравнения

| Возможность | До изменения | После изменения |
|---|---|---|
| General | 8 доменов | 130 дедуплицированных доменов в 8 разделах: видео/музыка, социальные сети, Discord, Telegram, ИИ/перевод, разработка/работа, игры/Xbox, DNS |
| User | Обычный текстовый domain-list | Сохранение строгого domain-list; импорт обычных листов и hostname из hosts-файлов с IPv4/IPv6, несколькими именами и комментариями |
| Источники User | Выбор локального файла | Файл или прямая HTTPS-ссылка; быстрый выбор URL malw/GeoHide, ручная загрузка до 2 МБ, проверка UTF-8 и ошибок перед записью |
| Блокирующие записи | Не было parser для hosts | Блокирующие, loopback, multicast и локальные IP/hostname исключаются из списка обхода; IP-подмена не применяется |
| DNS | 8 вариантов, часть адресов повторялась ошибочно | Раздел обхода геоблокировок по умолчанию: GeoHide RU/EU/US, Xbox/Supercell, COMSS, malw, Bezmezhau; системные Private DNS для Null’s Proxy, malw Gateway, DNS-AI, ASTRACAT. Всего 27 IPv4-профилей и 4 системных действия, обычные резолверы в отдельном разделе; свой IPv4 DNS |
| Хранение DNS | Зависимость runtime от отображаемой подписи | Стабильный ID; старые 9 сохранённых подписей остаются совместимыми; свой DNS — от 1 до 4 проверенных IPv4 адресов |
| Область применения DNS | Неочевидное различие транспорта | В UI указано: IPv4 в VPN без шифрования; Private DNS настраивается вручную в Android для всего устройства; VPN-выбор при открытии внешней настройки сохраняется |

Исходные 8 доменов и имена старых `{list:youtube/instagram/linkedin}` стратегий сохранены. Разделы служат для просмотра/совместимых placeholder; базовый General остаётся единым фиксированным списком. Подробные источники выбора доменов и DNS: [Hosts и DNS](HOSTS_AND_DNS.md).

Дополнительная проверка импортера выполнена на полных snapshot, приведённых выше: production `DomainParser.parseImport` принял malw с **199 доменами и 0 ошибками**, GeoHide с **1 003 доменами и 0 ошибками**. Это проверка извлечения/валидации hostname, а не сетевой доступности сервисов.

## Что остаётся для полного паритета

1. Для собственного DoH нужна реализация транспорта; наличия ссылки или hostname в каталоге недостаточно.
2. Если нужны автоисточники как в Windows, хранить snapshot, дату/commit, лимит размера, результат валидации и последнюю рабочую версию; обновлять через понятное действие или выбранную пользователем политику.
3. Для IP → hostname обеспечить отдельный DNS-движок, обработку IPv4/IPv6 и проверку на устройстве; системный Android hosts без root не является доступной точкой применения.
4. Для переносимых модов требуется собственная схема пакета Android. Windows BAT и ZIP моды нельзя напрямую исполнять как ByeDPI-стратегии.
5. Проверить доступность/изменение DNS и загрузку сервисов на физическом Android-устройстве. Компиляция и hostname-фильтр не подтверждают работоспособность Smart DNS, медиа или авторизации.
