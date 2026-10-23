"""SQLite data layer.

A thin, dependency-free wrapper around ``sqlite3``. The schema stores apps,
their rules (popup / keyword), per-app parameters and a global settings / log
tables. Connections are opened per-request which is perfectly adequate for a
single-user local tool and keeps the code trivially thread-safe.
"""
from __future__ import annotations

import sqlite3
from typing import Iterable, Sequence

from .config import config
from .logging_setup import get_logger

log = get_logger("db")

SCHEMA = """
CREATE TABLE IF NOT EXISTS apps (
    hash          INTEGER PRIMARY KEY,
    package       TEXT,
    display_name  TEXT,
    enabled       INTEGER NOT NULL DEFAULT 1,
    whitelisted   INTEGER NOT NULL DEFAULT 0,
    source        TEXT NOT NULL DEFAULT 'manual',
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rules (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    app_hash    INTEGER NOT NULL,
    rule_type   TEXT NOT NULL,            -- 'popup' | 'keyword' | 'keyword_append'
    match_id    TEXT NOT NULL,
    action      TEXT NOT NULL,
    times       INTEGER NOT NULL DEFAULT 1,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (app_hash) REFERENCES apps(hash) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_rules_app ON rules(app_hash);

CREATE TABLE IF NOT EXISTS app_params (
    app_hash INTEGER NOT NULL,
    key      TEXT NOT NULL,
    value    TEXT,
    PRIMARY KEY (app_hash, key),
    FOREIGN KEY (app_hash) REFERENCES apps(hash) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS skip_logs (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    app_hash      INTEGER,
    rule_id       INTEGER,
    rule_type     TEXT,
    trigger_text  TEXT,
    action_kind   TEXT,
    action_target TEXT,
    created_at    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_logs_time ON skip_logs(created_at);
"""


def get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(config.DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db() -> None:
    conn = get_conn()
    try:
        conn.executescript(SCHEMA)
        conn.commit()
    finally:
        conn.close()
    log.info("database initialised at %s", config.DB_PATH)


def query(sql: str, params: Sequence = ()) -> list[dict]:
    conn = get_conn()
    try:
        cur = conn.execute(sql, params)
        return [dict(row) for row in cur.fetchall()]
    finally:
        conn.close()


def execute(sql: str, params: Sequence = ()) -> int:
    conn = get_conn()
    try:
        cur = conn.execute(sql, params)
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def execute_many(sql: str, seq: Iterable[Sequence]) -> None:
    conn = get_conn()
    try:
        conn.executemany(sql, seq)
        conn.commit()
    finally:
        conn.close()
