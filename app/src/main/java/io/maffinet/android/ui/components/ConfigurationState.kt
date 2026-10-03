package io.maffinet.android.ui.components

import androidx.compose.runtime.*
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.data.AppStatus
import io.maffinet.android.data.appStatus
import kotlinx.coroutines.delay

fun configurationIsLocked(): Boolean = appStatus.first == AppStatus.Running || StrategyTestManager.isTesting

/** Legacy service status is not Compose state, so observe it while a configuration screen is visible. */
@Composable
fun rememberConfigurationLocked(): Boolean {
    var vpnRunning by remember { mutableStateOf(appStatus.first == AppStatus.Running) }
    LaunchedEffect(Unit) {
        while (true) {
            vpnRunning = appStatus.first == AppStatus.Running
            delay(500)
        }
    }
    return vpnRunning || StrategyTestManager.isTesting
}
