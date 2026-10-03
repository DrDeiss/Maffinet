package io.maffinet.android.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.maffinet.android.R
import io.maffinet.android.BuildConfig
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.ui.components.ProductCard
import io.maffinet.android.ui.components.ProductScreen
import io.maffinet.android.ui.formatTimer
import androidx.compose.ui.platform.LocalContext
import io.maffinet.android.ui.components.configurationIsLocked

@Composable
fun HomeScreen(
    connected: Boolean,
    connecting: Boolean,
    vpnEnabled: Boolean,
    elapsedSeconds: Int,
    focusRequester: FocusRequester,
    onConnect: () -> Unit,
    onBackground: () -> Unit,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { MaffinetSettingsRepository(context) }
    var selected by remember { mutableStateOf(settings.enabledServiceIds()) }
    val locked = connected || connecting || StrategyTestManager.isTesting
    val command = settings.getString("byedpi_cmd_args", "")
    val evaluation = StrategyTestManager.matrixResults[command]
    val relevantEvaluation = evaluation?.takeIf { it.services.map { service -> service.serviceId }.toSet() == selected }
    ProductScreen("Maffinet", focusRequester, modifier,
        subtitle = if (BuildConfig.SHOW_UPSTREAM_ATTRIBUTION) stringResource(R.string.fork_marking) else null) {
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth().height(164.dp),
            enabled = !StrategyTestManager.isTesting,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_power), null, Modifier.size(40.dp))
                Text(when { connecting -> "Подключение…"; connected -> "Подключено"; else -> "Подключиться" },
                    style = MaterialTheme.typography.titleLarge)
                Text(if (connected) formatTimer(elapsedSeconds) else if (vpnEnabled) "Выбранные приложения" else "Telegram-прокси",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        ProductCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Сервисы", style = MaterialTheme.typography.titleLarge)
                TextButton({ onNavigate(5) }) { Text("Все") }
            }
            ServiceCatalog.profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = profile.id in selected, enabled = !locked, onCheckedChange = { enabled ->
                        if (!configurationIsLocked()) {
                            settings.setServiceEnabled(profile.id, enabled)
                            selected = settings.enabledServiceIds()
                        }
                    })
                }
            }
            if (locked) Text("Остановите подключение или проверку, чтобы изменить выбор.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!vpnEnabled) {
                Text("Включён режим только Telegram-прокси. Для выбранных сервисов требуется режим VPN.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ onNavigate(7) }) { Text("Изменить режим подключения") }
            }
        }
        ProductCard {
            Text("Стратегия", style = MaterialTheme.typography.titleLarge)
            Text(if (settings.getBoolean("strategy_manual_mode", false)) "Ручной выбор" else "Auto", fontWeight = FontWeight.SemiBold)
            Text(StrategyTestManager.getActiveStrategyName(context), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ onNavigate(2) }) { Text("Проверить стратегии") }
        }
        ProductCard {
            Text("Статус", style = MaterialTheme.typography.titleLarge)
            Text(relevantEvaluation?.let { "Последняя проверка: ${it.passedServices} / ${it.totalServices} сервисов доступны по HTTP/TLS" }
                ?: "Выбрано сервисов: ${selected.size}. Доступность ещё не проверена.")
            Text("Проверка HTTP/TLS показывает доступность сайта, а не все функции приложения.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onBackground, Modifier.fillMaxWidth(), enabled = !StrategyTestManager.isTesting) { Text("Работать в фоне и выйти") }
    }
}
