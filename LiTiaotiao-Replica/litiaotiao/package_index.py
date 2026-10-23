"""Loads the generated package-index (hash <-> package <-> app name)."""
from __future__ import annotations

import json
from pathlib import Path

INDEX_PATH = (
    Path(__file__).resolve().parent.parent / "data" / "package_index.json"
)

_cache: dict | None = None


def _load() -> dict:
    global _cache
    if _cache is not None:
        return _cache
    if not INDEX_PATH.exists():
        _cache = {"by_hash": {}, "by_package": {}}
        return _cache
    _cache = json.loads(INDEX_PATH.read_text(encoding="utf-8"))
    return _cache


def lookup_by_hash(hash_value: int) -> dict:
    """Return ``{"package": str, "app_name": str}`` or empty dict if unknown."""
    data = _load()
    return data.get("by_hash", {}).get(str(hash_value), {})


def hash_for_package(package: str):
    data = _load()
    rec = data.get("by_package", {}).get(package)
    return rec.get("hash") if rec else None


def all_packages() -> list[dict]:
    data = _load()
    return [
        {"package": pkg, **rec}
        for pkg, rec in data.get("by_package", {}).items()
    ]
