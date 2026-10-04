"""Validate rebuild handoff, local Markdown links and preserved P00 archive."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/rebuild"


def main():
    state = json.loads((DOCS / "STATE.json").read_text(encoding="utf-8-sig"))
    stages = state["stages"]
    assert set(stages) == {f"P{i:02d}" for i in range(10)}, "Unexpected stage IDs"
    assert all(s in {"pending", "in_progress", "done"} for s in stages.values())
    for field in ("currentStage", "nextStage"):
        assert state[field] is None or state[field] in stages, field
    assert (DOCS / state["lastSessionReport"]).is_file(), "Missing session report"
    required = ["START_HERE.md", "NEXT_CHAT.md", "NEXT_SESSION_PROMPT.md", "ROADMAP.md",
                "AUDIT.md", "ARCHITECTURE.md", "SOURCES.md", "CLEANUP.md", "product.md",
                "research.md", "test-plan.md", "usage-map.md", "cleanup-ledger.md",
                "decisions/001-transport-prototype.md", "decisions/002-evidence-and-policy.md",
                "sessions/P00.md", "evidence/checks.json"]
    assert all((DOCS / p).is_file() for p in required), "Incomplete P00 handoff"
    for file in DOCS.rglob("*.json"):
        json.loads(file.read_text(encoding="utf-8-sig"))
    for file in (ROOT / "lab").rglob("*.json"):
        json.loads(file.read_text(encoding="utf-8-sig"))
    if state.get("currentStage") == "P01":
        assert stages["P01"] in {"in_progress", "done"}, "Current P01 cannot remain pending"
        assert (DOCS / "sessions/P01.md").is_file()
        assert (DOCS / "evidence/P01-checks.json").is_file()
    files = [*DOCS.rglob("*.md"), *(ROOT / "lab").rglob("*.md"), ROOT / "PLAN.md", ROOT / "AGENTS.md", ROOT / "README.md"]
    errors = []
    link_count = 0
    for file in files:
        data = file.read_text(encoding="utf-8-sig")
        if sum(line.strip().startswith("```") for line in data.splitlines()) % 2:
            errors.append(f"{file.relative_to(ROOT)}: unpaired fences")
        for match in re.finditer(r"\]\(([^)\n]+)\)", data):
            target = match.group(1).strip().strip("<>")
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target) or target.startswith("#"):
                continue
            target = target.split("#", 1)[0]
            if not target:
                continue
            link_count += 1
            if not (file.parent / target).exists():
                errors.append(f"{file.relative_to(ROOT)}: missing {target}")
    assert not errors, "\n".join(errors)
    manifest = json.loads((DOCS / "evidence/baseline-manifest.json").read_text(encoding="utf-8"))
    entry = next(e for e in manifest["entries"] if e["path"] == "PLAN.md")
    archive = DOCS / "archive/PLAN-0.3.2-alpha.md"
    historical_bytes = archive.read_bytes()
    for name in ("AUTOMATIC_ACCESS.md", "ANDROID36_AND_LINKEDIN.md", "NETFIX_COMPARISON.md",
                 "HOSTS_AND_DNS.md", "DEVICE_VALIDATION.md"):
        historical_bytes = historical_bytes.replace(
            f"](../../{name})".encode(), f"](docs/{name})".encode())
    raw_hash_match = hashlib.sha256(historical_bytes).hexdigest() == entry["sha256"]
    baseline = state["baseline"]["sourceCheckpointCommit"]
    original_blob = subprocess.check_output(["git", "show", f"{baseline}:PLAN.md"], cwd=ROOT)
    assert historical_bytes.replace(b"\r\n", b"\n") == original_blob.replace(b"\r\n", b"\n"), "Historical content changed"
    checkpoint = state.get("lastCheckpointCommit")
    if checkpoint:
        subprocess.run(["git", "cat-file", "-e", f"{checkpoint}^{{commit}}"], cwd=ROOT, check=True)
    results = list((ROOT / "verification/build/test-results/test").glob("TEST-*.xml"))
    totals = {key: sum(int(ET.parse(p).getroot().get(key, "0")) for p in results)
              for key in ("tests", "failures", "errors", "skipped")}
    output = {"schemaVersion": 1, "status": "passed", "markdownFiles": len(files),
              "localLinks": link_count, "archiveHistoricalContentVerified": True,
              "archiveOriginalRawHashMatch": raw_hash_match,
              "checkpointObjectVerified": bool(checkpoint),
              "jvmXmlAvailable": bool(results), "jvmXmlTotals": totals,
              "deviceAcceptance": "not_verified_by_this_tool"}
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
