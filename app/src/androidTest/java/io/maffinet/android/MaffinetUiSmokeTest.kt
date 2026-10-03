package io.maffinet.android

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.maffinet.android.data.domains.DomainListRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises real screens, saved state and the independent Telegram listener without opening external apps. */
@RunWith(AndroidJUnit4::class)
class MaffinetUiSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
    private val userDomainFile: File
        get() = File(context.filesDir, "maffinet-domains/user-domains.txt")
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalPreferences: Map<String, Any?> = emptyMap()
    private var originalUserDomains: ByteArray? = null

    @Before
    fun isolateSavedState() {
        io.maffinet.android.core.connection.ConnectionCoordinator.clearStartErrors()
        originalPreferences = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        originalUserDomains = userDomainFile.takeIf(File::exists)?.readBytes()
        check(preferences.edit().clear()
            .putBoolean("service_enabled", false)
            .putBoolean("auto_connect_on_start", false)
            .putBoolean("auto_update_enabled", false)
            .putBoolean("telegram_proxy_enabled_by_user", false)
            .putBoolean("wants_youtube_bypass", true)
            .putBoolean("performance_mode", true)
            .commit())
        check(DomainListRepository(context).saveUserDomains("").isValid)
        instrumentation.runOnMainSync { MainActivity.splashNotShownYet = true }
    }

    @After
    fun restoreSavedState() {
        closeActivity()
        if (io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)) {
            io.maffinet.android.core.connection.ConnectionCoordinator.stopAll(context)
            compose.waitUntil(10_000) {
                !io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)
            }
        }
        val editor = preferences.edit().clear()
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit())
        originalUserDomains?.let { userDomainFile.writeBytes(it) } ?: userDomainFile.delete()
    }

    @Test
    fun firstLaunchRespectsAttributionGateAndWelcomeSurvivesRelaunch() {
        launchActivity(onboardingCompleted = false, noticeSeen = false)
        if (BuildConfig.SHOW_UPSTREAM_ATTRIBUTION) {
            waitForText(UNOFFICIAL_NOTICE)
            compose.onNodeWithText(UNOFFICIAL_NOTICE).assertIsDisplayed()
            saveScreenshot("01-first-launch")
            compose.onNodeWithText("Продолжить").performClick()
        } else {
            waitForDisplayedText("Начать")
            assertUpstreamAttributionAbsent()
            check(!preferences.getBoolean("maffinet_fork_notice_seen", false))
            saveScreenshot("01-first-launch")
        }

        waitForText("Добро пожаловать в Maffinet")
        compose.onNodeWithText(UNOFFICIAL_NOTICE).assertDoesNotExist()
        waitForDisplayedText("Начать")
        compose.onNodeWithText("Начать").assertIsDisplayed()

        relaunchActivity()
        waitForText("Добро пожаловать в Maffinet")
        compose.onNodeWithText(UNOFFICIAL_NOTICE).assertDoesNotExist()
        waitForDisplayedText("Начать")
        compose.onNodeWithText("Начать").assertIsDisplayed()
        if (!BuildConfig.SHOW_UPSTREAM_ATTRIBUTION) assertUpstreamAttributionAbsent()
        saveScreenshot("02-onboarding-welcome")
    }

    @Test
    fun primaryDestinationsAndDirectHostsOpenAndAboutRespectsAttributionGate() {
        launchActivity()
        waitForText("Подключиться")
        assertHomeAttributionVisibility()
        saveScreenshot("03-home")

        compose.onAllNodesWithContentDescription("Сервисы").assertCountEquals(0)
        compose.onAllNodesWithText("YouTube").assertCountEquals(0)
        compose.onNodeWithText("Hosts").performScrollTo().performClick()
        waitForText("General · встроенный список")
        closeSoftKeyboard()
        compose.onNodeWithText("General · встроенный список").performScrollTo()
        saveScreenshot("04-hosts")
        compose.onNodeWithText("Показать General").performClick()
        compose.onNodeWithText("General · встроенный список").performScrollTo()
        saveScreenshot("17-hosts-general")
        compose.onNodeWithText("Скрыть General").performScrollTo().performClick()

        navigate("Стратегии", "Auto strategy")
        compose.onNodeWithText("Проверить стратегии").performScrollTo().assertIsDisplayed()
        saveScreenshot("05-strategies")

        navigate("Настройки", "Основные настройки и дополнительные возможности.")
        saveScreenshot("06-settings")
        compose.onNodeWithText("О Maffinet").performScrollTo().performClick()
        waitForTextToDisappear("Версия, исходники, лицензия и благодарности")
        compose.onNodeWithText("Версия, исходники, лицензия и благодарности").assertDoesNotExist()
        if (BuildConfig.SHOW_UPSTREAM_ATTRIBUTION) {
            waitForText(FORK_MARKING, substring = true)
            compose.onNodeWithText(FORK_MARKING, substring = true).performScrollTo().assertIsDisplayed()
        } else {
            waitForText("Версия и обновление")
            assertUpstreamAttributionAbsent()
            compose.onNodeWithText("Исходный проект").assertDoesNotExist()
            compose.onNodeWithText("Поддержать автора исходного проекта").assertDoesNotExist()
            compose.onNodeWithText("Разработчик:", substring = true).assertDoesNotExist()
        }
        compose.onNodeWithText("Обновления ещё не проверены").assertIsDisplayed()
        saveScreenshot("07-about")

        navigate("Главная", "Подключиться")
        assertHomeAttributionVisibility()
    }

    @Test
    fun modeChoicesAndApplicationsSurviveReopeningWithoutChangingHosts() {
        check(preferences.edit().putStringSet("selected_apps", setOf("com.android.settings")).commit())
        val originalHosts = DomainListRepository(context).activeDomains()
        launchActivity()
        waitForText("Подключиться")
        compose.onNodeWithTag("applications-mode").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("telegram-mode").performScrollTo().assertIsOff().performClick().assertIsOn()
        saveScreenshot("08-telegram-mode")

        relaunchActivity()
        waitForText("Подключиться")
        compose.onNodeWithTag("applications-mode").performScrollTo().assertIsOff()
        compose.onNodeWithTag("telegram-mode").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Включите «Приложения» или «Telegram», чтобы подключиться.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Выбрано: 1").performScrollTo().assertIsDisplayed()
        check(preferences.getStringSet("selected_apps", emptySet()) == setOf("com.android.settings"))
        check(DomainListRepository(context).activeDomains() == originalHosts)
        saveScreenshot("09-modes-off")
    }

    @Test
    fun homeProvidesDirectAppSelectionAndPersistentVpnDns() {
        check(preferences.edit().putStringSet("selected_apps", setOf("com.android.settings")).commit())
        launchActivity()
        waitForText("Подключиться")
        compose.onNodeWithText("Выбрать приложения").performScrollTo().performClick()
        waitForText("Через VPN идут только выбранные приложения. Пустой выбор не запускает VPN.")
        waitForDisplayedText("com.android.settings")
        compose.onNodeWithText("com.android.settings").performClick()
        check(preferences.getStringSet("selected_apps", emptySet()).isNullOrEmpty())
        compose.onNodeWithText("com.android.settings").performClick()
        check(preferences.getStringSet("selected_apps", emptySet()) == setOf("com.android.settings"))
        saveScreenshot("13-app-selection")
        compose.onNodeWithText("Готово").performClick()
        compose.onNodeWithText("DNS").performScrollTo().performClick()
        waitForText("DNS для VPN")
        saveScreenshot("14-dns-selection")
        compose.onNodeWithTag("dns-preset-list").performScrollToNode(hasText("Google Public DNS"))
        compose.onNodeWithText("Google Public DNS").performClick()
        relaunchActivity()
        waitForText("Подключиться")
        compose.onNodeWithText("Google Public DNS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("DNS применяется к VPN для выбранных приложений.").performScrollTo().assertIsDisplayed()
        check(preferences.getString("custom_dns_preset", "") == "Google Public DNS")
        check(preferences.getStringSet("selected_apps", emptySet()) == setOf("com.android.settings"))
        saveScreenshot("15-home-dns")
        compose.onNodeWithText("Настройки Telegram").performScrollTo().performClick()
        waitForText("Порт подключения")
        saveScreenshot("16-telegram-settings")
    }

    @Test
    fun domainEditorPersistsValidEditsAndRejectsInvalidReplacement() {
        launchActivity()
        waitForText("Подключиться")
        openDomainEditor()
        compose.onNode(isToggleable()).performScrollTo().assertIsOn().performClick().assertIsOff()
        replaceDomains("EXAMPLE.COM\nsub.example.org\nexample.com")
        saveDomains()
        waitForText("Сохранено доменов: 2")
        compose.onNode(hasSetTextAction()).assertTextContains(SAVED_DOMAINS)
        saveScreenshot("10-domains-saved")

        replaceDomains("https://example.com/path")
        saveDomains()
        waitForText("Исправьте строки ниже. Список не изменён.")
        compose.onNodeWithText("Исправьте строки ниже. Список не изменён.")
            .performScrollTo().assertIsDisplayed()
        saveScreenshot("11-domains-validation")

        relaunchActivity()
        waitForText("Подключиться")
        openDomainEditor()
        compose.onNode(isToggleable()).performScrollTo().assertIsOff()
        compose.onNode(hasSetTextAction()).performScrollTo().assertTextContains(SAVED_DOMAINS)
        saveScreenshot("12-domains-restored")
    }

    @Test
    fun telegramOnlyHomeShowsActualStartStopAndLocksConfiguration() {
        val settings = io.maffinet.android.data.settings.MaffinetSettingsRepository(context)
        settings.setApplicationsEnabled(false)
        settings.setTelegramEnabled(true)
        preferences.edit().putBoolean("open_tg_on_connect", false).putBoolean("tgproxy_cf_enabled", false).commit()
        launchActivity()
        waitForText("Подключиться")
        compose.onNodeWithTag("connect").performClick()
        waitForText("Подключено")
        compose.onNodeWithTag("telegram-status").performScrollTo().assertTextEquals("Telegram-прокси: работает")
        compose.onNodeWithTag("vpn-status").performScrollTo().assertTextEquals("VPN / ByeDPI: остановлен")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("applications-mode") and !isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("applications-mode").assertIsNotEnabled()
        saveScreenshot("18-telegram-running")
        compose.onNodeWithTag("connect").performScrollTo().performClick()
        waitForText("Подключиться")
        compose.onNodeWithTag("telegram-status").performScrollTo().assertTextEquals("Telegram-прокси: остановлен")
        check(!settings.anyModeRequested())
        saveScreenshot("19-telegram-stopped")
    }

    private fun launchActivity(onboardingCompleted: Boolean = true, noticeSeen: Boolean = true) {
        check(preferences.edit()
            .putBoolean("onboarding_completed", onboardingCompleted)
            .putBoolean("maffinet_fork_notice_seen", noticeSeen)
            .commit())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForSplashDismissal()
    }

    /** A new Activity reads persisted preferences/files; it cannot reuse rememberSaveable state. */
    private fun relaunchActivity() {
        closeActivity()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForSplashDismissal()
    }

    private fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    private fun navigate(label: String, destinationText: String) {
        compose.onNodeWithContentDescription(label).assertIsDisplayed().performClick()
        waitForText(destinationText)
        compose.onNodeWithText(destinationText).assertIsDisplayed()
    }

    private fun assertHomeAttributionVisibility() {
        if (BuildConfig.SHOW_UPSTREAM_ATTRIBUTION) {
            compose.onNodeWithText(FORK_MARKING).assertIsDisplayed()
        } else {
            assertUpstreamAttributionAbsent()
        }
    }

    private fun assertUpstreamAttributionAbsent() {
        compose.onAllNodesWithText("NetFix", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("rupleide", substring = true).assertCountEquals(0)
    }

    private fun openDomainEditor() {
        navigate("Настройки", "Основные настройки и дополнительные возможности.")
        compose.onNodeWithText("Hosts").performScrollTo().performClick()
        waitForText("User · ваши домены")
        compose.onNode(hasSetTextAction()).assertExists()
    }

    private fun replaceDomains(text: String) {
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(text)
        closeSoftKeyboard()
    }

    private fun saveDomains() {
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForDisplayedText(text: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            // Welcome uses a delayed AnimatedVisibility; presence alone is insufficient.
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(text).isDisplayed()
        }
    }

    private fun waitForTextToDisappear(text: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            // Dispose AnimatedContent's outgoing screen before capturing the incoming one.
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForSplashDismissal() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.mainClock.advanceTimeByFrame()
            // Home semantics already exist underneath the splash, so wait for its actual dismissal.
            compose.runOnIdle { !MainActivity.splashNotShownYet }
        }
    }

    private fun saveScreenshot(name: String) {
        // Include delayed entrance animations, which can begin after semantics appear.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val framesRendered = CountDownLatch(1)
        checkNotNull(scenario).onActivity { activity ->
            val decor = activity.window.decorView
            // Semantics can be current before SurfaceFlinger receives the updated Activity frame.
            decor.postOnAnimation {
                decor.postOnAnimation { framesRendered.countDown() }
            }
        }
        check(framesRendered.await(3, TimeUnit.SECONDS)) { "Android did not deliver screenshot frames: $name" }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        if (bitmap == null) {
            Log.w("MaffinetUiSmoke", "UiAutomation did not provide screenshot: $name")
            return
        }
        try {
            val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "ui-smoke")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val FORK_MARKING = "(fork of NetFix Mobile by rupleide)"
        const val SAVED_DOMAINS = "example.com\nsub.example.org\n"
        const val UNOFFICIAL_NOTICE = "ВНИМАНИЕ! Вы запускаете неофициальный форк программы NetFix Mobile. Оригинальный автор (rupleide) не имеет отношения к этой сборке, не гарантирует её безопасность и не оказывает поддержку. Оригинальный чистый NetFix Mobile доступен по адресу: github.com/rupleide/NetFixMobile"
    }
}
