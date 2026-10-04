package io.maffinet.lab.transport;

import android.net.VpnService;
import java.net.InetAddress;

final class NativeTransport {
    static { System.loadLibrary("maffinet-transport-lab"); }
    private final VpnService service;
    private final LabEvents events;
    NativeTransport(VpnService service, LabEvents events) { this.service = service; this.events = events; }
    native int start(String config, int borrowedTunFd);
    native int status(); // 1 Starting, 2 Ready, 3 Stopping, <=0 finished/error.
    native int stop(); // -20 retains resources; no restart until successful reap.
    @SuppressWarnings("unused") // JNI
    private boolean protectLoopback(int fd) { return service.protect(fd); }
    @SuppressWarnings("unused") // JNI, copied original application tuple, no UID inference.
    private void originalTuple(int protocol, int family, byte[] local, int lp, byte[] remote, int rp) {
        try {
            events.add("tuple", "protocol", protocol, "family", family,
                    "local", InetAddress.getByAddress(local).getHostAddress(), "localPort", lp,
                    "remote", InetAddress.getByAddress(remote).getHostAddress(), "remotePort", rp,
                    "owner", "unknown-P03-pending");
        } catch (Exception e) { events.add("tuple-error", "reason", e.getClass().getSimpleName()); }
    }
}
