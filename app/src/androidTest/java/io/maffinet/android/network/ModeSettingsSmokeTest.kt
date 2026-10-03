package io.maffinet.android.network

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModeSettingsSmokeTest {
    @Test fun migrationPreservesManualAppsAndSeparatesEveryModeCombination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("maffinet-isolated-mode-migration", Context.MODE_PRIVATE)
        try {
            for (apps in listOf(false, true)) for (telegram in listOf(false, true)) {
                val manual = setOf("org.example.browser", "com.google.android.youtube")
                assertTrue(prefs.edit().clear().putInt("maffinet_settings_schema", 1)
                    .putStringSet("selected_apps", manual)
                    .putBoolean("wants_youtube_bypass", apps)
                    .putBoolean("telegram_proxy_enabled_by_user", telegram)
                    .putBoolean("service_enabled", true)
                    .putStringSet("maffinet_enabled_services", setOf("youtube", "instagram", "linkedin"))
                    .putString("custom_dns_preset", "Google Public DNS").commit())
                val settings = MaffinetSettingsRepository(prefs)
                assertEquals(manual, settings.manualApplications())
                assertEquals(apps, settings.applicationsEnabled())
                assertEquals(telegram, settings.telegramEnabled())
                assertEquals(apps, settings.applicationsRequested())
                assertEquals(telegram, settings.telegramRequested())
                assertEquals("Google Public DNS", settings.getString("custom_dns_preset", ""))

                // Late legacy writes cannot change new mode choices or recover the other engine.
                prefs.edit().putBoolean("service_enabled", false).putBoolean("wants_youtube_bypass", !apps)
                    .putBoolean("telegram_proxy_enabled_by_user", !telegram).commit()
                val reopened = MaffinetSettingsRepository(prefs)
                assertEquals(apps, reopened.applicationsRequested())
                assertEquals(telegram, reopened.telegramRequested())
                assertEquals(apps, reopened.applicationsEnabled())
                assertEquals(telegram, reopened.telegramEnabled())
                settings.setApplicationsRequested(false)
                assertEquals(telegram, settings.telegramRequested())
                settings.setTelegramRequested(false)
                assertFalse(settings.anyModeRequested())
                assertEquals(manual, MaffinetSettingsRepository(prefs).manualApplications())
            }
        } finally { assertTrue(prefs.edit().clear().commit()) }
    }
}
