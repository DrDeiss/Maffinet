package io.maffinet.lab.helper;

import android.os.Process;
import android.os.SystemClock;
import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicit credential-free lab probes. Never disables certificate/name validation. */
final class ProbeRunner {
    private static final int TIMEOUT = 8000;
    static JSONObject run(String pkg, String protocol, String ip, int port, String name, int qtype) {
        JSONObject row = new JSONObject(); long started = SystemClock.elapsedRealtimeNanos();
        try {
            row.put("package", pkg).put("uid", Process.myUid()).put("protocol", protocol)
                    .put("runId", UUID.randomUUID().toString()).put("elapsedStartNs", started);
            if ("system-dns".equals(protocol)) {
                JSONArray results = new JSONArray();
                for (InetAddress value : InetAddress.getAllByName(hostname(name))) results.put(value.getHostAddress());
                row.put("addresses", results).put("attribution", "system-resolver-unknown");
            } else {
                if (port < 1 || port > 65535) throw new IllegalArgumentException("port");
                InetAddress address = numeric(ip);
                row.put("family", address.getAddress().length == 4 ? 4 : 6);
                InetSocketAddress target = new InetSocketAddress(address, port);
                byte[] request = ("maffinet-p01:" + row.getString("runId")).getBytes(StandardCharsets.US_ASCII);
                if (protocol.startsWith("dns-")) request = dnsQuery(name, qtype);
                if ("udp".equals(protocol) || "dns-udp".equals(protocol)) {
                    try (DatagramSocket socket = new DatagramSocket(null)) {
                        socket.setSoTimeout(TIMEOUT); socket.connect(target); tuple(row, socket.getLocalSocketAddress(), target);
                        socket.send(new DatagramPacket(request, request.length));
                        byte[] bytes = new byte[65535]; DatagramPacket received = new DatagramPacket(bytes, bytes.length);
                        socket.receive(received);
                        byte[] response = Arrays.copyOfRange(bytes, received.getOffset(), received.getOffset() + received.getLength());
                        if (protocol.startsWith("dns")) dnsResponse(row, request, response);
                        else if (!Arrays.equals(request, response)) throw new IOException("echo mismatch");
                        row.put("receivedBytes", response.length);
                    }
                } else {
                    try (Socket socket = new Socket()) {
                        socket.setSoTimeout(TIMEOUT); socket.connect(target, TIMEOUT);
                        tuple(row, socket.getLocalSocketAddress(), target);
                        switch (protocol) {
                            case "tcp": {
                                socket.getOutputStream().write(request);
                                byte[] response = new byte[request.length]; new DataInputStream(socket.getInputStream()).readFully(response);
                                if (!Arrays.equals(request, response)) throw new IOException("echo mismatch");
                                row.put("receivedBytes", response.length); break;
                            }
                            case "dns-tcp": {
                                DataOutputStream out = new DataOutputStream(socket.getOutputStream()); out.writeShort(request.length); out.write(request); out.flush();
                                DataInputStream in = new DataInputStream(socket.getInputStream()); int length = in.readUnsignedShort();
                                byte[] response = new byte[length]; in.readFully(response); dnsResponse(row, request, response);
                                row.put("receivedBytes", length); break;
                            }
                            case "tls": case "https": {
                                String originalName = hostname(name);
                                try (SSLSocket tls = (SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket, originalName, port, false)) {
                                    SSLParameters parameters = tls.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS");
                                    parameters.setServerNames(Collections.singletonList(new SNIHostName(originalName))); tls.setSSLParameters(parameters);
                                    tls.setSoTimeout(TIMEOUT); tls.startHandshake();
                                    row.put("verifiedTlsName", originalName).put("tlsProtocol", tls.getSession().getProtocol());
                                    if ("https".equals(protocol)) http(row, tls, originalName);
                                }
                                break;
                            }
                            default: throw new IllegalArgumentException("protocol");
                        }
                    }
                }
            }
            row.put("result", "passed");
        } catch (Exception e) {
            try { row.put("result", "failed").put("error", e.getClass().getSimpleName()).put("detail", String.valueOf(e.getMessage())); }
            catch (JSONException ignored) { }
        }
        try { row.put("durationMs", (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0); }
        catch (JSONException ignored) { }
        return row;
    }
    private static InetAddress numeric(String input) throws Exception {
        if (!(input.matches("[0-9.]+") || input.matches("[0-9a-fA-F:]+"))) throw new IllegalArgumentException("numeric IP only");
        return InetAddress.getByName(input);
    }
    private static String hostname(String name) {
        String value = IDN.toASCII(name);
        if (value.isEmpty() || value.length() > 253 || !value.matches("[A-Za-z0-9.-]+")) throw new IllegalArgumentException("hostname");
        return value;
    }
    private static void tuple(JSONObject row, SocketAddress local, InetSocketAddress remote) throws JSONException {
        InetSocketAddress l = (InetSocketAddress)local;
        row.put("local", l.getAddress().getHostAddress()).put("localPort", l.getPort())
                .put("remote", remote.getAddress().getHostAddress()).put("remotePort", remote.getPort());
    }
    private static byte[] dnsQuery(String name, int qtype) throws Exception {
        if (qtype != 1 && qtype != 28) throw new IllegalArgumentException("A or AAAA only");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(new java.security.SecureRandom().nextInt(65536)); out.writeShort(0x100); out.writeShort(1);
        out.writeShort(0); out.writeShort(0); out.writeShort(0);
        for (String label : hostname(name).split("\\.")) {
            byte[] part = label.getBytes(StandardCharsets.US_ASCII);
            if (part.length == 0 || part.length > 63) throw new IllegalArgumentException("DNS label");
            out.writeByte(part.length); out.write(part);
        }
        out.writeByte(0); out.writeShort(qtype); out.writeShort(1); return bytes.toByteArray();
    }
    private static void dnsResponse(JSONObject row, byte[] query, byte[] response) throws Exception {
        if (response.length < query.length || response[0] != query[0] || response[1] != query[1] ||
                (response[2] & 0x80) == 0 || response[4] != 0 || response[5] != 1 ||
                !Arrays.equals(Arrays.copyOfRange(query, 12, query.length), Arrays.copyOfRange(response, 12, query.length)))
            throw new IOException("DNS transaction/question mismatch");
        int rcode = response[3] & 15; boolean truncated = (response[2] & 2) != 0;
        row.put("dnsRcode", rcode).put("dnsTruncated", truncated);
        if (rcode != 0 || truncated) throw new IOException("DNS rcode/truncation; no retry in P01 helper");
    }
    private static void http(JSONObject row, SSLSocket tls, String name) throws Exception {
        tls.getOutputStream().write(("GET /p01 HTTP/1.1\r\nHost: " + name + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        // Bounded headers/body; no payload or cookies in evidence.
        ByteArrayOutputStream response = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
        while ((count = tls.getInputStream().read(buffer)) != -1) {
            if (response.size() + count > 65536) throw new IOException("HTTP response cap"); response.write(buffer, 0, count);
        }
        byte[] bytes = response.toByteArray();
        String status = new String(bytes, StandardCharsets.ISO_8859_1).split("\r\n", 2)[0];
        if (!status.matches("HTTP/1\\.[01] 200 .*")) throw new IOException("HTTP status not 200");
        row.put("httpStatus", 200).put("receivedBytes", bytes.length).put("responseSha256", hex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                .put("evidenceScope", "credential-free-/p01-only");
    }
    private static String hex(byte[] bytes) { StringBuilder text = new StringBuilder(); for (byte b : bytes) text.append(String.format(Locale.ROOT, "%02x", b & 255)); return text.toString(); }
}
