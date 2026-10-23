"""Central configuration. Sensitive / environment specific values are read from
environment variables with sane defaults so the project runs out-of-the-box while
still supporting deployment overrides (no secrets are hard-coded in source).
"""
from __future__ import annotations

import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, default))
    except (TypeError, ValueError):
        return default


def _env_bool(name: str, default: bool = False) -> bool:
    v = os.environ.get(name)
    if v is None:
        return default
    return v.strip().lower() in ("1", "true", "yes", "on")


class Config:
    HOST = os.environ.get("LTT_HOST", "127.0.0.1")
    PORT = _env_int("LTT_PORT", 8000)
    DB_PATH = os.environ.get("LTT_DB_PATH", str(DATA_DIR / "litiaotiao.db"))
    LOG_PATH = os.environ.get("LTT_LOG_PATH", str(DATA_DIR / "litiaotiao.log"))

    # Locations of the analysed source material (override via env if moved).
    CUSTOM_RULES_DIR = os.environ.get(
        "LTT_CUSTOM_RULES_DIR",
        str(
            Path(r"D:/software/LiTiaotiao-main/LiTiaotiao-Custom-Rules-main")
            / "LiTiaotiao-Custom-Rules-main"
        ),
    )
    SEED_ALL_RULES = os.environ.get(
        "LTT_SEED_ALL_RULES",
        str(
            Path(r"D:/software/LiTiaotiao-main/LiTiaotiao-Custom-Rules-main")
            / "LiTiaotiao-Custom-Rules-main"
            / "AllRules.json"
        ),
    )
    DEBUG = _env_bool("LTT_DEBUG", False)
    VERSION = "2.2-replica"


config = Config()
