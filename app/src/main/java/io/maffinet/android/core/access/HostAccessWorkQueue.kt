package io.maffinet.android.core.access

/** Observations from our own numeric-IP probes must not recursively add work. */
class HostAccessWorkQueue(private val capacity: Int = HostAccessPolicy.MAX_PENDING_HOSTS) {
    init { require(capacity > 0) }
    private val pending = ArrayDeque<HostObservation>()
    private val reserved = mutableSetOf<Pair<String, Int>>()

    @Synchronized fun offer(observation: HostObservation): Boolean {
        val key = observation.host to observation.port
        if (key in reserved || reserved.size >= capacity) return false
        reserved += key
        pending.addLast(observation)
        return true
    }

    @Synchronized fun poll(): HostObservation? = if (pending.isEmpty()) null else pending.removeFirst()
    @Synchronized fun complete(observation: HostObservation) { reserved -= observation.host to observation.port }
    @Synchronized fun clear() { pending.clear(); reserved.clear() }
    @Synchronized fun size(): Int = reserved.size
}
