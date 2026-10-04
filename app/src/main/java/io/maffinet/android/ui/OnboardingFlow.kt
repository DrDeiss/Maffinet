package io.maffinet.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.maffinet.android.R
import io.maffinet.android.BuildConfig
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.strategy.StrategyEvaluation
import io.maffinet.android.core.strategy.StrategyScorer
import io.maffinet.android.data.OnboardingStep
import io.maffinet.android.data.strategy.ProbeTargetRepository
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingFlow(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onboardingStepSaver = Saver<OnboardingStep, String>(
        save = { it::class.java.simpleName },
        restore = { name ->
            when (name) {
                "Welcome" -> OnboardingStep.Welcome
                "DeviceType" -> OnboardingStep.DeviceType
                "TelegramProxyQuestion" -> OnboardingStep.TelegramProxyQuestion
                "YoutubeBypassQuestion" -> OnboardingStep.YoutubeBypassQuestion
                "StrategyTestingExisting" -> OnboardingStep.StrategyTestingExisting
                "StrategyTestingRunning" -> OnboardingStep.StrategyTestingRunning
                "StrategyTestingResult" -> OnboardingStep.StrategyTestingResult
                "AboutAndSupport" -> OnboardingStep.AboutAndSupport
                "Summary" -> OnboardingStep.Summary
                "FinalGreeting" -> OnboardingStep.FinalGreeting
                else -> OnboardingStep.Welcome
            }
        }
    )

    val stepsHistorySaver = Saver<SnapshotStateList<OnboardingStep>, List<String>>(
        save = { list -> list.map { it::class.java.simpleName } },
        restore = { names ->
            val list = mutableStateListOf<OnboardingStep>()
            names.forEach { name ->
                val step = when (name) {
                    "Welcome" -> OnboardingStep.Welcome
                    "DeviceType" -> OnboardingStep.DeviceType
                    "TelegramProxyQuestion" -> OnboardingStep.TelegramProxyQuestion
                    "YoutubeBypassQuestion" -> OnboardingStep.YoutubeBypassQuestion
                    "StrategyTestingExisting" -> OnboardingStep.StrategyTestingExisting
                    "StrategyTestingRunning" -> OnboardingStep.StrategyTestingRunning
                    "StrategyTestingResult" -> OnboardingStep.StrategyTestingResult
                    "AboutAndSupport" -> OnboardingStep.AboutAndSupport
                    "Summary" -> OnboardingStep.Summary
                    "FinalGreeting" -> OnboardingStep.FinalGreeting
                    else -> OnboardingStep.Welcome
                }
                list.add(step)
            }
            list
        }
    )

    val nullableBooleanSaver = Saver<Boolean?, Any>(
        save = { it ?: -1 },
        restore = { saved ->
            when (saved) {
                true -> true
                false -> false
                else -> null
            }
        }
    )

    var currentStep by rememberSaveable(stateSaver = onboardingStepSaver) { mutableStateOf<OnboardingStep>(OnboardingStep.Welcome) }

    var wantsTelegramProxy by rememberSaveable(stateSaver = nullableBooleanSaver) { mutableStateOf<Boolean?>(null) }
    var wantsYoutubeBypass by rememberSaveable(stateSaver = nullableBooleanSaver) { mutableStateOf<Boolean?>(null) }
    var deviceIsAndroid by rememberSaveable(stateSaver = nullableBooleanSaver) { mutableStateOf<Boolean?>(null) }

    val stepsHistory = rememberSaveable(saver = stepsHistorySaver) { mutableStateListOf<OnboardingStep>() }

    fun navigateTo(step: OnboardingStep) {
        stepsHistory.add(currentStep)
        currentStep = step
    }

    fun navigateBack() {
        if (stepsHistory.isNotEmpty()) {
            currentStep = stepsHistory.removeAt(stepsHistory.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08141B))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn(tween(350)) togetherWith fadeOut(tween(200))
            },
            modifier = Modifier.fillMaxSize(),
            label = "onboardingTransition"
        ) { step ->
            when (step) {
                OnboardingStep.FinalGreeting -> {
                    FinalGreetingScreen(onCompleted = onCompleted)
                }
                else -> {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = if (isLandscape) 600.dp else 400.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            when (step) {
                                OnboardingStep.Welcome -> WelcomeStep(onNavigate = ::navigateTo)
                                OnboardingStep.DeviceType -> DeviceTypeStep(
                                    onNavigate = ::navigateTo,
                                    onSetDeviceIsAndroid = { deviceIsAndroid = it }
                                )
                                OnboardingStep.TelegramProxyQuestion -> TelegramProxyStep(
                                    onNavigate = ::navigateTo,
                                    deviceIsAndroid = deviceIsAndroid ?: true,
                                    onSetTelegramProxy = { wantsTelegramProxy = it }
                                )
                                OnboardingStep.YoutubeBypassQuestion -> YoutubeBypassStep(
                                    onNavigate = ::navigateTo,
                                    onSetYoutubeBypass = { wantsYoutubeBypass = it },
                                    isSmartTv = context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
                                        .getBoolean("is_smart_tv", false)
                                )
                                OnboardingStep.StrategyTestingExisting -> StrategyTestingExistingStep(onNavigate = ::navigateTo)
                                OnboardingStep.StrategyTestingRunning -> StrategyTestingRunningStep(
                                    context = context,
                                    onNavigate = ::navigateTo
                                )
                                OnboardingStep.StrategyTestingResult -> StrategyTestingResultStep(onNavigate = ::navigateTo)
                                OnboardingStep.AboutAndSupport -> AboutAndSupportStep(onNavigate = ::navigateTo)
                                OnboardingStep.Summary -> SummaryStep(
                                    wantsTelegramProxy = wantsTelegramProxy,
                                    wantsYoutubeBypass = wantsYoutubeBypass,
                                    onNavigate = ::navigateTo
                                )
                                else -> {}
                            }
                        }
                    }
                }
            }
        }

        if (currentStep != OnboardingStep.Welcome
            && currentStep != OnboardingStep.StrategyTestingRunning
            && currentStep != OnboardingStep.FinalGreeting
        ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val backPressed by backInteractionSource.collectIsPressedAsState()
            val backHovered by backInteractionSource.collectIsHoveredAsState()
            val backFocused by backInteractionSource.collectIsFocusedAsState()
            val backHighlighted = backPressed || backHovered || backFocused
            val backColor by animateColorAsState(
                targetValue = if (backHighlighted) Color.White else Color(0xFF93AEB7),
                animationSpec = tween(150),
                label = "backColor"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .clickable(
                        interactionSource = backInteractionSource,
                        indication = null
                    ) { navigateBack() }
                    .padding(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow),
                    contentDescription = null,
                    tint = backColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(rotationZ = -90f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Назад",
                    color = backColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ButtonSlot(
    visible: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val resolvedMinHeight = if (minHeight != androidx.compose.ui.unit.Dp.Unspecified) {
        minHeight
    } else {
        if (isLandscape) 44.dp else 52.dp
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = if (isLandscape) 360.dp else androidx.compose.ui.unit.Dp.Unspecified)
            .defaultMinSize(minHeight = resolvedMinHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            content()
        }
    }
}

@Composable
private fun WelcomeStep(onNavigate: (OnboardingStep) -> Unit) {
    var showText by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        showText = true
        delay(1300)
        showButton = true
    }

    LaunchedEffect(showButton) {
        if (showButton) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showText,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Добро пожаловать в Maffinet",
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Настройте доступ к выбранным сервисам. Стратегия зависит от вашей сети и провайдера.",
                    fontSize = 16.sp,
                    color = Color(0xFF93AEB7),
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        ButtonSlot(visible = showButton) {
            OnboardingButton(
                text = "Начать",
                modifier = Modifier.focusRequester(focusRequester),
                onClick = { onNavigate(OnboardingStep.DeviceType) }
            )
        }
    }
}

@Composable
private fun DeviceTypeStep(
    onNavigate: (OnboardingStep) -> Unit,
    onSetDeviceIsAndroid: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showContent by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { showContent = true }

    LaunchedEffect(showContent) {
        if (showContent) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Где вы будете использовать Maffinet?",
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Подстроим интерфейс под ваш экран.",
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF93AEB7),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CardButton(
                        title = "На смартфоне",
                        iconRes = R.drawable.ic_phone,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        onClick = {
                            onSetDeviceIsAndroid(true)
                            context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("device_type_tv", false)
                                .putBoolean("is_smart_tv", false)
                                .apply()
                            onNavigate(OnboardingStep.TelegramProxyQuestion)
                        }
                    )
                    CardButton(
                        title = "На Smart TV",
                        iconRes = R.drawable.ic_smart_tv,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSetDeviceIsAndroid(false)
                            context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("device_type_tv", true)
                                .putBoolean("is_smart_tv", true)
                                .apply()
                            onNavigate(OnboardingStep.TelegramProxyQuestion)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TelegramProxyStep(
    onNavigate: (OnboardingStep) -> Unit,
    deviceIsAndroid: Boolean,
    onSetTelegramProxy: (Boolean) -> Unit
) {
    var showText by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        showText = true
        delay(1300)
        showButtons = true
    }

    LaunchedEffect(showButtons) {
        if (showButtons) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val title = if (deviceIsAndroid) {
        "Починить Telegram?"
    } else {
        "Пользуетесь Telegram на ТВ?"
    }
    val description = if (deviceIsAndroid) {
        "Настроим прокси автоматически, останется нажать «Подключить» в самом Telegram."
    } else {
        "Если нет, пропустите, настроить можно позже."
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showText,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = description,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF93AEB7),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        ButtonSlot(visible = showButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SecondaryButton(
                    text = "Пропустить",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSetTelegramProxy(false)
                        onNavigate(OnboardingStep.YoutubeBypassQuestion)
                    }
                )
                OnboardingButton(
                    text = "Да, настроить",
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    onClick = {
                        onSetTelegramProxy(true)
                        onNavigate(OnboardingStep.YoutubeBypassQuestion)
                    }
                )
            }
        }
    }
}

