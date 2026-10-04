"""Check copied PRIVATE helper/transport JSONL for T01 tuple coverage and exclusion.

This is a narrow lab result check, never a P01 completion or physical-app claim.
Do not put its raw inputs in Git. Source/APK/device/SLO gates remain separate.
"""
import argparse
import ipaddress
import json
import math
from pathlib import Path

def require(condition, message):
    if not condition:
        raise AssertionError(message) # Explicit check remains enabled under python -O.

def rows(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]

def key(row):
    protocol = row["protocol"]
    if isinstance(protocol, str):
        protocol = 17 if protocol in ("udp", "dns-udp") else 6
    family = row["family"]
    local, remote = ipaddress.ip_address(row["local"]), ipaddress.ip_address(row["remote"])
    require(type(family) is int and family in (4, 6) and local.version == remote.version == family, "Tuple family mismatch")
    require(protocol in (6, 17), "Tuple protocol")
    for field in ("localPort", "remotePort"):
        require(type(row[field]) is int and 1 <= row[field] <= 65535, "Tuple port")
    return (protocol, family, str(local), row["localPort"], str(remote), row["remotePort"])

def interval(row):
    require(type(row["elapsedStartNs"]) is int and row["elapsedStartNs"] >= 0, "Invalid start timestamp")
    duration = row["durationMs"]
    require(type(duration) in (int, float) and math.isfinite(duration) and duration >= 0, "Invalid duration")
    start = row["elapsedStartNs"] / 1e6
    return math.floor(start), math.ceil(start + duration)

def validate_result(row):
    require(row.get("schemaVersion") == 2, "Helper schemaVersion 2 required; rebuild/capture current helper")
    require(type(row["uid"]) is int and row["uid"] > 0, "Invalid helper UID")
    require(row["result"] == "passed", "Failed/missing helper success")
    protocol = row["protocol"]
    require(protocol in ("tcp", "udp", "tls", "https", "dns-udp", "dns-tcp", "system-dns"), "Unknown helper protocol")
    interval(row)
    if protocol == "system-dns":
        require(row.get("attribution") == "system-resolver-unknown" and bool(row.get("addresses")), "System DNS result missing")
        for address in row["addresses"]: ipaddress.ip_address(address)
        return
    key(row)
    if protocol != "tls":
        require(type(row.get("receivedBytes")) is int and row["receivedBytes"] > 0, "Response bytes missing")
    if protocol in ("tls", "https"):
        require(bool(row.get("verifiedTlsName")) and bool(row.get("tlsProtocol")), "Verified TLS metadata missing")
    if protocol == "https":
        require(row.get("httpStatus") == 200 and row.get("validatedFixture") == "p01-v1" and row.get("bodyBytes") == 21,
                "Complete P01 HTTP fixture metadata required")
        digest = row.get("responseSha256", "")
        require(isinstance(digest, str) and len(digest) == 64 and all(c in "0123456789abcdef" for c in digest), "HTTP response digest missing")
    if protocol in ("dns-udp", "dns-tcp"):
        require(type(row.get("qtype")) is int and row["qtype"] in (1, 28) and type(row.get("dnsRcode")) is int and row["dnsRcode"] == 0 and row.get("dnsTruncated") is False and
                row.get("validatedDnsFixture") == "p01-v1", "Complete P01 DNS fixture metadata required")
        require(ipaddress.ip_address(row["dnsAnswer"]).version == (4 if row["qtype"] == 1 else 6), "DNS answer family mismatch")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("selected", "control", "events"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    args = parser.parse_args()
    selected, control, events = rows(args.selected), rows(args.control), rows(args.events)
    if not selected or not control:
        raise ValueError("Both helper result sets are required")
    require(all(r["package"] == "io.maffinet.lab.helper.selected" for r in selected), "Wrong selected package")
    require(all(r["package"] == "io.maffinet.lab.helper.control" for r in control), "Wrong control package")
    for result in selected + control: validate_result(result)
    selected_uids, control_uids = {r["uid"] for r in selected}, {r["uid"] for r in control}
    require(len(selected_uids) == len(control_uids) == 1 and not selected_uids & control_uids, "Distinct helper UIDs required")
    snapshots = [r for r in events if r["kind"] == "snapshot"]
    require(len(snapshots) == 1 and snapshots[0]["dropped"] == 0, "Incomplete native event buffer")
    running = [r for r in events if r["kind"] == "state" and r["state"] == "Running"]
    require(len(running) == 1, "One complete generation per check required")
    generation = running[0]["generation"]
    require(type(generation) is int and generation > 0 and all(r.get("generation") == generation for r in events), "Mixed/missing generations")
    require(all(type(r.get("elapsedMs")) is int and r["elapsedMs"] >= 0 for r in events), "Invalid/missing event timestamp")
    require(snapshots[0]["elapsedMs"] + 1 >= max(interval(r)[1] for r in selected + control), "Snapshot predates helper completion")
    require(all(r["elapsedMs"] <= snapshots[0]["elapsedMs"] for r in events), "Event after snapshot")
    tuples = [r for r in events if r["kind"] == "tuple"]
    for event in tuples: key(event)
    matched = 0
    for result in selected + control:
        # Native event timestamps have millisecond precision, helper starts use ns.
        start, finish = interval(result)
        require(start >= running[0]["elapsedMs"], "Helper started before native readiness")
        require(not any(r["kind"] == "state" and r["state"] != "Running" and start <= r["elapsedMs"] <= finish for r in events), "Interrupted helper run")
        if result["protocol"] == "system-dns":
            continue # Resolver attribution is not proved by this scenario.
        found = any(key(r) == key(result) and start <= r["elapsedMs"] <= finish for r in tuples)
        if result in selected:
            require(found, "Selected helper tuple missing from real TUN hook")
            matched += 1
        else:
            require(not any(key(r) == key(result) for r in tuples), "Control helper entered TUN")
    expected = {(p, f) for p in ("tcp", "udp", "tls", "https", "dns-udp", "dns-tcp") for f in (4, 6)}
    for label, results in (("selected", selected), ("control", control)):
        require(expected <= {(r["protocol"], r.get("family")) for r in results}, f"Incomplete TCP/TLS/HTTP/UDP/DNS dual-stack matrix: {label}")
        dns_expected = {(p, f, q) for p in ("dns-udp", "dns-tcp") for f in (4, 6) for q in (1, 28)}
        require(dns_expected <= {(r["protocol"], r.get("family"), r.get("qtype")) for r in results}, f"A/AAAA DNS coverage missing: {label}")
        require(any(r["protocol"] == "system-dns" for r in results), f"System DNS missing: {label}")
    print(json.dumps({"status": "passed", "selectedTuplesMatched": matched, "controlExcluded": True,
                      "scope": "T01-JSONL-only", "P01Completion": "requires-build-native-SLO-device-gates"}))

if __name__ == "__main__":
    main()
