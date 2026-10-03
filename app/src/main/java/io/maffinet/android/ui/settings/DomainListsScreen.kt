package io.maffinet.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.data.domains.DomainListRepository
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DomainListsScreen(focusRequester: FocusRequester, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { DomainListRepository(context) }
    val settings = remember { MaffinetSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val locked = rememberConfigurationLocked()
    var text by rememberSaveable { mutableStateOf(repository.exportUserDomains()) }
    var enabled by remember { mutableStateOf(settings.userDomainsEnabled()) }
    var message by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var working by remember { mutableStateOf(false) }
    var showBuiltIn by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val lists = remember(revision) { repository.getLists() }
    val activeDomainCount = remember(revision) { repository.activeDomains().size }
    val builtInDomains = remember { repository.builtInDomains() }
    fun configurationLocked(): Boolean = ConnectionCoordinator.isConfigurationLocked(context)

    fun save(importText: String? = null) {
        if (configurationLocked() || working) return
        working = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (configurationLocked()) throw IllegalStateException("Остановите подключение или проверку.")
                    if (importText != null) repository.importUserDomains(importText) else repository.saveUserDomains(text)
                }
                errors = result.errors.map { "Строка ${it.line}: ${it.input} — ${it.message}" }
                if (result.isValid) {
                    text = withContext(Dispatchers.IO) { repository.exportUserDomains() }
                    StrategyTestManager.refreshConfiguration(context)
                    revision++
                    message = "Сохранено доменов: ${result.domains.size}"
                } else message = "Исправьте строки ниже. Список не изменён."
            } catch (exception: Exception) {
                message = exception.message ?: "Не удалось сохранить список"
            } finally { working = false }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !configurationLocked()) scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val buffer = CharArray(512 * 1024 + 1)
                        var count = 0
                        while (count < buffer.size) {
                            val read = reader.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        require(count <= 512 * 1024) { "Список превышает 512 КБ" }
                        String(buffer, 0, count)
                    } ?: error("Не удалось открыть файл")
                }
                save(imported)
            } catch (exception: Exception) { message = exception.message ?: "Ошибка импорта" }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(repository.exportUserDomains().toByteArray(Charsets.UTF_8)) }
                        ?: error("Не удалось создать файл")
                }
                message = "Сохранённый пользовательский список экспортирован"
            } catch (exception: Exception) { message = exception.message ?: "Ошибка экспорта" }
        }
    }

    ProductScreen("Hosts", focusRequester, onBack = onBack,
        subtitle = "Домены для DPI bypass: встроенный General и ваше дополнение User. Выбор приложений не меняет hosts.") {
        ProductCard {
            Text("General · встроенный список", style = MaterialTheme.typography.titleLarge)
            Text("Базовых доменов: ${builtInDomains.size}. При стандартном фильтре User расширяет General. Явный Advanced override заменяет этот фильтр.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ showBuiltIn = !showBuiltIn }, Modifier.fillMaxWidth()) {
                Text(if (showBuiltIn) "Скрыть General" else "Показать General")
            }
            if (showBuiltIn) builtInDomains.forEach { domain ->
                Text(domain, style = MaterialTheme.typography.bodyMedium)
            }
            Text("Активных доменов: $activeDomainCount", style = MaterialTheme.typography.bodySmall)
        }
        ProductCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("User · ваши домены", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Switch(enabled, enabled = !locked && !working, onCheckedChange = { value ->
                    if (!configurationLocked()) {
                        repository.setUserDomainsEnabled(value)
                        StrategyTestManager.refreshConfiguration(context)
                        enabled = value
                        revision++
                    }
                })
            }
            Text("Один домен в строке. Можно добавлять, изменять и удалять строки. Комментарии начинаются с #.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Сохранённых доменов: ${lists.first { it.id == "user" }.domains.size}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { text = it; errors = emptyList(); message = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp), enabled = !locked && !working,
                label = { Text("Ваши домены") }, placeholder = { Text("example.com\nexample.net") },
                isError = errors.isNotEmpty())
            errors.take(10).forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (errors.size > 10) Text("Ещё ошибок: ${errors.size - 10}", color = MaterialTheme.colorScheme.error)
            Button({ save() }, Modifier.fillMaxWidth(), enabled = !locked && !working) { Text(if (working) "Сохранение…" else "Сохранить") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ importLauncher.launch(arrayOf("text/*", "application/octet-stream")) }, Modifier.weight(1f), enabled = !locked && !working) { Text("Импорт") }
                OutlinedButton({ exportLauncher.launch("maffinet-user-domains.txt") }, Modifier.weight(1f), enabled = !working) { Text("Экспорт") }
            }
            Text("Импорт объединяет файл с сохранённым списком. Экспорт содержит сохранённые строки.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (locked) Text("Остановите подключение или проверку стратегий, чтобы изменять домены.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
