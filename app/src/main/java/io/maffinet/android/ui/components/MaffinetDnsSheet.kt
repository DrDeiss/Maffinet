package io.maffinet.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import io.maffinet.android.core.dns.DnsCatalog
import io.maffinet.android.core.dns.DnsMode
import io.maffinet.android.core.dns.DnsPreset
import io.maffinet.android.core.dns.DnsPurpose

private val SheetBackground = Color(0xFF10232D)
private val ButtonBackground = Color(0xFF262626)
private val CloseText = Color(0xFFC4C4C6)
private val ButtonBorder = Color(0x1AFFFFFF)
private val TextPrimary = Color(0xFFEAF5F0)
private val TextSecondary = Color(0xFF93AEB7)

private val DecelerateEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)
private val AccelerateEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
private val OvershootEasing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1.0f)
private val IconOvershootEasing = CubicBezierEasing(0.34f, 1.6f, 0.64f, 1.0f)

private val PressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

@Composable
fun MaffinetDnsSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    icon: Any,
    title: String,
    subtitle: String,
    dnsList: List<String>,
    selectedDnsPreset: String,
    onPresetSelected: (String) -> Unit,
    closeLabel: String = "Отмена"
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences(context.packageName + "_preferences", 0) }
    val isTv = remember(sharedPrefs) { sharedPrefs.getBoolean("is_smart_tv", false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val useTvLayout = isTv || isLandscape

    val navBarPaddingValues = WindowInsets.navigationBars.asPaddingValues()
    val frozenNavBarPadding = remember { navBarPaddingValues }

    val transition = updateTransition(targetState = visible, label = "DnsSheetTransition")
    val lazyListState = rememberLazyListState()
    var purpose by remember { mutableStateOf(DnsPurpose.GEO_ACCESS) }
    val visibleDnsList = remember(dnsList, purpose) { DnsPresets.forPurpose(dnsList, purpose) }
    var showCustomEditor by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }
    var externalPreset by remember { mutableStateOf<DnsPreset?>(null) }
    val resolvedSelection = DnsCatalog.resolve(selectedDnsPreset)
    val selectedId = if (resolvedSelection.id.startsWith("custom:")) DnsCatalog.CUSTOM_ID else resolvedSelection.id
    val displayedSubtitle = if (visibleDnsList.none { it == selectedId })
        "$subtitle\nВыбрано: ${DnsCatalog.selectionLabel(selectedDnsPreset)}" else subtitle
    val selectPreset: (String) -> Unit = { presetId ->
        if (presetId == DnsCatalog.CUSTOM_ID) {
            customInput = DnsCatalog.customInput(selectedDnsPreset)
            showCustomEditor = true
        } else {
            val preset = DnsCatalog.resolve(presetId)
            if (preset.mode == DnsMode.PRIVATE_DNS || preset.mode == DnsMode.PROVIDER_SETTINGS) {
                externalPreset = preset
            } else {
                onPresetSelected(preset.id)
            }
        }
    }

    val focusRequesters = remember(visibleDnsList) { List(visibleDnsList.size) { FocusRequester() } }
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, purpose, visibleDnsList) {
        if (visible) {
            try {
                val selectedIndex = visibleDnsList.indexOfFirst { it == selectedId }.coerceAtLeast(0)
                lazyListState.scrollToItem(selectedIndex)
                delay(150)
                focusRequesters.getOrNull(selectedIndex)?.requestFocus()
            } catch (_: Exception) {}
        } else {
            showCustomEditor = false
            externalPreset = null
        }
    }

    if (visible && showCustomEditor) {
        val customSelection = DnsCatalog.customSelection(customInput)
        AlertDialog(
            onDismissRequest = { showCustomEditor = false },
            title = { Text("Свой IPv4 DNS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("От 1 до 4 IPv4-адресов через пробел или запятую. DNS применяется к выбранным приложениям в VPN без шифрования.")
                    MaffinetTextField(customInput, { customInput = it }, label = "DNS-серверы",
                        placeholder = "1.1.1.1, 1.0.0.1", modifier = Modifier.fillMaxWidth().testTag("custom-dns-input"))
                    if (customInput.isNotBlank() && customSelection == null) {
                        Text("Введите корректные IPv4-адреса, например 1.1.1.1. Имена хостов, URL и IPv6 здесь не поддерживаются.",
                            color = Color(0xFFFFB4AB))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = customSelection != null, onClick = {
                    customSelection?.let(onPresetSelected)
                    showCustomEditor = false
                }, modifier = Modifier.testTag("custom-dns-save")) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { showCustomEditor = false }) { Text("Отмена") } },
        )
    }
    externalPreset?.takeIf { visible }?.let { preset ->
        val isPrivateDns = preset.mode == DnsMode.PRIVATE_DNS
        AlertDialog(
            onDismissRequest = { externalPreset = null },
            title = { Text(preset.label) },
            text = {
                Text(if (isPrivateDns) {
                    "${preset.description}\n\nИмя хоста: ${preset.privateDnsHostname}\n\n" +
                        if (Build.VERSION.SDK_INT >= 28) "Имя будет скопировано. В настройках Android выберите имя хоста поставщика Private DNS и вставьте его. Это системная настройка для всего устройства; приложение не включает её автоматически. Выбранный IPv4 DNS для VPN сохранится."
                        else "Системный Private DNS доступен начиная с Android 9. Используйте IPv4-профиль другого поставщика в VPN."
                } else "Актуальные DNS-адреса GeoHide зависят от региона. Откройте сайт поставщика и внесите опубликованные IPv4-адреса через «Свой IPv4 DNS» либо настройте Private DNS в Android.")
            },
            confirmButton = {
                TextButton(enabled = !isPrivateDns || Build.VERSION.SDK_INT >= 28, onClick = {
                    if (!io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)) {
                        if (isPrivateDns) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Private DNS", preset.privateDnsHostname))
                            val opened = runCatching {
                                context.startActivity(Intent("android.settings.PRIVATE_DNS_SETTINGS"))
                            }.isSuccess || runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }.isSuccess
                            if (!opened) Toast.makeText(context, "Имя скопировано. Откройте Private DNS в настройках сети Android.", Toast.LENGTH_LONG).show()
                        } else {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(preset.sourceUrl))) }
                                .onFailure { Toast.makeText(context, "Откройте ${preset.sourceUrl} в браузере", Toast.LENGTH_LONG).show() }
                        }
                    }
                    externalPreset = null
                }) { Text(if (isPrivateDns) "Скопировать и открыть" else "Открыть сайт") }
            },
            dismissButton = { TextButton(onClick = { externalPreset = null }) { Text("Отмена") } },
        )
    }

    if (transition.currentState || transition.targetState) {
        BackHandler(onBack = onDismissRequest)

        val scrimAlpha by transition.animateFloat(
            transitionSpec = {
                if (targetState) tween(300, easing = DecelerateEasing) else tween(420, easing = AccelerateEasing)
            },
            label = "scrimAlpha"
        ) { if (it) 0.64f else 0f }

        val cardProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(360, easing = OvershootEasing)
                } else {
                    tween(300, delayMillis = 120, easing = AccelerateEasing)
                }
            },
            label = "cardProgress"
        ) { if (it) 1f else 0f }

        val innerIconScale by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(320, delayMillis = 100, easing = IconOvershootEasing)
                } else {
                    tween(200, delayMillis = 80, easing = AccelerateEasing)
                }
            },
            label = "innerIconScale"
        ) { if (it) 1f else 0f }

        val innerIconRotation by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(320, delayMillis = 100, easing = IconOvershootEasing)
                } else {
                    tween(200, delayMillis = 80, easing = AccelerateEasing)
                }
            },
            label = "innerIconRotation"
        ) { if (it) 0f else -25f }

        val headerTextProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(300, delayMillis = 60, easing = DecelerateEasing)
                } else {
                    tween(200, delayMillis = 80, easing = AccelerateEasing)
                }
            },
            label = "headerTextProgress"
        ) { if (it) 1f else 0f }

        val dnsListProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(300, delayMillis = 100, easing = DecelerateEasing)
                } else {
                    tween(200, delayMillis = 40, easing = AccelerateEasing)
                }
            },
            label = "dnsListProgress"
        ) { if (it) 1f else 0f }

        val closeButtonProgress by transition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(300, delayMillis = 140, easing = DecelerateEasing)
                } else {
                    tween(200, delayMillis = 0, easing = AccelerateEasing)
                }
            },
            label = "closeButtonProgress"
        ) { if (it) 1f else 0f }

        val infiniteTransition = rememberInfiniteTransition(label = "iconFloat")
        val floatTranslation by infiniteTransition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatTranslation"
        )

        val swayRotation by infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
                repeatMode = RepeatMode.Reverse
            ),
            label = "swayRotation"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .pointerInput(onDismissRequest) {
                    detectTapGestures(onTap = { onDismissRequest() })
                },
            contentAlignment = if (useTvLayout) Alignment.Center else Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(frozenNavBarPadding)
                    .padding(bottom = if (useTvLayout) 0.dp else 16.dp)
                    .widthIn(max = if (useTvLayout) 640.dp else 500.dp)
                    .fillMaxHeight(if (useTvLayout) 0.72f else 0.94f)
                    .graphicsLayer {
                        alpha = cardProgress
                        val scale = 0.88f + 0.12f * cardProgress
                        scaleX = scale
                        scaleY = scale
                        if (!useTvLayout) {
                            translationY = (1f - cardProgress) * 80f
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { })
                    }
            ) {
                if (useTvLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SheetBackground, RoundedCornerShape(28.dp))
                            .border(1.dp, ButtonBorder, RoundedCornerShape(28.dp))
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = headerTextProgress },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(ButtonBorder, CircleShape)
                                    .padding(2.dp)
                                    .background(SheetBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                when (icon) {
                                    is ImageVector -> {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = innerIconScale
                                                    scaleY = innerIconScale
                                                    translationY = floatTranslation * innerIconScale
                                                    rotationZ = innerIconRotation + (swayRotation * innerIconScale)
                                                }
                                                .size(32.dp)
                                        )
                                    }
                                    is Int -> {
                                        Icon(
                                            painter = painterResource(id = icon),
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = innerIconScale
                                                    scaleY = innerIconScale
                                                    translationY = floatTranslation * innerIconScale
                                                    rotationZ = innerIconRotation + (swayRotation * innerIconScale)
                                                }
                                                .size(32.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = title,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = displayedSubtitle,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )

                            Spacer(Modifier.height(20.dp))

                            DnsCloseButton(
                                label = closeLabel,
                                focusRequester = closeFocusRequester,
                                onClick = onDismissRequest
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = dnsListProgress }
                        ) {
                            DnsPurposeFilters(purpose, onPurposeSelected = { purpose = it })
                            Spacer(Modifier.height(8.dp))
                            val density = LocalDensity.current
                            val totalHeight = 92.dp * visibleDnsList.size
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.weight(1f).testTag("dns-preset-list"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(visibleDnsList.size) { index ->
                                        val preset = visibleDnsList[index]
                                        DnsOptionButton(
                                            preset = preset,
                                            selected = preset == selectedId,
                                            focusRequester = focusRequesters[index],
                                            onSelected = { selectPreset(preset) }
                                        )
                                    }
                                }
                                DnsCustomScrollbar(
                                    state = lazyListState,
                                    totalEstimatedHeight = with(density) { totalHeight.toPx() },
                                    itemHeight = with(density) { 84.dp.toPx() },
                                    spacing = with(density) { 8.dp.toPx() },
                                    density = density
                                )
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(SheetBackground, RoundedCornerShape(28.dp))
                                .padding(top = 16.dp, start = 22.dp, end = 22.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(ButtonBorder, CircleShape)
                                    .padding(2.dp)
                                    .background(SheetBackground, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                when (icon) {
                                    is ImageVector -> {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = innerIconScale
                                                    scaleY = innerIconScale
                                                    translationY = floatTranslation * innerIconScale
                                                    rotationZ = innerIconRotation + (swayRotation * innerIconScale)
                                                }
                                                .size(34.dp)
                                        )
                                    }
                                    is Int -> {
                                        Icon(
                                            painter = painterResource(id = icon),
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = innerIconScale
                                                    scaleY = innerIconScale
                                                    translationY = floatTranslation * innerIconScale
                                                    rotationZ = innerIconRotation + (swayRotation * innerIconScale)
                                                }
                                                .size(34.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Column(
                                modifier = Modifier.graphicsLayer { alpha = headerTextProgress },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = displayedSubtitle,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            Spacer(Modifier.height(20.dp))
                            DnsPurposeFilters(purpose, onPurposeSelected = { purpose = it })
                            Spacer(Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = dnsListProgress
                                        translationY = (1f - dnsListProgress) * 16f
                                    }
                            ) {
                                val density = LocalDensity.current
                                val totalHeight = 92.dp * visibleDnsList.size
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LazyColumn(
                                        state = lazyListState,
                                        modifier = Modifier.weight(1f).testTag("dns-preset-list"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(visibleDnsList.size) { index ->
                                            val preset = visibleDnsList[index]
                                            DnsOptionButton(
                                                preset = preset,
                                                selected = preset == selectedId,
                                                focusRequester = focusRequesters[index],
                                                onSelected = { selectPreset(preset) }
                                            )
                                        }
                                    }
                                    DnsCustomScrollbar(
                                        state = lazyListState,
                                        totalEstimatedHeight = with(density) { totalHeight.toPx() },
                                        itemHeight = with(density) { 84.dp.toPx() },
                                        spacing = with(density) { 8.dp.toPx() },
                                        density = density
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = closeButtonProgress
                                    translationY = (1f - closeButtonProgress) * 24f
                                }
                        ) {
                            DnsCloseButton(
                                label = closeLabel,
                                focusRequester = closeFocusRequester,
                                onClick = onDismissRequest
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsPurposeFilters(purpose: DnsPurpose, onPurposeSelected: (DnsPurpose) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DnsPurpose.GEO_ACCESS to "Обход геоблокировок", DnsPurpose.GENERAL to "Обычные DNS").forEach { (value, label) ->
            FilterChip(
                selected = purpose == value,
                onClick = { onPurposeSelected(value) },
                label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 2) },
                modifier = Modifier.weight(1f).testTag(if (value == DnsPurpose.GEO_ACCESS) "dns-purpose-geo" else "dns-purpose-general"),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = ButtonBackground,
                    labelColor = TextSecondary,
                    selectedContainerColor = Color(0xFF284451),
                    selectedLabelColor = TextPrimary,
                ),
            )
        }
    }
}

@Composable
private fun DnsCustomScrollbar(
    state: LazyListState,
    totalEstimatedHeight: Float,
    itemHeight: Float,
    spacing: Float,
    density: Density
) {
    Canvas(
        modifier = Modifier
            .fillMaxHeight()
            .width(4.dp)
    ) {
        val viewHeight = size.height
        if (viewHeight <= 0f || totalEstimatedHeight <= viewHeight) return@Canvas

        val maxThumbHeight = viewHeight / 3
        val minThumbHeight = minOf(with(density) { 40.dp.toPx() }, maxThumbHeight)
        val thumbHeight = (viewHeight * viewHeight / totalEstimatedHeight).coerceIn(minThumbHeight, maxThumbHeight)
        val maxScroll = totalEstimatedHeight - viewHeight
        val firstItemOffset = state.firstVisibleItemScrollOffset
        val currentScrolled = (state.firstVisibleItemIndex.toFloat() * (itemHeight + spacing)) + firstItemOffset
        val scrollFraction = if (maxScroll > 0) currentScrolled / maxScroll else 0f
        val thumbOffset = (viewHeight - thumbHeight) * scrollFraction.coerceIn(0f, 1f)

        drawRoundRect(
            color = Color(0x0DFFFFFF),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, viewHeight),
            cornerRadius = CornerRadius(size.width / 2)
        )

        drawRoundRect(
            color = Color(0x4DFFFFFF),
            topLeft = Offset(0f, thumbOffset),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2)
        )
    }
}

