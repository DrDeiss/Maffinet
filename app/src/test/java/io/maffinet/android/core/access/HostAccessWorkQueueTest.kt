package io.maffinet.android.core.access

import org.junit.Assert.*
import org.junit.Test

class HostAccessWorkQueueTest {
    @Test fun numericCandidateObservationsCannotRequeueAnInflightHost() {
        val queue = HostAccessWorkQueue()
        val original = HostObservation("unlisted.org", 443, "1.1.1.1")
        assertTrue(queue.offer(original))
        assertEquals(original, queue.poll())
        assertFalse(queue.offer(original.copy(originalIp = "8.8.8.8")))
        assertNull(queue.poll())
        queue.complete(original)
        assertTrue(queue.offer(original.copy(originalIp = "8.8.8.8")))
    }

    @Test fun distinctPortsRemainIndependentAndQueueIncludingInflightIsBounded() {
        val queue = HostAccessWorkQueue()
        repeat(HostAccessPolicy.MAX_PENDING_HOSTS) { index ->
            assertTrue(queue.offer(HostObservation("unlisted.org", 443 + index, "1.1.1.1")))
        }
        val running = queue.poll()!!
        assertFalse(queue.offer(HostObservation("other.org", 443, "8.8.8.8")))
        assertEquals(HostAccessPolicy.MAX_PENDING_HOSTS, queue.size())
        queue.complete(running)
        assertTrue(queue.offer(HostObservation("other.org", 443, "8.8.8.8")))
        queue.clear()
        assertEquals(0, queue.size())
        assertNull(queue.poll())
    }
}
