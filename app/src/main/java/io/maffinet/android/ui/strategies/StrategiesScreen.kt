package io.maffinet.android.ui.strategies

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.strategy.StrategyScorer
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.ui.components.*

@Composable
fun StrategiesScreen(focusRequester: FocusRequester, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    val settings = remember { MaffinetSettingsRepository(context) }
    var expandedCommand by remember { mutableStateOf<String?>(null) }
    val applied = settings.getString("byedpi_cmd_args", "")
    val selected = settings.enabledServiceIds()
    val results = StrategyTestManager.matrixResults.values.sortedWith(StrategyScorer.comparator)
    ProductScreen("Стратегии", focusRequester, subtitle = "Auto выбирает максимальное покрытие сервисов, затем меньшую задержку.") {
        ProductCard {
            Text("Auto strategy", style = MaterialTheme.typography.titleLarge)
            Text("Выбрано сервисов: ${selected.size}")
            if (StrategyTestManager.isTesting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("${StrategyTestManager.currentTestIndex} / ${StrategyTestManager.totalStrategiesCount}")
                Text(StrategyTestManager.currentProgress, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ StrategyTestManager.cancelTesting() }, Modifier.fillMaxWidth()) { Text("Остановить проверку") }
            } else {
                Button(onClick = {
                    settings.setBoolean("strategy_manual_mode", false)
                    StrategyTestManager.startTesting(context)
                }, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty()) { Text("Проверить выбранные сервисы") }
            }
            if (selected.isEmpty()) Text("Включите хотя бы один сервис для автоматической проверки.")
            if (!StrategyTestManager.isTesting && StrategyTestManager.currentProgress.isNotBlank()) {
                Text(StrategyTestManager.currentProgress, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Это HTTP/TLS-проверки сайтов. Успех требует HTTP 200–399; защита сайта может давать ложный отрицательный результат.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results.forEach { evaluation ->
            ProductCard {
                Text(StrategyTestManager.getStrategyName(evaluation.command, context), style = MaterialTheme.typography.titleLarge)
                Text("${evaluation.passedServices} / ${evaluation.totalServices} сервисов прошли последнюю проверку")
                if (evaluation.services.map { it.serviceId }.toSet() != selected) {
                    Text("Состав сервисов отличается от текущего выбора.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                evaluation.averageLatencyMs?.let { Text("Средняя задержка: $it мс", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                evaluation.services.forEach { service ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(service.serviceName)
                        Text(if (service.passed) "OK · ${service.latencyMs} мс" else "FAIL",
                            color = if (service.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    if (expandedCommand == evaluation.command) service.targets.forEach { target ->
                        Text("${target.url}\n${target.httpStatus?.let { "HTTP $it" } ?: target.error ?: "Ответ не получен"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    TextButton({ expandedCommand = if (expandedCommand == evaluation.command) null else evaluation.command }, Modifier.weight(1f)) { Text("Подробности") }
                    Button({
                        settings.setBoolean("strategy_manual_mode", true)
                        StrategyTestManager.applyStrategy(context, evaluation.candidateIndex, evaluation.command)
                    }, Modifier.weight(1f), enabled = !StrategyTestManager.isTesting) { Text(if (applied == evaluation.command) "Выбрана" else "Выбрать") }
                }
            }
        }
        if (results.isEmpty()) Text("Проверок ещё нет. Существующие сохранённые стратегии доступны в мастере настройки.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProductSettingLink("Управление сохранёнными стратегиями", "Избранное, названия, заметки и ручная проверка", { onNavigate(6) }, enabled = !StrategyTestManager.isTesting)
        ProductSettingLink("Advanced settings", "Редактирование команды, DNS, импорт и экспорт", { onNavigate(8) }, enabled = !StrategyTestManager.isTesting)
    }
}
