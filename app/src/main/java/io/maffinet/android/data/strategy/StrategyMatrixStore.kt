package io.maffinet.android.data.strategy

import android.content.Context
import io.maffinet.android.core.strategy.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Versioned local probe history; independent from legacy strategy import/export. */
class StrategyMatrixStore(context: Context) {
    private val file = File(context.filesDir, "strategy_matrix_v1.json")

    fun load(): List<StrategyEvaluation> = try {
        if (!file.isFile) emptyList() else {
            val root = JSONObject(file.readText())
            if (root.optInt("version") != 1) emptyList() else {
                val entries = root.getJSONArray("evaluations")
                (0 until entries.length()).map { index ->
                    val entry = entries.getJSONObject(index)
                    val services = entry.getJSONArray("services")
                    StrategyEvaluation(entry.getInt("candidateIndex"), entry.getString("command"),
                        (0 until services.length()).map { serviceIndex ->
                            val service = services.getJSONObject(serviceIndex)
                            val targets = service.getJSONArray("targets")
                            ServiceConnectivityResult(service.getString("id"), service.getString("name"),
                                (0 until targets.length()).map { targetIndex ->
                                    val target = targets.getJSONObject(targetIndex)
                                    TargetConnectivityResult(target.getString("url"), target.getBoolean("reachable"),
                                        target.getLong("latencyMs"),
                                        if (target.has("httpStatus")) target.getInt("httpStatus") else null,
                                        if (target.has("error")) target.getString("error") else null)
                                })
                        })
                }
            }
        }
    } catch (_: Exception) { emptyList() }

    fun save(evaluations: Collection<StrategyEvaluation>) {
        val entries = JSONArray()
        for (evaluation in evaluations) {
            val services = JSONArray()
            for (service in evaluation.services) {
                val targets = JSONArray()
                for (target in service.targets) {
                    targets.put(JSONObject().put("url", target.url).put("reachable", target.reachable)
                        .put("latencyMs", target.latencyMs).apply {
                            target.httpStatus?.let { put("httpStatus", it) }
                            target.error?.let { put("error", it) }
                        })
                }
                services.put(JSONObject().put("id", service.serviceId).put("name", service.serviceName)
                    .put("targets", targets))
            }
            entries.put(JSONObject().put("candidateIndex", evaluation.candidateIndex)
                .put("command", evaluation.command).put("services", services))
        }
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(JSONObject().put("version", 1).put("evaluations", entries).toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
