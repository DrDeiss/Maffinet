package io.maffinet.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import io.maffinet.android.core.domains.BuiltInDomainLists

/** New settings keys and future migrations belong here, not in UI composables. */
class MaffinetSettingsRepository(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE))

    init {
        val version = preferences.getInt(SCHEMA_VERSION, 0)
        require(version <= CURRENT_SCHEMA_VERSION) { "Settings belong to a newer Maffinet version" }
        if (version < CURRENT_SCHEMA_VERSION) {
            // Version 1 adds new keys without changing legacy advanced preferences.
            preferences.edit().putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply()
        }
    }

    val schemaVersion: Int get() = preferences.getInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)

    fun enabledServiceIds(): Set<String> = preferences.getStringSet(ENABLED_SERVICES, null)?.toSet()
        ?: BuiltInDomainLists.enabledByDefault

    fun setServiceEnabled(id: String, enabled: Boolean) {
        require(id.matches(Regex("[a-z][a-z0-9_-]*"))) { "Invalid service ID" }
        val selection = enabledServiceIds().toMutableSet()
        if (enabled) selection.add(id) else selection.remove(id)
        preferences.edit().putStringSet(ENABLED_SERVICES, selection).apply()
    }

    fun manualApplications(): Set<String> = preferences.getStringSet(MANUAL_APPLICATIONS, emptySet())?.toSet().orEmpty()
    fun setManualApplications(packages: Set<String>) {
        preferences.edit().putStringSet(MANUAL_APPLICATIONS, packages.toSet()).apply()
    }

    fun userDomainsEnabled(): Boolean = preferences.getBoolean(USER_DOMAINS_ENABLED, true)
    fun setUserDomainsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(USER_DOMAINS_ENABLED, enabled).apply()
    }

    /** Explicit opt-in lets existing advanced hosts mode/commands override service domains. */
    fun hostFilterOverride(): Boolean = preferences.getBoolean(HOST_FILTER_OVERRIDE, false)
    fun setHostFilterOverride(enabled: Boolean) {
        preferences.edit().putBoolean(HOST_FILTER_OVERRIDE, enabled).apply()
    }

    // Compatibility access for existing expert controls. New product settings use typed APIs above.
    fun getString(key: String, default: String): String {
        requireAdvancedKey(key)
        return preferences.getString(key, default) ?: default
    }
    fun getBoolean(key: String, default: Boolean): Boolean {
        requireAdvancedKey(key)
        return preferences.getBoolean(key, default)
    }
    fun setString(key: String, value: String) {
        requireAdvancedKey(key)
        preferences.edit().putString(key, value).apply()
    }
    fun setBoolean(key: String, value: Boolean) {
        requireAdvancedKey(key)
        preferences.edit().putBoolean(key, value).apply()
    }

    private fun requireAdvancedKey(key: String) {
        require(key.startsWith("byedpi_") || key in setOf("proxy_port", "custom_dns_preset", "ipv6_enable", "strategy_manual_mode")) {
            "Use a typed settings API for $key"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val SCHEMA_VERSION = "maffinet_settings_schema"
        private const val ENABLED_SERVICES = "maffinet_enabled_services"
        private const val USER_DOMAINS_ENABLED = "maffinet_user_domains_enabled"
        private const val HOST_FILTER_OVERRIDE = "maffinet_advanced_hosts_override"
        private const val MANUAL_APPLICATIONS = "selected_apps"
    }
}
