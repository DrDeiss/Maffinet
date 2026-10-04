package io.maffinet.android.ui.home

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.maffinet.android.BuildConfig
import io.maffinet.android.R
import io.maffinet.android.core.access.AutomaticAccessController
import io.maffinet.android.core.access.AutomaticAccessPhase
import io.maffinet.android.core.access.DnsConfigurationMonitor
import io.maffinet.android.core.access.DnsControlProbe
import io.maffinet.android.core.dns.DnsConfiguration
import io.maffinet.android.core.connection.ModeConnectionState
import io.maffinet.android.core.dns.DnsCatalog
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.ui.components.*
import io.maffinet.android.ui.formatTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(vpnState: ModeConnectionState, telegramState: ModeConnectionState, elapsedSeconds: Int,
    error: String?, focusRequester: FocusRequester, onConnect: () -> Unit, onBackground: () -> Unit,
    onNavigate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(context.packageName + "_preferences", 0) }
    val settings = remember { MaffinetSettingsRepository(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val applicationsEnabled = remember(revision) { settings.applicationsEnabled() }
    val telegramEnabled = remember(revision) { settings.telegramEnabled() }
    val selectedApps = remember(revision) { settings.manualApplications() }
    val dns = remember(revision) { settings.getString("custom_dns_preset", DnsPresets.DEFAULT) }
    val automaticAccess = remember(revision) { settings.automaticAccessEnabled() }
    val accessStatus by AutomaticAccessController.status.collectAsState()
    var dnsConfiguration by remember { mutableStateOf<DnsConfiguration?>(null) }
    var showDnsDetails by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        while (true) {
            dnsConfiguration = withContext(Dispatchers.IO) { runCatching { DnsConfigurationMonitor.read(context) }.getOrNull() }
            delay(500)
        }
    }
    val locked = rememberConfigurationLocked()
    val active = vpnState in ACTIVE_STATES || telegramState in ACTIVE_STATES || settings.anyModeRequested()
    val connected = (!applicationsEnabled || vpnState == ModeConnectionState.Running) &&
        (!telegramEnabled || telegramState == ModeConnectionState.Running) && (applicationsEnabled || telegramEnabled)
    val starting = vpnState == ModeConnectionState.Starting || telegramState == ModeConnectionState.Starting
    var showApps by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
                .sortedBy { it.applicationInfo?.loadLabel(context.packageManager)?.toString()?.lowercase() ?: it.packageName }
        }
    }
    DisposableEffect(showApps, showDns) {
        io.maffinet.android.data.isActionSheetVisibleGlobal = showApps || showDns
        onDispose { io.maffinet.android.data.isActionSheetVisibleGlobal = false }
    }
    ProductScreen("Maffinet", focusRequester, modifier,
        subtitle = if (BuildConfig.SHOW_UPSTREAM_ATTRIBUTION) stringResource(R.string.fork_marking) else null) {
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(164.dp).testTag("connect"),
            enabled = !StrategyTestManager.isTesting, shape = RoundedCornerShape(32.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_power), null, Modifier.size(40.dp))
                Text(when { starting -> "Подключение…"; connected -> "Подключено"; active -> "Остановить"; else -> "Подключиться" },
                    style = MaterialTheme.typography.titleLarge)
                Text(if (connected) formatTimer(elapsedSeconds) else if (active) "Нажмите, чтобы остановить" else "Запустить выбранные режимы")
            }
        }
        if (!applicationsEnabled && !telegramEnabled) Text("Включите «Приложения» или «Telegram», чтобы подключиться.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ProductCard {
            ModeRow("Приложения", applicationsEnabled, locked, "applications-mode") { settings.setApplicationsEnabled(it) }
            Text("VPN / ByeDPI: ${stateLabel(vpnState)}", Modifier.testTag("vpn-status"))
            if (automaticAccess && vpnState == ModeConnectionState.Running) {
                val evidenceCurrent = accessStatus.dnsConfiguration == dnsConfiguration && dnsConfiguration != null
                Text(if (!evidenceCurrent) "Автоматический доступ: обновляет состояние сети" else when (accessStatus.phase) {
                    AutomaticAccessPhase.IDLE -> "Автоматический доступ запускается"
                    AutomaticAccessPhase.OBSERVING -> "Автоматический доступ: ожидает подключения приложений"
                    AutomaticAccessPhase.CHECKING -> "Автоматический доступ: проверяет соединение"
                    AutomaticAccessPhase.ROUTE_APPLIED -> "Автоматический доступ: найден рабочий маршрут"
                    AutomaticAccessPhase.UNRESOLVED -> "Для одного из доменов рабочий маршрут пока не найден"
                }, Modifier.testTag("automatic-access-status"), style = MaterialTheme.typography.bodySmall,
                    color = if (accessStatus.phase == AutomaticAccessPhase.UNRESOLVED)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (evidenceCurrent && accessStatus.appliedRoutes > 0) Text("Проверенных маршрутов: ${accessStatus.appliedRoutes}",
                    style = MaterialTheme.typography.bodySmall)
            }
            ProductSettingLink("Выбрать приложения", "Выбрано: ${selectedApps.size}", { showApps = true }, !locked)
            ModeRow("Telegram", telegramEnabled, locked, "telegram-mode") { settings.setTelegramEnabled(it) }
            Text("Telegram-прокси: ${stateLabel(telegramState)}", Modifier.testTag("telegram-status"))
            ProductSettingLink("Настройки Telegram", "Отдельный MTProto-прокси", { onNavigate(1) }, !locked)
            if (locked) Text("Остановите подключение или проверку, чтобы изменить настройки.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProductSettingLink("DNS", DnsCatalog.selectionLabel(dns), { showDns = true }, !locked)
        Text("DNS применяется к VPN для выбранных приложений.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val currentDnsChecks = if (accessStatus.dnsConfiguration == dnsConfiguration) accessStatus.dnsChecks else emptyList()
        if (dnsConfiguration?.hasSelectionAssignmentMismatch == true) Text(
            "Выбор DNS изменён, но действующий VPN использует прежнее назначение. Переподключитесь для применения.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (accessStatus.checkingDns && accessStatus.dnsConfiguration == dnsConfiguration) Text(
            "Проверяем DNS и HTTPS контрольного домена…", style = MaterialTheme.typography.bodySmall)
        currentDnsChecks.firstOrNull { it.hasProblem }?.let { check ->
            Text(check.summary(), Modifier.testTag("dns-check-problem"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        TextButton({ showDnsDetails = !showDnsDetails }, Modifier.testTag("dns-details-toggle")) {
            Text(if (showDnsDetails) "Скрыть состояние DNS" else "Состояние и проверка DNS")
        }
        if (showDnsDetails) ProductCard {
            dnsConfiguration?.summaryLines()?.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
            Text("Пробы: обычный DNS по UDP, TCP при усечённом ответе. DoT/DoH не проверяются. " +
                "Контроль ${DnsControlProbe.HOST} не подтверждает доступ ко всем приложениям.", style = MaterialTheme.typography.bodySmall)
            if (currentDnsChecks.isEmpty()) Text("Для текущей конфигурации результатов пока нет.", style = MaterialTheme.typography.bodySmall)
            currentDnsChecks.forEach { Text(it.summary(), style = MaterialTheme.typography.bodySmall) }
        }
        ProductSettingLink("Режим доступа", if (automaticAccess) "Автоматически" else StrategyTestManager.getActiveStrategyName(context), { onNavigate(2) })
        ProductSettingLink("Hosts", if (automaticAccess) "Домены для ручного режима и импорта" else "Встроенный список и ваши домены", { onNavigate(9) })
        TextButton(onBackground, Modifier.fillMaxWidth(), enabled = !StrategyTestManager.isTesting) { Text("Работать в фоне и выйти") }
    }
    MaffinetAppsSheet(showApps, { showApps = false }, R.drawable.ic_settings, "Выбрать приложения",
        "Через VPN идут только выбранные приложения. Пустой выбор не запускает VPN.", installedApps, selectedApps,
        onAppToggled = { pkg ->
            if (!io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context) && pkg != context.packageName)
                settings.setManualApplications(if (pkg in selectedApps) selectedApps - pkg else selectedApps + pkg)
        })
    MaffinetDnsSheet(showDns, { showDns = false }, R.drawable.ic_settings, "DNS для VPN",
        "IPv4 в VPN без шифрования. Private DNS настраивается в Android.", DnsPresets.values, dns, onPresetSelected = {
            if (!io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)) settings.setString("custom_dns_preset", it)
            showDns = false
        })
}

@Composable
private fun ModeRow(title: String, checked: Boolean, locked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked, { if (!io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)) onChange(it) }, Modifier.testTag(tag), enabled = !locked)
    }
}

private val ACTIVE_STATES = setOf(ModeConnectionState.Starting, ModeConnectionState.Running, ModeConnectionState.Stopping)
private fun stateLabel(state: ModeConnectionState) = when (state) {
    ModeConnectionState.Stopped -> "остановлен"
    ModeConnectionState.Starting -> "запускается"
    ModeConnectionState.Running -> "работает"
    ModeConnectionState.Stopping -> "останавливается"
    ModeConnectionState.Failed -> "ошибка запуска; остановите и повторите"
}
