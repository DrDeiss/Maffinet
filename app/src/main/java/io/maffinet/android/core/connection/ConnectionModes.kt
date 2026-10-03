package io.maffinet.android.core.connection

/** Mode choices and desired connections are distinct from engine runtime state. */
data class ConnectionModes(val applications: Boolean, val telegram: Boolean) {
    val any: Boolean get() = applications || telegram
    fun requested(connect: Boolean): ConnectionModes =
        if (connect) this else ConnectionModes(false, false)
}

enum class ModeConnectionState { Stopped, Starting, Running, Stopping, Failed }
