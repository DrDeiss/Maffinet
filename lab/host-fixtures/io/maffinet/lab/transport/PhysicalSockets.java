package io.maffinet.lab.transport;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
/** Host fault seam. This does NOT implement or prove Android protect/bind. */
final class PhysicalSockets {
    final boolean deny;
    final AtomicInteger prepared = new AtomicInteger();
    final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch resume;
    PhysicalSockets(boolean deny) { this(deny, false); }
    PhysicalSockets(boolean deny, boolean blocked) {
        this.deny = deny;
        resume = new CountDownLatch(blocked ? 1 : 0);
    }
    void resume() { resume.countDown(); }
    void prepare(Socket socket) throws IOException { prepare(); }
    void prepare(DatagramSocket socket) throws IOException { prepare(); }
    private void prepare() throws IOException {
        prepared.incrementAndGet();
        entered.countDown();
        // Model a platform operation that does not finish on thread interruption.
        boolean interrupted = false;
        while (true) {
            try { resume.await(); break; }
            catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (deny) throw new IOException("injected pre-connect failure");
    }
}
