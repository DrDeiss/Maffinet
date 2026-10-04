package io.maffinet.android.data.strategy

import android.content.Context
import android.content.SharedPreferences
import io.maffinet.android.core.dpibypass.ByeDpiFilterConfiguration
import io.maffinet.android.core.strategy.ProbeConfigurationFingerprint
import io.maffinet.android.core.strategy.ProbeTargetParseResult
import io.maffinet.android.core.strategy.ProbeTargetParser
import io.maffinet.android.data.domains.DomainListRepository
import io.maffinet.android.data.settings.MaffinetSettingsRepository

data class StrategyProbeSnapshot(
    val urls: List<String>, val filters: ByeDpiFilterConfiguration, val fingerprint: String,
    val automaticAccessEnabled: Boolean = true,
)

/** Separate from service profiles, selected_apps, DNS and legacy strategy import/export. */
class ProbeTargetRepository(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    fun urls(): List<String> {
        val stored = preferences.getString(KEY, null) ?: return ProbeTargetParser.defaults
        val parsed = ProbeTargetParser.parse(stored)
        return if (parsed.isValid) parsed.urls else ProbeTargetParser.defaults
    }

    fun save(text: String): ProbeTargetParseResult {
        val parsed = ProbeTargetParser.parse(text)
        if (parsed.isValid) check(preferences.edit().putString(KEY, parsed.urls.joinToString("\n")).commit()) {
            "Не удалось сохранить проверочные адреса"
        }
        return parsed
    }

    fun snapshot(): StrategyProbeSnapshot {
        val domains = DomainListRepository(context)
        val lists = domains.getLists()
        // General is the merged active list; do not reread the file between snapshot fields.
        val active = lists.first { it.id == "general" }.domains
        val settings = MaffinetSettingsRepository(context)
        val override = settings.hostFilterOverride()
        val automaticAccess = settings.automaticAccessEnabled()
        val urls = urls()
        val mode = preferences.getString("byedpi_hosts_mode", "disable") ?: "disable"
        val hosts = when (mode) {
            "blacklist" -> preferences.getString("byedpi_hosts_blacklist", "")
            "whitelist" -> preferences.getString("byedpi_hosts_whitelist", "")
            else -> ""
        }.orEmpty()
        return StrategyProbeSnapshot(urls, ByeDpiFilterConfiguration(lists, active, override),
            ProbeConfigurationFingerprint.create(urls, lists, active, override, mode, hosts,
                automaticAccessEnabled = automaticAccess), automaticAccess)
    }

    companion object { const val KEY = "maffinet_probe_urls" }
}