@Composable
private fun TvYoutubeCardButton(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isPressed || isHovered || isFocused

    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFF284451) else Color(0xFF193440),
        animationSpec = tween(150),
        label = ""
    )
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) Color.White else Color(0x1AFFFFFF),
        animationSpec = tween(150),
        label = ""
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) Color.White else Color(0xFFEAF5F0)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF93AEB7),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun YoutubeBypassStep(
    onNavigate: (OnboardingStep) -> Unit,
    onSetYoutubeBypass: (Boolean) -> Unit,
    isSmartTv: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showText by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        showText = true
        delay(1300)
        showButtons = true
    }

    LaunchedEffect(showButtons) {
        if (showButtons) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showText,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isSmartTv) {
                        "Разблокировать YouTube?"
                    } else {
                        "Включить VPN для приложений?"
                    },
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSmartTv) {
                        "Для YouTube на Android TV доступна настройка SmartTube. Выберите вариант подключения."
                    } else {
                        "VPN направляет выбранные приложения через Maffinet. После настройки выберите нужные приложения на главном экране. Чтобы проверять сайты в браузере, добавьте и браузер."
                    },
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF93AEB7),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        ButtonSlot(
            visible = showButtons,
            minHeight = if (isSmartTv) 200.dp else androidx.compose.ui.unit.Dp.Unspecified
        ) {
            if (isSmartTv) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvYoutubeCardButton(
                        title = "SmartTube (Рекомендуется)",
                        subtitle = "Простой и удобный аналог YouTube для ТВ без рекламы. Maffinet автоматически скачает, установит и настроит его для плавной работы.",
                        modifier = Modifier.focusRequester(focusRequester),
                        onClick = {
                            context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putString("work_mode", "Proxy")
                                .putString("youtube_mode", "smarttube")
                                .putString("byedpi_proxy_port", "1080")
                                .putBoolean("is_smart_tv", true)
                                .putBoolean(io.maffinet.android.data.settings.MaffinetSettingsRepository.APPLICATIONS_ENABLED, true)
                                .apply()
                            onSetYoutubeBypass(true)
                            scope.launch {
                                try {
                                    val url = io.maffinet.android.core.update.UpdateManager.getSmartTubeLatestUrl()
                                    io.maffinet.android.core.update.UpdateManager.downloadAndInstallApk(
                                        context = context,
                                        downloadUrl = url,
                                        fileName = "smarttube.apk",
                                        onProgress = {},
                                        onError = {}
                                    )
                                } catch (_: Exception) {}
                            }
                            val hasResults = StrategyTestManager.testResults.isNotEmpty()
                            if (hasResults) {
                                onNavigate(OnboardingStep.StrategyTestingExisting)
                            } else {
                                onNavigate(OnboardingStep.StrategyTestingRunning)
                            }
                        }
                    )
                    TvYoutubeCardButton(
                        title = "Официальный YouTube",
                        subtitle = "Скорее всего, не запустится или будет постоянно вылетать в фоне из-за нехватки оперативной памяти ТВ.",
                        onClick = {
                            context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putString("work_mode", "VPN")
                                .putString("youtube_mode", "official")
                                .putBoolean("is_smart_tv", true)
                                .putBoolean(io.maffinet.android.data.settings.MaffinetSettingsRepository.APPLICATIONS_ENABLED, true)
                                .apply()
                            onSetYoutubeBypass(true)
                            val hasResults = StrategyTestManager.testResults.isNotEmpty()
                            if (hasResults) {
                                onNavigate(OnboardingStep.StrategyTestingExisting)
                            } else {
                                onNavigate(OnboardingStep.StrategyTestingRunning)
                            }
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SecondaryButton(
                        text = "Пропустить",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean(io.maffinet.android.data.settings.MaffinetSettingsRepository.APPLICATIONS_ENABLED, false)
                                .apply()
                            onSetYoutubeBypass(false)
                            onNavigate(OnboardingStep.AboutAndSupport)
                        }
                    )
                    OnboardingButton(
                        text = "Да, включить",
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        onClick = {
                            context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean(io.maffinet.android.data.settings.MaffinetSettingsRepository.APPLICATIONS_ENABLED, true)
                                .apply()
                            onSetYoutubeBypass(true)
                            val hasResults = StrategyTestManager.testResults.isNotEmpty()
                            if (hasResults) {
                                onNavigate(OnboardingStep.StrategyTestingExisting)
                            } else {
                                onNavigate(OnboardingStep.StrategyTestingRunning)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyTestingExistingStep(onNavigate: (OnboardingStep) -> Unit) {
    val context = LocalContext.current
    val isSmartTv = remember(context) {
        context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
            .getBoolean("is_smart_tv", false)
    }

    var showContent by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        showContent = true
        delay(1300)
        showButtons = true
    }

    LaunchedEffect(showButtons) {
        if (showButtons) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val strategyName = StrategyTestManager.getActiveStrategyName(context)
    val results = onboardingStrategyResults(context)
    val complete = StrategyScorer.bestComplete(results, ProbeTargetRepository(context).urls())
    val best = results.firstOrNull()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Есть сохранённые результаты",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when {
                        complete != null -> "В прошлой проверке все заданные адреса прошли проверку. Текущая стратегия:"
                        best != null && best.passedServices > 0 -> "Лучший прошлый результат: ${best.passedServices}/${best.totalServices} адресов. Полного результата нет. Текущая стратегия:"
                        else -> "Для текущих адресов нет подтверждённого полного результата. Текущая стратегия:"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF93AEB7),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10232D))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF8DE5C0).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (complete != null) R.drawable.ic_check else R.drawable.ic_bolt),
                            contentDescription = null,
                            tint = Color(0xFF8DE5C0),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = strategyName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEAF5F0),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Сохранить текущую стратегию или повторить проверку?",
                    fontSize = 14.sp,
                    color = Color(0xFF93AEB7),
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        ButtonSlot(
            visible = showButtons,
            minHeight = if (isSmartTv) 160.dp else 130.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OnboardingButton(
                    text = "Оставить как есть",
                    modifier = Modifier.focusRequester(focusRequester),
                    onClick = { onNavigate(OnboardingStep.AboutAndSupport) }
                )
                SecondaryButton(
                    text = "Перепроверить",
                    onClick = { onNavigate(OnboardingStep.StrategyTestingRunning) }
                )
            }
        }
    }
}

