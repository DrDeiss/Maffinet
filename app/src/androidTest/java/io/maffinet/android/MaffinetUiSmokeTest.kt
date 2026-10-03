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

/** Exercises the real Activity and saved UI state without opening external apps or starting VPN. */
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
    fun firstLaunchShowsRequiredNoticeAndAcknowledgmentSurvivesRelaunch() {
        launchActivity(onboardingCompleted = false, noticeSeen = false)
        waitForText(UNOFFICIAL_NOTICE)
        compose.onNodeWithText(UNOFFICIAL_NOTICE).assertIsDisplayed()
        saveScreenshot("01-first-launch-notice")

        compose.onNodeWithText("Продолжить").performClick()
        waitForText("Добро пожаловать в Maffinet")
        compose.onNodeWithText(UNOFFICIAL_NOTICE).assertDoesNotExist()
        waitForDisplayedText("Начать")
        compose.onNodeWithText("Начать").assertIsDisplayed()
        saveScreenshot("02-onboarding-welcome")

        relaunchActivity()
        waitForText("Добро пожаловать в Maffinet")
        compose.onNodeWithText(UNOFFICIAL_NOTICE).assertDoesNotExist()
        waitForDisplayedText("Начать")
        compose.onNodeWithText("Начать").assertIsDisplayed()
    }

    @Test
    fun fourPrimaryDestinationsOpenAndAttributionRemainsVisible() {
        launchActivity()
        waitForText("Подключиться")
        compose.onNodeWithText(FORK_MARKING).assertIsDisplayed()
        saveScreenshot("03-home")

        navigate("Сервисы", "Выберите сервисы для подключения и автоматической проверки.")
        compose.onNodeWithText("YouTube").assertIsDisplayed()
        saveScreenshot("04-services")

        navigate("Стратегии", "Auto strategy")
        compose.onNodeWithText("Проверить выбранные сервисы").assertIsDisplayed()
        saveScreenshot("05-strategies")

        navigate("Настройки", "Основные настройки и дополнительные возможности.")
        saveScreenshot("06-settings")
        compose.onNodeWithText("О Maffinet").performScrollTo().performClick()
        waitForTextToDisappear("Версия, исходники, лицензия и благодарности")
        compose.onNodeWithText("Версия, исходники, лицензия и благодарности").assertDoesNotExist()
        waitForText(FORK_MARKING, substring = true)
        compose.onNodeWithText(FORK_MARKING, substring = true).performScrollTo().assertIsDisplayed()
        saveScreenshot("07-about-attribution")

        navigate("Главная", "Подключиться")
        compose.onNodeWithText(FORK_MARKING).assertIsDisplayed()
    }

    @Test
    fun serviceSelectionSurvivesClosingAndReopeningActivity() {
        launchActivity()
        waitForText("Подключиться")
        navigate("Сервисы", "Выберите сервисы для подключения и автоматической проверки.")
        serviceSwitch("YouTube", "Instagram").assertIsOn()
        serviceSwitch("Instagram", "YouTube").performScrollTo().assertIsOff().performClick().assertIsOn()
        serviceSwitch("YouTube", "Instagram").performScrollTo().performClick().assertIsOff()
        saveScreenshot("08-services-changed")

        relaunchActivity()
        waitForText("Подключиться")
        navigate("Сервисы", "Выберите сервисы для подключения и автоматической проверки.")
        serviceSwitch("Instagram", "YouTube").performScrollTo().assertIsOn()
        serviceSwitch("YouTube", "Instagram").performScrollTo().assertIsOff()
        serviceSwitch("LinkedIn", "YouTube").performScrollTo().assertIsOff()
        saveScreenshot("09-services-restored")
    }

    @Test
    fun domainEditorPersistsValidEditsAndRejectsInvalidReplacement() {
        launchActivity()
        waitForText("Подключиться")
        openDomainEditor()
        compose.onNode(isToggleable()).assertIsOn().performClick().assertIsOff()
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
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNode(hasSetTextAction()).performScrollTo().assertTextContains(SAVED_DOMAINS)
        saveScreenshot("12-domains-restored")
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

    private fun serviceSwitch(name: String, otherCard: String): SemanticsNodeInteraction {
        // Restrict the ancestor to the service's card, excluding the whole scrolling screen.
        val card = hasAnyDescendant(hasText(name)) and !hasAnyDescendant(hasText(otherCard))
        return compose.onNode(isToggleable() and hasAnyAncestor(card))
    }

    private fun openDomainEditor() {
        navigate("Настройки", "Основные настройки и дополнительные возможности.")
        compose.onNodeWithText("Domain lists").performScrollTo().performClick()
        waitForText("User domains")
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
