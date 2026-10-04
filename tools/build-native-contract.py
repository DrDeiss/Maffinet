"""Build Linux host fixtures from the same pinned, patched ByeDPI sources as the APK."""
from pathlib import Path
import platform
import subprocess
import importlib.util
import os

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
flags = ["-D_DEFAULT_SOURCE", "-std=c99", "-O1", "-pthread", "-I", str(source)]
# Rename only the native CLI entry point; real parse_args remains unchanged.
subprocess.run(["cc", *flags, "-Dmain=ciadpi_main", "-c", str(source / "main.c"),
                "-o", str(build / "main.o")], check=True)
# extend.c is included directly by the fixture to expose the static selector.
sources = [source / name for name in
           ("packets.c", "conev.c", "proxy.c", "desync.c", "mpool.c", "automatic_access.c")]
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

access_fixture = build / "native-access-contract"
subprocess.run(["cc", *flags, str(root / "verification/native/native_access_contract.c"),
                str(build / "main.o"), *(str(path) for path in sources),
                "-Wl,--wrap=send", "-Wl,--wrap=connect", "-o", str(access_fixture)], check=True)
access_arguments = ["--auto-access", "-Kt", "-s2", "-r2", "--group-pacing=1", "-R1",
                    "-At,r,s,c", "-Kt", "-R1", "-An", "-Kh", "-s1", "-R1", "-An"]
access_cases = ("map", "route", "shared-ip", "probe", "partial", "tls-records", "timeout",
                "oversize", "other-port", "plaintext", "early-data", "hello-retry",
                "private-original", "ech", "empty-timeout", "upstream-error",
                "silent-response", "server-eof", "late-application", "pooled-buffer",
                "fragmented-response")
for mode in access_cases:
    subprocess.run([str(access_fixture), mode, "byedpi", *access_arguments], check=True, timeout=15)
print(f"Production native Automatic Access regressions: {len(access_cases)} map/SOCKS/socket cases passed")

# The pooled-buffer regression specifically covers the corrected allocation
# boundary. Instrument every production translation unit, including renamed
# main.c, and leave memory/undefined-behavior/leak failures fatal.
sanitizer_flags = [*flags, "-g", "-fno-omit-frame-pointer",
                   "-fsanitize=address,undefined", "-fno-sanitize-recover=all"]
sanitizer_main = build / "main-sanitized.o"
subprocess.run(["cc", *sanitizer_flags, "-Dmain=ciadpi_main", "-c", str(source / "main.c"),
                "-o", str(sanitizer_main)], check=True)
sanitizer_fixture = build / "native-access-contract-sanitized"
subprocess.run(["cc", *sanitizer_flags, str(root / "verification/native/native_access_contract.c"),
                str(sanitizer_main), *(str(path) for path in sources),
                "-Wl,--wrap=send", "-Wl,--wrap=connect", "-o", str(sanitizer_fixture)], check=True)
sanitizer_environment = {**os.environ,
                         "ASAN_OPTIONS": "detect_leaks=1:halt_on_error=1",
                         "UBSAN_OPTIONS": "halt_on_error=1:print_stacktrace=1"}
subprocess.run([str(sanitizer_fixture), "pooled-buffer", "byedpi", *access_arguments],
               check=True, timeout=15, env=sanitizer_environment)
print("Production pooled-buffer socket regression: ASan + UBSan + leak detection passed")
