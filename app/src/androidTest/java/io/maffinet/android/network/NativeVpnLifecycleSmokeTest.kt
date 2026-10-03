package io.maffinet.android.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.maffinet.android.core.dpibypass.ByeDpiVpnService
import io.maffinet.android.core.dpibypass.ServiceManager
import io.maffinet.android.core.dpibypass.StrategyTester
import io.maffinet.android.core.dpibypass.getPreferences
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.core.strategy.StrategyEvaluation
import io.maffinet.android.data.FAILED_BROADCAST
import io.maffinet.android.data.Mode
import io.maffinet.android.data.SENDER
import io.maffinet.android.data.STOPPED_BROADCAST
import io.maffinet.android.data.Sender
import io.maffinet.android.data.domains.DomainListRepository
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real JNI + TUN smoke checks. No remote service availability is required to pass. */
@RunWith(AndroidJUnit4::class)
class NativeVpnLifecycleSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getPreferences()
    private val userFile = File(context.filesDir, "maffinet-domains/user-domains.txt")
    private val stopped = AtomicInteger()
    private val failed = AtomicInteger()
    private var savedPreferences: Map<String, Any?>? = null
    private var savedUserFile: ByteArray? = null
    private var originalConsentMode: String? = null
    private var receiverRegistered = false
    private var originalTunCount = 0
    private var originalConfigs = emptySet<String>()
    private var proxyPort = 0
    private lateinit var helperPackage: String
    private lateinit var settings: MaffinetSettingsRepository

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra(SENDER, -1) != Sender.VPN.ordinal) return
            when (intent.action) {
                STOPPED_BROADCAST -> stopped.incrementAndGet()
                FAILED_BROADCAST -> failed.incrementAndGet()
            }
        }
    }

    @Before
    fun isolateSettingsAndGrantConsentOnOptedInEmulator() {
        // This shell authorization must never bypass consent on a user's physical device.
        assumeTrue("Native VPN smoke requires explicit CI emulator opt-in",
            InstrumentationRegistry.getArguments().getString(CONSENT_ARGUMENT) == "true")
        assertTrue("Refusing simulated VPN consent outside a qemu emulator",
            shell("getprop ro.kernel.qemu").trim() == "1" || shell("getprop ro.boot.qemu").trim() == "1")
        assertFalse("A VPN was already running before this isolated smoke test", ByeDpiVpnService.isVpnActive)
        assertFalse("Native resources were already present", ByeDpiVpnService.hasProxyResources)
        assertFalse("A strategy scan was already running", ServiceManager.isStrategyTestInProgress)
        assertFalse("A previous native cleanup failed", ServiceManager.isNativeProxyQuarantined)

        savedPreferences = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        savedUserFile = userFile.takeIf { it.exists() }?.readBytes()
        originalTunCount = tunDescriptorCount()
        assertEquals("A TUN descriptor leaked before this isolated test", 0, originalTunCount)
        originalConfigs = temporaryConfigs()
        ContextCompat.registerReceiver(context, statusReceiver,
            IntentFilter(STOPPED_BROADCAST).apply { addAction(FAILED_BROADCAST) },
            ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true

        assertTrue(preferences.edit().clear()
            .putBoolean("service_enabled", false)
            .putBoolean("econom_mode", true)
            .putBoolean("autostart", false)
            .putBoolean("auto_connect_on_start", false)
            .putBoolean("auto_update_enabled", false)
            .putBoolean("telegram_proxy_enabled_by_user", false)
            .putBoolean("byedpi_enable_cmd_settings", true)
            .putString("byedpi_mode", "vpn")
            .putString("byedpi_cmd_args", "-s1")
            .putString("byedpi_proxy_ip", "127.0.0.1")
            .putBoolean("ipv6_enable", false)
            .commit())
        proxyPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        assertTrue(preferences.edit().putString("proxy_port", proxyPort.toString()).commit())
        settings = MaffinetSettingsRepository(context)
        ServiceCatalog.profiles.forEach { settings.setServiceEnabled(it.id, false) }
        settings.setHostFilterOverride(false)
        settings.setUserDomainsEnabled(true)
        assertTrue(DomainListRepository(context).saveUserDomains("example.com").isValid)
        assertEquals(listOf("example.com"), DomainListRepository(context).activeDomains())

        // The test APK is installed with its own UID. The instrumented process uses the
        // target UID, so these checks exercise establishment/cleanup, not routed traffic.
        helperPackage = instrumentation.context.packageName
        assertTrue(helperPackage.matches(Regex("[a-zA-Z0-9_.]+")))
        assertNotEquals(context.packageName, helperPackage)
        assertNotEquals(context.applicationInfo.uid,
            context.packageManager.getApplicationInfo(helperPackage, 0).uid)
        settings.setManualApplications(setOf(helperPackage))
        assertFalse(context.packageName in settings.manualApplications())

        val operation = shell("appops get ${context.packageName} ACTIVATE_VPN")
        originalConsentMode = Regex("ACTIVATE_VPN: (allow|ignore|deny|default|foreground)")
            .find(operation)?.groupValues?.get(1)
            ?: if (operation.contains("No operations")) "default" else error("Cannot snapshot VPN app-op: $operation")
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        assertNull("Emulator app-op did not authorize VpnService.prepare", VpnService.prepare(context))
        stopAndAwaitCleanup() // Observe a completed STOP before any test START is queued.
    }

    @After
    fun stopAndRestoreIsolatedState() {
        if (savedPreferences == null) return // Assumption skipped before any mutation.
        try {
            if (receiverRegistered) stopAndAwaitCleanup() else ServiceManager.stop(context)
        } finally {
            try {
                originalConsentMode?.let { shell("appops set ${context.packageName} ACTIVATE_VPN $it") }
            } finally {
                try {
                    if (receiverRegistered) context.unregisterReceiver(statusReceiver)
                    savedUserFile?.let {
                        userFile.parentFile?.mkdirs()
                        userFile.writeBytes(it)
                    } ?: run { if (userFile.exists()) check(userFile.delete()) }
                } finally {
                    restorePreferences(savedPreferences.orEmpty())
                }
            }
        }
    }

    @Test(timeout = 30_000)
    fun nativeSocksAndTunStartStopAndStartAgain() {
        repeat(2) {
            startAndAwaitNativeTunnel()
            assertTrue("START must preserve desired connection state", preferences.getBoolean("service_enabled", false))
            assertTrue("A real TUN descriptor must be open", tunDescriptorCount() > originalTunCount)
            assertTrue("The HEV config must exist while its tunnel runs", temporaryConfigs().size > originalConfigs.size)
            assertSocks5Ready(proxyPort)
            stopAndAwaitCleanup()
            assertFalse("User STOP must persist", preferences.getBoolean("service_enabled", true))
        }
    }

    @Test(timeout = 30_000)
    fun routingFailureCleansStartedNativeProxyAndAllowsRetry() {
        settings.setManualApplications(emptySet())
        val beforeFailure = failed.get()
        ServiceManager.start(context, Mode.VPN)
        eventually("Empty routing must report VPN startup failure") { failed.get() > beforeFailure }
        awaitCleanResources()
        assertFalse(ByeDpiVpnService.isVpnActive)
        assertFalse("The native singleton must remain reusable", ServiceManager.isNativeProxyQuarantined)
        // The failed START had valid domains/native arguments but no installed allowed app.
        // Retrying proves partial startup did not leave the listener or JNI singleton behind.
        stopAndAwaitCleanup()
        settings.setManualApplications(setOf(helperPackage))
        startAndAwaitNativeTunnel()
        assertSocks5Ready(proxyPort)
        stopAndAwaitCleanup()
    }

    @Test(timeout = 20_000)
    fun userStopInvalidatesQueuedStartup() {
        ServiceManager.start(context, Mode.VPN)
        stopAndAwaitCleanup()
        assertFalse(preferences.getBoolean("service_enabled", true))
        assertTrue("A completed user STOP must leave the next explicit START available", ServiceManager.canStartVpn())
        assertFalse(ServiceManager.isStrategyTestInProgress)
    }

    @Test(timeout = 60_000)
    fun oneNativeCandidateTestsEverySelectedServiceAndRestoresVpn() {
        ServiceCatalog.profiles.forEach { settings.setServiceEnabled(it.id, true) }
        settings.setUserDomainsEnabled(false)
        startAndAwaitNativeTunnel()
        assertFalse("The isolated tester port must be available", isListening(1082))
        val command = StrategyTester.defaultStrategies.first()
        val evaluations = mutableListOf<StrategyEvaluation>()
        runBlocking {
            withTimeout(35_000) {
                StrategyTester(context).runTests(
                    excludedCommands = StrategyTester.defaultStrategies.drop(1).toSet(),
                    onProgress = { _, _, _ -> assertTrue(ServiceManager.isStrategyTestInProgress) },
                    onEvaluation = {
                        assertTrue("Testing gate remains held through native cleanup", ServiceManager.isStrategyTestInProgress)
                        assertFalse(ByeDpiVpnService.isVpnActive)
                        assertFalse(ByeDpiVpnService.hasProxyResources)
                        assertFalse("Candidate listener must close before evaluation", isListening(1082))
                        evaluations.add(it)
                    },
                )
            }
        }
        assertEquals(1, evaluations.size)
        assertEquals(command, evaluations.single().command)
        assertEquals(ServiceCatalog.profiles.map { it.id }, evaluations.single().services.map { it.serviceId })
        evaluations.single().services.forEach { service ->
            assertEquals(ServiceCatalog.get(service.serviceId)!!.testUrls, service.targets.map { it.url })
            assertTrue(service.targets.all { it.latencyMs >= 0 && (it.reachable || it.error != null) })
            assertTrue("The real native candidate must reach its probe path",
                service.targets.none { it.error?.startsWith("Локальный proxy не запустился") == true })
        }
        assertFalse(ServiceManager.isStrategyTestInProgress)
        assertFalse(ServiceManager.isNativeProxyQuarantined)
        assertTrue("Testing must preserve the prior desired VPN state", preferences.getBoolean("service_enabled", false))
        eventually("The original VPN must resume after candidate cleanup") { ByeDpiVpnService.isVpnActive }
        assertSocks5Ready(proxyPort)
        stopAndAwaitCleanup()
    }

    private fun startAndAwaitNativeTunnel() {
        ServiceManager.start(context, Mode.VPN)
        eventually("VPN must reach Connected with native/TUN resources") {
            ByeDpiVpnService.isVpnActive && ByeDpiVpnService.hasProxyResources
        }
        assertTrue("Connected must follow actual TUN establishment", tunDescriptorCount() > originalTunCount)
    }

    private fun stopAndAwaitCleanup() {
        val beforeStop = stopped.get()
        ServiceManager.stop(context)
        eventually("VPN must acknowledge STOP") { stopped.get() > beforeStop }
        awaitCleanResources()
    }

    private fun awaitCleanResources() {
        eventually("Native/TUN resources must stop within 8 seconds") {
            !ByeDpiVpnService.isVpnActive && !ByeDpiVpnService.hasProxyResources && !isListening(proxyPort)
        }
        assertEquals("TUN descriptors leaked", originalTunCount, tunDescriptorCount())
        assertEquals("HEV temporary config leaked", originalConfigs, temporaryConfigs())
    }

    private fun assertSocks5Ready(port: Int) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 1_000)
            socket.soTimeout = 1_000
            socket.getOutputStream().apply { write(byteArrayOf(5, 1, 0)); flush() }
            assertEquals("Native SOCKS version", 5, socket.getInputStream().read())
            assertEquals("Native SOCKS no-auth negotiation", 0, socket.getInputStream().read())
        }
    }

    private fun isListening(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 100) }
        true
    } catch (_: java.io.IOException) { false }

    private fun tunDescriptorCount(): Int = File("/proc/self/fd").listFiles().orEmpty().count {
        try { Os.readlink(it.absolutePath).let { path -> path == "/dev/tun" || path == "/dev/net/tun" } }
        catch (_: Exception) { false } // A different thread may close an unrelated fd during enumeration.
    }

    private fun temporaryConfigs(): Set<String> = context.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("config") && it.name.endsWith("tmp") }.map { it.name }.toSet()

    private fun eventually(message: String, timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun restorePreferences(snapshot: Map<String, Any?>) {
        val edit: SharedPreferences.Editor = preferences.edit().clear()
        snapshot.forEach { (key, value) ->
            when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is String -> edit.putString(key, value)
                is Set<*> -> edit.putStringSet(key, value.map { it as String }.toSet())
                null -> edit.remove(key)
                else -> error("Unsupported preference type for $key")
            }
        }
        assertTrue("Could not restore preferences", edit.commit())
    }

    companion object {
        private const val CONSENT_ARGUMENT = "maffinet.allowEmulatorVpnConsent"
    }
}
