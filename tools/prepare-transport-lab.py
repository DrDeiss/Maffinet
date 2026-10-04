"""Fetch pinned HEV repositories and export a separate patched P01 build tree.

No submodule mutation, SDK installation, license acceptance or legacy binary writes.
All paths are confined to this workspace's ignored .toolchain directory.
"""
import argparse
import configparser
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
PIN = "4d6c334dbfb68a79d1970c2744e62d09f71df12f"
LOCK = ROOT / "lab/transport/source-lock.json"


def git(path, *args):
    options = ["-c", "core.autocrlf=false", "-c", "core.eol=lf"] if args[0] == "archive" else []
    return subprocess.check_output(["git", "-c", "http.sslBackend=openssl", *options, "-C", str(path), *args])


def confined(path):
    path = path.resolve()
    if not path.is_relative_to((ROOT / ".toolchain").resolve()):
        raise ValueError("Transport source/output must stay inside workspace .toolchain")
    return path


def collect(path, prefix, revision, fetch, records):
    if not (path / ".git").exists():
        if not fetch:
            raise ValueError(f"Missing pinned repository: {prefix or '.'}; use --fetch")
        url = "https://github.com/heiher/hev-socks5-tunnel"
        if prefix:
            url = records[-1]["url"]
        path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "-c", "http.sslBackend=openssl", "clone", "--no-checkout", url, str(path)], check=True)
        subprocess.run(["git", "-C", str(path), "checkout", "--detach", revision], check=True)
    actual = git(path, "rev-parse", "HEAD").decode().strip()
    if actual != revision:
        raise ValueError(f"Unexpected HEAD at {prefix}: {actual}")
    # Ignore only the nested independent checkout directories, not tracked edits.
    if git(path, "status", "--porcelain", "--untracked-files=no").strip():
        raise ValueError(f"Dirty tracked source: {prefix}")
    archive = git(path, "archive", "--format=tar", revision)
    licenses = []
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        for item in tar:
            if item.isfile() and item.name.lower() in {"license", "copying", "notice"}:
                data = tar.extractfile(item).read()
                licenses.append({"path": item.name, "sha256": hashlib.sha256(data).hexdigest()})
    own = {"path": prefix or ".", "commit": revision,
           "tree": git(path, "rev-parse", f"{revision}^{{tree}}").decode().strip(),
           "archiveSha256": hashlib.sha256(archive).hexdigest(), "licenses": licenses}
    if not prefix:
        own["url"] = "https://github.com/heiher/hev-socks5-tunnel"
        records.append(own)
    else:
        records[-1].update(own)
    modules = path / ".gitmodules"
    if not modules.is_file():
        return
    config = configparser.ConfigParser()
    config.read_string(git(path, "show", f"{revision}:.gitmodules").decode())
    links = {}
    for line in git(path, "ls-tree", "-r", revision).decode().splitlines():
        info, name = line.split("\t", 1)
        mode, kind, sha = info.split()
        if mode == "160000":
            links[name] = sha
    declared = set()
    for section in config.sections():
        subpath, url = config[section]["path"], config[section]["url"]
        if not url.startswith("https://github.com/heiher/") or ".." in Path(subpath).parts:
            raise ValueError("Unexpected recursive dependency")
        declared.add(subpath)
        full = f"{prefix}/{subpath}".lstrip("/")
        records.append({"path": full, "url": url})
        collect(path / subpath, full, links[subpath], fetch, records)
    if declared != set(links):
        raise ValueError("Gitlinks and .gitmodules disagree")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / ".toolchain/rebuild-p01/hev-upstream")
    parser.add_argument("--output", type=Path, default=ROOT / ".toolchain/transport-source")
    parser.add_argument("--fetch", action="store_true")
    parser.add_argument("--record-lock", action="store_true", help="Only for intentional initial pin review")
    args = parser.parse_args()
    source, output = confined(args.source), confined(args.output)
    records = []
    collect(source, "", PIN, args.fetch, records)
    lock = {"schemaVersion": 1, "candidate": "HEV 2.14.4", "repositories": records}
    if args.record_lock:
        if LOCK.exists():
            raise ValueError("Refusing to overwrite existing lock")
        LOCK.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    if lock != json.loads(LOCK.read_text(encoding="utf-8")):
        raise ValueError("Recursive source/license inventory differs from reviewed lock")
    if output.exists():
        raise ValueError("Output exists: choose a fresh ignored output; no automatic deletion")
    output.mkdir(parents=True)
    aliases = []
    for record in records:
        relative = record["path"] if record["path"] != "." else ""
        archive = git(source / relative, "archive", "--format=tar", record["commit"])
        destination = output / relative
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            for item in tar:
                target = (destination / item.name).resolve()
                if not target.is_relative_to(output):
                    raise ValueError("Unsafe archive path")
                if item.isfile():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(tar.extractfile(item).read())
                elif item.issym():
                    linked = (target.parent / item.linkname).resolve()
                    if not linked.is_relative_to(output):
                        raise ValueError("Unsafe archive symlink")
                    aliases.append((target, linked))
    # Windows symlinks require privileges. Materialize pinned public header aliases.
    for target, linked in aliases:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(linked.read_bytes())
    patch_hashes = {}
    for patch in sorted((ROOT / "lab/transport/native/patches").glob("*.patch")):
        data = patch.read_bytes().replace(b"\r\n", b"\n")
        patch_hashes[patch.name] = hashlib.sha256(data).hexdigest()
        subprocess.run(["git", "-c", "core.autocrlf=false", "apply", "--check", "-"], input=data, cwd=output, check=True)
        subprocess.run(["git", "-c", "core.autocrlf=false", "apply", "-"], input=data, cwd=output, check=True)
    files = {p.relative_to(output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
             for p in sorted(output.rglob("*")) if p.is_file()}
    manifest = {"schemaVersion": 1, "sourceLockSha256": hashlib.sha256(LOCK.read_bytes().replace(b"\r\n", b"\n")).hexdigest(),
                "patches": patch_hashes,
                "files": files}
    data = (json.dumps(manifest, indent=2) + "\n").encode()
    (output / "prepared-manifest.json").write_bytes(data)
    print(json.dumps({"status": "prepared", "repositories": len(records), "files": len(files),
                      "manifestSha256": hashlib.sha256(data).hexdigest(), "output": str(output)}, indent=2))


if __name__ == "__main__":
    main()
