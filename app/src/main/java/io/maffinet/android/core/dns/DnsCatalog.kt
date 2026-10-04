package io.maffinet.android.core.dns

enum class DnsMode { SYSTEM, VPN_IPV4, PRIVATE_DNS, PROVIDER_SETTINGS }
enum class DnsPurpose { GEO_ACCESS, GENERAL }

data class DnsPreset(
    val id: String,
    val label: String,
    val description: String,
    val mode: DnsMode,
    val ipv4: List<String> = emptyList(),
    val privateDnsHostname: String? = null,
    val sourceUrl: String? = null,
    val legacyLabels: Set<String> = emptySet(),
    val purpose: DnsPurpose = DnsPurpose.GENERAL,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank())
        require(if (mode == DnsMode.VPN_IPV4) ipv4.isNotEmpty() else ipv4.isEmpty())
        require(ipv4.all(DnsCatalog::isUnicastIpv4))
        require(mode != DnsMode.PRIVATE_DNS || !privateDnsHostname.isNullOrBlank())
    }

    val detail: String get() = when (mode) {
        DnsMode.VPN_IPV4 -> "$description\n${ipv4.joinToString(" · ")}"
        DnsMode.PRIVATE_DNS -> "VPN: DNS сети · Private DNS в Android 9+\n$privateDnsHostname"
        DnsMode.PROVIDER_SETTINGS -> "VPN: DNS сети · откройте настройки поставщика"
        DnsMode.SYSTEM -> description
    }
}

/** Published resolver addresses, checked against provider documentation on 2026-10-04.
 * The VPN configures plain IPv4 DNS. A provider's DoH/DoT support does not encrypt it.
 */
object DnsCatalog {
    const val SYSTEM_ID = "system"
    const val CUSTOM_ID = "custom"
    private const val CUSTOM_PREFIX = "custom:"

    private fun vpn(id: String, label: String, description: String, first: String, second: String,
                    source: String, vararg aliases: String) = DnsPreset(
        id, label, description, DnsMode.VPN_IPV4, listOf(first, second),
        sourceUrl = source, legacyLabels = aliases.toSet(),
    )

    private fun geo(id: String, label: String, description: String, first: String, second: String,
                    source: String, vararg aliases: String) =
        vpn(id, label, description, first, second, source, *aliases).copy(purpose = DnsPurpose.GEO_ACCESS)

