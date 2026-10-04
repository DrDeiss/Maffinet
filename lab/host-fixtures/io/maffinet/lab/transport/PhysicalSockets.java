package io.maffinet.lab.transport;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Host fault seam. This does NOT implement or prove Android protect/bind. */
final class PhysicalSockets {
    final boolean deny;
    final AtomicInteger prepared = new AtomicInteger();
    PhysicalSockets(boolean deny) { this.deny = deny; }
    void prepare(Socket socket) throws IOException { prepare(); }
    void prepare(DatagramSocket socket) throws IOException { prepare(); }
    private void prepare() throws IOException {
        prepared.incrementAndGet();
        if (deny) throw new IOException("injected pre-connect failure");
    }
}