private fun onboardingStrategyResults(context: android.content.Context): List<StrategyEvaluation> =
    if (StrategyTestManager.historyMatchesCurrentConfiguration(context)) {
        StrategyTestManager.matrixResults.values.sortedWith(StrategyScorer.comparator)
    } else emptyList()

@Composable
private fun StrategyTestingRunningStep(
    context: android.content.Context,
    onNavigate: (OnboardingStep) -> Unit
) {
    LaunchedEffect(Unit) {
        val job = StrategyTestManager.startTesting(context)
        if (job != null) {
            job.join()
        } else {
            while (StrategyTestManager.isTesting) {
                delay(200)
            }
        }
        onNavigate(OnboardingStep.StrategyTestingResult)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Тестирование сети",
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEAF5F0),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Проверяем варианты обхода. Пожалуйста, подождите.",
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Color(0xFF93AEB7),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        CircularProgressIndicator(
            color = Color(0xFF8DE5C0),
            strokeWidth = 4.dp,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (StrategyTestManager.currentTestIndex > 0) {
                "Проверено вариантов: ${StrategyTestManager.currentTestIndex} из ${StrategyTestManager.totalStrategiesCount}"
            } else {
                "Подготовка к тестированию..."
            },
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Color(0xFF93AEB7),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        val progress = if (StrategyTestManager.totalStrategiesCount > 0) {
            StrategyTestManager.currentTestIndex / StrategyTestManager.totalStrategiesCount.toFloat()
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .background(Color(0xFF8DE5C0))
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val facts = remember {
            listOfNotNull(
                "Первый реестр запрещённых сайтов создан в России 1 ноября 2012 года на основании закона № 139-ФЗ.",
                "Закон о «суверенном Рунете» № 90-ФЗ вступил в силу 1 ноября 2019 года, обязав операторов установить ТСПУ.",
                "ТСПУ (технические средства противодействия угрозам) - это оборудование DPI, управляемое напрямую Роскомнадзором.",
                if (BuildConfig.SHOW_UPSTREAM_ATTRIBUTION) "Maffinet — независимый проект на основе открытого кода NetFix Mobile. Автор исходного проекта: rupleide." else null,
                "Мобильный Maffinet объединил в одном приложении два сложных нативных ядра на Rust и C. Главная цель проекта, убрать сложные настройки и консоли из рук пользователя.",
                "Instagram и Facebook были официально внесены в реестр запрещённых сайтов и заблокированы в России в марте 2022 года.",
                "С 2021 года Роскомнадзор активно блокирует VPN-протоколы (OpenVPN, WireGuard) по цифровым сигнатурам пакетов.",
                "Закон «Лугового» разрешил Генеральной прокуратуре блокировать сайты во внесудебном порядке за экстремизм.",
                "С 1 января 2022 года закон о «приземлении» обязал крупные IT-компании открывать филиалы в Российской Федерации.",
                "В 2021-2022 годах суды РФ назначили Google оборотные штрафы на сумму более 20 млрд рублей за неудаление контента.",
                "Замедление YouTube летом 2024 года фактически осуществляется через ТСПУ, анализирующие SNI сетевых пакетов.",
                "Серверы Google Global Cache устанавливались у провайдеров РФ для ускорения загрузки видео из локаческой сети.",
                "Технология DPI (Deep Packet Inspection) позволяет «вскрывать» пакеты и фильтровать HTTPS-трафик по доменному имени.",
                "Почтовый сервис ProtonMail был заблокирован в РФ в 2020 году из-за рассылки ложных угроз о минировании.",
                "Крупнейший русскоязычный торрент-трекер Rutracker.org был заблокирован «пожизненно» решением суда в 2016 году.",
                "В августе 2024 года Павел Дуров был задержан в аэропорту Парижа по обвинениям французских властей, связанным с модерацией в Telegram.",
                "Осенью 2024 года Telegram обновил правила, разрешив передавать властям IP-адреса и номера телефонов нарушителей при наличии ордера.",
                "В 2024 году Telegram запустил внутреннюю валюту Telegram Stars для оплаты цифровых услуг и покупок в мини-приложениях.",
                "В 2024-2025 годах бум кликеров (Notcoin, Hamster Kombat) привёл к рекордному росту ежемесячной аудитории Telegram до 950 млн человек.",
                "Интеграция блокчейна TON и кошелька Wallet превратила Telegram в один из крупнейших мировых хабов для криптовалютных микротранзакций.",
                "В 2025 году в Telegram появилась встроенная поддержка децентрализованных веб-страниц и глобальный каталог мини-приложений.",
                "Мессенджер Telegram был официально запущен 14 августа 2013 года. Его визитной карточкой стал собственный протокол шифрования MTProto.",
                "В 2025 году общее число платных подписчиков Premium-версии мессенджера Telegram превысило отметку в 10 миллионов пользователей.",
                "С 2024 года авторы публичных каналов в Telegram могут получать до 50% дохода от показа рекламы с мгновенным выводом средств в TON.",
                "Популярный геймерский мессенджер Discord был заблокирован Роскомнадзором на территории РФ в октябре 2024 года.",
                "В 2025 году бизнес-аккаунты Telegram получили поддержку ИИ-ассистентов, способных автоматически обрабатывать клиентские запросы.",
                "Функция поиска людей поблизости в Telegram была переработана из-за соображений приватности и борьбы с роботизированным спамом.",
                "В разное время доступ к Telegram полностью или частично блокировали в Китае, Иране, Бразилии и ряде других стран.",
                "В 2026 году Telegram активно развивает децентрализованные методы облачного хранения данных для повышения отказоустойчивости.",
                "Защищенный мессенджер Signal, использующий сквозное шифрование, был заблокирован на территории РФ в августе 2024 года."
            )
        }
        var currentFactIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(currentFactIndex) {
            delay(11000)
            currentFactIndex = (currentFactIndex + 1) % facts.size
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val prevInteractionSource = remember { MutableInteractionSource() }
            val prevPressed by prevInteractionSource.collectIsPressedAsState()
            val prevHovered by prevInteractionSource.collectIsHoveredAsState()
            val prevFocused by prevInteractionSource.collectIsFocusedAsState()
            val prevHighlighted = prevPressed || prevHovered || prevFocused
            val prevColor by animateColorAsState(
                targetValue = if (prevHighlighted) Color.White else Color(0x80FFFFFF),
                animationSpec = tween(150),
                label = "prevColor"
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (prevHighlighted) Color(0x1AFFFFFF) else Color.Transparent)
                    .clickable(
                        interactionSource = prevInteractionSource,
                        indication = null
                    ) {
                        currentFactIndex = if (currentFactIndex > 0) currentFactIndex - 1 else facts.size - 1
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow),
                    contentDescription = null,
                    tint = prevColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(rotationZ = -90f)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 155.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "ИНТЕРЕСНЫЙ ФАКТ",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(
                    targetState = facts[currentFactIndex],
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "factAnimation"
                ) { factText ->
                    Text(
                        text = factText,
                        color = Color(0xFF93AEB7),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        minLines = 4
                    )
                }
            }

            val nextInteractionSource = remember { MutableInteractionSource() }
            val nextPressed by nextInteractionSource.collectIsPressedAsState()
            val nextHovered by nextInteractionSource.collectIsHoveredAsState()
            val nextFocused by nextInteractionSource.collectIsFocusedAsState()
            val nextHighlighted = nextPressed || nextHovered || nextFocused
            val nextColor by animateColorAsState(
                targetValue = if (nextHighlighted) Color.White else Color(0x80FFFFFF),
                animationSpec = tween(150),
                label = "nextColor"
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (nextHighlighted) Color(0x1AFFFFFF) else Color.Transparent)
                    .clickable(
                        interactionSource = nextInteractionSource,
                        indication = null
                    ) {
                        currentFactIndex = (currentFactIndex + 1) % facts.size
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow),
                    contentDescription = null,
                    tint = nextColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(rotationZ = 90f)
                )
            }
        }
    }
}