    val presets: List<DnsPreset> = listOf(
        DnsPreset(SYSTEM_ID, "Стандартный (Отключено)", "Использовать DNS текущей сети", DnsMode.SYSTEM),
        geo("geohide", "GeoHide: Россия", "DNS в России, доступ через прокси в Европе и США", "193.233.112.67", "193.233.112.68",
            "https://geohide.ru/static/metadata/servers.json", "Geohide DNS (dns.geohide.ru)", "GeoHide DNS"),
        geo("geohide-eu", "GeoHide: Европа", "DNS и прокси в Европе", "217.60.245.219", "217.60.245.233",
            "https://geohide.ru/static/metadata/servers.json"),
        geo("geohide-us", "GeoHide: США", "DNS и прокси в США", "192.255.159.240", "192.255.159.241",
            "https://geohide.ru/static/metadata/servers.json"),
        geo("xbox", "Xbox DNS", "Smart DNS для сервисов с ограничением региона", "111.88.96.54", "111.88.96.55",
            "https://xbox-dns.ru/", "Xbox DNS (xbox-dns.ru / ChatGPT / Brawl)"),
        geo("xbox-supercell", "Supercell Xbox DNS", "Профиль Xbox DNS для игр Supercell", "111.88.96.50", "111.88.96.51",
            "https://supercell.xbox-dns.ru/", "Supercell Xbox DNS (supercell.xbox-dns.ru)"),
        geo("comss", "Comss.one DNS", "Доступ к ИИ, фильтрация рекламы и опасных сайтов", "83.220.169.155", "212.109.195.93",
            "https://www.comss.ru/page.php?id=7315", "Comss.one DNS (dns.comss.one)"),
        geo("malw", "dns.malw.link", "Доступ к сервисам с ограничением региона и фильтрация", "95.216.204.218", "80.253.249.40",
            "https://info.dns.malw.link/"),
        geo("bezmezhau", "Bezmezhau Smart DNS", "Геодоступ к сервисам; ориентирован на Беларусь", "143.20.64.55", "91.108.243.78",
            "https://bezmezhau.com/ru/setup"),
        vpn("cloudflare", "Cloudflare Secure DNS", "Без фильтрации · DNS в VPN без шифрования", "1.1.1.1", "1.0.0.1",
            "https://developers.cloudflare.com/1.1.1.1/ip-addresses/"),
        vpn("cloudflare-security", "Cloudflare: защита", "Блокировка вредоносных сайтов и фишинга", "1.1.1.2", "1.0.0.2",
            "https://developers.cloudflare.com/1.1.1.1/ip-addresses/"),
        vpn("cloudflare-family", "Cloudflare: семейный", "Защита и блокировка сайтов для взрослых", "1.1.1.3", "1.0.0.3",
            "https://developers.cloudflare.com/1.1.1.1/ip-addresses/"),
        vpn("google", "Google Public DNS", "Без фильтрации", "8.8.8.8", "8.8.4.4",
            "https://developers.google.com/speed/public-dns/docs/using"),
        vpn("adguard", "AdGuard DNS", "Блокировка рекламы и трекеров", "94.140.14.14", "94.140.15.15",
            "https://adguard-dns.io/en/public-dns.html", "AdGuard DNS (Блокировка рекламы)"),
        vpn("adguard-unfiltered", "AdGuard: без фильтрации", "Без блокировки запросов", "94.140.14.140", "94.140.14.141",
            "https://adguard-dns.io/en/public-dns.html"),
        vpn("adguard-family", "AdGuard: семейный", "Реклама, трекеры, взрослые сайты и безопасный поиск", "94.140.14.15", "94.140.15.16",
            "https://adguard-dns.io/en/public-dns.html"),
        vpn("quad9", "Quad9: защита", "Блокировка опасных сайтов и проверка DNSSEC", "9.9.9.9", "149.112.112.112",
            "https://docs.quad9.net/services/"),
        vpn("quad9-unfiltered", "Quad9: без фильтрации", "Без блокировки опасных сайтов", "9.9.9.10", "149.112.112.10",
            "https://docs.quad9.net/services/"),
        vpn("quad9-ecs", "Quad9: защита + ECS", "Защита и передача подсети для выбора ближайшего CDN", "9.9.9.11", "149.112.112.11",
            "https://docs.quad9.net/services/"),
        vpn("yandex", "Яндекс DNS: базовый", "Без фильтрации", "77.88.8.8", "77.88.8.1", "https://dns.yandex.ru/"),
        vpn("yandex-security", "Яндекс DNS: безопасный", "Блокировка опасных сайтов", "77.88.8.88", "77.88.8.2", "https://dns.yandex.ru/"),
        vpn("yandex-family", "Яндекс DNS: семейный", "Защита и блокировка сайтов для взрослых", "77.88.8.7", "77.88.8.3", "https://dns.yandex.ru/"),
        vpn("controld", "Control D: без фильтрации", "Без блокировки запросов", "76.76.2.0", "76.76.10.0", "https://docs.controld.com/docs/free-dns"),
        vpn("controld-security", "Control D: защита", "Блокировка вредоносных сайтов", "76.76.2.1", "76.76.10.1", "https://docs.controld.com/docs/free-dns"),
        vpn("controld-ads", "Control D: реклама и трекеры", "Фильтрация рекламы и отслеживания", "76.76.2.2", "76.76.10.2", "https://docs.controld.com/docs/free-dns"),
        vpn("controld-family", "Control D: семейный", "Семейная фильтрация", "76.76.2.4", "76.76.10.4", "https://docs.controld.com/docs/free-dns"),
        vpn("cleanbrowsing-security", "CleanBrowsing: защита", "Блокировка фишинга и вредоносных сайтов", "185.228.168.9", "185.228.169.9",
            "https://cleanbrowsing.org/filters"),
        vpn("cleanbrowsing-family", "CleanBrowsing: семейный", "Взрослые сайты, VPN/прокси, смешанный контент; безопасный поиск", "185.228.168.168", "185.228.169.168",
            "https://cleanbrowsing.org/filters"),
        DnsPreset("nullsproxy", "Null’s Proxy DNS", "Игры Supercell · настройка в системе", DnsMode.PRIVATE_DNS,
            privateDnsHostname = "dns.nullsproxy.com", sourceUrl = "https://nullsproxy.com/",
            legacyLabels = setOf("NullsProxy DNS (dns.nullsproxy.com)"), purpose = DnsPurpose.GEO_ACCESS),
        DnsPreset("malw-gateway", "dns.malw.link: Cloudflare Gateway", "Профиль malw · настройка в системе", DnsMode.PRIVATE_DNS,
            privateDnsHostname = "5u35p8m9i7.cloudflare-gateway.com", sourceUrl = "https://info.dns.malw.link/",
            purpose = DnsPurpose.GEO_ACCESS),
        DnsPreset("dns-ai", "DNS-AI", "Геодоступ к нейросетям для России и Беларуси · Private DNS", DnsMode.PRIVATE_DNS,
            privateDnsHostname = "dns.dns-ai.ru", sourceUrl = "https://dns-ai.ru/", purpose = DnsPurpose.GEO_ACCESS),
        DnsPreset("astracat", "ASTRACAT DNS", "Геодоступ к сервисам · Private DNS", DnsMode.PRIVATE_DNS,
            privateDnsHostname = "dns.astracat.network", sourceUrl = "https://github.com/ASTRACAT2022/host-DNS",
            purpose = DnsPurpose.GEO_ACCESS),
    )

