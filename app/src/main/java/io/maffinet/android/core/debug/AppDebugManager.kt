package io.maffinet.android.core.debug

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppDebugManager {
    private const val MAX_LOGS = 2000
    private val logs = java.util.ArrayList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var isEnabled = false

    fun i(tag: String, msg: String) {
        log("$tag: $msg")
        android.util.Log.i(tag, msg)
    }

    fun d(tag: String, msg: String) {
        log("$tag: $msg")
        android.util.Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        log("$tag: $msg")
        android.util.Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        log("$tag: $msg\n${android.util.Log.getStackTraceString(tr)}")
        android.util.Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String) {
        log("$tag: $msg")
        android.util.Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        log("$tag: $msg\n${android.util.Log.getStackTraceString(tr)}")
        android.util.Log.e(tag, msg, tr)
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean("debug_log_enabled", false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("debug_log_enabled", enabled).apply()
        if (!enabled) {
            synchronized(logs) {
                logs.clear()
            }
        } else {
            log("Отладка включена")
        }
    }

    fun isLoggingEnabled(): Boolean = isEnabled

    fun log(message: String) {
        if (!isEnabled) return
        val time = dateFormat.format(Date())
        val entry = "[$time] $message"
        synchronized(logs) {
            if (logs.size >= MAX_LOGS) {
                logs.removeAt(0)
            }
            logs.add(entry)
        }
    }

    fun getLogs(): List<String> {
        return synchronized(logs) {
            logs.toList().reversed()
        }
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
    }

    fun generateSystemInfoDump(context: Context): String {
        val sb = java.lang.StringBuilder()
        sb.append("=== Maffinet System Info ===\n")
        sb.append("Устройство: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})\n")
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            sb.append("Версия приложения: $versionName (код $versionCode)\n")
        } catch (_: Exception) {
            sb.append("Версия приложения: ошибка\n")
        }
        try {
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.getMemoryInfo(memoryInfo)
            val freeMem = memoryInfo.availMem / 1048576L
            val totalMem = memoryInfo.totalMem / 1048576L
            sb.append("Память: $freeMem МБ свободно из $totalMem МБ\n")
        } catch (_: Exception) {
            sb.append("Память: ошибка\n")
        }
        try {
            val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || 
                             status == android.os.BatteryManager.BATTERY_STATUS_FULL
            sb.append("Батарея: $pct% (${if (isCharging) "заряжается" else "разряжается"})\n")
        } catch (e: Exception) {
            sb.append("Батарея: неизвестно\n")
        }
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            val networkType = when {
                capabilities == null -> "Нет сети"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Мобильная сеть"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Другая"
            }
            sb.append("Тип сети: $networkType\n")
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val operatorName = tm.networkOperatorName
            if (!operatorName.isNullOrEmpty()) {
                sb.append("Оператор сотовой связи: $operatorName\n")
            }
            val linkProperties = cm.getLinkProperties(activeNetwork)
            val dnsList = linkProperties?.dnsServers?.map { it.hostAddress } ?: emptyList()
            if (dnsList.isNotEmpty()) {
                sb.append("Системные DNS: ${dnsList.joinToString(", ")}\n")
            }
        } catch (e: Exception) {
            sb.append("Сеть: ошибка определения\n")
        }
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            var otherVpnActive = false
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                    otherVpnActive = true
                }
            }
            sb.append("Активен другой VPN в системе: ${if (otherVpnActive) "Да" else "Нет"}\n")
        } catch (e: Exception) {
            sb.append("Проверка других VPN: ошибка\n")
        }
        try {
            val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            val settings = io.maffinet.android.data.settings.MaffinetSettingsRepository(context)
            sb.append("Запрошен VPN: ${settings.applicationsRequested()}, Telegram: ${settings.telegramRequested()}\n")
            sb.append("Режим Приложения: ${settings.applicationsEnabled()}, состояние: ${io.maffinet.android.core.dpibypass.ByeDpiVpnService.currentStatus.value}\n")
            sb.append("Режим Telegram: ${settings.telegramEnabled()}, состояние: ${io.maffinet.android.core.tgproxy.TgProxyController.status.value}\n")
            if (settings.applicationsEnabled()) {
                val manualMode = prefs.getBoolean("strategy_manual_mode", false)
                val byedpiArgs = prefs.getString("byedpi_cmd_args", "")
                sb.append("Стратегия: ${if (manualMode) "Вручную" else "Авто"}\n")
                sb.append("DNS для VPN: ${prefs.getString("custom_dns_preset", "Стандартный (Отключено)")}\n")
                sb.append("Аргументы ByeDpi: \"$byedpiArgs\"\n")
                val commandMode = prefs.getBoolean("byedpi_enable_cmd_settings", false)
                sb.append("Источник стратегии: ${if (commandMode) "команда" else "параметры обхода"}\n")
                val effectiveArguments = runCatching {
                    val arguments = when (val configuration = io.maffinet.android.core.dpibypass.ByeDpiProxyPreferences.fromSharedPreferences(prefs, context)) {
                        is io.maffinet.android.core.dpibypass.ByeDpiProxyCmdPreferences -> configuration.args
                        is io.maffinet.android.core.dpibypass.ByeDpiProxyUIPreferences -> configuration.uiargs
                    }
                    arguments.mapIndexed { index, argument ->
                        // Report the actual strategy without copying private User host lists.
                        if (index > 0 && arguments[index - 1] == "-H") "<список доменов>" else argument
                    }.joinToString(" ")
                }.getOrElse { "Не удалось собрать аргументы: ${it.message}" }
                sb.append("Эффективные аргументы ByeDPI: $effectiveArguments\n")
                val allowedApps = prefs.getStringSet("selected_apps", null)
                if (allowedApps != null) {
                    sb.append("Выбранные приложения для VPN (${allowedApps.size}): ${allowedApps.joinToString(", ")}\n")
                }
            }
            if (settings.telegramEnabled()) {
                val port = io.maffinet.android.core.tgproxy.TgProxyController.getPort(context)
                val cfEnabled = io.maffinet.android.core.tgproxy.TgProxyController.isCfEnabled(context)
                val cfPriority = io.maffinet.android.core.tgproxy.TgProxyController.isCfPriority(context)
                val isPortOpen = io.maffinet.android.core.tgproxy.TgProxyController.isPortOpen(
                    io.maffinet.android.core.tgproxy.TgProxyController.DEFAULT_BIND_IP,
                    port,
                    300
                )
                sb.append("Порт TG-прокси: $port (CF туннелирование: $cfEnabled, Приоритет CF: $cfPriority, Открыт: $isPortOpen)\n")
            }
        } catch (e: Exception) {
            sb.append("Настройки: ошибка чтения\n")
        }
        sb.append("=================================\n")
        return sb.toString()
    }
}
