package io.maffinet.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.core.connection.ConnectionModes
import io.maffinet.android.core.connection.ConnectionCoordinator

/** New settings keys and future migrations belong here, not in UI composables. */
class MaffinetSettingsRepository(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE))

    init {
        val version = preferences.getInt(SCHEMA_VERSION, 0)
        require(version <= CURRENT_SCHEMA_VERSION) { "Settings belong to a newer Maffinet version" }
        if (version < CURRENT_SCHEMA_VERSION) {
            val edit = preferences.edit()
            if (version < 2) {
                // Snapshot legacy choices once. Never derive routing or future recovery
                // from service profiles or the shared legacy service_enabled flag.
                val modes = ConnectionModes(
                    preferences.getBoolean("wants_youtube_bypass", true),
                    preferences.getBoolean("telegram_proxy_enabled_by_user", true),
                )
                val desired = modes.requested(preferences.getBoolean("service_enabled", false))
                edit.putBoolean(APPLICATIONS_ENABLED, modes.applications)
                    .putBoolean(TELEGRAM_ENABLED, modes.telegram)
                    .putBoolean(APPLICATIONS_REQUESTED, desired.applications)
                    .putBoolean(TELEGRAM_REQUESTED, desired.telegram)
            }
            // selected_apps and all engine preferences are deliberately untouched.
            edit.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply()
        }
    }

    val schemaVersion: Int get() = preferences.getInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)

    fun applicationsEnabled(): Boolean = preferences.getBoolean(APPLICATIONS_ENABLED, true)
    fun setApplicationsEnabled(enabled: Boolean) { preferences.edit().putBoolean(APPLICATIONS_ENABLED, enabled).apply() }
    fun telegramEnabled(): Boolean = preferences.getBoolean(TELEGRAM_ENABLED, true)
    fun setTelegramEnabled(enabled: Boolean) { preferences.edit().putBoolean(TELEGRAM_ENABLED, enabled).apply() }
    fun selectedModes(): ConnectionModes = ConnectionModes(applicationsEnabled(), telegramEnabled())
    fun applicationsRequested(): Boolean = preferences.getBoolean(APPLICATIONS_REQUESTED, false)
    fun telegramRequested(): Boolean = preferences.getBoolean(TELEGRAM_REQUESTED, false)
    fun anyModeRequested(): Boolean = applicationsRequested() || telegramRequested()

    fun setApplicationsRequested(requested: Boolean) = synchronized(preferences) { setRequested(requested, telegramRequested()) }
    fun setTelegramRequested(requested: Boolean) = synchronized(preferences) { setRequested(applicationsRequested(), requested) }
    fun setRequested(applications: Boolean, telegram: Boolean) = synchronized(preferences) {
        preferences.edit().putBoolean(APPLICATIONS_REQUESTED, applications)
            .putBoolean(TELEGRAM_REQUESTED, telegram)
            // Read-only compatibility mirror for old diagnostics, never a recovery input.
            .putBoolean("service_enabled", applications || telegram).apply()
    }

    fun enabledServiceIds(): Set<String> = preferences.getStringSet(ENABLED_SERVICES, null)?.toSet()
        ?: ServiceCatalog.enabledByDefault

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

    /** Explicit opt-in lets existing advanced hosts mode/commands override active hosts. */
    fun hostFilterOverride(): Boolean = preferences.getBoolean(HOST_FILTER_OVERRIDE, false)
    fun setHostFilterOverride(enabled: Boolean) {
        preferences.edit().putBoolean(HOST_FILTER_OVERRIDE, enabled).apply()
    }

    fun automaticAccessEnabled(): Boolean = preferences.getBoolean(AUTOMATIC_ACCESS, true)
    fun setAutomaticAccessEnabled(enabled: Boolean) = synchronized(preferences) {
        check(!anyModeRequested() && !ConnectionCoordinator.isConfigurationLocked()) {
            "Остановите подключение и проверку стратегий перед изменением режима доступа"
        }
        preferences.edit().putBoolean(AUTOMATIC_ACCESS, enabled).apply()
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
        const val CURRENT_SCHEMA_VERSION = 2
        private const val SCHEMA_VERSION = "maffinet_settings_schema"
        private const val ENABLED_SERVICES = "maffinet_enabled_services"
        private const val USER_DOMAINS_ENABLED = "maffinet_user_domains_enabled"
        private const val HOST_FILTER_OVERRIDE = "maffinet_advanced_hosts_override"
        const val AUTOMATIC_ACCESS = "maffinet_automatic_access"
        private const val MANUAL_APPLICATIONS = "selected_apps"
        const val APPLICATIONS_ENABLED = "maffinet_applications_enabled"
        const val TELEGRAM_ENABLED = "maffinet_telegram_enabled"
        const val APPLICATIONS_REQUESTED = "maffinet_applications_requested"
        const val TELEGRAM_REQUESTED = "maffinet_telegram_requested"
    }
}
