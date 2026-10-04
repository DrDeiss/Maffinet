package io.maffinet.android.network

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.SystemClock
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.maffinet.android.core.dpibypass.ByeDpiVpnService
import io.maffinet.android.core.dpibypass.ServiceManager
import io.maffinet.android.core.dpibypass.StrategyTester
import io.maffinet.android.core.dpibypass.getPreferences
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.core.connection.ModeConnectionState
import io.maffinet.android.core.tgproxy.TgProxyController
import io.maffinet.android.core.tgproxy.TgProxyService
import io.maffinet.android.service.WatchdogReceiver
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.core.strategy.StrategyEvaluation
import io.maffinet.android.data.Mode
import io.maffinet.android.data.domains.DomainListRepository
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
    private var statusObserver: Job? = null
    private var originalTunCount = 0
    private var originalConfigs = emptySet<String>()
    private var proxyPort = 0
    private lateinit var helperPackage: String
    private lateinit var settings: MaffinetSettingsRepository

    @Before
    fun isolateSettingsAndGrantConsentOnOptedInEmulator() {
        // This shell authorization must never bypass consent on a user's physical device.
        assumeTrue("Native VPN smoke requires explicit CI emulator opt-in",
            InstrumentationRegistry.getArguments().getString(CONSENT_ARGUMENT) == "true")
        assertTrue("Refusing simulated VPN consent outside a qemu emulator",
            shell("getprop ro.kernel.qemu").trim() == "1" || shell("getprop ro.boot.qemu").trim() == "1")
        assertEquals("The installed APK must target the current Android release", 36, context.applicationInfo.targetSdkVersion)
        assertFalse("A VPN was already running before this isolated smoke test", ByeDpiVpnService.isVpnActive)
        assertFalse("Native resources were already present", ByeDpiVpnService.hasProxyResources)
        assertFalse("Telegram resources were already present", TgProxyController.hasResources)
        assertFalse("A strategy scan was already running", ServiceManager.isStrategyTestInProgress)
        assertFalse("A previous native cleanup failed", ServiceManager.isNativeProxyQuarantined)

        savedPreferences = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        savedUserFile = userFile.takeIf { it.exists() }?.readBytes()
        originalTunCount = tunDescriptorCount()
        assertEquals("A TUN descriptor leaked before this isolated test", 0, originalTunCount)
        originalConfigs = temporaryConfigs()
        // Observe the same state as Home. Legacy implicit broadcasts are not a reliable
        // completion signal in modern instrumentation. Synchronous collection captures
        // even an already-empty pipeline's brief Stopping -> Stopped acknowledgement.
        statusObserver = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            var previous: ModeConnectionState? = null
            ByeDpiVpnService.currentStatus.collect { state ->
                if (state == ModeConnectionState.Stopped && previous == ModeConnectionState.Stopping) {
                    stopped.incrementAndGet()
                } else if (state == ModeConnectionState.Failed && previous != null) {
                    failed.incrementAndGet()
                }
                previous = state
            }
        }

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
        assertTrue(DomainListRepository(context).activeDomains().contains("example.com"))
        assertTrue("The built-in hosts remain active independently of legacy services", DomainListRepository(context).activeDomains().size > 1)
        settings.setApplicationsEnabled(true)
        settings.setTelegramEnabled(false)
        settings.setRequested(false, false)
        val tgPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        TgProxyController.setPort(context, tgPort)
        TgProxyController.setCfEnabled(context, false)

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
            if (statusObserver != null) {
                stopAndAwaitCleanup()
                stopTelegramAndAwaitCleanup()
            } else ConnectionCoordinator.stopAll(context)
        } finally {
            try {
                originalConsentMode?.let { shell("appops set ${context.packageName} ACTIVATE_VPN $it") }
            } finally {
                try {
                    statusObserver?.cancel()
                    statusObserver = null
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

    @Test(timeout = 40_000)
    fun modernForegroundServicesWorkWithDeniedOrGrantedNotifications() {
        assumeTrue("Runtime notification permission exists on Android 13+", Build.VERSION.SDK_INT >= 33)
        val expected = InstrumentationRegistry.getArguments().getString("maffinet.expectedNotificationPermission")
        assumeTrue("The emulator harness must choose the notification permission state", expected == "granted" || expected == "denied")
        val granted = expected == "granted"
        assertEquals("The harness must apply notification permission outside the instrumented UID", granted,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        preferences.edit().putBoolean("notification_permission_requested", true).commit()
        settings.setTelegramEnabled(true)
        val started = ConnectionCoordinator.startSelected(context)
        assertTrue(started.errors.toString(), started.errors.isEmpty())
        eventually("VPN and Telegram FGS must run even after the user denies notifications") {
            ByeDpiVpnService.isVpnActive && ByeDpiVpnService.hasProxyResources &&
                TgProxyController.status.value == ModeConnectionState.Running && TgProxyController.hasResources
        }
        assertTrue("Modern FGS startup must establish the real TUN", tunDescriptorCount() > originalTunCount)
        assertSocks5Ready(proxyPort)
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(granted, notifications.areNotificationsEnabled())
        if (granted) {
            eventually("Granted permission must expose the Telegram foreground notification") {
                notifications.activeNotifications.any { it.id == 100 &&
                    (it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 }
            }
        }
        ConnectionCoordinator.stopAll(context)
        eventually("Modern user STOP must release both foreground services") {
            !ByeDpiVpnService.isVpnActive && !ByeDpiVpnService.hasProxyResources && !TgProxyController.hasResources &&
                TgProxyController.status.value == ModeConnectionState.Stopped
        }
        assertFalse(settings.anyModeRequested())
        awaitCleanResources()
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
        startAndAwaitNativeTunnel()
        ServiceManager.restart(context, Mode.VPN)
        stopAndAwaitCleanup(common = true)
        ConnectionCoordinator.recover(context)
        SystemClock.sleep(350) // Allow the pending restart's stopped-pipeline continuation to run.
        assertFalse("Common user STOP must invalidate a pending VPN restart", ByeDpiVpnService.hasProxyResources)
        assertFalse(ByeDpiVpnService.isVpnActive)
        assertFalse(settings.anyModeRequested())
    }

    @Test(timeout = 30_000)
    fun telegramOnlyStartsStopsRestartsAndNeverCreatesVpn() {
        settings.setApplicationsEnabled(false)
        settings.setTelegramEnabled(true)
        repeat(2) {
            val result = ConnectionCoordinator.startSelected(context)
            assertTrue(result.errors.toString(), result.errors.isEmpty())
            assertFalse(result.startedApplications)
            assertTrue(result.startedTelegram)
            awaitTelegramRunning()
            val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            eventually("Telegram notification must describe the running proxy") {
                notifications.activeNotifications.firstOrNull { it.id == 100 }?.notification
                    ?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.contains("Telegram-прокси работает") == true
            }
            val notification = notifications.activeNotifications.first { it.id == 100 }.notification
            assertEquals("Telegram exposes only its supported STOP action", listOf("Выключить"), notification.actions.map { it.title.toString() })
            assertFalse(notification.actions.any { it.title.toString().contains("Приостановить") })
            assertFalse(ByeDpiVpnService.isVpnActive)
            assertFalse(ByeDpiVpnService.hasProxyResources)
            assertFalse(settings.applicationsRequested())
            assertTrue(settings.telegramRequested())
            // A VPN-only STOP must not clear Telegram's recovery intent or resources.
            stopAndAwaitCleanup()
            WatchdogReceiver().onReceive(context, Intent("io.maffinet.android.action.WATCHDOG_PING"))
            assertTrue(settings.telegramRequested())
            assertEquals(ModeConnectionState.Running, TgProxyController.status.value)
            if (it == 0) {
                // Exercise the actual notification's direct STOP, including desired-state clearing.
                notification.actions.single().actionIntent.send()
                eventually("Telegram notification STOP must close the listener and persist") {
                    !settings.telegramRequested() && !TgProxyController.hasResources &&
                        TgProxyController.status.value == ModeConnectionState.Stopped
                }
            } else stopTelegramAndAwaitCleanup()
            ConnectionCoordinator.recover(context)
            assertFalse(settings.anyModeRequested())
            assertFalse(TgProxyController.hasResources)
        }
    }

    @Test(timeout = 40_000)
    fun bothModesAndEachIndependentStopKeepTheOtherRunning() {
        settings.setApplicationsEnabled(true)
        settings.setTelegramEnabled(true)
        val beforeHosts = DomainListRepository(context).activeDomains()
        val result = ConnectionCoordinator.startSelected(context)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        eventually("Both modes should start") { ByeDpiVpnService.isVpnActive && TgProxyController.status.value == ModeConnectionState.Running }
        stopTelegramAndAwaitCleanup()
        assertTrue("Telegram STOP must preserve VPN", ByeDpiVpnService.isVpnActive)
        assertTrue(settings.applicationsRequested())
        assertFalse(settings.telegramRequested())
        // A stale legacy global flag must never disable the remaining VPN.
        preferences.edit().putBoolean("service_enabled", false).putBoolean("wants_youtube_bypass", false).commit()
        ConnectionCoordinator.recover(context)
        assertTrue(ByeDpiVpnService.isVpnActive)
        TgProxyService.requestStart(context)
        awaitTelegramRunning()
        stopAndAwaitCleanup()
        assertTrue("VPN STOP must preserve Telegram", TgProxyController.hasResources)
        assertTrue(settings.telegramRequested())
        assertEquals(beforeHosts, DomainListRepository(context).activeDomains())
        assertEquals(setOf(helperPackage), settings.manualApplications())
        ConnectionCoordinator.stopAll(context)
        eventually("Common STOP must close Telegram too") { !TgProxyController.hasResources && TgProxyController.status.value == ModeConnectionState.Stopped }
        assertFalse(settings.anyModeRequested())
        assertFalse(ByeDpiVpnService.hasProxyResources)
    }

    @Test(timeout = 20_000)
    fun noModesAndEmptyAppsDoNotStartDeviceWideVpn() {
        settings.setApplicationsEnabled(false)
        settings.setTelegramEnabled(false)
        val none = ConnectionCoordinator.startSelected(context)
        assertFalse(none.hasSelectedModes)
        assertTrue(none.errors.single().contains("Включите"))
        settings.setApplicationsEnabled(true)
        settings.setManualApplications(emptySet())
        val empty = ConnectionCoordinator.startSelected(context)
        assertFalse(empty.startedApplications)
        assertTrue(empty.errors.single().contains("приложение"))
        assertFalse(settings.anyModeRequested())
        assertFalse(ByeDpiVpnService.hasProxyResources)
    }

    @Test(timeout = 30_000)
    fun telegramStillStartsWhenApplicationSelectionFails() {
        settings.setApplicationsEnabled(true)
        settings.setTelegramEnabled(true)
        settings.setManualApplications(emptySet())
        val result = ConnectionCoordinator.startSelected(context)
        assertFalse(result.startedApplications)
        assertTrue(result.startedTelegram)
        assertTrue(result.errors.single().contains("приложение"))
        assertEquals("Background/autostart errors must also remain visible to Home", result.errors, ConnectionCoordinator.startErrors.value)
        awaitTelegramRunning()
        assertFalse(settings.applicationsRequested())
        assertTrue(settings.telegramRequested())
        assertFalse(ByeDpiVpnService.hasProxyResources)
        stopTelegramAndAwaitCleanup()
    }

    @Test(timeout = 30_000)
    fun telegramBindFailureDoesNotStopConnectedVpn() {
        startAndAwaitNativeTunnel()
        settings.setTelegramEnabled(true)
        ServerSocket(TgProxyController.getPort(context), 1, InetAddress.getByName("127.0.0.1")).use {
            TgProxyService.requestStart(context)
            eventually("Occupied Telegram port must report Failed rather than a foreign listener as Running") {
                TgProxyController.status.value == ModeConnectionState.Failed
            }
            assertFalse(TgProxyController.hasResources)
            assertTrue(ByeDpiVpnService.isVpnActive)
            assertTrue(settings.applicationsRequested())
            stopTelegramAndAwaitCleanupWhilePortOccupied()
        }
        TgProxyService.requestStart(context)
        awaitTelegramRunning()
        assertTrue("Telegram retry should preserve VPN", ByeDpiVpnService.isVpnActive)
        stopTelegramAndAwaitCleanup()
    }

    @Test(timeout = 30_000)
    fun bootHonorsAutostartAndWatchdogRecoversOnlyTheRequestedMode() {
        settings.setApplicationsEnabled(true)
        settings.setTelegramEnabled(true)
        settings.setRequested(true, true)
        preferences.edit().putBoolean("autostart", false).commit()
        ConnectionCoordinator.recover(context, boot = true)
        assertFalse("Disabled boot autostart must not establish saved VPN requests", ByeDpiVpnService.hasProxyResources)
        assertFalse("Disabled boot autostart must not establish saved Telegram requests", TgProxyController.hasResources)
        assertFalse("Disabled boot autostart must forget the previous boot's session requests", settings.anyModeRequested())
        ConnectionCoordinator.recover(context)
        WatchdogReceiver().onReceive(context, Intent("io.maffinet.android.action.WATCHDOG_PING"))
        assertFalse("A later watchdog recovery must not revive a session from the previous boot", settings.anyModeRequested())
        assertFalse(ByeDpiVpnService.hasProxyResources)
        assertFalse(TgProxyController.hasResources)
        assertTrue("Boot recovery must preserve the saved economy choice", preferences.getBoolean("econom_mode", false))
        settings.setRequested(false, false)
        settings.setApplicationsEnabled(false)
        preferences.edit().putBoolean("autostart", true).commit()
        ConnectionCoordinator.recover(context, boot = true)
        awaitTelegramRunning()
        assertFalse(settings.applicationsRequested())
        assertFalse(ByeDpiVpnService.hasProxyResources)
        stopTelegramAndAwaitCleanup()
        settings.setApplicationsEnabled(true)
        settings.setRequested(false, true)
        preferences.edit().putBoolean("service_enabled", false).putBoolean("wants_youtube_bypass", true).commit()
        WatchdogReceiver().onReceive(context, Intent("io.maffinet.android.action.WATCHDOG_PING"))
        awaitTelegramRunning()
        assertFalse("VPN selection alone must not create a watchdog VPN request", settings.applicationsRequested())
        assertFalse(ByeDpiVpnService.hasProxyResources)
        stopTelegramAndAwaitCleanup()
    }

    private fun stopTelegramAndAwaitCleanupWhilePortOccupied() {
        TgProxyService.requestStop(context)
        eventually("No owned Telegram resources should remain") {
            TgProxyController.status.value == ModeConnectionState.Stopped && !TgProxyController.hasResources
        }
    }

    @Test(timeout = 30_000)
    fun dnsAndHostsPersistAcrossVpnRestartWithoutChangingTelegramSettings() {
        settings.setString("custom_dns_preset", "Cloudflare Secure DNS")
        val secret = TgProxyController.getOrGenerateSecret(context)
        val port = TgProxyController.getPort(context)
        val domains = DomainListRepository(context).activeDomains()
        startAndAwaitNativeTunnel()
        assertVpnDns(setOf("1.1.1.1", "1.0.0.1"))
        val before = stopped.get()
        ServiceManager.restart(context, Mode.VPN)
        eventually("Restart should close the old VPN") { stopped.get() > before }
        eventually("Restart should establish VPN again") { ByeDpiVpnService.isVpnActive }
        assertSocks5Ready(proxyPort)
        assertVpnDns(setOf("1.1.1.1", "1.0.0.1"))
        assertEquals("Cloudflare Secure DNS", settings.getString("custom_dns_preset", ""))
        assertEquals(domains, DomainListRepository(context).activeDomains())
        assertEquals(setOf(helperPackage), settings.manualApplications())
        assertEquals(secret, TgProxyController.getOrGenerateSecret(context))
        assertEquals(port, TgProxyController.getPort(context))
        assertFalse(settings.telegramRequested())
        assertFalse(TgProxyController.hasResources)
    }

    private fun awaitTelegramRunning() {
        eventually("Telegram's real native listener must start") {
            TgProxyController.status.value == ModeConnectionState.Running && TgProxyController.hasResources &&
                isListening(TgProxyController.getPort(context))
        }
    }

    private fun assertVpnDns(expected: Set<String>) {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        eventually("VPN builder DNS must reach the Android VPN link properties") {
            connectivity.allNetworks.any { network ->
                connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                    connectivity.getLinkProperties(network)?.dnsServers?.map { it.hostAddress }?.toSet() == expected
            }
        }
    }

    private fun stopTelegramAndAwaitCleanup() {
        TgProxyService.requestStop(context)
        eventually("Telegram STOP must close the native listener") {
            TgProxyController.status.value == ModeConnectionState.Stopped && !TgProxyController.hasResources &&
                !isListening(TgProxyController.getPort(context))
        }
        assertFalse(settings.telegramRequested())
    }

    @Test(timeout = 60_000)
    fun oneNativeCandidateTestsIndependentTargetsAndRestoresVpn() {
        val urls = listOf("https://example.com", "https://www.wikipedia.org")
        assertTrue(io.maffinet.android.data.strategy.ProbeTargetRepository(context).save(urls.joinToString("\n")).isValid)
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
        assertEquals(listOf("probe_0", "probe_1"), evaluations.single().services.map { it.serviceId })
        evaluations.single().services.forEachIndexed { index, service ->
            assertEquals(listOf(urls[index]), service.targets.map { it.url })
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

    private fun stopAndAwaitCleanup(common: Boolean = false) {
        val beforeStop = stopped.get()
        if (common) ConnectionCoordinator.stopAll(context) else ServiceManager.stop(context)
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
