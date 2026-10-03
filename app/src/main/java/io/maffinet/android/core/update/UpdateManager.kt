package io.maffinet.android.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateManager.UpdateInfo) : UpdateCheckResult
    data class UpToDate(val version: String) : UpdateCheckResult
    data object NoRelease : UpdateCheckResult
    data class NoApk(val version: String) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

object UpdateManager {

    private const val GITHUB_API_URL = "https://api.github.com/repos/DrDeiss/Maffinet/releases/latest"

    class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val description: String
    )

    suspend fun checkUpdate(context: Context): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                currentCoroutineContext().ensureActive()
                val request = URL(GITHUB_API_URL).openConnection() as HttpURLConnection
                connection = request
                request.requestMethod = "GET"
                request.setRequestProperty("Accept", "application/vnd.github.v3+json")
                request.setRequestProperty("User-Agent", "Maffinet")
                request.connectTimeout = 5000
                request.readTimeout = 5000

                val responseCode = request.responseCode
                currentCoroutineContext().ensureActive()
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = request.inputStream.bufferedReader().use { it.readText() }
                    currentCoroutineContext().ensureActive()
                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "")
                    val body = json.optString("body", "")
                    val assets = json.optJSONArray("assets")

                    var downloadUrl = ""
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.startsWith("Maffinet", ignoreCase = true) && name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        ?: error("Не удалось определить установленную версию")

                    if (!isNewerVersion(currentVersion, tagName)) {
                        UpdateCheckResult.UpToDate(tagName)
                    } else if (downloadUrl.isEmpty()) {
                        UpdateCheckResult.NoApk(tagName)
                    } else {
                        UpdateCheckResult.Available(UpdateInfo(tagName, downloadUrl, body))
                    }
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    UpdateCheckResult.NoRelease
                } else {
                    UpdateCheckResult.Failed("HTTP $responseCode")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                UpdateCheckResult.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                connection?.disconnect()
            }
        }
    }

    internal fun isNewerVersion(current: String, latest: String): Boolean {
        val currentVersion = parseVersion(current)
        val latestVersion = parseVersion(latest)
        val maxLength = maxOf(currentVersion.numbers.size, latestVersion.numbers.size)
        for (i in 0 until maxLength) {
            val currVal = currentVersion.numbers.getOrNull(i) ?: 0L
            val lateVal = latestVersion.numbers.getOrNull(i) ?: 0L
            if (lateVal > currVal) return true
            if (currVal > lateVal) return false
        }
        val currentPre = currentVersion.prerelease
        val latestPre = latestVersion.prerelease
        if (currentPre == null) return false // A stable version outranks every prerelease of that version.
        if (latestPre == null) return true
        for (i in 0 until minOf(currentPre.size, latestPre.size)) {
            val currPart = currentPre[i]
            val latePart = latestPre[i]
            if (currPart == latePart) continue
            val currNumeric = currPart.all { it.isDigit() }
            val lateNumeric = latePart.all { it.isDigit() }
            val order = when {
                currNumeric && lateNumeric -> {
                    val currDigits = currPart.trimStart('0').ifEmpty { "0" }
                    val lateDigits = latePart.trimStart('0').ifEmpty { "0" }
                    lateDigits.length.compareTo(currDigits.length).takeIf { it != 0 }
                        ?: lateDigits.compareTo(currDigits)
                }
                currNumeric -> 1
                lateNumeric -> -1
                else -> latePart.compareTo(currPart)
            }
            if (order != 0) return order > 0
        }
        return latestPre.size > currentPre.size
    }

    private data class ReleaseVersion(val numbers: List<Long>, val prerelease: List<String>?)

    private fun parseVersion(version: String): ReleaseVersion {
        val clean = version.trim().let { if (it.startsWith("v", ignoreCase = true)) it.drop(1) else it }
        require(clean.matches(Regex("[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"))) {
            "Некорректная версия релиза: $version"
        }
        val release = clean.substringBefore('+')
        val numbers = release.substringBefore('-').split('.').map {
            it.toLongOrNull() ?: throw IllegalArgumentException("Некорректная версия релиза: $version")
        }
        val prerelease = release.substringAfter('-', "").takeIf { it.isNotEmpty() }?.split('.')
        return ReleaseVersion(numbers, prerelease)
    }

    suspend fun getSmartTubeLatestUrl(): String {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL("https://api.github.com/repos/yuliskov/SmartTube/releases/latest").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "Maffinet")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.contains("_stable_") && name.endsWith("_universal.apk")) {
                                val downloadUrl = asset.optString("browser_download_url", "")
                                if (downloadUrl.isNotEmpty()) {
                                    return@withContext downloadUrl
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            "https://github.com/yuliskov/SmartTube/releases/download/31.94/SmartTube_stable_31.94_universal.apk"
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        withContext(Dispatchers.Main) {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                        onError("Permission required")
                        return@withContext
                    }
                }

                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.instanceFollowRedirects = false
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 302 || responseCode == 301) {
                    val redirectUrl = connection.getHeaderField("Location")
                    downloadAndInstallApk(context, redirectUrl, fileName, onProgress, onError)
                    return@withContext
                }

                val fileLength = connection.contentLength
                val cacheFile = File(context.externalCacheDir ?: context.cacheDir, fileName)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                connection.inputStream.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        val data = ByteArray(8192)
                        var total = 0L
                        var count: Int
                        var lastPercent = -1
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            output.write(data, 0, count)
                            if (fileLength > 0) {
                                val percent = ((total * 100) / fileLength).toInt()
                                if (percent > lastPercent) {
                                    lastPercent = percent
                                    withContext(Dispatchers.Main) {
                                        onProgress(total.toFloat() / fileLength.toFloat())
                                    }
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    installApk(context, cacheFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Download failed")
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isAutoUpdateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_update_enabled", true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_update_enabled", enabled).apply()
    }

    fun isSmartTubeInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("org.smarttube.stable", 0)
            true
        } catch (e: Exception) {
            try {
                pm.getPackageInfo("org.smarttube.beta", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun openSmartTube(context: Context) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage("org.smarttube.stable")
            ?: pm.getLaunchIntentForPackage("org.smarttube.beta")
        if (intent != null) {
            context.startActivity(intent)
        }
    }
}
