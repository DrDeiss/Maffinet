package io.maffinet.android.core.tgproxy

import io.maffinet.android.core.connection.ModeConnectionState

/** Serializes command ownership and state publication; native work runs outside this lock. */
internal class ProxyLifecycleState(private val onStateChanged: (ModeConnectionState) -> Unit) {
    @Volatile private var generation = 0L
    @Volatile var currentState = ModeConnectionState.Stopped
        private set

    data class StopRequest(val generation: Long, val keepFailure: Boolean)

    @Synchronized fun beginStart(): Long {
        val token = ++generation
        publish(ModeConnectionState.Starting)
        return token
    }

    @Synchronized fun beginStop(preserveFailure: Boolean): StopRequest {
        val keepFailure = preserveFailure && currentState == ModeConnectionState.Failed
        val token = ++generation
        if (!keepFailure) publish(ModeConnectionState.Stopping)
        return StopRequest(token, keepFailure)
    }

    fun isCurrent(token: Long): Boolean = token == generation

    @Synchronized fun publishIfCurrent(token: Long, next: ModeConnectionState): Boolean {
        if (!isCurrent(token)) return false
        publish(next)
        return true
    }

    private fun publish(next: ModeConnectionState) {
        currentState = next
        onStateChanged(next)
    }
}
