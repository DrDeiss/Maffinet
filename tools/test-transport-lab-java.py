"""Run actual lab admission/helper validation code on the host; no Android/TUN claim."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    java_home = Path(os.environ.get("JAVA_HOME", ROOT / ".toolchain/jdk/jdk-21.0.12.1+1"))
    suffix = ".exe" if os.name == "nt" else ""
    output = ROOT / ".toolchain/rebuild-p01/lab-contract-classes"
    output.mkdir(parents=True, exist_ok=True)
    sources = [ROOT / "lab/transport/src/main/java/io/maffinet/lab/transport/LabStartTickets.java",
               ROOT / "lab/helper/src/main/java/io/maffinet/lab/helper/ProbeValidation.java",
               *sorted((ROOT / "lab/contract-fixtures").rglob("*.java"))]
    subprocess.run([str(java_home / "bin" / f"javac{suffix}"), "--release", "11", "-d", str(output),
                    *map(str, sources)], check=True)
    for contract in ("io.maffinet.lab.transport.StartTicketsContract", "io.maffinet.lab.helper.ProbeValidationContract"):
        subprocess.run([str(java_home / "bin" / f"java{suffix}"), "-cp", str(output), contract], check=True, timeout=15)


if __name__ == "__main__":
    main()
