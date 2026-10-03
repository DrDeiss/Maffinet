package io.maffinet.android.ui.services

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.data.AppStatus
import io.maffinet.android.data.appStatus
import io.maffinet.android.ui.components.ProductCard
import io.maffinet.android.ui.components.ProductScreen
import io.maffinet.android.ui.components.ProductSettingLink
import io.maffinet.android.ui.components.rememberConfigurationLocked
import io.maffinet.android.ui.components.configurationIsLocked

@Composable
fun ServicesScreen(focusRequester: FocusRequester, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    val settings = remember { MaffinetSettingsRepository(context) }
    var selected by remember { mutableStateOf(settings.enabledServiceIds()) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val locked = rememberConfigurationLocked()
    val detail = detailId?.let { ServiceCatalog.get(it) }
    val evaluation = StrategyTestManager.matrixResults[settings.getString("byedpi_cmd_args", "")]
    androidx.activity.compose.BackHandler(detail != null) { detailId = null }
    ProductScreen(detail?.name ?: "Сервисы", focusRequester,
        subtitle = if (detail == null) "Выберите сервисы для подключения и автоматической проверки." else detail.provider,
        onBack = if (detail != null) ({ detailId = null }) else null) {
        val profiles = detail?.let { listOf(it) } ?: ServiceCatalog.profiles
        profiles.forEach { profile ->
            ProductCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleLarge)
                        Text(profile.provider, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(profile.id in selected, enabled = !locked, onCheckedChange = { enabled ->
                        if (!configurationIsLocked()) {
                            settings.setServiceEnabled(profile.id, enabled)
                            selected = settings.enabledServiceIds()
                        }
                    })
                }
                Text("${profile.domains.size} доменов · ${profile.packages.size} приложений")
                val result = evaluation?.services?.find { it.serviceId == profile.id }
                Text(when (result?.passed) { true -> "Последняя HTTP/TLS-проверка: OK"; false -> "Последняя HTTP/TLS-проверка: FAIL"; null -> "Доступность ещё не проверена" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (detail == null) {
                    TextButton({ detailId = profile.id }) { Text("Приложения и домены →") }
                } else {
                    HorizontalDivider()
                    Text("Android-приложения", style = MaterialTheme.typography.titleMedium)
                    profile.packages.sorted().forEach { packageName ->
                        val installed = remember(packageName) { runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess }
                        Text("$packageName${if (installed) " · установлено" else " · не установлено"}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Домены", style = MaterialTheme.typography.titleMedium)
                    profile.domains.sorted().forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text("Для включённых сервисов установленные приложения попадают в маршрут VPN, а домены — в фильтр ByeDPI.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (locked) Text("Для изменения выбора остановите подключение или тестирование.", style = MaterialTheme.typography.bodySmall)
        if (detail == null) ProductSettingLink("Custom · свои домены", "Пользовательский список, импорт и экспорт", { onNavigate(9) })
    }
}
