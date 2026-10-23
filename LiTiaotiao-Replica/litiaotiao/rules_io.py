"""Import / export of the LiTiaotiao rule file format.

The on-device rule files (``AllRules.json`` / ``BasicRules.json`` /
``ExtendedRules.json``) are a JSON **array** of single-key objects::

    [
      {"-1835777899": "{\"popup_rules\":[{\"id\":\"青少年模式\",\"action\":\"我知道了\"}]}"},
      ...
    ]

The key is ``Java String.hashCode(packageName)`` and the value is a *string* that
itself contains JSON describing the rule object. This module parses that format and
rebuilds it for export, while also converting between that form and the
relational representation used by the database.
"""
from __future__ import annotations

import json
import re
from typing import Any, Optional

# LiTiaotiao's rule files are occasionally produced with trailing commas inside the
# inner JSON string (e.g. ``[{"id":"x"},]``). The on-device parser is lenient; we
# mirror that by stripping trailing commas as a fallback so valid rules are never
# dropped on import.
_TRAILING_COMMA_RE = re.compile(r",\s*([}\]])")
_MD_FENCE_RE = re.compile(r"```[a-zA-Z0-9]*\s*(.*?)```", re.S)
_BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)
_LINE_COMMENT_RE = re.compile(r"(?m)^\s*//.*$")


def _clean_rule_text(s: str) -> str:
    """去掉 markdown 规则块里的注释与尾随逗号。

    仅剥离块注释与「行首」行注释，避免误删字符串内的 ``//``（如 URL）。
    """
    s = _BLOCK_COMMENT_RE.sub("", s)
    s = _LINE_COMMENT_RE.sub("", s)
    s = _TRAILING_COMMA_RE.sub(r"\1", s)
    return s


def extract_rule_object(text: str) -> Optional[dict]:
    """从「单个应用的规则对象」或「readme.md 代码块」中提取规则对象。

    用于按包名导入某个应用（原版 readme 里的 ``` 代码块并非严格 JSON，
    含注释与尾随逗号，这里做兼容解析）。提取失败返回 None。
    """
    if not text or not text.strip():
        return None
    t = text.strip()
    m = _MD_FENCE_RE.search(t)
    candidate = m.group(1) if m else t
    candidate = _clean_rule_text(candidate).strip()
    obj = _lenient_json(candidate)
    if isinstance(obj, dict) and any(
        k in obj for k in ("popup_rules", "keywords", "keywords_append")
    ):
        return obj
    return None


def _lenient_json(text: str):
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        try:
            return json.loads(_TRAILING_COMMA_RE.sub(r"\1", text))
        except json.JSONDecodeError:
            return None

KNOWN_PARAM_KEYS = {
    "click_way_popup",
    "search_times_popup",
    "delay",
    "delay_popup",
    "unite_popup_rules",
    "ltt_service",
    "reverse_traversal",
    "filter_collection_nodes",
    "filter_default",
    "filter_invisible_nodes",
    "filter_young_popups",
    "node_whitelist",
    "task_type",
    "texts_task2",
    "try_times",
    "timeout",
    "search_interval",
    "search_interval_popup",
}


def parse_all_rules_text(text: str) -> list[dict]:
    """Parse a LiTiaotiao rule JSON string into a list of normalized records.

    Returns ``[{"hash": int, "rule_obj": dict|None, "raw": str}, ...]``.
    Tolerates both the array form and a bare object whose keys are hashes.
    """
    if not text or not text.strip():
        return []
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        # 外层数组也可能带尾随逗号（如 ExtendedRules.json）
        data = json.loads(_TRAILING_COMMA_RE.sub(r"\1", text))
    items = data if isinstance(data, list) else [data]
    results: list[dict] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        for h, v in item.items():
            raw = v if isinstance(v, str) else json.dumps(v, ensure_ascii=False)
            rule_obj: Optional[dict] = None
            if isinstance(v, str):
                parsed = _lenient_json(v)
                rule_obj = parsed if isinstance(parsed, dict) else None
            elif isinstance(v, dict):
                rule_obj = v
            results.append({"hash": int(h), "rule_obj": rule_obj, "raw": raw})
    return results


def rule_obj_to_parts(rule_obj: Optional[dict]):
    """Split a rule object into (keywords, keywords_append, popup_rules, params)."""
    obj = rule_obj or {}
    keywords = list(obj.get("keywords") or [])
    keywords_append = list(obj.get("keywords_append") or [])
    popup_rules: list[dict] = []
    for r in obj.get("popup_rules") or []:
        if not isinstance(r, dict):
            continue
        rid = r.get("id")
        if rid is None:
            continue
        popup_rules.append(
            {
                "id": rid,
                "action": r.get("action", rid),
                "times": int(r.get("times", 1)),
            }
        )
    params = {
        k: v
        for k, v in obj.items()
        if k not in ("keywords", "keywords_append", "popup_rules")
    }
    return keywords, keywords_append, popup_rules, params


def parts_to_rule_obj(
    keywords: list,
    keywords_append: list,
    popup_rules: list,
    params: dict,
) -> dict:
    obj: dict[str, Any] = {}
    if keywords:
        obj["keywords"] = list(keywords)
    if keywords_append:
        obj["keywords_append"] = list(keywords_append)
    if popup_rules:
        obj["popup_rules"] = [
            {
                "id": r["id"],
                "action": r.get("action", r["id"]),
                "times": int(r.get("times", 1)),
            }
            for r in popup_rules
        ]
    for k, v in (params or {}).items():
        obj[k] = v
    return obj


def build_export_array(apps: list[dict]) -> str:
    """Build the LiTiaotiao rule JSON string from a list of stored app records.

    Each ``app`` record: ``{"hash": int, "keywords": [...], "keywords_append": [...],
    "popup_rules": [...], "params": {...}}``.
    """
    out: list[dict] = []
    for app in apps:
        obj = parts_to_rule_obj(
            app.get("keywords") or [],
            app.get("keywords_append") or [],
            app.get("popup_rules") or [],
            app.get("params") or {},
        )
        if not obj:
            continue
        inner = json.dumps(obj, ensure_ascii=False, separators=(",", ":"))
        out.append({str(app["hash"]): inner})
    return json.dumps(out, ensure_ascii=False, indent=2)
