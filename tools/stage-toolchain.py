"""Stage official portable Windows tools; never accept Android SDK licenses.

Usage: py -3 tools/stage-toolchain.py
Installs a checksum-verified portable JDK and downloads Android command-line
tools to .toolchain/downloads. SDK license texts are saved for human review.
The Android ZIP remains staged until the SDK agreement has been accepted.
"""

from concurrent.futures import ThreadPoolExecutor
from hashlib import sha1, sha256
import json
from pathlib import Path
from shutil import copyfileobj
import urllib.request
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".toolchain"
DOWNLOADS = TOOLS / "downloads"
REPOSITORY_URL = "https://dl.google.com/android/repository/repository2-1.xml"
JDK_URL = "https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.zip"
PACKAGES = (
    "cmdline-tools;latest", "platforms;android-36", "build-tools;36.0.0",
    "ndk;30.0.14904198", "cmake;3.22.1",
)


def download(url, destination, expected, algorithm):
    if destination.exists() and algorithm(destination.read_bytes()).hexdigest() == expected:
        return destination
    print(f"Downloading {destination.name}", flush=True)
    with urllib.request.urlopen(url, timeout=60) as response, destination.open("wb") as output:
        copyfileobj(response, output)
    actual = algorithm(destination.read_bytes()).hexdigest()
    if actual != expected:
        raise RuntimeError(f"Checksum mismatch for {destination.name}: {actual}")
    return destination


def stage_jdk():
    with urllib.request.urlopen(JDK_URL + ".sha256sum.txt", timeout=30) as response:
        checksum, filename = response.read().decode().strip().split(maxsplit=1)
    archive = download(JDK_URL, DOWNLOADS / filename, checksum, sha256)
    with ZipFile(archive) as contents:
        # Official archives are verified before extracting, and targets stay
        # inside the workspace-owned toolchain directory.
        destination = (TOOLS / "jdk").resolve()
        for member in contents.namelist():
            target = (destination / member).resolve()
            if not target.is_relative_to(destination):
                raise RuntimeError("JDK ZIP contains an unsafe path")
        contents.extractall(destination)
    java = next((TOOLS / "jdk").glob("*/bin/java.exe"))
    print(f"Portable JAVA_HOME: {java.parent.parent}", flush=True)


def stage_android():
    with urllib.request.urlopen(REPOSITORY_URL, timeout=30) as response:
        xml = response.read()
    (TOOLS / "repository.xml").write_bytes(xml)
    repository = ET.fromstring(xml)
    review = TOOLS / "licenses-to-review"
    review.mkdir(exist_ok=True)
    for entry in repository.findall("license"):
        (review / f"{entry.attrib['id']}.txt").write_text(entry.text or "", encoding="utf-8")
    packages = []
    for package_id in PACKAGES:
        candidates = [entry for entry in repository.findall("remotePackage")
                      if entry.attrib["path"] == package_id]
        # Android 36 extension releases use the same package ID. Preserve the
        # plain API 36 platform rather than installing an extension variant.
        if package_id == "platforms;android-36":
            candidates = [entry for entry in candidates
                          if entry.findtext("display-name") == "Android SDK Platform 36"]
        if not candidates:
            raise RuntimeError(f"Official repository does not contain {package_id}")
        entry = candidates[-1]
        archive = next(archive for archive in entry.findall("archives/archive")
                       if archive.findtext("host-os") in (None, "windows"))
        packages.append({
            "package": package_id,
            "display_name": entry.findtext("display-name"),
            "license": entry.find("uses-license").attrib["ref"],
            "url": "https://dl.google.com/android/repository/" + archive.findtext("complete/url"),
            "sha1": archive.findtext("complete/checksum"),
            "bytes": int(archive.findtext("complete/size")),
        })
    (TOOLS / "android-package-plan.json").write_text(json.dumps(packages, indent=2), encoding="utf-8")
    command_tools = packages[0]
    filename = command_tools["url"].rsplit("/", 1)[-1]
    download(command_tools["url"], DOWNLOADS / filename, command_tools["sha1"], sha1)
    print(f"Android tools staged. Review agreements in {review}; no license was accepted.", flush=True)


if __name__ == "__main__":
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=2) as executor:
        tasks = [executor.submit(stage_jdk), executor.submit(stage_android)]
        for task in tasks:
            task.result()
