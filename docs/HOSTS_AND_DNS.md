# Hosts и DNS

Проверено 4 октября 2026 года. Сравнение с Windows: [NetFix v1.2.0](NETFIX_COMPARISON.md).

## Доменная база

General расширен с 8 до **130 уникальных доменов**. Первые восемь доменов и старые
`{list:youtube}`, `{list:instagram}`, `{list:linkedin}` сохранены. Встроенные категории:

| Категория / ID | Доменов | Примеры |
|---|---:|---|
| Видео и музыка / `video` | 28 | YouTube API/CDN, Twitch, Spotify, Tidal, Deezer |
| Социальные сети / `social` | 12 | Instagram, LinkedIn, Facebook, X, TikTok |
| Discord / `discord` | 24 | Основной сайт, приглашения, CDN, вложения и активности |
| Telegram / `telegram` | 8 | Веб-сайты, короткие ссылки, Telegraph, CDN |
| Нейросети и перевод / `ai` | 27 | ChatGPT, Claude, Gemini/API, Copilot, Grok, ElevenLabs, DeepL |
| Разработка и работа / `development` | 13 | GitHub/API/CDN, Copilot, JetBrains, Notion, Linear, Windsurf |
| Игры и Xbox / `gaming` | 8 | Xbox Live и Supercell |
| DNS и сетевые сервисы / `dns` | 10 | Публичные зашифрованные DNS endpoint и ECH-сервисы |

Категории служат для просмотра и подстановки `{list:ID}` в совместимых стратегиях.
General включает их все; выбор приложений и режим Telegram этот список не меняют.
Включённый User дополняет General с удалением дубликатов. Родительский домен в
ByeDPI также покрывает поддомены, поэтому не нужны сотни `discord-N.discord.gg`
или отдельные записи для каждого CDN-хоста.

