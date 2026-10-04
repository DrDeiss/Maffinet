package io.maffinet.lab.transport;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RelayContract {
    private static final byte[] MESSAGE = "p01-host-contract".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    private static Socket connect(LabSocksServer relay, int command, InetSocketAddress target) throws Exception {
        Socket client = new Socket("127.0.0.1", relay.port()); client.setSoTimeout(2000);
        DataInputStream in = new DataInputStream(client.getInputStream()); DataOutputStream out = new DataOutputStream(client.getOutputStream());
        out.write(new byte[]{5, 1, 0}); out.flush(); check(in.readUnsignedShort() == 0x500, "auth");
        out.write(new byte[]{5, (byte)command, 0}); byte[] address = target.getAddress().getAddress();
        out.writeByte(address.length == 4 ? 1 : 4); out.write(address); out.writeShort(target.getPort()); out.flush();
        return client;
    }
    private static InetSocketAddress reply(Socket client) throws Exception {
        DataInputStream in = new DataInputStream(client.getInputStream());
        check(in.readUnsignedByte() == 5 && in.readUnsignedByte() == 0 && in.readUnsignedByte() == 0, "reply");
        byte[] ip = new byte[in.readUnsignedByte() == 1 ? 4 : 16]; in.readFully(ip);
        return new InetSocketAddress(InetAddress.getByAddress(ip), in.readUnsignedShort());
    }
    private static void tcp(String ip) throws Exception {
        InetAddress host = InetAddress.getByName(ip);
        try (ServerSocket target = new ServerSocket(0, 4, host)) {
            ExecutorService server = Executors.newSingleThreadExecutor();
            Future<?> echo = server.submit(() -> {
                try (Socket socket = target.accept()) {
                    socket.setSoTimeout(2000); byte[] bytes = new byte[MESSAGE.length];
                    new DataInputStream(socket.getInputStream()).readFully(bytes); socket.getOutputStream().write(bytes);
                } catch (IOException e) { throw new RuntimeException(e); }
            });
            PhysicalSockets factory = new PhysicalSockets(false);
            try (LabSocksServer relay = new LabSocksServer(factory, new LabEvents());
                 Socket client = connect(relay, 1, new InetSocketAddress(host, target.getLocalPort()))) {
                reply(client); client.getOutputStream().write(MESSAGE);
                byte[] bytes = new byte[MESSAGE.length]; new DataInputStream(client.getInputStream()).readFully(bytes);
                check(Arrays.equals(MESSAGE, bytes), "TCP echo " + ip); check(factory.prepared.get() == 1, "TCP preparation");
            } finally { echo.get(3, TimeUnit.SECONDS); server.shutdownNow(); }
        }
    }
    private static void udp(String ip) throws Exception {
        InetAddress host = InetAddress.getByName(ip);
        try (DatagramSocket target = new DatagramSocket(new InetSocketAddress(host, 0))) {
            target.setSoTimeout(2000); ExecutorService server = Executors.newSingleThreadExecutor();
            Future<?> echo = server.submit(() -> {
                try { DatagramPacket p = new DatagramPacket(new byte[100], 100); target.receive(p); target.send(p); }
                catch (IOException e) { throw new RuntimeException(e); }
            });
            PhysicalSockets factory = new PhysicalSockets(false);
            try (LabSocksServer relay = new LabSocksServer(factory, new LabEvents());
                 Socket control = connect(relay, 3, new InetSocketAddress(host, 1)); DatagramSocket local = new DatagramSocket()) {
                InetSocketAddress endpoint = reply(control); local.setSoTimeout(2000);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
                out.write(new byte[]{0,0,0}); byte[] address = host.getAddress(); out.writeByte(address.length == 4 ? 1 : 4);
                out.write(address); out.writeShort(target.getLocalPort()); out.write(MESSAGE);
                byte[] framed = bytes.toByteArray(); local.send(new DatagramPacket(framed, framed.length, endpoint));
                DatagramPacket answer = new DatagramPacket(new byte[200], 200); local.receive(answer);
                check(Arrays.equals(framed, Arrays.copyOf(answer.getData(), answer.getLength())), "UDP source/echo " + ip);
                check(factory.prepared.get() == 1, "UDP preparation");
            } finally { echo.get(3, TimeUnit.SECONDS); server.shutdownNow(); }
        }
    }
    private static void denied() throws Exception {
        try (ServerSocket target = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))) {
            target.setSoTimeout(300); PhysicalSockets factory = new PhysicalSockets(true);
            try (LabSocksServer relay = new LabSocksServer(factory, new LabEvents());
                 Socket client = connect(relay, 1, new InetSocketAddress("127.0.0.1", target.getLocalPort()))) {
                check(client.getInputStream().read() == -1, "pre-connect failure closes flow");
                try { target.accept().close(); throw new AssertionError("connection escaped failure gate"); }
                catch (SocketTimeoutException expected) { }
                check(factory.prepared.get() == 1, "factory invoked before connect");
            }
        }
    }
    private static void stops() throws Exception {
        for (int i = 0; i < 100; i++) {
            LabSocksServer relay = new LabSocksServer(new PhysicalSockets(false), new LabEvents());
            try (Socket blocked = new Socket("127.0.0.1", relay.port())) {
                blocked.setSoTimeout(2500); long start = System.nanoTime(); relay.close();
                try { check(blocked.getInputStream().read() == -1, "STOP closes blocked client"); }
                catch (SocketException closedBeforeAccept) { /* OS resets a not-yet-accepted connection. */ }
                check((System.nanoTime() - start) < 2_100_000_000L, "STOP bounded host wait");
            } finally { relay.close(); }
        }
    }
    private static void deniedUdp() throws Exception {
        try (DatagramSocket target = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            target.setSoTimeout(300); PhysicalSockets factory = new PhysicalSockets(true);
            try (LabSocksServer relay = new LabSocksServer(factory, new LabEvents());
                 Socket control = connect(relay, 3, new InetSocketAddress("127.0.0.1", 1)); DatagramSocket local = new DatagramSocket()) {
                InetSocketAddress endpoint = reply(control);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
                out.write(new byte[]{0,0,0,1,127,0,0,1}); out.writeShort(target.getLocalPort()); out.write(MESSAGE);
                byte[] framed = bytes.toByteArray(); local.send(new DatagramPacket(framed, framed.length, endpoint));
                try { target.receive(new DatagramPacket(new byte[100], 100)); throw new AssertionError("UDP escaped failure gate"); }
                catch (SocketTimeoutException expected) { }
                check(factory.prepared.get() == 1, "UDP factory invoked before send");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        tcp("127.0.0.1"); udp("127.0.0.1"); tcp("::1"); udp("::1"); denied(); deniedUdp(); stops();
        System.out.println("{\"status\":\"passed\",\"tcpFamilies\":2,\"udpFamilies\":2,\"preConnectDenial\":true,\"relayStopCycles\":100,\"androidTun\":\"not-tested\"}");
    }
}
