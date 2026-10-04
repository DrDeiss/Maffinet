package io.maffinet.android.ui.strategies

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.strategy.StrategyScorer
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.data.strategy.ProbeTargetRepository
import io.maffinet.android.ui.components.*

@Composable
fun StrategiesScreen(focusRequester: FocusRequester, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    val settings = remember { MaffinetSettingsRepository(context) }
    val targets = remember { ProbeTargetRepository(context) }
    val preferences = remember { context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    var expandedCommand by remember { mutableStateOf<String?>(null) }
    var editTargets by remember { mutableStateOf(false) }
    var targetText by remember { mutableStateOf("") }
    var targetError by remember { mutableStateOf<String?>(null) }
    var routeError by remember { mutableStateOf<String?>(null) }
    val locked = rememberConfigurationLocked()
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(revision) { StrategyTestManager.refreshConfiguration(context) }
    val urls = remember(revision) { targets.urls() }
    val linkedInRoute = remember(revision) { settings.linkedInAlternativeRouteEnabled() }
    val applied = settings.getString("byedpi_cmd_args", "")
        .takeIf { settings.getBoolean("byedpi_enable_cmd_settings", false) }
    val current = StrategyTestManager.historyMatchesCurrentConfiguration(context)
    val results = if (current) StrategyTestManager.matrixResults.values.sortedWith(StrategyScorer.comparator) else emptyList()

    if (editTargets) AlertDialog(
        onDismissRequest = { editTargets = false },
        title = { Text("Проверочные адреса") },
        text = {
            Column {
                Text("Один HTTP/HTTPS-адрес на строку. Список независим от приложений и Hosts.")
                OutlinedTextField(targetText, { targetText = it; targetError = null }, Modifier.fillMaxWidth(),
                    minLines = 4, maxLines = 8, enabled = !locked, label = { Text("Адреса") }, isError = targetError != null)
                targetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val parsed = targets.save(targetText)
                    if (parsed.isValid) {
                        StrategyTestManager.refreshConfiguration(context)
                        revision++
                        editTargets = false
                    } else targetError = parsed.errors.joinToString("\n")
                } catch (error: Exception) { targetError = error.message ?: "Не удалось сохранить адреса" }
            }, enabled = !locked) { Text("Сохранить") }
        },
        dismissButton = { TextButton({ editTargets = false }) { Text("Отмена") } },
    )

    ProductScreen("Стратегии", focusRequester, subtitle = "Auto применяет стратегию, только если все заданные адреса прошли проверку с текущими Hosts.") {
        ProductCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Альтернативный маршрут LinkedIn", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Switch(checked = linkedInRoute, onCheckedChange = { enabled ->
                    try {
                        settings.setLinkedInAlternativeRouteEnabled(enabled)
                        routeError = null
                        StrategyTestManager.refreshConfiguration(context)
                    } catch (error: Exception) { routeError = error.message ?: "Не удалось сохранить маршрут" }
                }, enabled = !locked)
            }
            Text("Использует альтернативный сервер LinkedIn для HTTPS-соединений с www.linkedin.com. Может помочь, если сайт или приложение не загружаются; результат зависит от сети.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Для VPN действует в выбранных приложениях. Обычная стратегия и Hosts сохраняются. Auto проверяет адреса с этим маршрутом, если он включён.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (locked) Text("Остановите подключение и проверку стратегий, чтобы изменить маршрут.", style = MaterialTheme.typography.bodySmall)
            routeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        ProductCard {
            Text("Auto strategy", style = MaterialTheme.typography.titleLarge)
            Text("Проверочных адресов: ${urls.size}")
            urls.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = {
                targetText = urls.joinToString("\n")
                targetError = null
                editTargets = true
            }, Modifier.fillMaxWidth(), enabled = !locked) { Text("Изменить проверочные адреса") }
            if (locked && !StrategyTestManager.isTesting) Text("Остановите подключение, чтобы изменить адреса.", style = MaterialTheme.typography.bodySmall)
            if (StrategyTestManager.isTesting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("${StrategyTestManager.currentTestIndex} / ${StrategyTestManager.totalStrategiesCount}")
                Text(StrategyTestManager.currentProgress, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ StrategyTestManager.cancelTesting() }, Modifier.fillMaxWidth()) { Text("Остановить проверку") }
            } else {
                Button(onClick = {
                    StrategyTestManager.startTesting(context)
                }, Modifier.fillMaxWidth(), enabled = urls.isNotEmpty()) { Text("Проверить стратегии") }
            }
            if (!StrategyTestManager.isTesting && StrategyTestManager.currentProgress.isNotBlank()) {
                Text(StrategyTestManager.currentProgress, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Выбор приложений и режима Telegram не меняет проверку. Каждый адрес проверяется через локальный SOCKS/ByeDPI; DNS-пресет относится к VPN.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Успех требует HTTP 200–399 и загрузки первых 64 КиБ тела либо всего меньшего ответа. Ответы без тела и редиректы допустимы. Проверка сайта не подтверждает работу всех API и функций приложения; защита сайта может давать ложный отрицательный результат.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (StrategyTestManager.hasStaleResults || !current) {
            Text("Hosts или параметры проверки изменились. Предыдущая матрица устарела; запустите проверку заново.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results.forEach { evaluation ->
            ProductCard {
                Text(StrategyTestManager.getStrategyName(evaluation.command, context), style = MaterialTheme.typography.titleLarge)
                Text("${evaluation.passedServices} / ${evaluation.totalServices} адресов прошли последнюю проверку")
                if (!evaluation.fullyReachable && evaluation.passedServices > 0) {
                    Text("Частичный результат: доступен для ручного выбора.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                evaluation.averageLatencyMs?.let { Text("Средняя задержка: $it мс", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                evaluation.services.forEach { targetGroup ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(targetGroup.serviceName, Modifier.weight(1f))
                        Text(if (targetGroup.passed) "OK · ${targetGroup.latencyMs} мс" else "FAIL",
                            color = if (targetGroup.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    targetGroup.targets.filter { !it.reachable || expandedCommand == evaluation.command }.forEach { target ->
                        val details = listOfNotNull(target.httpStatus?.let { "HTTP $it" }, target.error).joinToString(" · ")
                        Text("${target.url}\n${details.ifBlank { "Ответ не получен" }}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    TextButton({ expandedCommand = if (expandedCommand == evaluation.command) null else evaluation.command }, Modifier.weight(1f)) { Text("Подробности") }
                    Button({
                        settings.setBoolean("strategy_manual_mode", true)
                        StrategyTestManager.applyStrategy(context, evaluation.candidateIndex + 1, evaluation.command)
                    }, Modifier.weight(1f), enabled = !StrategyTestManager.isTesting) { Text(if (applied == evaluation.command) "Выбрана" else "Выбрать") }
                }
            }
        }
        if (results.isEmpty()) Text("Актуальных проверок ещё нет. Сохранённые стратегии доступны в мастере настройки.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProductSettingLink("Управление сохранёнными стратегиями", "Избранное, названия, заметки и ручная проверка", { onNavigate(6) }, enabled = !StrategyTestManager.isTesting)
        ProductSettingLink("Advanced settings", "Редактирование команды, импорт и экспорт", { onNavigate(8) }, enabled = !StrategyTestManager.isTesting)
    }
}
