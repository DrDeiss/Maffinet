"""Explicit local P01 fixture: numeric TCP/UDP echo, DNS UDP/TCP and optional TLS /p01.

Run on a reachable lab host. TLS requires an operator-provided normally trusted
certificate/key; this tool never creates a CA or changes a device's trust store.
"""
import argparse
import ipaddress
import json
from pathlib import Path
import socket
import socketserver
import ssl
import struct
import threading


def read_exact(stream, length):
    data = b""
    while len(data) < length:
        chunk = stream.recv(length - len(data))
        if not chunk:
            raise EOFError("short frame")
        data += chunk
    return data


def dns_answer(query, ipv4, ipv6):
    if len(query) < 17 or struct.unpack_from("!H", query, 4)[0] != 1:
        raise ValueError("one-question DNS fixture only")
    pos = 12
    while query[pos]:
        size = query[pos]
        if size > 63 or pos + size + 1 >= len(query):
            raise ValueError("malformed label")
        pos += size + 1
    end = pos + 5
    if end > len(query):
        raise ValueError("short question")
    qtype, qclass = struct.unpack_from("!HH", query, pos + 1)
    if qclass != 1:
        raise ValueError("IN only")
    data = ipaddress.ip_address(ipv4 if qtype == 1 else ipv6).packed if qtype in (1, 28) else None
    flags = 0x8080 | (struct.unpack_from("!H", query, 2)[0] & 0x100)
    header = query[:2] + struct.pack("!HHHHH", flags, 1, 1 if data else 0, 0, 0)
    answer = b"\xc0\x0c" + struct.pack("!HHIH", qtype, 1, 30, len(data)) + data if data else b""
    return header + query[12:end] + answer


class TCP(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


class UDP(socketserver.ThreadingUDPServer):
    allow_reuse_address = True
    daemon_threads = True


class Echo(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(8)
        try:
            while True:
                data = self.request.recv(16384)
                if not data:
                    break
                self.request.sendall(data)
        except (OSError, TimeoutError):
            pass


class Datagram(socketserver.BaseRequestHandler):
    def handle(self):
        data, sock = self.request
        try:
            result = dns_answer(data, self.server.ipv4, self.server.ipv6) if self.server.is_dns else data
            sock.sendto(result, self.client_address)
        except (ValueError, IndexError, struct.error, OSError):
            pass


class DnsTCP(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(8)
        try:
            while True:
                length = struct.unpack("!H", read_exact(self.request, 2))[0]
                response = dns_answer(read_exact(self.request, length), self.server.ipv4, self.server.ipv6)
                self.request.sendall(struct.pack("!H", len(response)) + response)
        except (EOFError, ValueError, IndexError, struct.error, OSError):
            pass


class HTTPS(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(8)
        try:
            with self.server.context.wrap_socket(self.request, server_side=True) as tls:
                headers = b""
                while b"\r\n\r\n" not in headers and len(headers) < 8192:
                    part = tls.recv(1024)
                    if not part:
                        return
                    headers += part
                if headers.startswith(b"GET /p01 HTTP/1.1\r\n"):
                    body = b"maffinet-p01-fixture\n"
                    tls.sendall(b"HTTP/1.1 200 OK\r\nContent-Length: " + str(len(body)).encode() + b"\r\nConnection: close\r\n\r\n" + body)
        except OSError:
            pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="Explicit numeric reachable lab address")
    parser.add_argument("--tcp-port", type=int, default=15001)
    parser.add_argument("--udp-port", type=int, default=15002)
    parser.add_argument("--dns-port", type=int, default=1053)
    parser.add_argument("--tls-port", type=int, default=15443)
    parser.add_argument("--answer-v4", default="192.0.2.1")
    parser.add_argument("--answer-v6", default="2001:db8::1")
    parser.add_argument("--cert", type=Path)
    parser.add_argument("--key", type=Path)
    args = parser.parse_args()
    host = ipaddress.ip_address(args.host)
    family = socket.AF_INET6 if host.version == 6 else socket.AF_INET
    servers = []
    for cls, handler, port, dns in ((TCP, Echo, args.tcp_port, False), (UDP, Datagram, args.udp_port, False),
                                    (TCP, DnsTCP, args.dns_port, True), (UDP, Datagram, args.dns_port, True)):
        configured = type("Configured", (cls,), {"address_family": family})
        server = configured((str(host), port), handler)
        server.ipv4, server.ipv6, server.is_dns = args.answer_v4, args.answer_v6, dns
        servers.append(server)
    if bool(args.cert) != bool(args.key):
        parser.error("TLS requires both --cert and --key")
    if args.cert:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); context.load_cert_chain(args.cert, args.key)
        configured = type("ConfiguredTLS", (TCP,), {"address_family": family})
        server = configured((str(host), args.tls_port), HTTPS); server.context = context; servers.append(server)
    for server in servers:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    print(json.dumps({"status": "listening", "family": host.version, "tls": bool(args.cert), "metadataOnly": True}), flush=True)
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        for server in servers:
            server.shutdown(); server.server_close()


if __name__ == "__main__":
    main()
