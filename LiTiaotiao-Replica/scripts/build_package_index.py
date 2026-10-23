#!/usr/bin/env python3
"""Build a lookup table that maps LiTiaotiao rule hashes <-> app package names.

The LiTiaotiao rule files (AllRules.json / BasicRules.json / ExtendedRules.json) are
keyed by ``Java String.hashCode(packageName)`` (the key can be negative). The package
name itself is NOT stored in those files. However, the community rule repository
``LiTiaotiao-Custom-Rules`` organises every app into a folder named after its package,
so we can recover the package name (and the human readable app name from the readme
header) and build a reverse index.

This script walks the custom-rules repository, computes the Java hashCode for every
package folder, extracts the Chinese app name from the readme header, and writes
``data/package_index.json``.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# Default location of the LiTiaotiao-Custom-Rules checkout (overridable via argv[1]).
DEFAULT_RULES_DIR = (
    Path(r"D:/software/LiTiaotiao-main/LiTiaotiao-Custom-Rules-main")
    / "LiTiaotiao-Custom-Rules-main"
)
OUT_PATH = Path(__file__).resolve().parent.parent / "data" / "package_index.json"

APP_NAME_RE = re.compile(r"#\s+[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*\s*[（(](.+)[)）]")


def java_hashcode(text: str) -> int:
    """Reproduce Java's ``String.hashCode()`` (may return a negative int)."""
    h = 0
    for ch in text:
        code = ord(ch)
        if code > 0xFFFF:  # surrogate pair handling (rare for package names)
            h = (h * 31 + ((code >> 10) + 0xD800)) & 0xFFFFFFFF
            h = (h * 31 + ((code & 0x3FF) + 0xDC00)) & 0xFFFFFFFF
        else:
            h = (h * 31 + code) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def extract_app_name(readme_path: Path) -> str:
    try:
        text = readme_path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""
    for line in text.splitlines():
        m = APP_NAME_RE.match(line.strip())
        if m:
            return m.group(1).strip()
    return ""


def main() -> int:
    rules_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_RULES_DIR
    if not rules_dir.exists():
        print(f"[package_index] rules dir not found: {rules_dir}", file=sys.stderr)
        return 2

    entries = []
    for readme in rules_dir.rglob("readme.md"):
        # The package folder is the directory that directly contains readme.md.
        pkg_folder = readme.parent
        pkg_name = pkg_folder.name
        # Skip the per-letter / per-prefix index readme files (e.g. A/readme.md,
        # C/com/readme.md). App folders always contain a dot in their name.
        if "." not in pkg_name:
            continue
        app_name = extract_app_name(readme)
        entries.append(
            {
                "package": pkg_name,
                "hash": java_hashcode(pkg_name),
                "app_name": app_name,
            }
        )

    # Build lookup structures.
    by_hash: dict[str, dict] = {}
    by_package: dict[str, dict] = {}
    for e in entries:
        rec = {"package": e["package"], "app_name": e["app_name"]}
        by_hash[str(e["hash"])] = rec
        by_package[e["package"]] = {"hash": e["hash"], "app_name": e["app_name"]}

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(
        json.dumps(
            {
                "generated_from": str(rules_dir),
                "count": len(entries),
                "by_hash": by_hash,
                "by_package": by_package,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"[package_index] wrote {len(entries)} apps -> {OUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
