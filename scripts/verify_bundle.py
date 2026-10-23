#!/usr/bin/env python3
"""Inspects a Maven Central bundle produced with `-Pcentral -DskipUpload=true` (nothing is uploaded).

Checks: the five expected files per artifact, a .asc signature and checksums for each,
checksum correctness, POM metadata required by Central, the bytecode level of the jar
(Java 17 = class file major 61) and, if gpg is available, that every signature verifies.

Usage: scripts/verify_bundle.py target/central-publishing/central-bundle.zip [--no-gpg]
"""
import hashlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

REQUIRED_POM_TAGS = ["name", "description", "url", "licenses", "developers", "scm"]


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    use_gpg = "--no-gpg" not in sys.argv and shutil.which("gpg") is not None
    if len(args) != 1:
        print(__doc__)
        return 2
    errors = []
    with zipfile.ZipFile(args[0]) as z:
        names = set(z.namelist())
        poms = sorted(n for n in names if n.endswith(".pom"))
        if len(poms) != 1:
            print("expected exactly one .pom, found", poms)
            return 1
        base = poms[0][: -len(".pom")]
        files = {
            "pom": base + ".pom",
            "jar": base + ".jar",
            "sources": base + "-sources.jar",
            "javadoc": base + "-javadoc.jar",
        }
        for kind, name in files.items():
            if name not in names:
                errors.append(f"missing {name}")
                continue
            data = z.read(name)
            if name + ".asc" not in names:
                errors.append(f"missing signature for {name}")
            for algo in ("md5", "sha1", "sha256", "sha512"):
                sidecar = f"{name}.{algo}"
                if sidecar not in names:
                    errors.append(f"missing {algo} for {name}")
                elif z.read(sidecar).decode().split()[0].strip() != hashlib.new(algo, data).hexdigest():
                    errors.append(f"wrong {algo} for {name}")
        pom = z.read(files["pom"]).decode("utf-8") if files["pom"] in names else ""
        for tag in REQUIRED_POM_TAGS:
            if not re.search(rf"<{tag}[ >]", pom):
                errors.append(f"POM lacks <{tag}>")
        if "SNAPSHOT" in files["pom"]:
            errors.append("bundle contains a SNAPSHOT version")
        if files["jar"] in names:
            with tempfile.TemporaryDirectory() as tmp:
                jar = Path(tmp) / "x.jar"
                jar.write_bytes(z.read(files["jar"]))
                with zipfile.ZipFile(jar) as jz:
                    classes = [n for n in jz.namelist() if n.endswith(".class")]
                    majors = {jz.read(n)[7] for n in classes}
                    if majors != {61}:
                        errors.append(f"class file major versions {sorted(majors)}, expected only 61 (Java 17)")
                    print(f"jar: {len(classes)} classes, major version(s) {sorted(majors)}")
                    if "META-INF/MANIFEST.MF" in jz.namelist() and b"Automatic-Module-Name" not in jz.read("META-INF/MANIFEST.MF"):
                        errors.append("Automatic-Module-Name missing in MANIFEST.MF")
        if use_gpg:
            with tempfile.TemporaryDirectory() as tmp:
                for name in files.values():
                    if name in names and name + ".asc" in names:
                        f = Path(tmp) / Path(name).name
                        f.write_bytes(z.read(name))
                        Path(str(f) + ".asc").write_bytes(z.read(name + ".asc"))
                        r = subprocess.run(["gpg", "--batch", "--verify", str(f) + ".asc", str(f)], capture_output=True)
                        if r.returncode != 0:
                            errors.append(f"signature of {Path(name).name} does not verify with the local keyring")
        else:
            print("gpg verification skipped")
    for e in errors:
        print("ERROR:", e)
    print("bundle OK" if not errors else f"{len(errors)} problem(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
