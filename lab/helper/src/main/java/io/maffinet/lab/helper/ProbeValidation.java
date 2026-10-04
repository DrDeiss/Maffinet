package io.maffinet.lab.helper;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Narrow P01 fixture contracts. No DNS resolution or general HTTP client here. */
final class ProbeValidation {
    private static final byte[] BODY = "maffinet-p01-fixture\n".getBytes(StandardCharsets.US_ASCII);

    static InetAddress numeric(String input) throws IOException {
        if (input == null) throw new IllegalArgumentException("numeric IP only");
        if (input.indexOf(':') >= 0 && input.matches("[0-9a-fA-F:]+")) {
            // A colon and only hex/colon characters exclude hostnames and scoped DNS lookup.
            return InetAddress.getByName(input);
        }
        String[] parts = input.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("four-octet IPv4 or unscoped IPv6 required");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (!part.matches("0|[1-9][0-9]{0,2}")) throw new IllegalArgumentException("IPv4 octet");
            int value = Integer.parseInt(part);
            if (value > 255) throw new IllegalArgumentException("IPv4 octet");
            bytes[i] = (byte)value;
        }
        return InetAddress.getByAddress(bytes);
    }

    static void protocol(String value) {
        if (value == null || !(value.equals("tcp") || value.equals("udp") || value.equals("tls") ||
                value.equals("https") || value.equals("dns-udp") || value.equals("dns-tcp") || value.equals("system-dns")))
            throw new IllegalArgumentException("protocol");
    }

    static InetAddress dns(byte[] query, byte[] response) throws IOException {
        // The explicit P01 server emits exactly one compressed A/AAAA answer.
        // General DNS forwarding, CNAME/EDNS parsing and recovery belong to P04.
        if (query.length < 17) throw new IOException("DNS query short");
        int qtype = unsignedShort(query, query.length - 4);
        int size = qtype == 1 ? 4 : qtype == 28 ? 16 : 0;
        if (size == 0 || response.length != query.length + 12 + size) throw new IOException("DNS fixture frame size");
        if (response[0] != query[0] || response[1] != query[1] ||
                (unsignedShort(response, 2) & 0xfa0f) != 0x8000 ||
                unsignedShort(response, 4) != 1 || unsignedShort(response, 6) != 1 ||
                unsignedShort(response, 8) != 0 || unsignedShort(response, 10) != 0 ||
                !Arrays.equals(Arrays.copyOfRange(query, 12, query.length), Arrays.copyOfRange(response, 12, query.length)))
            throw new IOException("DNS fixture transaction/question/flags/count mismatch");
        int answer = query.length;
        if (unsignedShort(response, answer) != 0xc00c || unsignedShort(response, answer + 2) != qtype ||
                unsignedShort(response, answer + 4) != 1 || unsignedShort(response, answer + 10) != size)
            throw new IOException("DNS fixture answer mismatch");
        return InetAddress.getByAddress(Arrays.copyOfRange(response, answer + 12, response.length));
    }

    private static int unsignedShort(byte[] bytes, int offset) { return (bytes[offset] & 255) << 8 | (bytes[offset + 1] & 255); }

    static int http(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        int end = text.indexOf("\r\n\r\n");
        if (end < 0 || end > 8192) throw new IOException("HTTP headers incomplete/over cap");
        String[] lines = text.substring(0, end).split("\r\n", -1);
        if (!lines[0].matches("HTTP/1\\.[01] 200 .*")) throw new IOException("HTTP status not 200");
        int length = -1;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon < 1) throw new IOException("HTTP header malformed");
            String name = lines[i].substring(0, colon).toLowerCase(Locale.ROOT);
            String value = lines[i].substring(colon + 1).trim();
            if (name.equals("transfer-encoding")) throw new IOException("P01 fixture requires Content-Length framing");
            if (name.equals("content-length")) {
                if (length != -1 || !value.matches("[0-9]{1,5}")) throw new IOException("HTTP Content-Length malformed/duplicate");
                length = Integer.parseInt(value);
            }
        }
        int bodyStart = end + 4;
        if (length < 0 || bytes.length - bodyStart != length) throw new IOException("HTTP body incomplete/length mismatch");
        if (!Arrays.equals(Arrays.copyOfRange(bytes, bodyStart, bytes.length), BODY))
            throw new IOException("P01 fixture body mismatch");
        return length;
    }
}
