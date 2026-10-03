package io.maffinet.android.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

const val START_ACTION = "start"
const val STOP_ACTION = "stop"
const val RESUME_ACTION = "resume"
const val PAUSE_ACTION = "pause"

const val STARTED_BROADCAST = "io.maffinet.android.STARTED"
const val STOPPED_BROADCAST = "io.maffinet.android.STOPPED"
const val FAILED_BROADCAST = "io.maffinet.android.FAILED"

const val SENDER = "sender"

var appStatus = AppStatus.Halted to Mode.VPN
var performanceModeGlobal = false
var connectionStartTime: Long = 0L
var isTgProxyRunningGlobal = false
var rxSpeedGlobal = 0.0
var txSpeedGlobal = 0.0
var pingMsGlobal: Int? = null

var updateInfoGlobal by mutableStateOf<io.maffinet.android.core.update.UpdateManager.UpdateInfo?>(null)
var updateProgressGlobal by mutableStateOf(-1f)
var updateStatusGlobal by mutableStateOf<String?>(null)
var onNavigateToTab: ((Int) -> Unit)? = null
var forceUpdateTestGlobal by mutableStateOf(false)
var isActionSheetVisibleGlobal by mutableStateOf(false)

fun setStatus(status: AppStatus, mode: Mode) {
    appStatus = status to mode
}
