"""Prepare the pinned ByeDPI sources plus reviewed patches, without changing the submodule."""
from pathlib import Path
import argparse
import hashlib
import subprocess


PIN = "ba532298de7b28cfe854aea83d061369d13ca290"
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/cpp/byedpi"
PATCHES = ROOT / "app/src/main/cpp/patches"


def prepare(destination: Path) -> Path:
    destination = destination.resolve()
    if destination == SOURCE.resolve() or SOURCE.resolve() in destination.parents:
        raise RuntimeError("Prepared sources must not overwrite the ByeDPI submodule")
    revision = subprocess.check_output(
        ["git", "-C", str(SOURCE), "rev-parse", "HEAD"], text=True
    ).strip()
    if revision != PIN:
        raise RuntimeError(f"ByeDPI requires pin {PIN}, found {revision}")
    files = subprocess.check_output(
        ["git", "-C", str(SOURCE), "ls-tree", "--name-only", PIN], text=True
    ).splitlines()
    destination.mkdir(parents=True, exist_ok=True)
    # Read the committed pin, rather than mutating or resetting a developer's checkout.
    for name in files:
        if Path(name).suffix not in (".c", ".h"):
            continue
        content = subprocess.check_output(["git", "-C", str(SOURCE), "show", f"{PIN}:{name}"])
        (destination / name).write_bytes(content)
    for patch in sorted(PATCHES.glob("*.patch")):
        args = ["git", "apply", "--unsafe-paths", f"--directory={destination.as_posix()}", str(patch)]
        subprocess.run([*args[:2], "--check", *args[2:]], cwd=ROOT, check=True)
        subprocess.run(args, cwd=ROOT, check=True)
        print(f"ByeDPI {PIN} + {patch.name} sha256={hashlib.sha256(patch.read_bytes()).hexdigest()}")
    return destination


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    prepare(arguments.output)
