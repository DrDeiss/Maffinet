package io.maffinet.lab.transport;

import android.net.Network;
import android.net.VpnService;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;

final class PhysicalSockets {
    private final VpnService vpn;
    private final Network network;
    private final LabEvents events;
    private final boolean failProtect, failBind;
    PhysicalSockets(VpnService vpn, Network network, LabEvents events, boolean failProtect, boolean failBind) {
        this.vpn = vpn; this.network = network; this.events = events;
        this.failProtect = failProtect; this.failBind = failBind;
    }
    void prepare(Socket socket) throws IOException {
        if (failProtect || !vpn.protect(socket)) throw new IOException("protect rejected");
        if (failBind) throw new IOException("bind injected failure");
        network.bindSocket(socket); // MUST precede connect.
        events.add("external-prepared", "protocol", 6, "network", network.getNetworkHandle());
    }
    void prepare(DatagramSocket socket) throws IOException {
        if (failProtect || !vpn.protect(socket)) throw new IOException("protect rejected");
        if (failBind) throw new IOException("bind injected failure");
        network.bindSocket(socket); // MUST precede any send/connect.
        events.add("external-prepared", "protocol", 17, "network", network.getNetworkHandle());
    }
}
