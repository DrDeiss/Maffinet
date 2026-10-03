package io.maffinet.android.ui.components

import androidx.compose.runtime.*
import io.maffinet.android.core.dpibypass.StrategyTestManager
import io.maffinet.android.core.dpibypass.ByeDpiVpnService
import kotlinx.coroutines.delay

fun configurationIsLocked(): Boolean = io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked()

/** Legacy service status is not Compose state, so observe it while a configuration screen is visible. */
@Composable
fun rememberConfigurationLocked(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    var vpnRunning by remember { mutableStateOf(io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            vpnRunning = io.maffinet.android.core.connection.ConnectionCoordinator.isConfigurationLocked(context)
            delay(500)
        }
    }
    return vpnRunning || StrategyTestManager.isTesting
}
