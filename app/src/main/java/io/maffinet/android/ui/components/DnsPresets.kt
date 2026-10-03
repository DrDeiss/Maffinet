package io.maffinet.android.ui.components

import io.maffinet.android.core.dns.DnsCatalog
import io.maffinet.android.core.dns.DnsMode
import io.maffinet.android.core.dns.DnsPurpose

/** UI compatibility facade; addresses and labels live in the runtime catalog. */
object DnsPresets {
    const val DEFAULT = DnsCatalog.SYSTEM_ID
    val values: List<String> = DnsCatalog.presets.map { it.id } + DnsCatalog.CUSTOM_ID

    fun forPurpose(values: List<String>, purpose: DnsPurpose): List<String> = values.filter { value ->
        val preset = DnsCatalog.resolve(value)
        value == DnsCatalog.CUSTOM_ID || preset.id.startsWith("custom:") ||
            preset.mode == DnsMode.SYSTEM || preset.purpose == purpose
    }
}
