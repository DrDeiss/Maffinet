package io.maffinet.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import io.maffinet.android.core.domains.DomainParser
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.ui.components.*

private data class ExpertField(val key: String, val title: String, val default: String, val numeric: Boolean = false)
private val expertFields = listOf(
    ExpertField("byedpi_proxy_ip", "Адрес локального SOCKS", "127.0.0.1"),
    ExpertField("byedpi_proxy_port", "Порт SOCKS", "1080", true),
    ExpertField("byedpi_max_connections", "Максимум соединений", "512", true),
    ExpertField("byedpi_buffer_size", "Размер буфера", "16384", true),
    ExpertField("byedpi_default_ttl", "Default TTL", "0", true),
    ExpertField("byedpi_split_position", "Split position", "1", true),
    ExpertField("byedpi_fake_ttl", "Fake TTL", "8", true),
    ExpertField("byedpi_fake_sni", "Fake SNI", "www.iana.org"),
    ExpertField("byedpi_oob_data", "OOB character", "a"),
    ExpertField("byedpi_fake_offset", "Fake offset", "0", true),
    ExpertField("byedpi_tlsrec_position", "TLS record split position", "0", true),
    ExpertField("byedpi_udp_fake_count", "UDP fake count", "1", true)
)
private val expertSwitches = listOf(
    Triple("byedpi_desync_https", "HTTPS desync", true),
    Triple("byedpi_desync_http", "HTTP desync", true),
    Triple("byedpi_desync_udp", "UDP desync", true),
    Triple("byedpi_no_domain", "Обрабатывать соединения без домена", false),
    Triple("byedpi_split_at_host", "Split at Host", false),
    Triple("byedpi_host_mixed_case", "Host mixed case", false),
    Triple("byedpi_domain_mixed_case", "Domain mixed case", false),
    Triple("byedpi_host_remove_spaces", "Remove Host spaces", false),
    Triple("byedpi_tlsrec_enabled", "TLS record split", false),
    Triple("byedpi_tlsrec_at_sni", "TLS record split at SNI", false),
    Triple("byedpi_tcp_fast_open", "TCP Fast Open", false),
    Triple("byedpi_drop_sack", "Drop SACK", false),
    Triple("ipv6_enable", "IPv6 в VPN", false)
)

