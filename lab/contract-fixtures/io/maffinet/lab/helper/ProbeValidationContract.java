package io.maffinet.lab.helper;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

public final class ProbeValidationContract {
    private interface Attempt { void run() throws Exception; }
    private static int rejected;
    private static void reject(Attempt action) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { rejected++; return; }
        throw new AssertionError("invalid input accepted");
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.ISO_8859_1); }
    private static void dns(int qtype) throws Exception {
        ByteArrayOutputStream queryBytes = new ByteArrayOutputStream(); DataOutputStream q = new DataOutputStream(queryBytes);
        q.writeShort(123); q.writeShort(0x100); q.writeShort(1); q.write(new byte[6]);
        q.write(new byte[]{3, 'l', 'a', 'b', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 0}); q.writeShort(qtype); q.writeShort(1);
        byte[] query = queryBytes.toByteArray(); byte[] ip = ProbeValidation.numeric(qtype == 1 ? "192.0.2.1" : "2001:db8::1").getAddress();
        ByteArrayOutputStream answerBytes = new ByteArrayOutputStream(); DataOutputStream a = new DataOutputStream(answerBytes);
        a.writeShort(123); a.writeShort(0x8180); a.writeShort(1); a.writeShort(1); a.writeInt(0);
        a.write(query, 12, query.length - 12); a.writeShort(0xc00c); a.writeShort(qtype); a.writeShort(1); a.writeInt(30); a.writeShort(ip.length); a.write(ip);
        byte[] answer = answerBytes.toByteArray();
        if (!java.util.Arrays.equals(ProbeValidation.dns(query, answer).getAddress(), ip)) throw new AssertionError("DNS positive fixture");
        reject(() -> ProbeValidation.dns(query, java.util.Arrays.copyOf(answer, answer.length - 1)));
        reject(() -> ProbeValidation.dns(query, java.util.Arrays.copyOf(answer, query.length)));
        for (int offset : new int[]{0, 2, 3, 5, 7, 9, 11, 13, query.length, query.length + 3, query.length + 5, query.length + 11}) {
            byte[] wrong = answer.clone(); wrong[offset] ^= 1;
            // Offset 2 flips the TC bit explicitly instead of RD, which is allowed.
            if (offset == 2) wrong[offset] = (byte)(answer[offset] | 2);
            reject(() -> ProbeValidation.dns(query, wrong));
        }
    }
    public static void main(String[] args) throws Exception {
        if (ProbeValidation.numeric("192.0.2.1").getAddress().length != 4 ||
                ProbeValidation.numeric("2001:db8::1").getAddress().length != 16) throw new AssertionError("numeric families");
        for (String value : new String[]{"face", "deadbeef", "127.1", "123", "256.0.0.1", "01.2.3.4", "1.2.3.", "example.com", "fe80::1%eth0", "[::1]", "::g", ""})
            reject(() -> ProbeValidation.numeric(value));
        reject(() -> ProbeValidation.numeric(null));
        for (String protocol : new String[]{"tcp", "udp", "tls", "https", "dns-udp", "dns-tcp", "system-dns"}) ProbeValidation.protocol(protocol);
        reject(() -> ProbeValidation.protocol("ftp")); reject(() -> ProbeValidation.protocol(null));
        String body = "maffinet-p01-fixture\n";
        String header = "HTTP/1.1 200 OK\r\nContent-Length: 21\r\nConnection: close\r\n\r\n";
        if (ProbeValidation.http(bytes(header + body)) != 21) throw new AssertionError("complete fixture");
        reject(() -> ProbeValidation.http(bytes("HTTP/1.1 200 OK")));
        reject(() -> ProbeValidation.http(bytes(header)));
        reject(() -> ProbeValidation.http(bytes(header + body.substring(0, 20))));
        reject(() -> ProbeValidation.http(bytes(header + body + "x")));
        reject(() -> ProbeValidation.http(bytes(header + "x".repeat(21))));
        reject(() -> ProbeValidation.http(bytes(header.replace("200 OK", "403 Denied") + body)));
        reject(() -> ProbeValidation.http(bytes(header.replace("Content-Length: 21\r\n", "") + body)));
        reject(() -> ProbeValidation.http(bytes(header.replace("Content-Length: 21", "Content-Length: -1") + body)));
        reject(() -> ProbeValidation.http(bytes(header.replace("Content-Length: 21", "Content-Length: 21\r\nContent-Length: 21") + body)));
        reject(() -> ProbeValidation.http(bytes(header.replace("Connection: close", "Transfer-Encoding: chunked") + body)));
        reject(() -> ProbeValidation.http(bytes(header.replace("Connection: close", "broken-header") + body)));
        reject(() -> ProbeValidation.http(bytes(header.replace("Connection: close", "X-Long: " + "x".repeat(8192)) + body)));
        dns(1); dns(28);
        System.out.println("{\"status\":\"passed\",\"numericFamilies\":2,\"invalidInputsRejected\":" + rejected + ",\"httpFixtureComplete\":true,\"scope\":\"actual-helper-validation-host-only\",\"androidTls\":\"not-tested\"}");
    }
}
