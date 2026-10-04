package io.maffinet.android.core.tgproxy

import io.maffinet.android.core.connection.ModeConnectionState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyLifecycleStateTest {
    @Test fun queuedStartInvalidatesOlderStopCompletion() {
        val published = mutableListOf<ModeConnectionState>()
        val lifecycle = ProxyLifecycleState(published::add)
        val oldStop = lifecycle.beginStop(false)
        val queuedStart = lifecycle.beginStart()
        assertFalse(lifecycle.publishIfCurrent(oldStop.generation, ModeConnectionState.Stopped))
        assertEquals(ModeConnectionState.Starting, lifecycle.currentState)
        assertTrue(lifecycle.publishIfCurrent(queuedStart, ModeConnectionState.Running))
        assertEquals(listOf(ModeConnectionState.Stopping, ModeConnectionState.Starting, ModeConnectionState.Running), published)
    }

    @Test fun userStopRejectsOlderReadyAndFailureCallbacks() {
        val lifecycle = ProxyLifecycleState {}
        val oldStart = lifecycle.beginStart()
        val stop = lifecycle.beginStop(false)
        assertFalse(lifecycle.publishIfCurrent(oldStart, ModeConnectionState.Running))
        assertFalse(lifecycle.publishIfCurrent(oldStart, ModeConnectionState.Failed))
        assertEquals(ModeConnectionState.Stopping, lifecycle.currentState)
        assertTrue(lifecycle.publishIfCurrent(stop.generation, ModeConnectionState.Stopped))
        assertEquals(ModeConnectionState.Stopped, lifecycle.currentState)
    }

    @Test fun destroyPreservesFailureAndExplicitStopClearsItAfterCleanup() {
        val lifecycle = ProxyLifecycleState {}
        val start = lifecycle.beginStart()
        assertTrue(lifecycle.publishIfCurrent(start, ModeConnectionState.Failed))
        val destroy = lifecycle.beginStop(true)
        assertTrue(destroy.keepFailure)
        assertEquals(ModeConnectionState.Failed, lifecycle.currentState)
        assertTrue(lifecycle.publishIfCurrent(destroy.generation, ModeConnectionState.Failed))
        val userStop = lifecycle.beginStop(false)
        assertFalse(userStop.keepFailure)
        assertEquals(ModeConnectionState.Stopping, lifecycle.currentState)
        assertTrue(lifecycle.publishIfCurrent(userStop.generation, ModeConnectionState.Stopped))
        assertEquals(ModeConnectionState.Stopped, lifecycle.currentState)
    }

    @Test fun concurrentStopPublicationCannotArriveAfterNewerCleanup() {
        val firstPublication = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCallerStarted = CountDownLatch(1)
        val secondPublication = CountDownLatch(1)
        val stops = AtomicInteger()
        val published = CopyOnWriteArrayList<ModeConnectionState>()
        val lifecycle = ProxyLifecycleState { next ->
            published.add(next)
            if (next == ModeConnectionState.Stopping) {
                if (stops.incrementAndGet() == 1) {
                    firstPublication.countDown()
                    assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
                } else secondPublication.countDown()
            }
        }
        val callers = Executors.newFixedThreadPool(2)
        try {
            val older = callers.submit {
                val request = lifecycle.beginStop(false)
                lifecycle.publishIfCurrent(request.generation, ModeConnectionState.Stopped)
            }
            assertTrue(firstPublication.await(2, TimeUnit.SECONDS))
            val newer = callers.submit {
                secondCallerStarted.countDown()
                val request = lifecycle.beginStop(false)
                lifecycle.publishIfCurrent(request.generation, ModeConnectionState.Stopped)
            }
            assertTrue(secondCallerStarted.await(2, TimeUnit.SECONDS))
            // A preempted initial publisher still owns the same lock as token allocation.
            assertFalse(secondPublication.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            older.get(2, TimeUnit.SECONDS)
            newer.get(2, TimeUnit.SECONDS)
            assertEquals(ModeConnectionState.Stopped, lifecycle.currentState)
            assertEquals(ModeConnectionState.Stopped, published.last())
            assertEquals(2, stops.get())
        } finally {
            releaseFirst.countDown()
            callers.shutdownNow()
        }
    }
}
