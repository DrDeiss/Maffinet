"""Check shipped hashes, Maffinet JNI bindings and the exact inherited byte delta."""
from hashlib import sha256
from pathlib import Path
from rebind_hev_jni import load_contract, audit_library

root = Path(__file__).resolve().parents[1]
manifest = root / "docs/native-binaries.sha256"
old_jni, new_jni, bindings = load_contract(root)
bindings = {entry["path"]: entry for entry in bindings}
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
        raise RuntimeError(f"Shipped native binary differs from its manifest: {name}")
    if file.name == "libhev-socks5-tunnel.so":
        audit_library(contents, bindings[name], old_jni, new_jni)
    entries.append(name)

expected_files = {
    f"app/src/main/{folder}/{abi}/{library}"
    for folder, library in (("jniLibs", "libhev-socks5-tunnel.so"),
                            ("jniLibsRust", "libtgwsproxy.so"))
    for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}
if set(entries) != expected_files or len(entries) != len(expected_files):
    raise RuntimeError("Native manifest must contain exactly the eight inherited ABI libraries")
bridge = root / "app/src/main/java/io/maffinet/android/core/dpibypass/TProxyService.kt"
if not bridge.is_file() or "package io.maffinet.android.core.dpibypass" not in bridge.read_text(encoding="utf-8"):
    raise RuntimeError("Maffinet HEV Kotlin bridge is missing")
old_bridge = root / "app/src/main/java" / (old_jni.decode("ascii") + ".kt")
if old_bridge.exists():
    raise RuntimeError("The legacy HEV Kotlin namespace must not be shipped")
print("Verified eight shipped native hashes and four Maffinet HEV JNI bindings with exact inverse provenance.")