@Composable
private fun StrategyTestingResultStep(onNavigate: (OnboardingStep) -> Unit) {
    val context = LocalContext.current
    var showContent by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        showContent = true
        delay(1300)
        showButton = true
    }

    LaunchedEffect(showButton) {
        if (showButton) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val isError = StrategyTestManager.hasConnectionError
    val results = onboardingStrategyResults(context)
    val complete = StrategyScorer.bestComplete(results, ProbeTargetRepository(context).urls())
    val best = results.firstOrNull()
    val fullApplied = complete != null && complete.command == StrategyTestManager.bestStrategyResult &&
        complete.command == StrategyTestManager.appliedStrategy
    val needsRetry = isError || !fullApplied

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        isError -> "Проверка завершилась с ошибкой"
                        complete != null -> "Все адреса прошли проверку"
                        best != null && best.passedServices > 0 -> "Частичный результат"
                        else -> "Полного результата нет"
                    },
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) Color(0xFFEF4444) else Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                results.take(2).forEach { result ->
                    ResultCard(
                        strategy = result.command,
                        status = "${result.passedServices}/${result.totalServices} адресов" +
                            (result.averageLatencyMs?.let { " · $it мс" } ?: ""),
                        fullyReachable = StrategyScorer.bestComplete(listOf(result), ProbeTargetRepository(context).urls()) != null
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = when {
                        isError -> StrategyTestManager.currentProgress.ifBlank { "Не удалось завершить проверку." } +
                            if (fullApplied) "\nСтратегия, прошедшая полную проверку, применена." else "\nПрежняя стратегия сохранена."
                        fullApplied -> "Стратегия применена: все заданные адреса прошли HTTP/TLS-проверку. Это не подтверждает работу всех функций приложений."
                        complete != null -> "Все адреса прошли проверку, но автоматический выбор не был завершён. Прежняя стратегия сохранена."
                        best != null && best.passedServices > 0 -> "Лучший результат: ${best.passedServices}/${best.totalServices} адресов. Прежняя стратегия сохранена. Повторить проверку или выбрать вариант вручную можно в разделе «Стратегии»."
                        best != null -> "Ни один из ${best.totalServices} адресов не прошёл проверку. Прежняя стратегия сохранена."
                        else -> "Нет завершённых результатов проверки. Прежняя стратегия сохранена."
                    },
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF93AEB7),
                    textAlign = TextAlign.Center
                )
                best?.failedTargets?.map { it.url }?.distinct()?.takeIf { it.isNotEmpty() }?.let { failedUrls ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Не прошли проверку:\n${failedUrls.joinToString("\n")}",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF93AEB7),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ButtonSlot(
            visible = showButton,
            minHeight = if (needsRetry) (if (isLandscape) 100.dp else 116.dp) else (if (isLandscape) 44.dp else 52.dp)
        ) {
            if (needsRetry) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OnboardingButton(
                        text = "Повторить попытку",
                        modifier = Modifier.focusRequester(focusRequester),
                        onClick = { onNavigate(OnboardingStep.StrategyTestingRunning) }
                    )
                    SecondaryButton(
                        text = "Продолжить с текущей стратегией",
                        onClick = { onNavigate(OnboardingStep.AboutAndSupport) }
                    )
                }
            } else {
                OnboardingButton(
                    text = "Продолжить",
                    modifier = Modifier.focusRequester(focusRequester),
                    onClick = { onNavigate(OnboardingStep.AboutAndSupport) }
                )
            }
        }
    }
}

