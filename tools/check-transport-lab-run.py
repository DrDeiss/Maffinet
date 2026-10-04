"""Check copied PRIVATE helper/transport JSONL for T01 tuple coverage and exclusion.

This is a narrow lab result check, never a P01 completion or physical-app claim.
Do not put its raw inputs in Git. Source/APK/device/SLO gates remain separate.
"""
import argparse
import ipaddress
import json
import math
from pathlib import Path

def rows(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]

def key(row):
    protocol = row["protocol"]
    if isinstance(protocol, str):
        protocol = 17 if protocol in ("udp", "dns-udp") else 6
    return (protocol, int(row["family"]), str(ipaddress.ip_address(row["local"])), int(row["localPort"]),
            str(ipaddress.ip_address(row["remote"])), int(row["remotePort"]))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("selected", "control", "events"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    args = parser.parse_args()
    selected, control, events = rows(args.selected), rows(args.control), rows(args.events)
    if not selected or not control:
        raise ValueError("Both helper result sets are required")
    assert all(r["package"] == "io.maffinet.lab.helper.selected" for r in selected), "Wrong selected package"
    assert all(r["package"] == "io.maffinet.lab.helper.control" for r in control), "Wrong control package"
    selected_uids, control_uids = {r["uid"] for r in selected}, {r["uid"] for r in control}
    assert len(selected_uids) == len(control_uids) == 1 and not selected_uids & control_uids, "Distinct helper UIDs required"
    assert all(r["result"] == "passed" for r in selected + control), "Failed/missing helper success"
    snapshots = [r for r in events if r["kind"] == "snapshot"]
    assert len(snapshots) == 1 and snapshots[0]["dropped"] == 0, "Incomplete native event buffer"
    running = [r for r in events if r["kind"] == "state" and r["state"] == "Running"]
    assert len(running) == 1, "One complete generation per check required"
    generation = running[0]["generation"]
    assert all(r["generation"] == generation for r in events if "generation" in r), "Mixed generations"
    tuples = [r for r in events if r["kind"] == "tuple"]
    matched = 0
    for result in selected + control:
        if result["protocol"] == "system-dns":
            continue # Resolver attribution is not proved by this scenario.
        # Native event timestamps have millisecond precision, helper starts use ns.
        start = math.floor(result["elapsedStartNs"] / 1e6)
        finish = math.ceil(result["elapsedStartNs"] / 1e6 + result["durationMs"])
        assert start >= running[0]["elapsedMs"], "Helper started before native readiness"
        assert not any(r["kind"] == "state" and r["state"] != "Running" and start <= r["elapsedMs"] <= finish for r in events), "Interrupted helper run"
        found = any(key(r) == key(result) and start <= r["elapsedMs"] <= finish for r in tuples)
        if result in selected:
            assert found, "Selected helper tuple missing from real TUN hook"
            matched += 1
        else:
            assert not any(key(r) == key(result) for r in tuples), "Control helper entered TUN"
    expected = {(p, f) for p in ("tcp", "udp", "tls", "https", "dns-udp", "dns-tcp") for f in (4, 6)}
    for label, results in (("selected", selected), ("control", control)):
        assert expected <= {(r["protocol"], r.get("family")) for r in results}, f"Incomplete TCP/TLS/HTTP/UDP/DNS dual-stack matrix: {label}"
        assert any(r["protocol"] == "system-dns" for r in results), f"System DNS missing: {label}"
    print(json.dumps({"status": "passed", "selectedTuplesMatched": matched, "controlExcluded": True,
                      "scope": "T01-JSONL-only", "P01Completion": "requires-build-native-SLO-device-gates"}))

if __name__ == "__main__":
    main()
