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
        mutableStateOf((updateInfo != null && autoUpdateEnabled) || forceUpdateTest) 
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

    val auraBaseColor by animateColorAsState(
        targetValue = when {
            isRunning -> Color(0xFF8DE5C0)
            isPending -> Color(0xFF8DE5C0)
            else -> Color(0xFF8DE5C0)
        },
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
        label = "auraColor"
    )
    val auraAlpha by animateFloatAsState(
        targetValue = when {
            isRunning -> 0f
            isPending -> 0f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 1200, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
        label = "auraAlpha"
    )

    val performanceMode = performanceModeGlobal

    val infiniteTransition = rememberInfiniteTransition(label = "aura")
    val auraAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (performanceMode) Int.MAX_VALUE else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auraAngle"
    )
    val auraSecond by infiniteTransition.animateFloat(
        initialValue = (2f * Math.PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (performanceMode) Int.MAX_VALUE else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auraSecond"
    )
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = if (performanceMode) 0.65f else 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (performanceMode) Int.MAX_VALUE else 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraPulse"
    )

    val noiseImage = ImageBitmap.imageResource(id = R.drawable.noise)
    val noiseBrush = remember(noiseImage) {
        ShaderBrush(ImageShader(noiseImage, TileMode.Repeated, TileMode.Repeated))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08141B))
    ) {
        val screenHeight = maxHeight
        val buttonSize = if (isLandscape) {
            (configuration.screenWidthDp.dp * 0.28f).coerceIn(180.dp, maxButtonSize)
        } else {
            val topReserve = 180.dp
            val bottomReserve = navOverlayReserve + 142.dp
            val availableHeightForButton = maxHeight - topReserve - bottomReserve - 24.dp
            (configuration.screenWidthDp.dp * 0.72f)
                .coerceAtMost(availableHeightForButton)
                .coerceIn(150.dp, maxButtonSize)
        }

        if (!performanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(noiseBrush, alpha = 0f)
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = navOverlayReserve),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val inlineContent = remember {
                            mapOf(
                                "bolt_icon" to InlineTextContent(
                                    Placeholder(
                                        width = 24.sp,
                                        height = 24.sp,
                                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_maffinet_mark),
                                        contentDescription = null,
                                        tint = Color(0xFFEAF5F0),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        }
                        val annotatedText = remember {
                            buildAnnotatedString {
                                appendInlineContent("bolt_icon", "[bolt]")
                                append("  Maffinet")
                            }
                        }
                        Text(
                            text = annotatedText,
                            inlineContent = inlineContent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEAF5F0),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "(fork of NetFix Mobile by rupleide)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF93AEB7),
                            textAlign = TextAlign.Center
                        )

                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10232D))
                            .border(
                                width = 1.dp,
                                color = Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_maffinet_mark),
                                contentDescription = null,
                                tint = Color(0xFF93AEB7),
                                modifier = Modifier.size(18.dp)
                            )
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Задержка сети",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFEAF5F0)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = StrategyTestManager.getActiveStrategyName(context),
                                    fontSize = 12.sp,
                                    color = Color(0xFF93AEB7)
                                )
                            }
                        }
                        Text(
                            text = if (latencyMs >= 0) "$latencyMs мс" else "недоступно",
                            color = if (latencyMs >= 0) Color(0xFF8DE5C0) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    val tgButtonEnabled = isRunning
                    val ytButtonEnabled = currentStatus == AppStatus.Running

                    val tgInteractionSource = remember { MutableInteractionSource() }
                    val tgPressed by tgInteractionSource.collectIsPressedAsState()
                    val tgHovered by tgInteractionSource.collectIsHoveredAsState()
                    val tgFocused by tgInteractionSource.collectIsFocusedAsState()
                    val tgHighlighted = tgPressed || tgHovered || tgFocused

                    val tgBgColor by animateColorAsState(
                        targetValue = if (tgButtonEnabled) {
                            if (tgHighlighted) Color(0xFF284451) else Color(0xFF193440)
                        } else {
                            if (tgHighlighted) Color(0xFF202022) else Color(0xFF161618)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "tgBg"
                    )
                    val tgBorderColor by animateColorAsState(
                        targetValue = if (tgButtonEnabled) {
                            if (tgHighlighted) Color(0xFFFFFFFF) else Color(0x1AFFFFFF)
                        } else {
                            if (tgHighlighted) Color(0x33FFFFFF) else Color(0x0DFFFFFF)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "tgBorder"
                    )
                    val tgContentColor by animateColorAsState(
                        targetValue = if (tgButtonEnabled) {
                            if (tgHighlighted) Color.White else Color(0xFFEAF5F0)
                        } else {
                            if (tgHighlighted) Color(0xFF93AEB7) else Color(0xFF555555)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "tgContent"
                    )

                    val ytInteractionSource = remember { MutableInteractionSource() }
                    val ytPressed by ytInteractionSource.collectIsPressedAsState()
                    val ytHovered by ytInteractionSource.collectIsHoveredAsState()
                    val ytFocused by ytInteractionSource.collectIsFocusedAsState()
                    val ytHighlighted = ytPressed || ytHovered || ytFocused

                    val ytBgColor by animateColorAsState(
                        targetValue = if (ytButtonEnabled) {
                            if (ytHighlighted) Color(0xFF284451) else Color(0xFF193440)
                        } else {
                            if (ytHighlighted) Color(0xFF202022) else Color(0xFF161618)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "ytBg"
                    )
                    val ytBorderColor by animateColorAsState(
                        targetValue = if (ytButtonEnabled) {
                            if (ytHighlighted) Color(0xFFFFFFFF) else Color(0x1AFFFFFF)
                        } else {
                            if (ytHighlighted) Color(0x33FFFFFF) else Color(0x0DFFFFFF)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "ytBorder"
                    )
                    val ytContentColor by animateColorAsState(
                        targetValue = if (ytButtonEnabled) {
                            if (ytHighlighted) Color.White else Color(0xFFEAF5F0)
                        } else {
                            if (ytHighlighted) Color(0xFF93AEB7) else Color(0xFF555555)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "ytContent"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (telegramProxyEnabledByUser) {
                            Box(
                                modifier = Modifier
                                    .then(if (wantsYoutubeBypass) Modifier.weight(1f) else Modifier.fillMaxWidth())
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tgBgColor)
                                    .border(
                                        width = 1.dp,
                                        color = tgBorderColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .focusProperties { down = navBarFocusRequester }
                                    .clickable(
                                        enabled = tgButtonEnabled,
                                        interactionSource = tgInteractionSource,
                                        indication = ripple()
                                    ) {
                                        val port = TgProxyController.getPort(context)
                                        val secret = TgProxyController.getOrGenerateSecret(context)
                                        val url = TgProxyController.getTgProxyUrl(
                                            TgProxyController.DEFAULT_BIND_IP,
                                            port,
                                            secret
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            context.startActivity(intent)
                                            val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                                            prefs.edit().putBoolean("tg_proxy_configured", true).apply()
                                        } catch (_: Exception) {}
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_telegram),
                                        contentDescription = null,
                                        tint = tgContentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Telegram",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tgContentColor
                                    )
                                }
                            }
                        }

                        if (wantsYoutubeBypass) {
                            Box(
                                modifier = Modifier
                                    .then(if (telegramProxyEnabledByUser) Modifier.weight(1f) else Modifier.fillMaxWidth())
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ytBgColor)
                                    .border(
                                        width = 1.dp,
                                        color = ytBorderColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .focusProperties { down = navBarFocusRequester }
                                    .clickable(
                                        enabled = ytButtonEnabled && !isDownloadingSmartTube,
                                        interactionSource = ytInteractionSource,
                                        indication = ripple()
                                    ) {
                                        val isSmartTv = prefs.getBoolean("is_smart_tv", false)
                                        val youtubeMode = prefs.getString("youtube_mode", "official") ?: "official"
                                        val useSmartTube = youtubeMode == "smarttube"
                                        if (useSmartTube) {
                                            if (smartTubeInstalled) {
                                                io.maffinet.android.core.update.UpdateManager.openSmartTube(context)
                                            } else {
                                                scope.launch {
                                                    isDownloadingSmartTube = true
                                                    val url = io.maffinet.android.core.update.UpdateManager.getSmartTubeLatestUrl()
                                                    io.maffinet.android.core.update.UpdateManager.downloadAndInstallApk(
                                                        context = context,
                                                        downloadUrl = url,
                                                        fileName = "smarttube.apk",
                                                        onProgress = { progress ->
                                                            smartTubeDownloadProgress = progress
                                                        },
                                                        onError = {
                                                            isDownloadingSmartTube = false
                                                        }
                                                    )
                                                    isDownloadingSmartTube = false
                                                }
                                            }
                                        } else {
                                            val pm = context.packageManager
                                            val tvIntent = if (isSmartTv) pm.getLaunchIntentForPackage("com.google.android.youtube.tv") else null
                                            val intent = tvIntent
                                                ?: pm.getLaunchIntentForPackage("com.google.android.youtube")
                                                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                            try { context.startActivity(intent) } catch (_: Exception) {}
                                        }
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_youtube),
                                        contentDescription = null,
                                        tint = ytContentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isDownloadingSmartTube) {
                                            "Скачивание: ${(smartTubeDownloadProgress * 100).toInt()}%"
                                        } else {
                                            val youtubeMode = prefs.getString("youtube_mode", "official") ?: "official"
                                            if (youtubeMode == "smarttube") {
                                                if (smartTubeInstalled) "SmartTube" else "Установить SmartTube"
                                            } else {
                                                "YouTube"
                                            }
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ytContentColor
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = navOverlayReserve),
                    contentAlignment = Alignment.Center
                ) {
                    PowerButton(
                        buttonSize = buttonSize,
                        isRunning = isRunning,
                        isPending = isPending,
                        elapsedSeconds = elapsedSeconds,
                        performanceMode = performanceMode,
                        focusRequester = focusRequester,
                        navBarFocusRequester = navBarFocusRequester,
                        onClick = {
                            if (isRunning || isPending) {
                                stopAll()
                            } else {
                                val hintCount = hintPrefs.getInt("long_press_hint_count", 0)
                                if (hintCount < 2) {
                                    hintPrefs.edit().putInt("long_press_hint_count", hintCount + 1).apply()
                                    showHintDialog = true
                                } else {
                                    handleNormalStart()
                                }
                            }
                        },
                        onLongClick = { showUnloadDialog = true },
                        auraAngle = auraAngle,
                        auraSecond = auraSecond,
                        auraPulse = auraPulse,
                        auraBaseColor = auraBaseColor,
                        auraAlpha = auraAlpha,
                        noiseBrush = noiseBrush
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp)
                    .graphicsLayer {
                        alpha = headerAlpha
                        translationY = (1f - headerAlpha) * 22.dp.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val inlineContent = remember {
                            mapOf(
                                "bolt_icon" to InlineTextContent(
                                    Placeholder(
                                        width = 24.sp,
                                        height = 24.sp,
                                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_maffinet_mark),
                                        contentDescription = null,
                                        tint = Color(0xFFEAF5F0),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        }
                        val annotatedText = remember {
                            buildAnnotatedString {
                                appendInlineContent("bolt_icon", "[bolt]")
                                append("  Maffinet")
                            }
                        }
                        Text(
                            text = annotatedText,
                            inlineContent = inlineContent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEAF5F0),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "(fork of NetFix Mobile by rupleide)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF93AEB7),
                            textAlign = TextAlign.Center
                        )

                    }
                }

            val density = LocalDensity.current
            val buttonSizePx = with(density) { buttonSize.toPx() }
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.BiasAlignment(horizontalBias = 0f, verticalBias = -0.25f))
                    .size(buttonSize),
                contentAlignment = Alignment.Center
            ) {
                if (!performanceMode) {
                    Box(
                        modifier = Modifier
                            .size(buttonSize * 3.8f)
                            .drawBehind {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val orbitR = buttonSizePx * 0.2f
                                val c1x = cx + kotlin.math.cos(auraAngle) * orbitR
                                val c1y = cy + kotlin.math.sin(auraAngle) * orbitR
                                val c2x = cx + kotlin.math.cos(auraSecond) * orbitR * 0.7f
                                val c2y = cy + kotlin.math.sin(auraSecond) * orbitR * 0.7f
                                val r = buttonSizePx * 1.3f * auraPulse
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            auraBaseColor.copy(alpha = auraAlpha * buttonAlpha),
                                            Color.Transparent
                                        ),
                                        center = Offset(c1x, c1y),
                                        radius = r
                                    ),
                                    center = Offset(c1x, c1y),
                                    radius = r
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            auraBaseColor.copy(alpha = auraAlpha * 0.6f * buttonAlpha),
                                            Color.Transparent
                                        ),
                                        center = Offset(c2x, c2y),
                                        radius = r * 0.75f
                                    ),
                                    center = Offset(c2x, c2y),
                                    radius = r * 0.75f
                                )
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = buttonAlpha
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                ) {
                    PowerButton(
                        buttonSize = buttonSize,
                        isRunning = isRunning,
                        isPending = isPending,
                        elapsedSeconds = elapsedSeconds,
                        performanceMode = performanceMode,
                        focusRequester = focusRequester,
                        navBarFocusRequester = navBarFocusRequester,
                        onClick = {
                            if (isRunning || isPending) {
                                stopAll()
                            } else {
                                val hintCount = hintPrefs.getInt("long_press_hint_count", 0)
                                if (hintCount < 2) {
                                    hintPrefs.edit().putInt("long_press_hint_count", hintCount + 1).apply()
                                    showHintDialog = true
                                } else {
                                    handleNormalStart()
                                }
                            }
                        },
                        onLongClick = { showUnloadDialog = true },
                        auraAngle = auraAngle,
                        auraSecond = auraSecond,
                        auraPulse = auraPulse,
                        auraBaseColor = auraBaseColor,
                        auraAlpha = auraAlpha,
                        noiseBrush = noiseBrush
                    )
                }
            }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 500.dp)
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = navOverlayReserve + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = cardAlpha
                            translationY = (1f - cardAlpha) * 30.dp.toPx()
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10232D))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_maffinet_mark),
                                contentDescription = null,
                                tint = Color(0xFF93AEB7),
                                modifier = Modifier.size(18.dp)
                            )
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Задержка сети",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFEAF5F0)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = StrategyTestManager.getActiveStrategyName(context),
                                    fontSize = 12.sp,
                                    color = Color(0xFF93AEB7)
                                )
                            }
                        }
                        Text(
                            text = if (latencyMs >= 0) "$latencyMs мс" else "недоступно",
                            color = if (latencyMs >= 0) Color(0xFF8DE5C0) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    val tgButtonEnabled = isRunning
                    val ytButtonEnabled = currentStatus == AppStatus.Running

                    val tgInteractionSource = remember { MutableInteractionSource() }
                    val tgPressed by tgInteractionSource.collectIsPressedAsState()
                    val tgHovered by tgInteractionSource.collectIsHoveredAsState()
                    val tgFocused by tgInteractionSource.collectIsFocusedAsState()
                    val tgHighlighted = tgPressed || tgHovered || tgFocused

                    val tgBgColor by animateColorAsState(
                        targetValue = if (tgButtonEnabled) {
                            if (tgHighlighted) Color(0xFF284451) else Color(0xFF193440)
                        } else {
                            if (tgHighlighted) Color(0xFF202022) else Color(0xFF161618)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "tgBg"
                    )
                    val tgBorderColor by animateColorAsState(
                        targetValue = if (tgButtonEnabled) {
                            if (tgHighlighted) Color(0xFFFFFFFF) else Color(0x1AFFFFFF)
                        } else {
                            if (tgHighlighted) Color(0x33FFFFFF) else Color(0x0DFFFFFF)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "tgBorder"
                    )
                    val tgContentColor by animateColorAsState(
                        targetValue = if (tgButtonEnabled) {
                            if (tgHighlighted) Color.White else Color(0xFFEAF5F0)
                        } else {
                            if (tgHighlighted) Color(0xFF93AEB7) else Color(0xFF555555)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "tgContent"
                    )

                    val ytInteractionSource = remember { MutableInteractionSource() }
                    val ytPressed by ytInteractionSource.collectIsPressedAsState()
                    val ytHovered by ytInteractionSource.collectIsHoveredAsState()
                    val ytFocused by ytInteractionSource.collectIsFocusedAsState()
                    val ytHighlighted = ytPressed || ytHovered || ytFocused

                    val ytBgColor by animateColorAsState(
                        targetValue = if (ytButtonEnabled) {
                            if (ytHighlighted) Color(0xFF284451) else Color(0xFF193440)
                        } else {
                            if (ytHighlighted) Color(0xFF202022) else Color(0xFF161618)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "ytBg"
                    )
                    val ytBorderColor by animateColorAsState(
                        targetValue = if (ytButtonEnabled) {
                            if (ytHighlighted) Color(0xFFFFFFFF) else Color(0x1AFFFFFF)
                        } else {
                            if (ytHighlighted) Color(0x33FFFFFF) else Color(0x0DFFFFFF)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "ytBorder"
                    )
                    val ytContentColor by animateColorAsState(
                        targetValue = if (ytButtonEnabled) {
                            if (ytHighlighted) Color.White else Color(0xFFEAF5F0)
                        } else {
                            if (ytHighlighted) Color(0xFF93AEB7) else Color(0xFF555555)
                        },
                        animationSpec = tween(if (performanceMode) 0 else 150),
                        label = "ytContent"
                    )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = quickButtonsAlpha
                            translationY = (1f - quickButtonsAlpha) * 30.dp.toPx()
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        if (telegramProxyEnabledByUser) {
                            Box(
                                modifier = Modifier
                                    .then(if (wantsYoutubeBypass) Modifier.weight(1f) else Modifier.fillMaxWidth())
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tgBgColor)
                                    .border(
                                        width = 1.dp,
                                        color = tgBorderColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .focusProperties { down = navBarFocusRequester }
                                    .clickable(
                                        enabled = tgButtonEnabled,
                                        interactionSource = tgInteractionSource,
                                        indication = ripple()
                                    ) {
                                        val port = TgProxyController.getPort(context)
                                        val secret = TgProxyController.getOrGenerateSecret(context)
                                        val url = TgProxyController.getTgProxyUrl(
                                            TgProxyController.DEFAULT_BIND_IP,
                                            port,
                                            secret
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            context.startActivity(intent)
                                            val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                                            prefs.edit().putBoolean("tg_proxy_configured", true).apply()
                                        } catch (_: Exception) {}
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_telegram),
                                        contentDescription = null,
                                        tint = tgContentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Telegram",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tgContentColor
                                    )
                                }
                            }
                        }

                        if (wantsYoutubeBypass) {
                            Box(
                                modifier = Modifier
                                    .then(if (telegramProxyEnabledByUser) Modifier.weight(1f) else Modifier.fillMaxWidth())
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ytBgColor)
                                    .border(
                                        width = 1.dp,
                                        color = ytBorderColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .focusProperties { down = navBarFocusRequester }
                                    .clickable(
                                        enabled = ytButtonEnabled && !isDownloadingSmartTube,
                                        interactionSource = ytInteractionSource,
                                        indication = ripple()
                                    ) {
                                        val isSmartTv = prefs.getBoolean("is_smart_tv", false)
                                        val youtubeMode = prefs.getString("youtube_mode", "official") ?: "official"
                                        val useSmartTube = youtubeMode == "smarttube"
                                        if (useSmartTube) {
                                            if (smartTubeInstalled) {
                                                io.maffinet.android.core.update.UpdateManager.openSmartTube(context)
                                            } else {
                                                scope.launch {
                                                    isDownloadingSmartTube = true
                                                    val url = io.maffinet.android.core.update.UpdateManager.getSmartTubeLatestUrl()
                                                    io.maffinet.android.core.update.UpdateManager.downloadAndInstallApk(
                                                        context = context,
                                                        downloadUrl = url,
                                                        fileName = "smarttube.apk",
                                                        onProgress = { progress ->
                                                            smartTubeDownloadProgress = progress
                                                        },
                                                        onError = {
                                                            isDownloadingSmartTube = false
                                                        }
                                                    )
                                                    isDownloadingSmartTube = false
                                                }
                                            }
                                        } else {
                                            val pm = context.packageManager
                                            val tvIntent = if (isSmartTv) pm.getLaunchIntentForPackage("com.google.android.youtube.tv") else null
                                            val intent = tvIntent
                                                ?: pm.getLaunchIntentForPackage("com.google.android.youtube")
                                                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                            try { context.startActivity(intent) } catch (_: Exception) {}
                                        }
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_youtube),
                                        contentDescription = null,
                                        tint = ytContentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isDownloadingSmartTube) {
                                            "Скачивание: ${(smartTubeDownloadProgress * 100).toInt()}%"
                                        } else {
                                            val youtubeMode = prefs.getString("youtube_mode", "official") ?: "official"
                                            if (youtubeMode == "smarttube") {
                                                if (smartTubeInstalled) "SmartTube" else "Установить SmartTube"
                                            } else {
                                                "YouTube"
                                            }
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ytContentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    }

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
            UnloadOption(key = "both", label = "Telegram и YouTube", subtitle = "Обход для мессенджера и стриминга"),
            UnloadOption(key = "telegram", label = "Только Telegram", subtitle = "Только MTProto-прокси, без VPN"),
            UnloadOption(key = "youtube", label = "Только YouTube", subtitle = "Только VPN-тоннель, без прокси")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun PowerButton(
    buttonSize: androidx.compose.ui.unit.Dp,
    isRunning: Boolean,
    isPending: Boolean,
    elapsedSeconds: Int,
    performanceMode: Boolean,
    focusRequester: FocusRequester,
    navBarFocusRequester: FocusRequester,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    auraAngle: Float,
    auraSecond: Float,
    auraPulse: Float,
    auraBaseColor: Color,
    auraAlpha: Float,
    noiseBrush: ShaderBrush
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed && !performanceMode) 0.97f else 1f, label = "connectionPress")
    val container by animateColorAsState(
        if (isRunning) Color(0xFF8DE5C0) else Color(0xFF10232D),
        tween(if (performanceMode) 0 else 250), label = "connectionBackground"
    )
    val foreground = if (isRunning) Color(0xFF082A21) else Color(0xFF8DE5C0)
    val label = when {
        isPending -> "Подключение…"
        isRunning -> "Подключено"
        else -> "Подключиться"
    }
    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .focusRequester(focusRequester)
            .focusProperties { down = navBarFocusRequester }
            .clip(RoundedCornerShape(40.dp))
            .background(container)
            .border(if (focused) 3.dp else 1.dp, foreground, RoundedCornerShape(40.dp))
            .combinedClickable(
                interactionSource = interactions,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_power), contentDescription = label, tint = foreground,
                modifier = Modifier.size(buttonSize * 0.25f))
            Text(label, color = foreground, fontWeight = FontWeight.SemiBold,
                fontSize = if (buttonSize < 220.dp) 16.sp else 20.sp, textAlign = TextAlign.Center)
            Text(if (isRunning) formatTimer(elapsedSeconds) else if (isPending) "Ожидаем подключение" else "Выбранные приложения",
                color = foreground.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
@Composable
fun StatItem(icon: String, title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 14.sp, color = Color(0xFF93AEB7))
            Text(text = title, fontSize = 12.sp, color = Color(0xFF93AEB7))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEAF5F0)
        )
    }
}