Основания выбора имён: [Flowseal list-general](https://github.com/Flowseal/zapret-discord-youtube/blob/865da4f4c3659523bf79bc6edf0446e7d7969614/lists/list-general.txt),
[list-google](https://github.com/Flowseal/zapret-discord-youtube/blob/865da4f4c3659523bf79bc6edf0446e7d7969614/lists/list-google.txt),
[OpenAI network domains](https://help.openai.com/en/articles/9247338-network-recommendations-for-chatgpt-errors-on-web-and-apps),
[dns.malw.link](https://github.com/ImMALWARE/dns.malw.link/blob/bf91eb9f3e971d0943a27fc9ac0a234e6c5fe9db/hosts)
и [GeoHide](https://github.com/Internet-Helper/GeoHideDNS/blob/dd9ed6a37b5a2dbdf82881fda57cccd929107570/hosts/hosts).
Это курируемый набор сетевых имён, а не гарантия полной работоспособности сервиса.
Записи `^host` из Zapret не импортированы автоматически: точное совпадение в Zapret
и суффиксное совпадение в ByeDPI имеют разные границы. DNS-категория использует
сознательно выбранные имена с семантикой ByeDPI; она не включает DoH в приложении.

## Расширение User

Редактор принимает домены, комментарии `#`, IDN, разделители пробел/запятая.
Импорт из файла или по прямой HTTPS-ссылке дополнительно принимает строки
`IP domain aliases`. Из них извлекаются домены; IP-сопоставления не применяются.
Записи блокировки на `0.0.0.0`, loopback, локальные и multicast-адреса исключаются.
Невалидный домен отменяет весь импорт; сохранённый список остаётся прежним.
Импорт объединяет имена с User, а редактирование и сохранение заменяет его.

В интерфейсе есть кнопки заполнения URL для двух источников NetFix Windows:

- [dns.malw.link hosts](https://raw.githubusercontent.com/ImMALWARE/dns.malw.link/refs/heads/master/hosts).
- [GeoHide hosts](https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts).

Загрузка запускается отдельной кнопкой «Загрузить и добавить» и требует остановить
подключение/проверку стратегий. Предельный размер файла/ответа — 2 MiB UTF-8;
таймаут соединения и чтения — 10 секунд, максимум 5 HTTPS-перенаправлений.
Ошибочные HTTP-ответы и HTML-страницы не импортируются. У источников нет фонового
обновления: повторная загрузка добавляет новые имена, а удалённые upstream записи
можно удалить в редакторе User. Изменение доменов инвалидирует результаты проверки
стратегий.

## DNS-каталог

`core/dns/DnsCatalog.kt` — единый источник IP, подписей, режима, описаний и ссылок.
UI и VPN используют этот каталог. Значения старого `custom_dns_preset` разрешаются
по прежним подписям; новые записи используют стабильный ID. Собственный DNS
принимает 1–4 буквальных IPv4-адреса, с проверкой формата и удалением дубликатов.

Выбор DNS по умолчанию открывает **«Обход геоблокировок»**. Здесь 12 профилей:
8 подключений IPv4 DNS в VPN и 4 действия системного Private DNS. Обычные публичные
резолверы и их семейные/защитные варианты вынесены в **«Обычные DNS»**. Стандартный
режим и ввод своих адресов доступны в обоих разделах; переключение раздела само
по себе сохранённый DNS не меняет.

GeoHide теперь применяется непосредственно в VPN, с выбором региона:

| Профиль | Основной и резервный DNS |
|---|---|
| GeoHide: Россия | `193.233.112.67`, `193.233.112.68` |
| GeoHide: Европа | `217.60.245.219`, `217.60.245.233` |
| GeoHide: США | `192.255.159.240`, `192.255.159.241` |

Адреса проверены в официальном [servers.json](https://geohide.ru/static/metadata/servers.json)
и региональных [RU](https://geohide.ru/dnsmasq/dns/ru/domains.txt),
[EU](https://geohide.ru/dnsmasq/dns/eu/domains.txt),
[US](https://geohide.ru/dnsmasq/dns/us/domains.txt) конфигурациях. Скрипт сайта использует
эти адреса для секции DNS-over-UDP; выбраны первые два сервера каждого региона с
`traffic=unlimit`. Это опубликованные resolver-адреса, не результат разрешения A
сайта. Версия данных сайта — 02.10.2026 03:13:27. Старый ID `geohide` и подпись
`Geohide DNS (dns.geohide.ru)` выбирают профиль Россия.

| Провайдер | Профили обычного DNS в VPN | Первичный источник адресов |
|---|---|---|
| GeoHide | Россия, Европа, США | [Официальная конфигурация](https://geohide.ru/static/metadata/servers.json) |
| Xbox DNS | Основной и отдельный Supercell | [Xbox](https://xbox-dns.ru/), [Supercell](https://supercell.xbox-dns.ru/) |
| Comss.one | Основной | [Настройки COMSS](https://www.comss.ru/page.php?id=7315) |
| dns.malw.link | Основной | [Настройки malw](https://info.dns.malw.link/) |
| Bezmezhau | Smart DNS, преимущественно для Беларуси | [Настройки](https://bezmezhau.com/ru/setup) |
| Cloudflare | Обычный, защита, семейный | [Адреса Cloudflare](https://developers.cloudflare.com/1.1.1.1/ip-addresses/) |
| Google | Public DNS | [Google Public DNS](https://developers.google.com/speed/public-dns/docs/using) |
| AdGuard | Реклама/трекеры, без фильтрации, семейный | [Public DNS](https://adguard-dns.io/en/public-dns.html) |
| Quad9 | Защита, без фильтрации, защита + ECS | [Quad9 Services](https://docs.quad9.net/services/) |
| Яндекс | Базовый, безопасный, семейный | [Яндекс DNS](https://dns.yandex.ru/) |
| Control D | Без фильтрации, защита, реклама/трекеры, семейный | [Free DNS](https://docs.controld.com/docs/free-dns) |
| CleanBrowsing | Защита и семейный | [Фильтры](https://cleanbrowsing.org/filters) |

Обычный DNS в VPN использует `VpnService.Builder.addDnsServer` и не шифрует
DNS-запросы. Он относится к выбранным приложениям, а отдельный Telegram-прокси
этой настройкой не управляется. Smart DNS отличается от обычного публичного
резолвера: он может возвращать адреса региональных прокси. Адреса таких провайдеров
меняются, поэтому ссылки на первичные инструкции сохранены в каталоге.

Для [Null’s Proxy](https://nullsproxy.com/), Cloudflare Gateway-профиля malw,
[DNS-AI](https://dns-ai.ru/) и [ASTRACAT](https://github.com/ASTRACAT2022/host-DNS)
есть
отдельное действие Private DNS: копирование hostname и открытие системных настроек
Android 9+. Пользователь применяет hostname в системе; это настройка устройства,
а не встроенный DoT/DoH-транспорт Maffinet. Старые записи NullsProxy больше не
подставляют IP Xbox. DNS-AI закрывает обычный порт 53; ASTRACAT публикует настройку
DoT `dns.astracat.network`. Их IP не добавляются как обычные VPN DNS.

## Что ещё отличается от десктопа

Нет локального обработчика DNS с правилами A/AAAA из hosts, фонового обновления
источников и менеджера ZIP-модов. UDP в текущем выборочном режиме проходит без
desync; расширение списка игр не добавляет поддержку обхода UDP. DoH внутри VPN
потребует отдельного транспорта. Реальную доступность Smart DNS, авторизацию,
голос, медиа и поведение Private DNS нужно проверять на Android-устройстве.

## Проверки этого изменения

SDK-независимый Gradle-прогон production моделей и тестов: **60 прошли, 0 ошибок**.
Шесть native contract тестов пропущены, поскольку на этом Windows-хосте нет их
скомпилированного Linux fixture. Проверены также группы выбора DNS и совместимость
прежнего выбора GeoHide с новым прямым VPN-профилем.
Импорт обоих полных source snapshot отдельно проверен production parser: malw —
199 доменов, GeoHide — 1003, в обоих случаях 0 ошибок. Синтаксический разбор 84
Kotlin-файлов production/UI/instrumentation не обнаружил ошибок.

Локальный `:app:assembleDebug` остановился на `SDK location not found`. Сборка
перенесена на настроенный GitHub runner с существующей лицензией SDK и stable NDK
r29. Для исходников `9efbce486c50c34a3b2a6eed335aa6f7be7f0018`
[CI run 37157372931](https://github.com/DrDeiss/Maffinet/actions/runs/37157372931)
успешно собрал debug, unsigned release и test APK: **66 JVM/native, 65 Android JVM
и 22 instrumentation теста API29**, без ошибок и пропусков. Lint: 0 ошибок,
136 предупреждений, 5 hints. Получены 19 скриншотов эмулятора. Тест редактора
выбирает User-поле по отдельному tag, поскольку рядом теперь есть поле URL.

Готовый подписанный debug APK: `build/apk/Maffinet-smartdns-debug.apk`, 30 528 787
байт, Android 8/API26+. APK Signature Scheme v2 проверена через ApkVerifier;
движки для arm64-v8a, armeabi-v7a, x86 и x86_64 присутствуют. SHA-256:
`ce01c19fd674054b9e95c4a87b71d1d5dec4a814e206a39103573f561113088e`.
Сведения о сборке сохранены рядом в `build-info.json`; CI-артефакт
`maffinet-debug-apk` (ID `11285608775`) хранится семь дней. Реальную доступность
Smart DNS и поведение выбранных приложений следует проверить на устройстве.
