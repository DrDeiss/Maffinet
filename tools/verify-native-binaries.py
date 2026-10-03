"""Check inherited native files against their immutable SHA256 and JNI contract."""
from hashlib import sha256
from pathlib import Path

root = Path(__file__).resolve().parents[1]
manifest = root / "docs/native-binaries.sha256"
entries = []
for line in manifest.read_text(encoding="utf-8-sig").splitlines():
    if not line.strip():
        continue
    expected, name = line.split(maxsplit=1)
    file = (root / name).resolve()
    if not file.is_relative_to(root):
        raise RuntimeError("Native manifest path escapes the repository")
    contents = file.read_bytes()
    actual = sha256(contents).hexdigest()
    if actual != expected:
        raise RuntimeError(f"Inherited native binary changed: {name}")
    if file.name == "libhev-socks5-tunnel.so":
        for required in (
            b"com/rupleide/netfix/core/dpibypass/TProxyService",
            b"(Ljava/lang/String;IZ)V",
        ):
            if required not in contents:
                raise RuntimeError(f"Inherited HEV JNI contract is missing: {name}")
    entries.append(name)

expected_files = {
    f"app/src/main/{folder}/{abi}/{library}"
    for folder, library in (("jniLibs", "libhev-socks5-tunnel.so"),
                            ("jniLibsRust", "libtgwsproxy.so"))
    for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}
if set(entries) != expected_files or len(entries) != len(expected_files):
    raise RuntimeError("Native manifest must contain exactly the eight inherited ABI libraries")
print("Verified all eight inherited native hashes and four HEV JNI contracts.")
