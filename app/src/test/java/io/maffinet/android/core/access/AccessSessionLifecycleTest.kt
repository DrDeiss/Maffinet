package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AccessSessionLifecycleTest {
    @Test fun delayedNetworkMonitorCannotClearAProxyStartedAfterStop() {
        val lifecycle = AccessSessionLifecycle()
        val oldOwner = lifecycle.reserve()
        assertTrue(lifecycle.claim(oldOwner))
        val snapshotStarted = CountDownLatch(1)
        val snapshotReturned = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val nativeEpoch = AtomicLong(oldOwner)
        val oldMonitor = Thread {
            snapshotStarted.countDown()
            assertTrue(snapshotReturned.await(2, TimeUnit.SECONDS))
            lifecycle.nextEpoch(oldOwner)?.let { nativeEpoch.set(it) }
            completed.countDown()
        }
        oldMonitor.start()
        assertTrue(snapshotStarted.await(2, TimeUnit.SECONDS))
        lifecycle.stop()
        val newOwner = lifecycle.reserve()
        assertTrue(lifecycle.claim(newOwner))
        nativeEpoch.set(newOwner)
        snapshotReturned.countDown()
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        oldMonitor.join()
        assertEquals(newOwner, nativeEpoch.get())
        assertTrue(lifecycle.isCurrent(newOwner, newOwner))
    }

    @Test fun networkLossInvalidatesOldWorkButTheSameOwnerCanResumeOnANewPolicy() {
        val lifecycle = AccessSessionLifecycle()
        val owner = lifecycle.reserve()
        assertTrue(lifecycle.claim(owner))
        val oldEpoch = lifecycle.currentEpoch
        val noNetworkEpoch = lifecycle.nextEpoch(owner)!!
        assertFalse(lifecycle.isCurrent(owner, oldEpoch))
        assertTrue(lifecycle.isOwner(owner))
        val recoveredEpoch = lifecycle.nextEpoch(owner)!!
        assertFalse(lifecycle.isCurrent(owner, noNetworkEpoch))
        assertTrue(lifecycle.isCurrent(owner, recoveredEpoch))
        assertTrue(recoveredEpoch > oldEpoch)
    }

    @Test fun aLateReadyCallbackCannotClaimAReplacementStart() {
        val lifecycle = AccessSessionLifecycle()
        val late = lifecycle.reserve()
        val replacement = lifecycle.reserve()
        assertFalse(lifecycle.claim(late))
        assertEquals(replacement, lifecycle.reservedEpoch())
        assertTrue(lifecycle.claim(replacement))
        assertFalse(lifecycle.claim(replacement))
        lifecycle.stop()
        assertFalse(lifecycle.isCurrent(replacement, replacement))
        assertNull(lifecycle.nextEpoch(replacement))
    }
}
