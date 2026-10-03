package io.maffinet.android.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.core.connection.ModeConnectionState
import io.maffinet.android.core.dpibypass.ByeDpiVpnService
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.tgproxy.TgProxyController
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import io.maffinet.android.ui.components.MaffinetUpdateSheet
import io.maffinet.android.ui.home.HomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainTab(focusRequester: FocusRequester, navBarFocusRequester: FocusRequester,
    playEntranceAnimation: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { MaffinetSettingsRepository(context) }
    val prefs = remember { context.getSharedPreferences(context.packageName + "_preferences", 0) }
    val vpnState by ByeDpiVpnService.currentStatus.collectAsState()
    val telegramState by TgProxyController.status.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var exitAfterStart by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun startSelected() {
        val result = ConnectionCoordinator.startSelected(context, openTelegram = prefs.getBoolean("open_tg_on_connect", true))
        error = result.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
        if (exitAfterStart && result.errors.isEmpty() && result.hasSelectedModes) (context as? Activity)?.finishAndRemoveTask()
        exitAfterStart = false
    }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startSelected()
        else {
            exitAfterStart = false
            // Telegram remains an independent selected mode when Android VPN consent is denied.
            startSelected()
        }
    }
    fun connect() {
        if (ConnectionCoordinator.isConfigurationLocked(context) && !StrategyTestManager.isTesting) {
            ConnectionCoordinator.stopAll(context)
            error = null
        } else {
            val permission = if (settings.applicationsEnabled()) android.net.VpnService.prepare(context) else null
            if (permission != null) vpnLauncher.launch(permission) else startSelected()
        }
    }
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        if (activity?.intent?.getBooleanExtra("maffinet_connect_selected", false) == true) {
            activity.intent.removeExtra("maffinet_connect_selected")
            connect()
        }
    }
    LaunchedEffect(vpnState, telegramState) {
        if (vpnState == ModeConnectionState.Running || telegramState == ModeConnectionState.Running) {
            val started = System.currentTimeMillis()
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - started) / 1000).toInt()
                delay(1000)
            }
        } else elapsedSeconds = 0
    }
    HomeScreen(vpnState, telegramState, elapsedSeconds, error, focusRequester,
        onConnect = { if (!StrategyTestManager.isTesting) connect() },
        onBackground = {
            if (!StrategyTestManager.isTesting) {
                if (vpnState == ModeConnectionState.Running || telegramState == ModeConnectionState.Running)
                    (context as? Activity)?.finishAndRemoveTask()
                else {
                    exitAfterStart = true
                    val permission = if (settings.applicationsEnabled()) android.net.VpnService.prepare(context) else null
                    if (permission != null) vpnLauncher.launch(permission) else startSelected()
                }
            }
        }, onNavigate = { io.maffinet.android.data.onNavigateToTab?.invoke(it) }, modifier = modifier)

    val updateInfo = io.maffinet.android.data.updateInfoGlobal
    val forced = io.maffinet.android.data.forceUpdateTestGlobal
    var showUpdate by remember(updateInfo, forced) { mutableStateOf(updateInfo != null &&
        (forced || io.maffinet.android.core.update.UpdateManager.isAutoUpdateEnabled(context))) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var updateError by remember { mutableStateOf<String?>(null) }
    fun dismissUpdate() { showUpdate = false; io.maffinet.android.data.forceUpdateTestGlobal = false }
    MaffinetUpdateSheet(visible = showUpdate, onDismissRequest = { if (!downloading) dismissUpdate() },
        versionText = updateInfo?.version.orEmpty(), descriptionText = updateInfo?.description.orEmpty(),
        isDownloading = downloading, downloadProgress = progress, errorMessage = updateError,
        onUpdate = {
            updateInfo?.let { update -> scope.launch {
                downloading = true
                updateError = null
                io.maffinet.android.core.update.UpdateManager.downloadAndInstallApk(context, update.downloadUrl, "update.apk",
                    onProgress = { progress = it }, onError = { updateError = it })
                downloading = false
                if (updateError == null) dismissUpdate()
            } }
        }, onLater = { dismissUpdate() })
}

fun formatTimer(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}
