#!/usr/bin/env python3
"""Keeps the Java code blocks of the READMEs identical to the compiled examples.

In a README, put a marker line right before a ```java block:

    <!-- snippet:plain-java/QuickStart.java -->            (default snippet)
    <!-- snippet:plain-java/AdvancedUsage.java#async -->    (named snippet)

In the Java source, delimit the code with `// snippet:start` / `// snippet:end`
(or `// snippet:NAME:start` / `// snippet:NAME:end`).

Usage:
    scripts/readme_snippets.py check [README.md ...]    exit 1 if any block differs
    scripts/readme_snippets.py update [README.md ...]   rewrite the blocks from the sources
"""
import re
import sys
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MARKER = re.compile(r"^<!-- snippet:(?P<file>[^#\s]+)(?:#(?P<name>\w+))? -->$")


def extract(spec_file: str, name: str | None) -> str:
    project, filename = spec_file.split("/", 1)
    path = ROOT / "examples" / project / "src" / "main" / "java" / "example" / filename
    lines = path.read_text(encoding="utf-8").splitlines()
    prefix = f"snippet:{name}" if name else "snippet"
    start = f"// {prefix}:start"
    end = f"// {prefix}:end"
    try:
        i = next(n for n, l in enumerate(lines) if l.strip() == start)
        j = next(n for n, l in enumerate(lines) if n > i and l.strip() == end)
    except StopIteration:
        raise SystemExit(f"snippet '{prefix}' not found in {path}")
    return textwrap.dedent("\n".join(lines[i + 1 : j])).strip("\n")


def process(readme: Path, update: bool) -> list[str]:
    lines = readme.read_text(encoding="utf-8").split("\n")
    out, problems, n = [], [], 0
    while n < len(lines):
        line = lines[n]
        out.append(line)
        m = MARKER.match(line.strip())
        if m and n + 1 < len(lines) and lines[n + 1].startswith("```java"):
            expected = extract(m["file"], m["name"])
            close = next(k for k in range(n + 2, len(lines)) if lines[k].startswith("```"))
            current = "\n".join(lines[n + 2 : close])
            if current != expected:
                problems.append(f"{readme.name}:{n + 2}: block differs from {m['file']}#{m['name'] or 'default'}")
            out.append("```java")
            out.extend(expected.split("\n"))
            out.append("```")
            n = close + 1
            continue
        n += 1
    if update:
        readme.write_text("\n".join(out), encoding="utf-8")
        return []
    return problems


def main() -> int:
    if len(sys.argv) < 2 or sys.argv[1] not in ("check", "update"):
        print(__doc__)
        return 2
    update = sys.argv[1] == "update"
    files = [Path(a) for a in sys.argv[2:]] or [ROOT / "README.md", ROOT / "README.es.md"]
    problems = []
    for f in files:
        if f.exists():
            problems += process(f, update)
    for p in problems:
        print("MISMATCH:", p)
    if problems:
        print("Run: scripts/readme_snippets.py update")
        return 1
    print(("updated " if update else "ok: ") + ", ".join(f.name for f in files if f.exists()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