@Composable
private fun AboutAndSupportStep(onNavigate: (OnboardingStep) -> Unit) {
    var showContent by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        showContent = true
        delay(1300)
        showButton = true
    }

    LaunchedEffect(showButton) {
        if (showButton) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF8DE5C0).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bolt),
                        contentDescription = null,
                        tint = Color(0xFF8DE5C0),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Безопасность и поддержка",
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Maffinet не использует аккаунты, серверный backend или телеметрию. Исходники, известные ограничения и сообщения об ошибках доступны в независимом репозитории Maffinet. Ссылки находятся в разделе «Информация».",
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF93AEB7),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        ButtonSlot(visible = showButton) {
            OnboardingButton(
                text = "Понятно",
                modifier = Modifier.focusRequester(focusRequester),
                onClick = { onNavigate(OnboardingStep.Summary) }
            )
        }
    }
}

@Composable
private fun SummaryStep(
    wantsTelegramProxy: Boolean?,
    wantsYoutubeBypass: Boolean?,
    onNavigate: (OnboardingStep) -> Unit
) {
    val context = LocalContext.current
    var showContent by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        showContent = true
        delay(1300)
        showButton = true
    }

    LaunchedEffect(showButton) {
        if (showButton) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Настройка завершена",
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEAF5F0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Вот что настроено:",
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF93AEB7),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                SummaryCard(
                    iconRes = R.drawable.ic_telegram,
                    text = if (wantsTelegramProxy == true) "Telegram, прокси настроен и готов к работе" else "Telegram, прокси не используется",
                    isActive = wantsTelegramProxy == true
                )

                Spacer(modifier = Modifier.height(12.dp))

                val strategyName = if (wantsYoutubeBypass == true) StrategyTestManager.getActiveStrategyName(context) else null
                val results = onboardingStrategyResults(context)
                val activeResult = results.firstOrNull { it.command == StrategyTestManager.appliedStrategy }
                val activePassed = activeResult != null && StrategyScorer.bestComplete(
                    listOf(activeResult), ProbeTargetRepository(context).urls()
                ) != null

                SummaryCard(
                    iconRes = R.drawable.ic_youtube,
                    text = when {
                        strategyName == null -> "VPN для сервисов не используется"
                        activePassed -> "Стратегия «$strategyName» прошла проверку всех заданных адресов."
                        else -> "Полного результата нет. Сохранена текущая стратегия «$strategyName»."
                    },
                    isActive = wantsYoutubeBypass == true && activePassed
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        ButtonSlot(visible = showButton) {
            OnboardingButton(
                text = "Начать пользоваться",
                modifier = Modifier.focusRequester(focusRequester),
                onClick = {
                    io.maffinet.android.data.settings.MaffinetSettingsRepository(context).setTelegramEnabled(wantsTelegramProxy ?: true)
                    onNavigate(OnboardingStep.FinalGreeting)
                }
            )
        }
    }
}

