package io.maffinet.android.core.dpibypass

import android.content.Context
import io.maffinet.android.core.debug.AppDebugManager as Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import io.maffinet.android.core.domains.LegacyStrategyAliases
import io.maffinet.android.core.strategy.StrategyEvaluation
import io.maffinet.android.core.strategy.StrategyScorer
import io.maffinet.android.data.strategy.StrategyMatrixStore
import io.maffinet.android.data.strategy.ProbeTargetRepository

object StrategyTestManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var isTesting by mutableStateOf(false)
        private set

    var currentProgress by mutableStateOf("")
        private set

    var hasConnectionError by mutableStateOf(false)

    var currentTestIndex by mutableStateOf(0)
        private set

    val testResults = mutableStateListOf<Triple<Int, String, String>>()
    val matrixResults = mutableStateMapOf<String, StrategyEvaluation>()
    var hasStaleResults by mutableStateOf(false)
        private set
    private var historyFingerprint: String? = null
    @Volatile private var testingJob: Job? = null

    @Synchronized fun cancelTesting() { testingJob?.cancel() }

    var bestStrategyResult by mutableStateOf<String?>(null)
        private set

    var appliedStrategy by mutableStateOf<String?>(null)

    val totalStrategiesCount: Int
        get() = StrategyTester.defaultStrategies.size

    val pinnedStrategies = mutableStateMapOf<String, Boolean>()
    val customNames = mutableStateMapOf<String, String>()
    val strategyNotes = mutableStateMapOf<String, String>()
    val deletedStrategies = mutableStateMapOf<String, Boolean>()

    private fun loadCustomizations(context: Context) {
        pinnedStrategies.clear()
        deletedStrategies.clear()
        customNames.clear()
        strategyNotes.clear()
        val file = File(context.filesDir, "strategy_customizations.json")
        if (!file.exists()) return
        try {
            val json = org.json.JSONObject(file.readText())
            val pinnedArr = json.optJSONArray("pinned")
            if (pinnedArr != null) {
                pinnedStrategies.clear()
                for (i in 0 until pinnedArr.length()) {
                    pinnedStrategies[pinnedArr.getString(i)] = true
                }
            }
            val deletedArr = json.optJSONArray("deleted")
            if (deletedArr != null) {
                deletedStrategies.clear()
                for (i in 0 until deletedArr.length()) {
                    deletedStrategies[deletedArr.getString(i)] = true
                }
            }
            val namesObj = json.optJSONObject("names")
            if (namesObj != null) {
                customNames.clear()
                val keys = namesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    customNames[key] = namesObj.getString(key)
                }
            }
            val notesObj = json.optJSONObject("notes")
            if (notesObj != null) {
                strategyNotes.clear()
                val keys = notesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    strategyNotes[key] = notesObj.getString(key)
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveCustomizations(context: Context) {
        try {
            val file = File(context.filesDir, "strategy_customizations.json")
            val json = org.json.JSONObject()
            
            val pinnedArr = org.json.JSONArray()
            pinnedStrategies.forEach { (strategy, pinned) ->
                if (pinned) pinnedArr.put(strategy)
            }
            json.put("pinned", pinnedArr)

            val deletedArr = org.json.JSONArray()
            deletedStrategies.forEach { (strategy, deleted) ->
                if (deleted) deletedArr.put(strategy)
            }
            json.put("deleted", deletedArr)

            val namesObj = org.json.JSONObject()
            customNames.forEach { (strategy, name) ->
                namesObj.put(strategy, name)
            }
            json.put("names", namesObj)

            val notesObj = org.json.JSONObject()
            strategyNotes.forEach { (strategy, notes) ->
                notesObj.put(strategy, notes)
            }
            json.put("notes", notesObj)

            file.writeText(json.toString())
        } catch (_: Exception) {}
    }

    @Synchronized fun init(context: Context) {
        if (isTesting) return
        loadCustomizations(context)
        matrixResults.clear()
        testResults.clear()
        val store = StrategyMatrixStore(context)
        val saved = store.load()
        val snapshot = ProbeTargetRepository(context).snapshot()
        val fingerprint = snapshot.fingerprint
        historyFingerprint = fingerprint
        val currentHistory = saved?.takeIf { it.fingerprint == fingerprint }
        hasStaleResults = store.hasSavedHistory() && currentHistory == null
        currentHistory?.evaluations?.filterNot { deletedStrategies[it.command] == true }
            ?.forEach { matrixResults[it.command] = it }
        val settings = context.getPreferences()
        val activeCommand = settings.getString("byedpi_cmd_args", null)
            .takeIf { settings.getBoolean("byedpi_enable_cmd_settings", false) }
        bestStrategyResult = StrategyScorer.bestComplete(matrixResults.values, snapshot.urls)?.command
        appliedStrategy = activeCommand
        val file = File(context.filesDir, "proxy_test_results.txt")
        if (file.exists()) {
            try {
                val lines = file.readLines()
                val loaded = mutableListOf<Triple<Int, String, String>>()
                for (i in lines.indices step 3) {
                    if (i + 2 < lines.size) {
                        val index = lines[i].toIntOrNull() ?: continue
                        val strategy = lines[i + 1]
                        val status = lines[i + 2].replace(" (Успешно)", "")
                        loaded.add(Triple(index, strategy, status))
                    }
                }

                if (loaded.isNotEmpty()) {
                    testResults.clear()
                    val activeApplied = appliedStrategy
                    val filtered = loaded.filter { !deletedStrategies.containsKey(it.second) }.mapNotNull {
                        if (matrixResults.containsKey(it.second)) it else savedEntry(it)
                    }
                    val sortedLoaded = filtered.sortedWith(compareBy<Triple<Int, String, String>> {
                        if (pinnedStrategies.containsKey(it.second)) 0 else 1
                    }.thenBy {
                        if (activeApplied != null && it.second.replace("{sni}", LegacyStrategyAliases.SNI) == activeApplied) 0 else 1
                    })
                    testResults.addAll(sortedLoaded)
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Hosts/editor navigation calls this before displaying any last-run evidence. */
    @Synchronized fun refreshConfiguration(context: Context) {
        val fingerprint = ProbeTargetRepository(context).snapshot().fingerprint
        if (historyFingerprint == fingerprint) return
        if (matrixResults.isNotEmpty()) hasStaleResults = true
        matrixResults.clear()
        val retained = testResults.mapNotNull(::savedEntry)
        testResults.clear()
        testResults.addAll(retained)
        bestStrategyResult = null
        historyFingerprint = fingerprint
        if (isTesting) {
            currentProgress = "Настройки проверки изменились; повторите проверку"
            testingJob?.cancel()
        }
    }

    fun historyMatchesCurrentConfiguration(context: Context): Boolean =
        historyFingerprint == ProbeTargetRepository(context).snapshot().fingerprint

    private fun savedEntry(item: Triple<Int, String, String>): Triple<Int, String, String>? = when {
        deletedStrategies[item.second] == true -> null
        item.third.startsWith("Импортировано") -> item
        pinnedStrategies[item.second] == true || customNames.containsKey(item.second) ||
            strategyNotes.containsKey(item.second) || item.second == appliedStrategy ->
                Triple(item.first, item.second, "Сохранена · проверка устарела")
        else -> null
    }

    @Synchronized private fun recordEvaluation(evaluation: StrategyEvaluation) {
        if (deletedStrategies[evaluation.command] != true) matrixResults[evaluation.command] = evaluation
    }

    @Synchronized private fun recordProgress(index: Int, strategy: String, status: String) {
        if (deletedStrategies[strategy] != true) testResults.add(0, Triple(index + 1, strategy, status))
    }

    @Synchronized
    fun startTesting(context: Context): Job? {
        if (isTesting) return null
        val snapshot = ProbeTargetRepository(context).snapshot()
        val retained = testResults.mapNotNull(::savedEntry)
        historyFingerprint = snapshot.fingerprint
        isTesting = true
        val applicationContext = context.applicationContext
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                Log.i("StrategyTestManager", "Запуск автоподбора стратегий")
                hasConnectionError = false
                hasStaleResults = false
                bestStrategyResult = null
                testResults.clear()
                matrixResults.clear()
                currentTestIndex = 0
                currentProgress = "Готовимся к тестированию..."
                val tester = StrategyTester(applicationContext)
                var selectedBest: String? = null
                var bestObserved: StrategyEvaluation? = null
                tester.runTests(onProgress = { index, strategy, status ->
                    currentTestIndex = index + 1
                    currentProgress = "Проверяем стратегию ${index + 1} из $totalStrategiesCount"
                    recordProgress(index, strategy, status)
                }, onEvaluation = { evaluation ->
                    check(snapshot.fingerprint == ProbeTargetRepository(applicationContext).snapshot().fingerprint) {
                        "Hosts или проверочные адреса изменились; повторите проверку"
                    }
                    recordEvaluation(evaluation)
                }, excludedCommands = deletedStrategies.keys.toSet(), snapshot = snapshot, onBestReady = {
                        currentCoroutineContext().ensureActive()
                        synchronized(this@StrategyTestManager) {
                            val evaluations = matrixResults.values.filterNot {
                                deletedStrategies[it.command] == true
                            }
                            bestObserved = evaluations.minWithOrNull(StrategyScorer.comparator)
                            val best = StrategyScorer.bestComplete(evaluations, snapshot.urls)?.command
                            selectedBest = best
                            if (best != null && !io.maffinet.android.data.settings.MaffinetSettingsRepository(applicationContext).automaticAccessEnabled()) {
                                bestStrategyResult = best
                                appliedStrategy = best
                                applicationContext.getPreferences().edit()
                                    .putString("byedpi_cmd_args", best)
                                    .putBoolean("byedpi_enable_cmd_settings", true)
                                    .putBoolean("strategy_manual_mode", false)
                                    .apply()
                            }
                        }
                    })
                val best = selectedBest

                Log.i("StrategyTestManager", "Автоподбор завершен. Лучшая стратегия: \"$best\"")

                resortResults(applicationContext)
                currentProgress = if (io.maffinet.android.data.settings.MaffinetSettingsRepository(applicationContext).automaticAccessEnabled()) {
                    "Диагностика завершена: ${bestObserved?.passedServices ?: 0}/${snapshot.urls.size} адресов. Автоматический доступ сохранён."
                } else completionSummary(bestObserved, best != null)
                showNotification(applicationContext, currentProgress)
            } catch (cancelled: CancellationException) {
                currentProgress = "Проверка отменена"
                throw cancelled
            } catch (error: Exception) {
                hasConnectionError = true
                currentProgress = error.message ?: "Ошибка проверки стратегий"
                Log.e("StrategyTestManager", "Strategy testing failed", error)
            } finally {
                synchronized(this@StrategyTestManager) {
                    try {
                        if (snapshot.fingerprint != ProbeTargetRepository(applicationContext).snapshot().fingerprint) {
                            matrixResults.clear()
                            testResults.clear()
                            bestStrategyResult = null
                            hasStaleResults = true
                        }
                        retained.filter { saved -> deletedStrategies[saved.second] != true &&
                            testResults.none { it.second == saved.second } }.forEach { testResults.add(it) }
                        StrategyMatrixStore(applicationContext).save(snapshot.fingerprint, matrixResults.toMap().values)
                        saveResults(applicationContext)
                    } catch (error: Exception) { Log.e("StrategyTestManager", "Failed to save probe history", error) }
                    isTesting = false
                    testingJob = null
                }
                Log.i("StrategyTestManager", "Тестирование полностью завершено")
            }
        }
        testingJob = job
        job.start()
        return job
    }

    private fun completionSummary(evaluation: StrategyEvaluation?, applied: Boolean): String {
        if (applied && evaluation != null) {
            return "Проверка завершена: ${evaluation.passedServices}/${evaluation.totalServices} адресов. Стратегия применена."
        }
        val result = when {
            evaluation == null -> "Нет результатов проверки."
            evaluation.passedServices == 0 -> "Ни один из ${evaluation.totalServices} адресов не прошёл проверку."
            else -> "Полного результата нет. Лучший результат: ${evaluation.passedServices}/${evaluation.totalServices} адресов."
        }
        val failedUrls = evaluation?.failedTargets?.map { it.url }?.distinct().orEmpty()
        return "$result Прежняя стратегия сохранена." +
            if (failedUrls.isEmpty()) "" else "\nНе прошли проверку: ${failedUrls.joinToString(", ")}"
    }

    private fun showNotification(context: Context, summary: String) {
        val channelId = "MaffinetNotifications"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                channelId,
                "Уведомления Maffinet",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(io.maffinet.android.R.drawable.ic_notification)
            .setContentTitle("Maffinet")
            .setContentText(summary.substringBefore('\n'))
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1001, builder.build())
    }

    private fun saveResults(context: Context) {
        try {
            val file = File(context.filesDir, "proxy_test_results.txt")
            val sb = StringBuilder()
            testResults.forEach {
                sb.append(it.first).append("\n")
                sb.append(it.second).append("\n")
                sb.append(it.third).append("\n")
            }
            file.writeText(sb.toString())
        } catch (_: Exception) {
        }
    }

    fun applyStrategy(context: Context, originalIndex: Int, strategy: String) {
        if (io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)) {
            android.widget.Toast.makeText(context, "Остановите подключение перед выбором ручной стратегии", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val accepted = synchronized(this) {
            if (deletedStrategies[strategy] == true) false else {
                io.maffinet.android.data.settings.MaffinetSettingsRepository(context).setAutomaticAccessEnabled(false)
                context.getPreferences().edit()
                    .putString("byedpi_cmd_args", strategy)
                    .putBoolean("byedpi_enable_cmd_settings", true)
                    .apply()
                appliedStrategy = strategy
                true
            }
        }
        if (!accepted) {
            Log.w("StrategyTestManager", "Ignoring a deleted strategy")
            android.widget.Toast.makeText(context, "Стратегия удалена", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        Log.i("StrategyTestManager", "Вручную применена стратегия №$originalIndex: \"$strategy\"")
        resortResults(context)

        android.widget.Toast.makeText(
            context,
            "Применена стратегия: $originalIndex",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        if (ByeDpiVpnService.isVpnActive) {
            io.maffinet.android.core.dpibypass.ServiceManager.restart(
                context,
                io.maffinet.android.data.Mode.VPN
            )
        }
    }

    fun togglePin(context: Context, strategy: String) {
        val current = pinnedStrategies[strategy] ?: false
        if (current) {
            pinnedStrategies.remove(strategy)
        } else {
            pinnedStrategies[strategy] = true
        }
        saveCustomizations(context)
        resortResults(context)
    }

    fun renameStrategy(context: Context, strategy: String, newName: String?) {
        if (newName.isNullOrBlank()) {
            customNames.remove(strategy)
        } else {
            customNames[strategy] = newName
        }
        saveCustomizations(context)
    }

    fun updateNotes(context: Context, strategy: String, notes: String?) {
        if (notes.isNullOrBlank()) {
            strategyNotes.remove(strategy)
        } else {
            strategyNotes[strategy] = notes
        }
        saveCustomizations(context)
    }

    @Synchronized fun deleteStrategy(context: Context, strategy: String) {
        deletedStrategies[strategy] = true
        saveCustomizations(context)
        testResults.removeAll { it.second == strategy }
        matrixResults.remove(strategy)
        if (bestStrategyResult == strategy) bestStrategyResult = StrategyScorer.bestComplete(
            matrixResults.values, ProbeTargetRepository(context).urls()
        )?.command
        saveResults(context)
        try { StrategyMatrixStore(context).removeCommand(strategy) }
        catch (error: Exception) { Log.e("StrategyTestManager", "Failed to delete saved matrix candidate", error) }
    }

    private fun resortResults(context: Context) {
        val activeApplied = appliedStrategy
        val current = testResults.filter { !it.third.contains("тайм-аут") }
        val sorted = current.sortedWith(compareBy<Triple<Int, String, String>> {
            if (pinnedStrategies.containsKey(it.second)) 0 else 1
        }.thenBy {
            if (activeApplied != null && it.second.replace("{sni}", LegacyStrategyAliases.SNI) == activeApplied) 0 else 1
        }.thenByDescending {
            matrixResults[it.second]?.passedServices ?: -1
        }.thenBy {
            matrixResults[it.second]?.averageLatencyMs ?: Long.MAX_VALUE
        }.thenBy {
            if (it.third.contains("мс")) 0 else 1
        }.thenBy {
            if (it.third.contains("мс")) {
                it.third.substringBefore(" мс").toLongOrNull() ?: Long.MAX_VALUE
            } else {
                Long.MAX_VALUE
            }
        })
        testResults.clear()
        testResults.addAll(sorted)
        saveResults(context)
    }

    fun getStrategyName(strategy: String, context: Context? = null): String {
        val clean = strategy.replace("{sni}", LegacyStrategyAliases.SNI)
        val custom = customNames[clean] ?: customNames[strategy]
        if (custom != null) return custom
        
        val index = StrategyTester.defaultStrategies.indexOfFirst {
            it.replace("{sni}", LegacyStrategyAliases.SNI) == clean
        }
        return if (index >= 0) "Способ ${index + 1}" else "Кастомный способ"
    }

    fun getActiveStrategyName(context: Context? = null): String {
        val active = appliedStrategy
        if (active == null) return "Способ по умолчанию"
        return getStrategyName(active, context)
    }

    fun exportWorkingStrategiesJson(context: Context): String {
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("app", "Maffinet")
        val array = org.json.JSONArray()

        val exportedSet = LinkedHashSet<String>()

        pinnedStrategies.forEach { (strategy, pinned) ->
            if (pinned && !deletedStrategies.containsKey(strategy)) {
                exportedSet.add(strategy)
            }
        }

        testResults.forEach { item ->
            val strategy = item.second
            val normalized = strategy.replace("{sni}", LegacyStrategyAliases.SNI)
            val manualStatus = prefs.getString("manual_status_$normalized", null)
            val isWorking = manualStatus == "working"
            if (isWorking && !deletedStrategies.containsKey(strategy)) {
                exportedSet.add(strategy)
            }
        }

        exportedSet.forEach { strategy ->
            val obj = org.json.JSONObject()
            obj.put("strategy", strategy)
            val name = getStrategyName(strategy, context)
            obj.put("name", name)
            val notes = strategyNotes[strategy]
            if (!notes.isNullOrBlank()) {
                obj.put("notes", notes)
            }
            array.put(obj)
        }

        root.put("strategies", array)
        return root.toString(2)
    }

    fun importStrategiesFromJson(context: Context, jsonString: String): Int {
        try {
            val root = org.json.JSONObject(jsonString)
            val array = root.optJSONArray("strategies") ?: return 0
            var importedCount = 0

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val strategy = obj.optString("strategy", "")
                if (strategy.isBlank()) continue

                val rawName = obj.optString("name", "Способ")
                val cleanName = if (rawName.startsWith("EX: ")) rawName else "EX: $rawName"
                val notes = obj.optString("notes", "")

                // An explicit import can restore a previously deleted saved command.
                deletedStrategies.remove(strategy)
                customNames[strategy] = cleanName
                pinnedStrategies[strategy] = true
                if (notes.isNotBlank()) {
                    strategyNotes[strategy] = notes
                }

                val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                val normalized = strategy.replace("{sni}", LegacyStrategyAliases.SNI)
                prefs.edit().putString("manual_status_$normalized", "working").apply()

                val existingIndex = testResults.indexOfFirst { it.second == strategy }
                if (existingIndex >= 0) {
                    testResults[existingIndex] = Triple(testResults[existingIndex].first, strategy, "Импортировано (Успешно)")
                } else {
                    val newIdx = testResults.size + 1
                    testResults.add(0, Triple(newIdx, strategy, "Импортировано (Успешно)"))
                }

                importedCount++
            }

            if (importedCount > 0) {
                saveCustomizations(context)
                saveResults(context)
            }
            return importedCount
        } catch (e: Exception) {
            Log.e("StrategyTestManager", "Ошибка импорта JSON", e)
            return 0
        }
    }
}
