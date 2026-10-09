package io.maffinet.lab.transport;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

/** Small bounded numeric-address SOCKS5 lab relay. No DPI, name resolution or retry. */
final class LabSocksServer implements AutoCloseable {
    private final ServerSocket listener;
    private final PhysicalSockets factory;
    private final LabEvents events;
    private final Set<Closeable> sockets = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 32, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "p01-relay"); t.setDaemon(true); return t; });
    private volatile boolean closed;
    private final Thread acceptor;
    LabSocksServer(PhysicalSockets factory, LabEvents events) throws IOException {
        this.factory = factory; this.events = events;
        listener = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        acceptor = new Thread(this::accept, "p01-listener");
        acceptor.setDaemon(true); acceptor.start();
    }
    int port() { return listener.getLocalPort(); }
    private synchronized boolean own(Closeable socket) {
        if (closed) { release(socket); return false; }
        sockets.add(socket); return true;
    }
    private void release(Closeable socket) {
        sockets.remove(socket);
        try { socket.close(); } catch (IOException ignored) { }
    }
    private void accept() {
        while (!closed) {
            try {
                Socket client = listener.accept();
                if (!own(client)) continue;
                try { workers.execute(() -> handle(client)); }
                catch (RejectedExecutionException e) { release(client); events.add("relay-capacity"); }
            } catch (IOException e) { if (!closed) events.add("listener-error", "reason", e.getClass().getSimpleName()); }
        }
    }
    private void handle(Socket client) {
        try {
            client.setSoTimeout(8000);
            DataInputStream in = new DataInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            if (in.readUnsignedByte() != 5) throw new IOException("SOCKS version");
            int count = in.readUnsignedByte(); boolean noAuth = false;
            for (int i = 0; i < count; i++) if (in.readUnsignedByte() == 0) noAuth = true;
            out.write(new byte[]{5, (byte)(noAuth ? 0 : 255)}); out.flush();
            if (!noAuth) return;
            if (in.readUnsignedByte() != 5) throw new IOException("request version");
            int command = in.readUnsignedByte();
            if (in.readUnsignedByte() != 0) throw new IOException("reserved byte");
            InetSocketAddress target = address(in);
            if (command == 1) tcp(client, target, out);
            else if (command == 3) udp(client, in, out);
            else throw new IOException("unsupported command");
        } catch (Exception e) { if (!closed) events.add("relay-error", "reason", e.getClass().getSimpleName()); }
        finally { release(client); }
    }
    private static InetSocketAddress address(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        int size = type == 1 ? 4 : type == 4 ? 16 : 0;
        if (size == 0) throw new IOException("numeric address required");
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return new InetSocketAddress(InetAddress.getByAddress(bytes), in.readUnsignedShort());
    }
    private static byte[] wireAddress(InetSocketAddress endpoint) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        byte[] ip = endpoint.getAddress().getAddress();
        out.writeByte(ip.length == 4 ? 1 : 4); out.write(ip); out.writeShort(endpoint.getPort());
        return bytes.toByteArray();
    }
    private static void reply(OutputStream out, InetSocketAddress endpoint) throws IOException {
        out.write(new byte[]{5, 0, 0}); out.write(wireAddress(endpoint)); out.flush();
    }
    private void tcp(Socket client, InetSocketAddress target, OutputStream out) throws IOException {
        Socket upstream = new Socket();
        if (!own(upstream)) throw new IOException("stopping");
        try {
            factory.prepare(upstream);
            upstream.connect(target, 8000);
            upstream.setSoTimeout(8000);
            reply(out, (InetSocketAddress)upstream.getLocalSocketAddress());
            Future<?> reverse = workers.submit(() -> {
                try { copy(upstream.getInputStream(), client.getOutputStream()); client.shutdownOutput(); }
                catch (IOException ignored) { release(client); }
            });
            try {
                copy(client.getInputStream(), upstream.getOutputStream()); upstream.shutdownOutput();
                try { reverse.get(8, TimeUnit.SECONDS); }
                catch (Exception e) { reverse.cancel(true); }
            } finally { reverse.cancel(true); }
            events.add("tcp-relayed", "remote", target.getAddress().getHostAddress(), "port", target.getPort());
        } finally { release(upstream); }
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] bytes = new byte[16384]; int n;
        while ((n = in.read(bytes)) >= 0) { out.write(bytes, 0, n); out.flush(); }
    }
    private void udp(Socket client, DataInputStream control, OutputStream out) throws Exception {
        DatagramSocket relay = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        if (!own(relay)) throw new IOException("stopping");
        try {
            relay.setSoTimeout(8000);
            client.setSoTimeout(0); // Control is closed explicitly on STOP; no 8s association expiry.
            reply(out, (InetSocketAddress)relay.getLocalSocketAddress());
            Future<?> packets = workers.submit(() -> udpPackets(relay));
            try { while (control.read() != -1) { /* Keep UDP association alive. */ } }
            finally { release(relay); packets.cancel(true); }
        } finally { release(relay); }
    }
    private void udpPackets(DatagramSocket relay) {
        SocketAddress source = null;
        while (!closed && !relay.isClosed()) {
            try {
                byte[] bytes = new byte[65507]; DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                relay.receive(packet);
                if (source == null) { source = packet.getSocketAddress(); relay.connect(source); }
                if (!source.equals(packet.getSocketAddress())) continue;
                DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes, 0, packet.getLength()));
                if (data.readUnsignedShort() != 0 || data.readUnsignedByte() != 0) throw new IOException("fragment unsupported");
                InetSocketAddress target = address(data);
                byte[] payload = new byte[data.available()]; data.readFully(payload);
                // One outstanding lab datagram, bounded resources and no replay/retry.
                DatagramSocket upstream = new DatagramSocket(null);
                if (!own(upstream)) return;
                try {
                    factory.prepare(upstream); upstream.connect(target); upstream.setSoTimeout(8000);
                    upstream.send(new DatagramPacket(payload, payload.length));
                    DatagramPacket response = new DatagramPacket(bytes, bytes.length); upstream.receive(response);
                    ByteArrayOutputStream framed = new ByteArrayOutputStream();
                    framed.write(new byte[]{0, 0, 0}); framed.write(wireAddress((InetSocketAddress)response.getSocketAddress()));
                    framed.write(bytes, response.getOffset(), response.getLength());
                    byte[] returned = framed.toByteArray(); relay.send(new DatagramPacket(returned, returned.length));
                    events.add("udp-relayed", "remote", target.getAddress().getHostAddress(), "port", target.getPort());
                } finally { release(upstream); }
            } catch (SocketTimeoutException e) { /* Timeout emits no success, association remains bounded by control. */ }
            catch (Exception e) { if (!closed) events.add("udp-error", "reason", e.getClass().getSimpleName()); }
        }
    }
    /** STOP is complete only after the listener and all workers have been reaped. */
    boolean stop() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        synchronized (this) {
            if (!closed) {
                closed = true;
                release(listener);
                for (Closeable socket : sockets) release(socket);
                workers.shutdownNow();
            }
        }
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(acceptor, remaining);
            remaining = deadline - System.nanoTime();
            if (remaining > 0) workers.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        boolean reaped = !acceptor.isAlive() && workers.isTerminated() && sockets.isEmpty();
        events.add(reaped ? "relay-closed" : "relay-stop-timeout", "workers", workers.getActiveCount(),
                "sockets", sockets.size(), "listenerAlive", acceptor.isAlive(), "reaped", reaped);
        return reaped;
    }
    @Override public void close() { stop(); }
}
