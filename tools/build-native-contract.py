"""Build a Linux host fixture around pinned ByeDPI; never modify native sources."""
from pathlib import Path
import platform
import subprocess

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/cpp/byedpi"
expected = "ba532298de7b28cfe854aea83d061369d13ca290"
revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
if revision != expected:
    raise RuntimeError(f"Native contract requires the production ByeDPI pin {expected}, found {revision}")
if platform.system() != "Linux":
    raise RuntimeError("This host native fixture requires Linux and a C compiler; JVM-only tests remain available.")

build = root / "verification/build/native"
build.mkdir(parents=True, exist_ok=True)
flags = ["-D_DEFAULT_SOURCE", "-std=c99", "-O1", "-I", str(source)]
# Rename only the native CLI entry point; real parse_args remains unchanged.
subprocess.run(["cc", *flags, "-Dmain=ciadpi_main", "-c", str(source / "main.c"),
                "-o", str(build / "main.o")], check=True)
# extend.c is included directly by the fixture to expose the static selector.
sources = [source / name for name in
           ("packets.c", "conev.c", "proxy.c", "desync.c", "mpool.c")]
fixture = build / "native-contract"
subprocess.run(["cc", *flags, str(root / "verification/native/native_contract.c"),
                str(build / "main.o"), *(str(path) for path in sources),
                "-o", str(fixture)], check=True)
print(f"Pinned production native contract fixture: {fixture}")
