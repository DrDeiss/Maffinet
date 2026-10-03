package io.maffinet.android.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.maffinet.android.core.debug.AppDebugManager as Log
import android.content.res.Configuration
import android.net.TrafficStats
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.maffinet.android.ui.home.HomeScreen
import io.maffinet.android.ui.components.MaffinetUpdateSheet
import io.maffinet.android.ui.components.MaffinetHintSheet
import io.maffinet.android.ui.components.MaffinetUnloadSheet
import io.maffinet.android.ui.components.UnloadOption
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.maffinet.android.R
import io.maffinet.android.core.dpibypass.ServiceManager
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.tgproxy.TgProxyController
import io.maffinet.android.data.AppStatus
import io.maffinet.android.data.Mode
import io.maffinet.android.data.appStatus
import io.maffinet.android.data.STARTED_BROADCAST
import io.maffinet.android.data.STOPPED_BROADCAST
import io.maffinet.android.data.FAILED_BROADCAST
import io.maffinet.android.data.performanceModeGlobal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun MainTab(
    focusRequester: FocusRequester,
    navBarFocusRequester: FocusRequester,
    playEntranceAnimation: Boolean = true,
    modifier: Modifier = Modifier
) {
    val headerProgress = rememberEntranceProgress(playEntranceAnimation, delayMillis = 20L, dampingRatio = 1f, stiffness = 180f)
    val buttonProgress = rememberEntranceProgress(playEntranceAnimation, delayMillis = 90L, dampingRatio = 0.62f, stiffness = 150f)
    val cardProgress = rememberEntranceProgress(playEntranceAnimation, delayMillis = 190L, dampingRatio = 0.86f, stiffness = 160f)
    val quickButtonsProgress = rememberEntranceProgress(playEntranceAnimation, delayMillis = 250L, dampingRatio = 0.86f, stiffness = 160f)

    val headerAlpha = headerProgress.coerceIn(0f, 1f)
    val buttonAlpha = buttonProgress.coerceIn(0f, 1f)
    val buttonScale = 0.62f + 0.38f * buttonProgress
    val cardAlpha = cardProgress.coerceIn(0f, 1f)
    val quickButtonsAlpha = quickButtonsProgress.coerceIn(0f, 1f)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE) }
    var telegramProxyEnabledByUser by remember { mutableStateOf(prefs.getBoolean("telegram_proxy_enabled_by_user", true)) }
    var wantsYoutubeBypass by remember { mutableStateOf(prefs.getBoolean("wants_youtube_bypass", true)) }

    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "telegram_proxy_enabled_by_user") {
                telegramProxyEnabledByUser = sharedPreferences.getBoolean("telegram_proxy_enabled_by_user", true)
            }
            if (key == "wants_youtube_bypass") {
                wantsYoutubeBypass = sharedPreferences.getBoolean("wants_youtube_bypass", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val realUpdateInfo = io.maffinet.android.data.updateInfoGlobal
    val forceUpdateTest = io.maffinet.android.data.forceUpdateTestGlobal
    val updateInfo = realUpdateInfo
    val autoUpdateEnabled = io.maffinet.android.core.update.UpdateManager.isAutoUpdateEnabled(context)
    var showUpdateDialog by remember(updateInfo, forceUpdateTest) {
        mutableStateOf(updateInfo != null && (autoUpdateEnabled || forceUpdateTest))
    }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableStateOf(0f) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    var isDownloadingSmartTube by remember { mutableStateOf(false) }
    var smartTubeDownloadProgress by remember { mutableStateOf(0f) }
    var smartTubeInstalled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showUnloadDialog by remember { mutableStateOf(false) }
    var showHintDialog by remember { mutableStateOf(false) }
    val hintPrefs = remember { context.getSharedPreferences("maffinet_hints", Context.MODE_PRIVATE) }
    LaunchedEffect(showUpdateDialog, showUnloadDialog, showHintDialog) {
        io.maffinet.android.data.isActionSheetVisibleGlobal = showUpdateDialog || showUnloadDialog || showHintDialog
    }

    val lifecycleOwnerForSmartTube = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwnerForSmartTube) {
        lifecycleOwnerForSmartTube.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            smartTubeInstalled = io.maffinet.android.core.update.UpdateManager.isSmartTubeInstalled(context)
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val safeBottomInset = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
    val navOverlayReserve = safeBottomInset + 86.dp
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxButtonSize = if (isLandscape) 200.dp else 280.dp

    LaunchedEffect(Unit) {
        repeat(8) { index ->
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
            delay(100L + index * 50L)
        }
    }

    var currentStatus by remember { mutableStateOf(appStatus.first) }
    var isTgProxyRunning by remember { mutableStateOf(io.maffinet.android.data.isTgProxyRunningGlobal) }
    var isStarting by remember { mutableStateOf(false) }

    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(currentStatus, isStarting, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val wantsTelegram = prefs.getBoolean("telegram_proxy_enabled_by_user", true)
                val wantsYoutube = prefs.getBoolean("wants_youtube_bypass", true)
                var open = false
                if (wantsTelegram) {
                    val port = TgProxyController.getPort(context)
                    open = withContext(Dispatchers.IO) {
                        TgProxyController.isPortOpen(
                            TgProxyController.DEFAULT_BIND_IP,
                            port,
                            500
                        )
                    }
                    isTgProxyRunning = open
                    io.maffinet.android.data.isTgProxyRunningGlobal = open
                } else {
                    isTgProxyRunning = false
                    io.maffinet.android.data.isTgProxyRunningGlobal = false
                }
                val conditionToStopStarting = when {
                    wantsYoutube && wantsTelegram -> currentStatus == AppStatus.Running && open
                    wantsYoutube -> currentStatus == AppStatus.Running
                    wantsTelegram -> open
                    else -> true
                }
                if (conditionToStopStarting) {
                    isStarting = false
                }
                delay(if (isStarting) 1000 else 5000)
            }
        }
    }

    LaunchedEffect(isStarting) {
        if (isStarting) {
            delay(12_000)
            isStarting = false
        }
    }

    val isRunning = when {
        wantsYoutubeBypass && telegramProxyEnabledByUser -> currentStatus == AppStatus.Running && isTgProxyRunning
        wantsYoutubeBypass -> currentStatus == AppStatus.Running
        telegramProxyEnabledByUser -> isTgProxyRunning
        else -> false
    }

    val isPending = when {
        wantsYoutubeBypass && telegramProxyEnabledByUser -> isStarting || (currentStatus == AppStatus.Running && !isTgProxyRunning)
        wantsYoutubeBypass -> isStarting && currentStatus != AppStatus.Running
        telegramProxyEnabledByUser -> isStarting && !isTgProxyRunning
        else -> false
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            if (io.maffinet.android.data.connectionStartTime == 0L) {
                io.maffinet.android.data.connectionStartTime = System.currentTimeMillis()
            }
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - io.maffinet.android.data.connectionStartTime) / 1000).toInt()
                delay(1000)
            }
        } else {
            io.maffinet.android.data.connectionStartTime = 0L
            elapsedSeconds = 0
        }
    }

    var latencyMs by remember { mutableStateOf(-1) }

    LaunchedEffect(currentStatus, performanceModeGlobal, lifecycleOwner) {
        if (currentStatus != AppStatus.Running) {
            latencyMs = -1
        } else {
            if (performanceModeGlobal) {
                latencyMs = withContext(Dispatchers.IO) {
                    TgProxyController.measureLatency("1.1.1.1", 53)
                }
            } else {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        latencyMs = withContext(Dispatchers.IO) {
                            TgProxyController.measureLatency("1.1.1.1", 53)
                        }
                        delay(20000)
                    }
                }
            }
        }
    }

    fun startAll() {
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        val wantsYoutube = prefs.getBoolean("wants_youtube_bypass", true)
        val wantsTelegram = prefs.getBoolean("telegram_proxy_enabled_by_user", true)
        io.maffinet.android.core.debug.AppDebugManager.log("Запуск всех служб (YouTube: $wantsYoutube, Telegram: $wantsTelegram)")
        isStarting = true
        prefs.edit()
            .putBoolean("service_enabled", true)
            .apply()
        try {
            android.service.quicksettings.TileService.requestListeningState(
                context,
                android.content.ComponentName(context, io.maffinet.android.service.MaffinetTileService::class.java)
            )
        } catch (_: Exception) {}

        if (wantsYoutube) {
            io.maffinet.android.core.debug.AppDebugManager.log("Запуск VPN ByeDPI")
            ServiceManager.start(context, Mode.VPN)
        }

        if (wantsTelegram) {
            val openTg = prefs.getBoolean("open_tg_on_connect", true)
            if (wantsYoutube) {
                TgProxyController.startAsync(
                    context = context,
                    onSuccess = {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            isStarting = false
                        }
                        val port = TgProxyController.getPort(context)
                        val secret = TgProxyController.getOrGenerateSecret(context)
                        val url = TgProxyController.getTgProxyUrl(
                            TgProxyController.DEFAULT_BIND_IP,
                            port,
                            secret
                        )
                        val alreadyConfigured = prefs.getBoolean("tg_proxy_configured", false)
                        if (openTg && !alreadyConfigured) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                                prefs.edit().putBoolean("tg_proxy_configured", true).apply()
                            } catch (e: Exception) {
                                Log.e("MainTab", "Failed to open Telegram link", e)
                            }
                        }
                    },
                    onError = {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            isStarting = false
                        }
                        Log.e("MainTab", "Failed to start TG proxy")
                    }
                )
            } else {
                val intent = Intent(context, io.maffinet.android.core.tgproxy.TgProxyService::class.java).apply {
                    action = io.maffinet.android.data.START_ACTION
                    putExtra("open_tg", openTg)
                }
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        } else {
            if (!wantsYoutube) {
                isStarting = false
            }
        }
    }

    fun stopAll() {
        io.maffinet.android.core.debug.AppDebugManager.log("Остановка всех служб")
        isStarting = false
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_enabled", false)
            .apply()
        try {
            android.service.quicksettings.TileService.requestListeningState(
                context,
                android.content.ComponentName(context, io.maffinet.android.service.MaffinetTileService::class.java)
            )
        } catch (_: Exception) {}
        ServiceManager.stop(context)
        val intent = Intent(context, io.maffinet.android.core.tgproxy.TgProxyService::class.java).apply {
            action = io.maffinet.android.data.STOP_ACTION
        }
        context.startService(intent)
        TgProxyController.stop()
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startAll()
        }
    }

    fun handleNormalStart() {
        val wantsYoutube = prefs.getBoolean("wants_youtube_bypass", true)
        if (wantsYoutube) {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnLauncher.launch(intent)
            } else {
                startAll()
            }
        } else {
            startAll()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                currentStatus = appStatus.first
                if (currentStatus == AppStatus.Halted) {
                    isStarting = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    HomeScreen(
        connected = isRunning,
        connecting = isPending,
        vpnEnabled = wantsYoutubeBypass,
        elapsedSeconds = elapsedSeconds,
        focusRequester = focusRequester,
        onConnect = {
            if (!StrategyTestManager.isTesting) {
                if (isRunning || isPending) stopAll() else handleNormalStart()
            }
        },
        onBackground = { if (!StrategyTestManager.isTesting) showUnloadDialog = true },
        onNavigate = { io.maffinet.android.data.onNavigateToTab?.invoke(it) },
        modifier = modifier
    )
    MaffinetUpdateSheet(
        visible = showUpdateDialog,
        onDismissRequest = {
            if (!isDownloadingUpdate) {
                showUpdateDialog = false
                io.maffinet.android.data.forceUpdateTestGlobal = false
            }
        },
        versionText = if (updateInfo != null) (if (updateInfo.version.startsWith("v", ignoreCase = true)) updateInfo.version else "v${updateInfo.version}") else "",
        descriptionText = updateInfo?.description ?: "",
        isDownloading = isDownloadingUpdate,
        downloadProgress = updateDownloadProgress,
        errorMessage = updateErrorMessage,
        onUpdate = {
            scope.launch {
                isDownloadingUpdate = true
                updateErrorMessage = null
                io.maffinet.android.core.update.UpdateManager.downloadAndInstallApk(
                    context = context,
                    downloadUrl = updateInfo!!.downloadUrl,
                    fileName = "update.apk",
                    onProgress = { progress ->
                        updateDownloadProgress = progress
                    },
                    onError = { err ->
                        isDownloadingUpdate = false
                        updateErrorMessage = err
                    }
                )
                isDownloadingUpdate = false
                showUpdateDialog = false
                io.maffinet.android.data.forceUpdateTestGlobal = false
            }
        },
        onLater = {
            showUpdateDialog = false
            io.maffinet.android.data.forceUpdateTestGlobal = false
        }
    )

    val unloadWantsYt = prefs.getBoolean("wants_youtube_bypass", true)
    val unloadWantsTg = prefs.getBoolean("telegram_proxy_enabled_by_user", true)
    val unloadCurrentMode = when {
        unloadWantsYt && unloadWantsTg -> "both"
        unloadWantsTg -> "telegram"
        unloadWantsYt -> "youtube"
        else -> "both"
    }

    MaffinetUnloadSheet(
        visible = showUnloadDialog,
        onDismissRequest = { showUnloadDialog = false },
        selectedKey = unloadCurrentMode,
        options = listOf(
            UnloadOption(key = "both", label = "VPN и Telegram", subtitle = "Выбранные сервисы и MTProto-прокси"),
            UnloadOption(key = "telegram", label = "Только Telegram", subtitle = "Только MTProto-прокси, без VPN"),
            UnloadOption(key = "youtube", label = "Только VPN", subtitle = "Выбранные сервисы через VPN")
        ),
        onOptionSelected = { key ->
            showUnloadDialog = false
            val edit = prefs.edit()
            when (key) {
                "both" -> {
                    edit.putBoolean("wants_youtube_bypass", true)
                    edit.putBoolean("telegram_proxy_enabled_by_user", true)
                }
                "telegram" -> {
                    edit.putBoolean("wants_youtube_bypass", false)
                    edit.putBoolean("telegram_proxy_enabled_by_user", true)
                }
                "youtube" -> {
                    edit.putBoolean("wants_youtube_bypass", true)
                    edit.putBoolean("telegram_proxy_enabled_by_user", false)
                }
            }
            edit.putBoolean("service_enabled", true)
            edit.putBoolean("econom_mode", true)
            edit.apply()

            ServiceManager.stop(context)
            val stopIntent = Intent(context, io.maffinet.android.core.tgproxy.TgProxyService::class.java).apply {
                action = io.maffinet.android.data.STOP_ACTION
            }
            context.startService(stopIntent)
            TgProxyController.stop()
            io.maffinet.android.service.WatchdogWorker.cancelPeriodicWork(context)
            io.maffinet.android.service.WatchdogReceiver.cancelWatchdogAlarm(context)

            scope.launch {
                kotlinx.coroutines.delay(800)
                val finalWantsYoutube = prefs.getBoolean("wants_youtube_bypass", true)
                val finalWantsTelegram = prefs.getBoolean("telegram_proxy_enabled_by_user", true)
                if (finalWantsYoutube) {
                    ServiceManager.start(context, Mode.VPN)
                }
                if (finalWantsTelegram) {
                    val openTg = prefs.getBoolean("open_tg_on_connect", true)
                    if (finalWantsYoutube) {
                        TgProxyController.startAsync(
                            context = context,
                            onSuccess = {
                                val port = TgProxyController.getPort(context)
                                val secret = TgProxyController.getOrGenerateSecret(context)
                                val url = TgProxyController.getTgProxyUrl(
                                    TgProxyController.DEFAULT_BIND_IP, port, secret
                                )
                                val alreadyConfigured = prefs.getBoolean("tg_proxy_configured", false)
                                if (openTg && !alreadyConfigured) {
                                    val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(tgIntent)
                                        prefs.edit().putBoolean("tg_proxy_configured", true).apply()
                                    } catch (_: Exception) {}
                                }
                            },
                            onError = {}
                        )
                    } else {
                        val startIntent = Intent(context, io.maffinet.android.core.tgproxy.TgProxyService::class.java).apply {
                            action = io.maffinet.android.data.START_ACTION
                            putExtra("open_tg", openTg)
                        }
                        androidx.core.content.ContextCompat.startForegroundService(context, startIntent)
                    }
                }
                val activity = context as? android.app.Activity
                activity?.finishAndRemoveTask()
            }
        }
    )

    MaffinetHintSheet(
        visible = showHintDialog,
        onDismissRequest = { showHintDialog = false },
        onRunInBackground = {
            showHintDialog = false
            showUnloadDialog = true
        },
        onNormalStart = {
            showHintDialog = false
            handleNormalStart()
        }
    )
}

fun formatTimer(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}
