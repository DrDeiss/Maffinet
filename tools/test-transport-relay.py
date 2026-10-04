"""Compile the actual Java SOCKS lab relay against host fault seams and exercise sockets."""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
def main():
    java_home = Path(os.environ.get("JAVA_HOME", ROOT / ".toolchain/jdk/jdk-21.0.12.1+1"))
    suffix = ".exe" if os.name == "nt" else ""
    javac, java = java_home / "bin" / f"javac{suffix}", java_home / "bin" / f"java{suffix}"
    output = ROOT / ".toolchain/rebuild-p01/relay-classes"
    output.mkdir(parents=True, exist_ok=True)
    sources = [ROOT / "lab/transport/src/main/java/io/maffinet/lab/transport/LabSocksServer.java",
               *sorted((ROOT / "lab/host-fixtures").rglob("*.java"))]
    subprocess.run([str(javac), "--release", "11", "-d", str(output), *map(str, sources)], check=True)
    subprocess.run([str(java), "-cp", str(output), "io.maffinet.lab.transport.RelayContract"], check=True, timeout=120)

if __name__ == "__main__":
    main()
