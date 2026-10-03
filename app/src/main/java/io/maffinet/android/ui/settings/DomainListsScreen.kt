package io.maffinet.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.core.domains.BuiltInDomainLists
import io.maffinet.android.core.domains.DomainSourceDownloader
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.data.domains.DomainListRepository
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SAVED_EDITOR_BYTES = 64 * 1024

@Composable
fun DomainListsScreen(focusRequester: FocusRequester, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { DomainListRepository(context) }
    val settings = remember { MaffinetSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val locked = rememberConfigurationLocked()
    val editorTextSaver = remember {
        Saver<String, String>(
            save = { draft -> draft.takeIf {
                it.length <= MAX_SAVED_EDITOR_BYTES && it.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_EDITOR_BYTES
            } },
            restore = { it },
        )
    }
    var text by rememberSaveable(stateSaver = editorTextSaver) { mutableStateOf(repository.exportUserDomains()) }
    var enabled by remember { mutableStateOf(settings.userDomainsEnabled()) }
    var message by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var working by remember { mutableStateOf(false) }
    var showBuiltIn by rememberSaveable { mutableStateOf(false) }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val lists = remember(revision) { repository.getLists() }
    val activeDomainCount = remember(revision) { repository.activeDomains().size }
    val builtInDomains = remember { repository.builtInDomains() }
    val downloader = remember { DomainSourceDownloader() }
    fun configurationLocked(): Boolean = ConnectionCoordinator.isConfigurationLocked(context)

    fun save(importSource: (() -> String)? = null) {
        if (configurationLocked() || working) return
        working = true
        errors = emptyList()
        message = null
        val editedText = text
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (configurationLocked()) throw IllegalStateException("Остановите подключение или проверку.")
                    val imported = importSource?.invoke()
                    currentCoroutineContext().ensureActive()
                    if (configurationLocked()) throw IllegalStateException("Остановите подключение или проверку.")
                    if (imported != null) repository.importUserDomains(imported) else repository.saveUserDomains(editedText)
                }
                errors = result.errors.map { "Строка ${it.line}: ${it.input} — ${it.message}" }
                if (result.isValid) {
                    text = withContext(Dispatchers.IO) { repository.exportUserDomains() }
                    StrategyTestManager.refreshConfiguration(context)
                    revision++
                    message = "Сохранено доменов: ${result.domains.size}"
                } else message = if (importSource == null) "Исправьте строки ниже. Список не изменён."
                    else "Источник содержит ошибки. Список не изменён."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                message = exception.message ?: "Не удалось сохранить список"
            } finally { working = false }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) save {
            context.contentResolver.openInputStream(uri)?.use { DomainSourceDownloader.readUtf8(it) }
                ?: error("Не удалось открыть файл")
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
            if (showBuiltIn) BuiltInDomainLists.categories.forEach { category ->
                Text("${category.name} · ${category.domains.size}", style = MaterialTheme.typography.titleMedium)
                category.domains.forEach { domain -> Text(domain, style = MaterialTheme.typography.bodyMedium) }
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
            Button({ save() }, Modifier.fillMaxWidth(), enabled = !locked && !working) { Text(if (working) "Обработка…" else "Сохранить") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ importLauncher.launch(arrayOf("text/*", "application/octet-stream")) }, Modifier.weight(1f), enabled = !locked && !working) { Text("Импорт") }
                OutlinedButton({ exportLauncher.launch("maffinet-user-domains.txt") }, Modifier.weight(1f), enabled = !working) { Text("Экспорт") }
            }
            Text("Импорт объединяет источник с сохранённым списком. Перед импортом сохраните изменения редактора. Экспорт содержит сохранённые строки.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Импорт по HTTPS", style = MaterialTheme.typography.titleMedium)
            Text("Поддерживаются списки доменов и файлы hosts с IP и несколькими именами. Добавляются только домены: IP-подмены не применяются. Записи блокировки и локальных адресов пропускаются.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ sourceUrl = "https://raw.githubusercontent.com/ImMALWARE/dns.malw.link/refs/heads/master/hosts" },
                    Modifier.weight(1f), enabled = !locked && !working) { Text("dns.malw.link") }
                OutlinedButton({ sourceUrl = "https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts" },
                    Modifier.weight(1f), enabled = !locked && !working) { Text("GeoHide") }
            }
            OutlinedTextField(sourceUrl, { sourceUrl = it }, Modifier.fillMaxWidth(),
                enabled = !locked && !working, label = { Text("Прямая HTTPS-ссылка") },
                placeholder = { Text("https://example.org/hosts.txt") }, singleLine = true)
            OutlinedButton({ val requestedUrl = sourceUrl; save { downloader.download(requestedUrl) } },
                Modifier.fillMaxWidth(), enabled = !locked && !working && sourceUrl.isNotBlank()) { Text("Загрузить и добавить") }
            Text("Загрузка вручную, до 2 МБ. Пресет заполняет ссылку; источники могут менять содержимое. Для DNS этих сервисов используйте настройки DNS.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (locked) Text("Остановите подключение или проверку стратегий, чтобы изменять домены.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
