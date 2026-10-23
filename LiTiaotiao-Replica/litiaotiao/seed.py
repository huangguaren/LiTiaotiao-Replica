"""Seed the database from the analysed AllRules.json rule file."""
from __future__ import annotations

from pathlib import Path

from .config import config
from . import db
from .api import import_rules
from .logging_setup import get_logger

log = get_logger("seed")


def seed_from_file(path: str | None = None) -> dict:
    path = Path(path or config.SEED_ALL_RULES)
    if not path.exists():
        msg = f"seed file not found: {path}"
        log.warning(msg)
        return {"imported_apps": 0, "imported_rules": 0, "errors": [msg]}
    content = path.read_text(encoding="utf-8")
    db.init_db()
    from .api import ensure_default_settings

    ensure_default_settings()
    result = import_rules(content, "auto")
    log.info("seed complete: %s", result)
    return result