    /** Accepts both stable IDs and values saved by previous releases. */
    fun resolve(saved: String?): DnsPreset {
        val value = saved?.trim().orEmpty()
        if (value.startsWith(CUSTOM_PREFIX)) {
            customSelection(value.removePrefix(CUSTOM_PREFIX))?.let { canonical ->
                return DnsPreset(canonical, "Свой IPv4 DNS", "Пользовательские DNS в VPN без шифрования",
                    DnsMode.VPN_IPV4, canonical.removePrefix(CUSTOM_PREFIX).split(','))
            }
        }
        return presets.firstOrNull { it.id == value || it.label == value || value in it.legacyLabels }
            ?: presets.first()
    }

    fun vpnAddresses(saved: String?): List<String> = resolve(saved).ipv4
    fun canonicalSelection(saved: String?): String = resolve(saved).id
    fun selectionLabel(saved: String?): String {
        val preset = resolve(saved)
        return when (preset.mode) {
            DnsMode.PRIVATE_DNS -> "${preset.label}: VPN использует DNS сети; настройте Private DNS в Android"
            DnsMode.PROVIDER_SETTINGS -> "${preset.label}: VPN использует DNS сети; откройте настройки поставщика"
            DnsMode.VPN_IPV4 -> if (preset.id.startsWith(CUSTOM_PREFIX)) "${preset.label}: ${preset.ipv4.joinToString(", ")}" else preset.label
            DnsMode.SYSTEM -> preset.label
        }
    }

    /** One to four literal IPv4 addresses. No name resolution or URL parsing. */
    fun customSelection(input: String): String? {
        val ips = input.trim().split(Regex("[\\s,;]+"))
        if (ips.size !in 1..4 || ips.any { !isUnicastIpv4(it) }) return null
        return CUSTOM_PREFIX + ips.distinct().joinToString(",")
    }

    fun customInput(saved: String?): String = if (saved?.startsWith(CUSTOM_PREFIX) == true)
        resolve(saved).ipv4.joinToString(", ") else ""

    fun isUnicastIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || it.any { c -> c !in '0'..'9' } ||
                (it.length > 1 && it.startsWith('0')) }) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        return octets.all { it in 0..255 } && octets[0] in 1..223 && octets[0] != 127 &&
            !(octets[0] == 169 && octets[1] == 254)
    }
}
