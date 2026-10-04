package io.maffinet.android.core.access

import java.util.concurrent.atomic.AtomicLong

/** Ownership survives network changes, but never STOP or a new proxy startup. */
class AccessSessionLifecycle {
    private val epochs = AtomicLong()
    @Volatile private var owner: Long? = null
    private var reserved: Long? = null
    val currentEpoch: Long get() = epochs.get()

    @Synchronized fun reserve(): Long = epochs.incrementAndGet().also { owner = null; reserved = it }
    @Synchronized fun reservedEpoch(): Long? = reserved
    @Synchronized fun claim(expected: Long): Boolean {
        if (reserved != expected || currentEpoch != expected) return false
        reserved = null
        owner = expected
        return true
    }
    @Synchronized fun stop() { epochs.incrementAndGet(); owner = null; reserved = null }
    fun isOwner(expected: Long): Boolean = owner == expected
    fun isCurrent(expectedOwner: Long, expectedEpoch: Long): Boolean = isOwner(expectedOwner) && currentEpoch == expectedEpoch
    @Synchronized fun nextEpoch(expectedOwner: Long): Long? =
        if (owner == expectedOwner) epochs.incrementAndGet() else null
}