@Composable
private fun FinalGreetingScreen(onCompleted: () -> Unit) {
    val scaleAnim = remember { Animatable(0.85f) }
    val alphaAnim = remember { Animatable(0f) }
    val bgScaleAnim = remember { Animatable(1.0f) }
    val bgAlphaAnim = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val entryDuration = 550
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = entryDuration - 150, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(
                    durationMillis = entryDuration,
                    easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.15f)
                )
            )
        }

        delay(entryDuration + 800L)

        val exitDuration = 400
        launch {
            bgAlphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = exitDuration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            scaleAnim.animateTo(
                targetValue = 1.12f,
                animationSpec = tween(durationMillis = exitDuration, easing = FastOutSlowInEasing)
            )
        }
        launch {
            bgScaleAnim.animateTo(
                targetValue = 1.04f,
                animationSpec = tween(durationMillis = exitDuration, easing = FastOutSlowInEasing)
            )
        }

        delay(exitDuration + 50L)
        onCompleted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = bgAlphaAnim.value
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = bgScaleAnim.value
                    scaleY = bgScaleAnim.value
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF222224),
                            Color(0xFF131314)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    alpha = alphaAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_maffinet_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun OnboardingButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val height = if (isLandscape) 44.dp else 52.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isPressed || isHovered || isFocused

    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFF4F8FF7) else Color(0xFF8DE5C0),
        animationSpec = tween(150),
        label = "btnBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) Color.White else Color.Transparent,
        animationSpec = tween(150),
        label = "btnBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (isLandscape) 15.sp else 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val height = if (isLandscape) 44.dp else 52.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isPressed || isHovered || isFocused

    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFF284451) else Color(0xFF193440),
        animationSpec = tween(150),
        label = "secBtnBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) Color.White else Color(0x1AFFFFFF),
        animationSpec = tween(150),
        label = "secBtnBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isHighlighted) Color.White else Color(0xFFEAF5F0),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CardButton(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isPressed || isHovered || isFocused

    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFF284451) else Color(0xFF193440),
        animationSpec = tween(150),
        label = "cardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) Color.White else Color(0x1AFFFFFF),
        animationSpec = tween(150),
        label = "cardBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isHighlighted) Color.White else Color(0xFF93AEB7),
        animationSpec = tween(150),
        label = "cardContent"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isHighlighted) Color.White else Color(0xFFEAF5F0),
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SummaryCard(
    iconRes: Int,
    text: String,
    isActive: Boolean
) {
    val iconTint = if (isActive) Color(0xFF8DE5C0) else Color(0xFF93AEB7)
    val textColor = if (isActive) Color(0xFFEAF5F0) else Color(0xFF93AEB7)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10232D))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (isActive) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = Color(0xFF8DE5C0),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ResultCard(
    strategy: String,
    status: String,
    fullyReachable: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10232D))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bolt),
                contentDescription = null,
                tint = Color(0xFF93AEB7),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = StrategyTestManager.getStrategyName(strategy, LocalContext.current),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFEAF5F0)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = strategy,
                    fontSize = 12.sp,
                    color = Color(0xFF93AEB7),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = status,
            color = if (fullyReachable) Color(0xFF8DE5C0) else Color(0xFF93AEB7),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
