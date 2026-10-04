"""Fail closed on modified source, stale patches, extra files or a changed source lock."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def text_sha(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", type=Path, default=ROOT / ".toolchain/transport-source")
    args = parser.parse_args()
    source = args.source.resolve()
    if not source.is_relative_to((ROOT / ".toolchain").resolve()):
        raise ValueError("Only workspace ignored source trees are accepted")
    manifest = json.loads((source / "prepared-manifest.json").read_text())
    assert manifest["sourceLockSha256"] == text_sha(ROOT / "lab/transport/source-lock.json"), "Changed lock"
    patches = {p.name: text_sha(p) for p in (ROOT / "lab/transport/native/patches").glob("*.patch")}
    assert manifest["patches"] == patches, "Prepared patches are stale"
    actual = {p.relative_to(source).as_posix(): sha(p) for p in source.rglob("*")
              if p.is_file() and p != source / "prepared-manifest.json"}
    assert manifest["files"] == actual, "Prepared source changed/extra files"
    inventory = json.loads((ROOT / "lab/transport/license-inventory.json").read_text())
    for record in inventory["licenses"]:
        assert sha(ROOT / "lab/transport/licenses" / record["file"]) == record["sha256"], "Packaged license asset changed"
    print(json.dumps({"status": "passed", "files": len(actual), "patches": len(patches),
                      "licenseAssets": len(inventory["licenses"]),
                      "manifestSha256": sha(source / "prepared-manifest.json"), "androidBuild": "not-proven"}))

if __name__ == "__main__":
    main()
