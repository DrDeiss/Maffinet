"""Build Linux host fixtures from the same pinned, patched ByeDPI sources as the APK."""
from pathlib import Path
import platform
import subprocess
import importlib.util

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
spec = importlib.util.spec_from_file_location("prepare_byedpi", root / "tools/prepare-byedpi.py")
preparation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preparation)
source = preparation.prepare(build / "byedpi-prepared")
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

stream_fixture = build / "native-stream-contract"
subprocess.run(["cc", *flags, str(root / "verification/native/native_stream_contract.c"),
                str(build / "main.o"), *(str(path) for path in sources),
                "-Wl,--wrap=send", "-o", str(stream_fixture)], check=True)
paced = ["-H:linkedin.com", "-s0", "-s256:9:256", "-r0+sm", "--group-pacing=1", "-R1", "-An"]
scope = ["-H:www.linkedin.com", "-Kt", "-V443", "--group-redirect=tcp://127.0.0.1:443",
         "--group-pacing=20", "-R1", "-An", "-H:example.org"]
stream_cases = {
    "race": paced,
    "legacy-race": ["-H:linkedin.com", "-s0", "-s256:9:256", "-r0+sm", "-Z", "-W1", "-R1", "-An"],
    "again": ["-r0+sm", "-R1"],
    "part-short": paced,
    "rest-short": ["-r0+sm", "-R1"],
    "partial": paced,
    "replay": paced,
    "fallback": paced,
    "disorder": ["-H:linkedin.com", "-d1", "-s1+s", "-r1+s", "--group-pacing=1", "-R1", "-An"],
    "scope": scope,
    "server-first": scope,
    "legacy-scope": ["-H:linkedin.com", "-Z", "-W7", "-Ctcp://127.0.0.1:443", "-An"],
}
for mode, arguments in stream_cases.items():
    subprocess.run([str(stream_fixture), mode, "byedpi", *arguments], check=True, timeout=15)
for invalid in ("0", "60001", "20oops", ""):
    result = subprocess.run([str(stream_fixture), "parse-only", "byedpi", f"--group-pacing={invalid}"],
                            capture_output=True, timeout=15)
    if result.returncode == 0:
        raise RuntimeError(f"Native parser accepted invalid group pacing {invalid!r}")
print(f"Production native stream regressions: {len(stream_cases)} socket/parser cases + 4 invalid-value checks passed")