@Composable
private fun DnsOptionButton(
    preset: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onSelected: () -> Unit
) {
    val entry = DnsCatalog.resolve(preset)
    val label = if (preset == DnsCatalog.CUSTOM_ID) "Свой IPv4 DNS" else entry.label
    val detail = if (preset == DnsCatalog.CUSTOM_ID) "Ввести от 1 до 4 адресов DNS-серверов" else entry.detail
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val finalActive = isPressed || isFocused

    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = PressSpring,
        label = "dnsPress"
    )

    val currentBgColor by animateColorAsState(
        targetValue = if (finalActive) Color(0xFF284451) else ButtonBackground,
        animationSpec = tween(150),
        label = "dnsBg"
    )

    val baseBorder = if (selected) Color(0x4DFFFFFF) else Color.Transparent
    val currentBorderColor by animateColorAsState(
        targetValue = when {
            isPressed -> Color.White
            isFocused -> Color(0x33FFFFFF)
            else -> baseBorder
        },
        animationSpec = tween(150),
        label = "dnsBorder"
    )

    val currentTextColor by animateColorAsState(
        targetValue = if (finalActive) Color.White else TextPrimary,
        animationSpec = tween(150),
        label = "dnsText"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                val isDpadClick = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
                if (isDpadClick && keyEvent.type == KeyEventType.KeyUp) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelected()
                    true
                } else {
                    false
                }
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelected()
                }
            )
            .background(currentBorderColor, RoundedCornerShape(12.dp))
            .padding(1.dp)
            .background(currentBgColor, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = currentTextColor, fontSize = 13.sp,
                fontWeight = if (selected || finalActive) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 2)
            Text(text = detail, color = TextSecondary, fontSize = 10.sp, lineHeight = 12.sp,
                textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

@Composable
private fun DnsCloseButton(
    label: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val finalActive = isPressed || isFocused

    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = PressSpring,
        label = "closePress"
    )

    val currentBgColor by animateColorAsState(
        targetValue = if (finalActive) Color(0xFF284451) else ButtonBackground,
        animationSpec = tween(150),
        label = "closeBg"
    )
    val currentBorderColor by animateColorAsState(
        targetValue = when {
            isPressed -> Color.White
            isFocused -> Color(0x33FFFFFF)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "closeBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                val isDpadClick = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
                if (isDpadClick && keyEvent.type == KeyEventType.KeyUp) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                    true
                } else {
                    false
                }
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .background(currentBorderColor, RoundedCornerShape(20.dp))
            .padding(1.dp)
            .background(currentBgColor, RoundedCornerShape(19.dp))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (finalActive) Color.White else CloseText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
