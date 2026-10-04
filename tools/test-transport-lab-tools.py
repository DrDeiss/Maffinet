"""Host-only rejection tests for the P01 evidence checker and fixture wire framing."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import socket
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools" / filename)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value); return value

SERVER = module("fixture", "transport-lab-server.py")
CHECKER = module("checker", "check-transport-lab-run.py")
PREP = module("prep", "prepare-transport-lab.py")

class Contracts(unittest.TestCase):
    def query(self, qtype):
        return struct.pack("!HHHHHH", 123, 0x100, 1, 0, 0, 0) + b"\x03lab\x07example\0" + struct.pack("!HH", qtype, 1)
    def test_dns_a_aaaa_preserves_transaction_question(self):
        for qtype, size in ((1, 4), (28, 16)):
            query = self.query(qtype)
            answer = SERVER.dns_answer(query, "192.0.2.1", "2001:db8::1")
            self.assertEqual(answer[:2], query[:2]); self.assertEqual(answer[12:len(query)], query[12:])
            self.assertEqual(struct.unpack_from("!H", answer, 6)[0], 1)
            self.assertEqual(len(answer) - len(query), 12 + size)
    def test_dns_bad_wire_rejected(self):
        for query in (b"", b"\0" * 12, self.query(1)[:-1], self.query(1)[:12] + b"\xff" * 8):
            with self.assertRaises((ValueError, IndexError)): SERVER.dns_answer(query, "192.0.2.1", "2001:db8::1")
    def test_tcp_short_frame_not_success(self):
        writer, reader = socket.socketpair()
        try:
            writer.sendall(b"abc"); writer.shutdown(socket.SHUT_WR)
            with self.assertRaises(EOFError): SERVER.read_exact(reader, 4)
        finally:
            writer.close(); reader.close()
    def test_source_output_cannot_escape_toolchain(self):
        with self.assertRaises(ValueError): PREP.confined(ROOT / "app/src/main")
        self.assertTrue(PREP.confined(ROOT / ".toolchain/new-lab").is_relative_to(ROOT))
    def fixture(self):
        selected, control = [], []
        events = [{"kind": "snapshot", "dropped": 0}, {"kind": "state", "state": "Running", "generation": 1, "elapsedMs": 1}]
        counter = 0
        for protocol in ("tcp", "udp", "tls", "https", "dns-udp", "dns-tcp"):
            for family in (4, 6):
                counter += 1
                row = {"package": "io.maffinet.lab.helper.selected", "uid": 101, "protocol": protocol, "family": family,
                       "local": "198.18.0.1" if family == 4 else "fd00:1::1", "localPort": 20000 + counter,
                       "remote": "192.0.2.1" if family == 4 else "2001:db8::1", "remotePort": 15001,
                       "result": "passed", "elapsedStartNs": 1_000_000_000, "durationMs": 100}
                selected.append(row)
                outside = dict(row, package="io.maffinet.lab.helper.control", uid=102, localPort=30000 + counter,
                               local="192.0.2.2" if family == 4 else "2001:db8::2")
                control.append(outside)
                events.append(dict(row, kind="tuple", protocol=17 if protocol in ("udp", "dns-udp") else 6,
                                   generation=1, elapsedMs=1050))
        selected.append({"package": "io.maffinet.lab.helper.selected", "uid": 101, "protocol": "system-dns", "result": "passed"})
        control.append({"package": "io.maffinet.lab.helper.control", "uid": 102, "protocol": "system-dns", "result": "passed"})
        return selected, control, events
    def invoke(self, selected, control, events):
        base = ROOT / ".toolchain/rebuild-p01"; base.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=base) as tmp:
            argv = ["checker"]
            for label, values in (("selected", selected), ("control", control), ("events", events)):
                path = Path(tmp) / f"{label}.jsonl"; path.write_text("\n".join(json.dumps(r) for r in values))
                argv += ["--" + label, str(path)]
            with patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()): CHECKER.main()
    def test_synthetic_complete_matrix_accepted(self): self.invoke(*self.fixture())
    def test_submillisecond_tuple_rounding(self):
        selected, control, events = self.fixture()
        for row in selected + control:
            if "elapsedStartNs" in row: row["elapsedStartNs"] += 900_000
        for row in events:
            if row["kind"] == "tuple": row["elapsedMs"] = 1000
        self.invoke(selected, control, events)
    def test_missing_selected_tuple_rejected(self):
        selected, control, events = self.fixture(); events.pop()
        with self.assertRaises(AssertionError): self.invoke(selected, control, events)
    def test_control_in_tun_rejected(self):
        selected, control, events = self.fixture(); outside = control[0]
        events.append(dict(outside, kind="tuple", protocol=6, generation=1, elapsedMs=1050))
        with self.assertRaises(AssertionError): self.invoke(selected, control, events)
    def test_event_loss_or_shared_uid_rejected(self):
        selected, control, events = self.fixture(); events[0]["dropped"] = 1
        with self.assertRaises(AssertionError): self.invoke(selected, control, events)
        selected, control, events = self.fixture()
        for row in control: row["uid"] = 101
        with self.assertRaises(AssertionError): self.invoke(selected, control, events)
    def test_wrong_generation_or_failed_probe_rejected(self):
        selected, control, events = self.fixture(); events[-1]["generation"] = 2
        with self.assertRaises(AssertionError): self.invoke(selected, control, events)
        selected, control, events = self.fixture(); selected[0]["result"] = "failed"
        with self.assertRaises(AssertionError): self.invoke(selected, control, events)

if __name__ == "__main__": unittest.main()
