"""Audit/apply the fixed-width HEV JNI class rebind; never rebuild tunnel code."""
import argparse
from hashlib import sha256
import json
import os
from pathlib import Path
import struct
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def load_contract(root=ROOT):
    contract = json.loads((root / "docs/native-jni-rebind.json").read_text(encoding="utf-8"))
    if contract["schemaVersion"] != 1:
        raise RuntimeError("Unsupported JNI rebind contract")
    old = contract["originalJniClass"].encode("ascii")
    new = contract["maffinetJniClass"].encode("ascii")
    if new != b"io/maffinet/android/core/dpibypass/TProxyService":
        raise RuntimeError("JNI target must match the Maffinet Kotlin bridge")
    if old == new or len(old) != len(new) or len(new) != contract["classNameBytes"]:
        raise RuntimeError("JNI rebind must preserve the class-name byte length")
    entries = contract["libraries"]
    if len(entries) != 4 or {entry["abi"] for entry in entries} != ABIS:
        raise RuntimeError("JNI rebind requires exactly the four shipped HEV ABIs")
    for entry in entries:
        expected = f"app/src/main/jniLibs/{entry['abi']}/libhev-socks5-tunnel.so"
        if entry["path"] != expected:
            raise RuntimeError("JNI rebind path does not match its ABI")
    return old, new, entries


def assert_readonly_data(data, offset, size):
    """Require the NUL-terminated name in .rodata without WRITE/EXEC section flags."""
    if data[:4] != b"\x7fELF" or data[5] != 1:
        raise RuntimeError("Expected a little-endian ELF library")
    if data[4] == 1:
        shoff = struct.unpack_from("<I", data, 32)[0]
        shsize, count, strings_index = struct.unpack_from("<HHH", data, 46)
        fmt = "<IIIIIIIIII"
    elif data[4] == 2:
        shoff = struct.unpack_from("<Q", data, 40)[0]
        shsize, count, strings_index = struct.unpack_from("<HHH", data, 58)
        fmt = "<IIQQQQIIQQ"
    else:
        raise RuntimeError("Unsupported ELF class")
    if shsize < struct.calcsize(fmt) or shoff + shsize * count > len(data):
        raise RuntimeError("Invalid ELF section table")
    sections = [struct.unpack_from(fmt, data, shoff + i * shsize) for i in range(count)]
    strings = sections[strings_index]
    names = data[strings[4]:strings[4] + strings[5]]
    for section in sections:
        name = names[section[0]:].split(b"\0", 1)[0]
        if name == b".rodata" and section[4] <= offset and offset + size <= section[4] + section[5]:
            if section[1] != 1 or section[2] & (1 | 4):
                raise RuntimeError("JNI class name must not be writable or executable code")
            return
    raise RuntimeError("JNI class name is outside ELF .rodata")


def audit_library(data, entry, old, new, require_bound=True):
    digest = sha256(data).hexdigest()
    offset = entry["offset"]
    if not isinstance(offset, int) or offset < 1 or offset + len(new) >= len(data):
        raise RuntimeError("Invalid JNI rebind offset")
    if data[offset - 1] != 0 or data[offset + len(new)] != 0:
        raise RuntimeError("JNI class name is not a complete NUL-bounded string")
    assert_readonly_data(data, offset, len(new) + 1)
    if digest == entry["originalSha256"]:
        if require_bound:
            raise RuntimeError("HEV still uses its original JNI namespace")
        if data.count(old + b"\0") != 1 or new in data or data[offset:offset + len(old)] != old:
            raise RuntimeError("Original HEV JNI binding differs from the pinned contract")
        bound = data[:offset] + new + data[offset + len(old):]
    elif digest == entry["maffinetSha256"]:
        if data.count(new + b"\0") != 1 or old in data or data[offset:offset + len(new)] != new:
            raise RuntimeError("Maffinet HEV JNI binding differs from its contract")
        bound = data
    else:
        raise RuntimeError(f"Unrecognized HEV bytes for {entry['abi']}; refusing a namespace change")
    original = bound[:offset] + old + bound[offset + len(new):]
    if sha256(original).hexdigest() != entry["originalSha256"] or sha256(bound).hexdigest() != entry["maffinetSha256"]:
        raise RuntimeError("HEV changes extend beyond the declared JNI class-name bytes")
    for signature in (b"TProxyStartService", b"(Ljava/lang/String;IZ)V",
                      b"TProxyStopService", b"()V", b"TProxyGetStats", b"()[J", b"JNI_OnLoad"):
        if signature not in bound:
            raise RuntimeError("HEV native method contract is missing")
    return bound


def write_atomic(path, data):
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False) as output:
            temporary = Path(output.name)
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        temporary.replace(path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true", help="Rebind the recognized original libraries in place")
    mode.add_argument("--check", action="store_true", help="Audit the shipped Maffinet binding (the default)")
    args = parser.parse_args()
    old, new, entries = load_contract()
    planned = []
    for entry in entries:
        path = ROOT / entry["path"]
        current = path.read_bytes()
        bound = audit_library(current, entry, old, new, require_bound=not args.apply)
        planned.append((path, current, bound))
    # Validate all four original/target libraries before changing any file.
    if args.apply:
        for path, current, bound in planned:
            if current != bound:
                write_atomic(path, bound)
    print("Verified four Maffinet HEV JNI bindings; inverse hashes prove all other native bytes unchanged.")


if __name__ == "__main__":
    main()
