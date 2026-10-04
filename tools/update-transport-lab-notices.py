"""Reproduce P01 packaged license assets from reviewed source pins (no network)."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
LIBYAML = "2c891fc7a770e8ba2fec34fc6b545c672beb37e6"

def blob(repo, revision, path):
    return subprocess.check_output(["git", "-C", str(repo), "show", f"{revision}:{path}"])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / ".toolchain/rebuild-p01/hev-upstream")
    parser.add_argument("--libyaml-source", type=Path, default=ROOT / ".toolchain/rebuild-p01/libyaml-license-source")
    args = parser.parse_args()
    assets = ROOT / "lab/transport/licenses"; assets.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / "lab/transport/source-lock.json").read_text())
    names = {".": "hev-tunnel-LICENSE", "src/core": "hev-core-LICENSE", "third-part/hev-task-system": "hev-task-system-LICENSE",
             "third-part/yaml": "hev-yaml-LICENSE", "third-part/lwip": "lwip-LICENSE"}
    inventory = []
    for repo in lock["repositories"]:
        source = args.source / (repo["path"] if repo["path"] != "." else "")
        for license in repo["licenses"]:
            data = blob(source, repo["commit"], license["path"])
            assert hashlib.sha256(data).hexdigest() == license["sha256"], "License differs from canonical lock"
            filename = names[repo["path"]]; (assets / filename).write_bytes(data)
            inventory.append({"file": filename, "source": repo["url"], "commit": repo["commit"],
                              "path": license["path"], "sha256": license["sha256"]})
    data = blob(args.libyaml_source, LIBYAML, "License")
    (assets / "libyaml-LICENSE").write_bytes(data)
    inventory.append({"file": "libyaml-LICENSE", "source": "https://github.com/yaml/libyaml", "commit": LIBYAML,
                      "path": "License", "sha256": hashlib.sha256(data).hexdigest(),
                      "reason": "HEV YAML README identifies libyaml; retain original authors too"})
    repo = next(r for r in lock["repositories"] if r["path"] == "third-part/lwip")
    source = args.source / repo["path"]
    tree = subprocess.check_output(["git", "-C", str(source), "ls-tree", "-r", "--name-only", repo["commit"]]).decode().splitlines()
    notices = {}
    for name in tree:
        if not name.startswith("src/") or Path(name).suffix not in (".h", ".c"):
            continue
        for comment in re.findall(r"/\*.*?\*/", blob(source, repo["commit"], name).decode(errors="replace"), re.S):
            if "copyright" in comment.lower() and ("redistribution" in comment.lower() or "permission" in comment.lower()):
                notices.setdefault(comment, []).append(name)
    text = "Additional verbatim lwIP license/copyright blocks (conservative source superset).\n\n"
    for comment, paths in notices.items():
        text += "Files: " + ", ".join(paths) + "\n" + comment + "\n\n"
    data = text.encode(); (assets / "lwip-source-NOTICES.txt").write_bytes(data)
    inventory.append({"file": "lwip-source-NOTICES.txt", "source": repo["url"], "commit": repo["commit"],
                      "sha256": hashlib.sha256(data).hexdigest(), "uniqueSourceBlocks": len(notices)})
    (ROOT / "lab/transport/license-inventory.json").write_bytes((json.dumps({"schemaVersion": 1, "licenses": inventory}, indent=2) + "\n").encode())
    print(json.dumps({"licenseFiles": len(inventory), "lwipBlocks": len(notices), "noticeBytes": len(data)}))

if __name__ == "__main__":
    main()
