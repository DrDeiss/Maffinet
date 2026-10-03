package io.maffinet.android.strategy

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.core.strategy.ServiceConnectivityResult
import io.maffinet.android.core.strategy.StrategyEvaluation
import io.maffinet.android.core.strategy.TargetConnectivityResult
import io.maffinet.android.data.domains.DomainListRepository
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.data.strategy.ProbeTargetRepository
import io.maffinet.android.data.strategy.StrategyMatrixStore
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Saved HTTP evidence must describe this configuration, independent of app/service choices. */
@RunWith(AndroidJUnit4::class)
class StrategyConfigurationSmokeTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val testId = System.nanoTime().toString()
    private val directory = File(base.cacheDir, "strategy-config-$testId")
    private val context = object : ContextWrapper(base) {
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("${name}_strategy_test_$testId", mode)
    }
    private val settings by lazy { MaffinetSettingsRepository(context) }
    private val probes by lazy { ProbeTargetRepository(context) }

    @Before fun prepare() { assertTrue(directory.mkdirs()) }

    @After fun restore() {
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        directory.deleteRecursively()
        StrategyTestManager.init(base)
    }

    @Test fun targetConfigurationPersistsWithoutSelectedAppsOrServiceProfiles() {
        settings.setManualApplications(emptySet())
        ServiceCatalog.profiles.forEach { settings.setServiceEnabled(it.id, false) }
        assertTrue(probes.save("https://example.com/health\nhttps://custom.org").isValid)
        assertEquals(listOf("https://example.com/health", "https://custom.org"), ProbeTargetRepository(context).urls())
        assertFalse(probes.save("https://user:secret@example.com").isValid)
        assertEquals(listOf("https://example.com/health", "https://custom.org"), probes.urls())
        val before = probes.snapshot().fingerprint
        settings.setManualApplications(setOf("com.example.application"))
        settings.setServiceEnabled("youtube", true)
        settings.setString("custom_dns_preset", "cloudflare")
        settings.setTelegramEnabled(false)
        assertEquals("App/profile/DNS/Telegram choices cannot change the SOCKS candidate check", before, probes.snapshot().fingerprint)
    }

    @Test fun savedMatrixIsHiddenAfterHostsTargetsOrOverrideChange() {
        assertTrue(probes.save("https://example.com").isValid)
        val snapshot = probes.snapshot()
        val result = StrategyEvaluation(0, "-s1", listOf(ServiceConnectivityResult("probe_0", "example.com",
            listOf(TargetConnectivityResult("https://example.com", true, 12, httpStatus = 200)))))
        val store = StrategyMatrixStore(context)
        store.save(snapshot.fingerprint, listOf(result))
        assertEquals(snapshot.fingerprint, store.load()!!.fingerprint)
        StrategyTestManager.init(context)
        assertEquals(result, StrategyTestManager.matrixResults["-s1"])

        assertTrue(DomainListRepository(context).saveUserDomains("new-domain.example").isValid)
        StrategyTestManager.refreshConfiguration(context)
        assertTrue(StrategyTestManager.matrixResults.isEmpty())
        assertTrue(StrategyTestManager.hasStaleResults)
        StrategyTestManager.init(context)
        assertTrue("Stale saved results must also be hidden after process initialization", StrategyTestManager.matrixResults.isEmpty())

        val hostsFingerprint = probes.snapshot().fingerprint
        assertTrue(probes.save("https://example.com/changed").isValid)
        assertNotEquals(hostsFingerprint, probes.snapshot().fingerprint)
        val targetFingerprint = probes.snapshot().fingerprint
        settings.setHostFilterOverride(true)
        assertNotEquals(targetFingerprint, probes.snapshot().fingerprint)
    }
}