@Composable
fun AdvancedScreen(focusRequester: FocusRequester, onBack: () -> Unit, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    val settings = remember { MaffinetSettingsRepository(context) }
    val locked = rememberConfigurationLocked()
    var commandMode by remember { mutableStateOf(settings.getBoolean("byedpi_enable_cmd_settings", false)) }
    var command by remember { mutableStateOf(settings.getString("byedpi_cmd_args", "-o1 -a1 -r-5+se")) }
    var override by remember { mutableStateOf(settings.hostFilterOverride()) }
    var hostsMode by remember { mutableStateOf(settings.getString("byedpi_hosts_mode", "disable")) }
    var whitelist by remember { mutableStateOf(settings.getString("byedpi_hosts_whitelist", "")) }
    var blacklist by remember { mutableStateOf(settings.getString("byedpi_hosts_blacklist", "")) }
    var method by remember { mutableStateOf(settings.getString("byedpi_desync_method", "oob")) }
    val strings = remember { mutableStateMapOf<String, String>().apply { expertFields.forEach { put(it.key, settings.getString(it.key, it.default)) } } }
    val switches = remember { mutableStateMapOf<String, Boolean>().apply { expertSwitches.forEach { put(it.first, settings.getBoolean(it.first, it.third)) } } }
    var message by remember { mutableStateOf<String?>(null) }
    ProductScreen("Advanced · ByeDPI", focusRequester, onBack = onBack,
        subtitle = "Сохранение включает ручной режим для следующего подключения. Остановите VPN и проверку стратегий.") {
        ProductCard {
            Text("Команда стратегии", style = MaterialTheme.typography.titleLarge)
            ExpertSwitch("Использовать команду вместо UI-настроек", commandMode, !locked) { commandMode = it }
            OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), enabled = !locked,
                label = { Text("Аргументы ByeDPI") }, minLines = 3)
            Text("{domains} и {list:general} используют активные списки. Совместимость с {sni} сохранена.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProductCard {
            Text("Host filtering", style = MaterialTheme.typography.titleLarge)
            ExpertSwitch("Переопределить фильтр Hosts", override, !locked) { override = it }
            Text(if (override && commandMode) "Автоматический фильтр Hosts выключен. В командном режиме задайте host filtering аргументом -H в команде. UI host mode ниже не используется."
                else if (override) "Применяются ваши UI host mode и host lists. Disable отключает выборочный фильтр."
                else "Maffinet применяет активные активные Hosts и пользовательского списка.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExpertChoice("Hosts mode · UI", hostsMode, listOf("disable", "whitelist", "blacklist"), !locked && override && !commandMode) { hostsMode = it }
            if (hostsMode != "disable") OutlinedTextField(if (hostsMode == "whitelist") whitelist else blacklist,
                { if (hostsMode == "whitelist") whitelist = it else blacklist = it }, Modifier.fillMaxWidth(),
                enabled = !locked && override && !commandMode, label = { Text("Домены · один в строке") }, minLines = 4)
        }
        ProductCard {
            Text("UI-параметры десинхронизации", style = MaterialTheme.typography.titleLarge)
            Text("Используются, когда командный режим выключен.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExpertChoice("Desync method", method, listOf("none", "split", "disorder", "fake", "oob", "disoob"), !locked) { method = it }
            expertFields.forEach { field ->
                OutlinedTextField(strings[field.key].orEmpty(), { strings[field.key] = it }, Modifier.fillMaxWidth(),
                    enabled = !locked, label = { Text(field.title) }, singleLine = true)
            }
            expertSwitches.forEach { (key, title, _) -> ExpertSwitch(title, switches[key] == true, !locked) { switches[key] = it } }
        }
        Button(onClick = {
            if (!configurationIsLocked()) {
                val invalid = expertFields.firstOrNull { it.numeric && strings[it.key]?.toIntOrNull() == null }
                val port = strings["byedpi_proxy_port"]?.toIntOrNull()
                val hosts = DomainParser.parse(if (hostsMode == "whitelist") whitelist else blacklist)
                message = when {
                    invalid != null -> "${invalid.title}: требуется целое число"
                    port == null || port !in 1..65535 -> "Порт должен быть от 1 до 65535"
                    strings["byedpi_oob_data"].isNullOrEmpty() -> "Укажите OOB character"
                    strings["byedpi_proxy_ip"].isNullOrBlank() -> "Укажите адрес локального SOCKS"
                    commandMode && command.isBlank() -> "Укажите команду стратегии"
                    override && !commandMode && hostsMode != "disable" && !hosts.isValid -> hosts.errors.joinToString("\n") { "${it.line}: ${it.message}" }
                    override && !commandMode && hostsMode != "disable" && hosts.domains.isEmpty() -> "Добавьте домены для выбранного UI host filter"
                    else -> {
                        try {
                            settings.setAutomaticAccessEnabled(false)
                            settings.setBoolean("byedpi_enable_cmd_settings", commandMode)
                            settings.setString("byedpi_cmd_args", command)
                            settings.setHostFilterOverride(override)
                            settings.setString("byedpi_hosts_mode", hostsMode)
                            settings.setString("byedpi_hosts_whitelist", if (hostsMode == "whitelist") hosts.domains.joinToString("\n") else whitelist)
                            settings.setString("byedpi_hosts_blacklist", if (hostsMode == "blacklist") hosts.domains.joinToString("\n") else blacklist)
                            settings.setString("byedpi_desync_method", method)
                            strings.forEach { (key, value) -> settings.setString(key, value) }
                            switches.forEach { (key, value) -> settings.setBoolean(key, value) }
                            "Настройки сохранены · ручной режим включён"
                        } catch (error: Exception) { error.message ?: "Не удалось сохранить ручной режим" }
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !locked) { Text("Сохранить Advanced settings") }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (locked) Text("Сохранение доступно после остановки подключения и проверки стратегий.")
        ProductSettingLink("DNS, приложения и стратегии", "Прежние настройки, импорт и экспорт JSON", { onNavigate(7) })
    }
}

@Composable
private fun ExpertSwitch(title: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(value, onChange, enabled = enabled)
    }
}

@Composable
private fun ExpertChoice(title: String, value: String, choices: List<String>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$title: $value ▾") }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { onChange(choice); expanded = false }) }
        }
    }
}
