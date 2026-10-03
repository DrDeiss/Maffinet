package io.maffinet.android.ui.components

import androidx.compose.runtime.*
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.dpibypass.ByeDpiVpnService
import kotlinx.coroutines.delay

fun configurationIsLocked(): Boolean = ByeDpiVpnService.isVpnActive || ByeDpiVpnService.hasProxyResources || StrategyTestManager.isTesting

/** Legacy service status is not Compose state, so observe it while a configuration screen is visible. */
@Composable
fun rememberConfigurationLocked(): Boolean {
    var vpnRunning by remember { mutableStateOf(configurationIsLocked()) }
    LaunchedEffect(Unit) {
        while (true) {
            vpnRunning = configurationIsLocked()
            delay(500)
        }
    }
    return vpnRunning || StrategyTestManager.isTesting
}
