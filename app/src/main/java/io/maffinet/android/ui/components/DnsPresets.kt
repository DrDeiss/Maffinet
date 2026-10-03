package io.maffinet.android.ui.components

object DnsPresets {
    const val DEFAULT = "Стандартный (Отключено)"
    val values = listOf(DEFAULT, "Cloudflare Secure DNS", "Google Public DNS", "AdGuard DNS (Блокировка рекламы)",
        "Xbox DNS (xbox-dns.ru / ChatGPT / Brawl)", "Supercell Xbox DNS (supercell.xbox-dns.ru)",
        "NullsProxy DNS (dns.nullsproxy.com)", "Comss.one DNS (dns.comss.one)", "Geohide DNS (dns.geohide.ru)")
}
