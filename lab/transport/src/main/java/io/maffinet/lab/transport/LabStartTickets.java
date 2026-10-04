package io.maffinet.lab.transport;

/** Lab-only admission fence. STOP invalidates every START admitted before it. */
final class LabStartTickets {
    private long epoch;
    private boolean destroyed;

    synchronized long admitStart() { return destroyed ? -1 : epoch; }
    synchronized void cancelStarts() { epoch++; }
    synchronized void destroy() { destroyed = true; epoch++; }
    synchronized boolean current(long ticket) { return !destroyed && ticket >= 0 && ticket == epoch; }
}
