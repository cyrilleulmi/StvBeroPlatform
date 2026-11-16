#!/usr/bin/env python3
"""Checks relative links in all Markdown files of the repository (NF-40, F-09).

Usage: python3 scripts/check-links.py
Exit code 0 if every relative link points to an existing file and, for links with
#anchor into a Markdown file, to an existing heading. External links (http, mailto)
are not checked. Lists every broken link otherwise.
"""
import os
import re
import subprocess
import sys

ROOT = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)|!\[[^\]]*\]\(([^)\s]+)\)")
FENCE = re.compile(r"^\s*(```|~~~)")


def files():
    out = subprocess.check_output(["git", "ls-files", "-co", "--exclude-standard", "*.md"], cwd=ROOT, text=True)
    return [f for f in out.splitlines() if "node_modules/" not in f and os.path.exists(os.path.join(ROOT, f))]


def slug(heading):
    """GitHub heading anchor."""
    h = re.sub(r"<[^>]+>", "", heading).strip().lower()
    h = re.sub(r"[`*_~]|\[([^\]]*)\]\([^)]*\)", lambda m: m.group(1) or "", h)
    h = re.sub(r"[^\w\- ]", "", h, flags=re.UNICODE)
    return h.replace(" ", "-")


_anchors = {}


def anchors(path):
    if path not in _anchors:
        found, seen, fence = set(), {}, False
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if FENCE.match(line):
                    fence = not fence
                    continue
                if fence:
                    continue
                m = re.match(r"^#{1,6}\s+(.*?)\s*#*\s*$", line)
                if m:
                    s = slug(m.group(1))
                    n = seen.get(s, 0)
                    found.add(s if n == 0 else f"{s}-{n}")
                    seen[s] = n + 1
                for a in re.findall(r'<a\s+(?:name|id)="([^"]+)"', line):
                    found.add(a)
        _anchors[path] = found
    return _anchors[path]


def main():
    broken = []
    for rel in files():
        src = os.path.join(ROOT, rel)
        fence = False
        with open(src, encoding="utf-8") as fh:
            for no, line in enumerate(fh, 1):
                if FENCE.match(line):
                    fence = not fence
                    continue
                if fence:
                    continue
                line = re.sub(r"`[^`]*`", "", line)
                for m in LINK.finditer(line):
                    target = m.group(1) or m.group(2)
                    if re.match(r"^[a-z][a-z0-9+.-]*:", target, re.I):
                        continue
                    path, _, anchor = target.partition("#")
                    dest = src if not path else os.path.normpath(os.path.join(os.path.dirname(src), path))
                    if not os.path.exists(dest):
                        broken.append(f"{rel}:{no}: {target} (file missing)")
                    elif anchor and dest.endswith(".md") and anchor not in anchors(dest):
                        broken.append(f"{rel}:{no}: {target} (anchor missing)")
    for b in broken:
        print(b)
    print(f"{len(broken)} broken link(s)" if broken else "All relative links OK.")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
