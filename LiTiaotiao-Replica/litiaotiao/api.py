"""HTTP API handlers for the LiTiaotiao replica backend.

Every handler returns ``(status_code, payload)`` where *payload* is either a
JSON-serialisable ``dict`` or a :class:`Raw` value (for streaming rule-file
downloads). Errors are raised as :class:`ApiError` and turned into JSON by the
server.
"""
from __future__ import annotations

import json
from dataclasses import asdict
from datetime import datetime, timezone
from typing import Any, Optional

from .config import config
from . import db, engine, package_index, rules_io
from .hashutil import java_hashcode
from .logging_setup import get_logger

log = get_logger("api")


class ApiError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


class Raw:
    def __init__(self, text: str, content_type: str = "application/json; charset=utf-8"):
        self.text = text
        self.content_type = content_type


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #
def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


DEFAULT_SETTINGS = {
    "is_main_switch_on": True,
    "hide_toast": False,
    "toast_text": "",
    "show_all_apps": True,
    "whitelist_all_apps": False,
    "filter_invisible_nodes": True,
    "filter_young_popups": True,
    "reverse_traversal": False,
    "is_user_check": False,
    # 全局规则：对所有包生效，专治小程序 / 异包开屏广告
    "global_rules_enabled": True,
    "global_rules": {
        "keywords": ["跳过广告", "关闭广告", "跳过"],
        "keywords_append": [],
        "popup_rules": [],
    },
    # 小程序宿主：命中这些包且找不到跳过按钮时，可选择自动返回
    "mini_program_hosts": [],
    "mini_program_auto_back": False,
    "mini_program_back_delay": 0,
}

META = {
    "app_name": "李跳跳",
    "version": config.VERSION,
    "package": "hello.litiaotiao.app",
    "accessibility_service_description": (
        "我的作用：旨在帮助用户跳过那些被工信部提名的，影响用户体验的开屏广告和弹窗。\n"
        "--------------\n"
        "用户须知：李跳跳是一个不联网的单机应用，不会也没办法泄露你的任何隐私。"
    ),
    "source": "高保真复刻 · Web 工作台",
}


def _coerce_setting(value: str) -> Any:
    try:
        return json.loads(value)
    except (TypeError, ValueError):
        return value


def ensure_default_settings() -> None:
    existing = {r["key"] for r in db.query("SELECT key FROM settings")}
    for k, v in DEFAULT_SETTINGS.items():
        if k not in existing:
            db.execute(
                "INSERT INTO settings(key, value) VALUES (?, ?)",
                (k, json.dumps(v)),
            )


def get_all_settings() -> dict:
    rows = db.query("SELECT key, value FROM settings")
    out: dict[str, Any] = {}
    for r in rows:
        out[r["key"]] = _coerce_setting(r["value"])
    for k, v in DEFAULT_SETTINGS.items():
        out.setdefault(k, v)
    return out


def update_settings(body: dict) -> dict:
    for k, v in body.items():
        db.execute(
            "INSERT INTO settings(key, value) VALUES (?, ?) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            (k, json.dumps(v)),
        )
    return get_all_settings()


def get_global_config() -> dict:
    s = get_all_settings()
    return {
        "enabled": bool(s.get("global_rules_enabled", True)),
        "rules": s.get("global_rules") or {},
        "mini_program": {
            "hosts": s.get("mini_program_hosts") or [],
            "auto_back": bool(s.get("mini_program_auto_back", False)),
            "back_delay": int(s.get("mini_program_back_delay", 0) or 0),
        },
    }


def update_global_config(body: dict) -> dict:
    upd: dict[str, Any] = {}
    if "enabled" in body:
        upd["global_rules_enabled"] = bool(body["enabled"])
    if "rules" in body:
        upd["global_rules"] = body["rules"] or {}
    mp = body.get("mini_program")
    if isinstance(mp, dict):
        if "hosts" in mp:
            upd["mini_program_hosts"] = mp.get("hosts") or []
        if "auto_back" in mp:
            upd["mini_program_auto_back"] = bool(mp["auto_back"])
        if "back_delay" in mp:
            upd["mini_program_back_delay"] = int(mp["back_delay"] or 0)
    if upd:
        update_settings(upd)
    return get_global_config()


def load_global_rules() -> Optional[dict]:
    s = get_all_settings()
    if not s.get("global_rules_enabled", True):
        return None
    gr = s.get("global_rules") or {}
    if not isinstance(gr, dict):
        return None
    return {
        "keywords": gr.get("keywords") or [],
        "keywords_append": gr.get("keywords_append") or [],
        "popup_rules": gr.get("popup_rules") or [],
        "params": gr.get("params") or {},
    }


def load_mini_program() -> dict:
    s = get_all_settings()
    return {
        "hosts": s.get("mini_program_hosts") or [],
        "auto_back": bool(s.get("mini_program_auto_back", False)),
        "back_delay": int(s.get("mini_program_back_delay", 0) or 0),
    }


def load_app_rules(app_hash: int) -> dict:
    rows = db.query(
        "SELECT rule_type, match_id, action, times FROM rules WHERE app_hash=? "
        "ORDER BY sort_order, id",
        (app_hash,),
    )
    keywords: list[str] = []
    keywords_append: list[str] = []
    popup: list[dict] = []
    for r in rows:
        if r["rule_type"] == "keyword":
            keywords.append(r["match_id"])
        elif r["rule_type"] == "keyword_append":
            keywords_append.append(r["match_id"])
        else:
            popup.append(
                {"id": r["match_id"], "action": r["action"], "times": r["times"]}
            )
    params_rows = db.query(
        "SELECT key, value FROM app_params WHERE app_hash=?", (app_hash,)
    )
    params = {r["key"]: _coerce_setting(r["value"]) for r in params_rows}
    return {
        "keywords": keywords,
        "keywords_append": keywords_append,
        "popup_rules": popup,
        "params": params,
    }


# --------------------------------------------------------------------------- #
# apps
# --------------------------------------------------------------------------- #
def list_apps(query: dict) -> dict:
    clauses: list[str] = []
    params: list[Any] = []
    q = (query.get("q") or "").strip()
    if q:
        clauses.append("(package LIKE ? OR display_name LIKE ? OR CAST(hash AS TEXT)=?)")
        params += [f"%{q}%", f"%{q}%", q]
    if query.get("enabled") in ("0", "1"):
        clauses.append("enabled=?")
        params.append(int(query["enabled"]))
    if query.get("whitelisted") in ("0", "1"):
        clauses.append("whitelisted=?")
        params.append(int(query["whitelisted"]))

    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    total = db.query(f"SELECT COUNT(*) AS c FROM apps {where}", params)[0]["c"]

    limit = int(query.get("limit") or 50)
    offset = int(query.get("offset") or 0)
    limit = max(1, min(limit, 500))
    rows = db.query(
        "SELECT a.*, (SELECT COUNT(*) FROM rules r WHERE r.app_hash=a.hash) AS rule_count "
        f"FROM apps a {where} ORDER BY a.display_name COLLATE NOCASE LIMIT ? OFFSET ?",
        params + [limit, offset],
    )
    items = []
    for r in rows:
        item = dict(r)
        item["app_name"] = package_index.lookup_by_hash(r["hash"]).get("app_name") or ""
        items.append(item)
    return {"total": total, "limit": limit, "offset": offset, "items": items}


def get_app(app_hash: int) -> dict:
    rows = db.query("SELECT * FROM apps WHERE hash=?", (app_hash,))
    if not rows:
        raise ApiError(404, f"app {app_hash} not found")
    app = dict(rows[0])
    info = package_index.lookup_by_hash(app_hash)
    app["app_name"] = info.get("app_name") or ""
    if not app["package"]:
        app["package"] = info.get("package") or ""
    rules = db.query(
        "SELECT id, rule_type, match_id, action, times FROM rules "
        "WHERE app_hash=? ORDER BY sort_order, id",
        (app_hash,),
    )
    params_rows = db.query(
        "SELECT key, value FROM app_params WHERE app_hash=?", (app_hash,)
    )
    app["rules"] = [dict(r) for r in rules]
    app["params"] = {r["key"]: _coerce_setting(r["value"]) for r in params_rows}
    return app


def upsert_app(
    app_hash: int,
    *,
    enabled: bool = True,
    whitelisted: bool = False,
    display_name: Optional[str] = None,
    source: str = "manual",
) -> dict:
    info = package_index.lookup_by_hash(app_hash)
    pkg = (display_name is None and info.get("package")) or ""
    name = display_name or info.get("app_name") or f"未知应用 {app_hash}"
    ts = now_iso()
    existing = db.query("SELECT hash FROM apps WHERE hash=?", (app_hash,))
    if existing:
        db.execute(
            "UPDATE apps SET enabled=?, whitelisted=?, display_name=?, updated_at=? "
            "WHERE hash=?",
            (int(enabled), int(whitelisted), name, ts, app_hash),
        )
    else:
        db.execute(
            "INSERT INTO apps(hash, package, display_name, enabled, whitelisted, source, created_at, updated_at) "
            "VALUES (?,?,?,?,?,?,?,?)",
            (
                app_hash,
                pkg,
                name,
                int(enabled),
                int(whitelisted),
                source,
                ts,
                ts,
            ),
        )
    return get_app(app_hash)


def update_app(app_hash: int, body: dict) -> dict:
    fields = []
    params: list[Any] = []
    if "enabled" in body:
        fields.append("enabled=?")
        params.append(int(bool(body["enabled"])))
    if "whitelisted" in body:
        fields.append("whitelisted=?")
        params.append(int(bool(body["whitelisted"])))
    if "display_name" in body:
        fields.append("display_name=?")
        params.append(body["display_name"])
    if fields:
        fields.append("updated_at=?")
        params.append(now_iso())
        params.append(app_hash)
        db.execute(
            f"UPDATE apps SET {', '.join(fields)} WHERE hash=?", params
        )
    return get_app(app_hash)


def delete_app(app_hash: int) -> dict:
    db.execute("DELETE FROM apps WHERE hash=?", (app_hash,))
    return {"deleted": app_hash}


def create_app(body: dict) -> dict:
    """手动新增一个应用（按包名计算 hash）。用于给社区规则未覆盖的 App 自定义规则。"""
    pkg = (body.get("package") or "").strip()
    if not pkg:
        raise ApiError(400, "包名不能为空")
    h = java_hashcode(pkg)
    existing = db.query("SELECT hash FROM apps WHERE hash=?", (h,))
    name = (
        (body.get("display_name") or "").strip()
        or package_index.lookup_by_hash(h).get("app_name")
        or pkg
    )
    upsert_app(h, display_name=name, source="manual")
    db.execute("UPDATE apps SET package=? WHERE hash=?", (pkg, h))
    app = get_app(h)
    app["created"] = not bool(existing)
    return app


# --------------------------------------------------------------------------- #
# rules
# --------------------------------------------------------------------------- #
def add_rule(app_hash: int, body: dict) -> dict:
    rule_type = body.get("rule_type", "popup")
    if rule_type not in ("popup", "keyword", "keyword_append"):
        raise ApiError(400, "rule_type must be popup | keyword | keyword_append")
    match_id = (body.get("match_id") or "").strip()
    if not match_id:
        raise ApiError(400, "match_id is required")
    action = body.get("action") or match_id
    times = int(body.get("times", 1) if body.get("times") is not None else 1)
    cnt = db.query(
        "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM rules WHERE app_hash=?",
        (app_hash,),
    )[0]["n"]
    rid = db.execute(
        "INSERT INTO rules(app_hash, rule_type, match_id, action, times, sort_order) "
        "VALUES (?,?,?,?,?,?)",
        (app_hash, rule_type, match_id, action, times, cnt),
    )
    rows = db.query(
        "SELECT id, rule_type, match_id, action, times FROM rules WHERE id=?", (rid,)
    )
    return dict(rows[0])


def update_rule(app_hash: int, rid: int, body: dict) -> dict:
    fields = []
    params: list[Any] = []
    if "rule_type" in body and body["rule_type"] in (
        "popup",
        "keyword",
        "keyword_append",
    ):
        fields.append("rule_type=?")
        params.append(body["rule_type"])
    if "match_id" in body:
        fields.append("match_id=?")
        params.append(body["match_id"])
    if "action" in body:
        fields.append("action=?")
        params.append(body["action"])
    if "times" in body:
        fields.append("times=?")
        params.append(int(body["times"]))
    if not fields:
        raise ApiError(400, "nothing to update")
    params.append(rid)
    params.append(app_hash)
    db.execute(
        f"UPDATE rules SET {', '.join(fields)} WHERE id=? AND app_hash=?", params
    )
    rows = db.query(
        "SELECT id, rule_type, match_id, action, times FROM rules WHERE id=?",
        (rid,),
    )
    if not rows:
        raise ApiError(404, "rule not found")
    return dict(rows[0])


def delete_rule(app_hash: int, rid: int) -> dict:
    db.execute(
        "DELETE FROM rules WHERE id=? AND app_hash=?", (rid, app_hash)
    )
    return {"deleted": rid}


def set_app_param(app_hash: int, key: str, value: Any) -> dict:
    db.execute(
        "INSERT INTO app_params(app_hash, key, value) VALUES (?,?,?) "
        "ON CONFLICT(app_hash, key) DO UPDATE SET value=excluded.value",
        (app_hash, key, json.dumps(value)),
    )
    return {key: value}


# --------------------------------------------------------------------------- #
# import / export
# --------------------------------------------------------------------------- #
def import_rules(
    content: str, fmt: str = "auto", package: Optional[str] = None
) -> dict:
    """导入规则。

    - 不传 ``package``：解析官方 AllRules/BasicRules/ExtendedRules 数组格式（键为 hash）。
    - 传 ``package``：把内容当作「单个应用的规则对象」或「readme.md 代码块」，
      用 package 计算 Java hashCode 后导入该应用（支持从社区文档复制规则）。
    """
    single_name = None
    single_pkg = None
    if package and package.strip():
        obj = rules_io.extract_rule_object(content)
        if obj is None:
            raise ApiError(
                400, "无法从内容中解析出规则对象（请确认是规则 JSON 或 readme 代码块）"
            )
        single_pkg = package.strip()
        h = java_hashcode(single_pkg)
        single_name = package_index.lookup_by_hash(h).get("app_name") or single_pkg
        records = [{"hash": h, "rule_obj": obj, "raw": ""}]
    else:
        try:
            records = rules_io.parse_all_rules_text(content)
        except json.JSONDecodeError as e:
            raise ApiError(400, f"JSON 解析失败: {e}")

    imported_apps = 0
    imported_rules = 0
    errors: list[str] = []
    for rec in records:
        h = rec["hash"]
        obj = rec["rule_obj"]
        if obj is None:
            errors.append(f"hash {h}: 规则内容不是合法 JSON，已跳过")
            continue
        keywords, keywords_append, popup, params = rules_io.rule_obj_to_parts(obj)
        upsert_app(h, display_name=single_name, source="import")
        if single_pkg:
            db.execute(
                "UPDATE apps SET package=? WHERE hash=? AND (package IS NULL OR package='')",
                (single_pkg, h),
            )
        db.execute("DELETE FROM rules WHERE app_hash=?", (h,))
        db.execute("DELETE FROM app_params WHERE app_hash=?", (h,))
        order = 0
        for kw in keywords:
            db.execute(
                "INSERT INTO rules(app_hash, rule_type, match_id, action, times, sort_order) "
                "VALUES (?,?,?,?,?,?)",
                (h, "keyword", kw, kw, 1, order),
            )
            order += 1
        for kw in keywords_append:
            db.execute(
                "INSERT INTO rules(app_hash, rule_type, match_id, action, times, sort_order) "
                "VALUES (?,?,?,?,?,?)",
                (h, "keyword_append", kw, kw, 1, order),
            )
            order += 1
        for pr in popup:
            db.execute(
                "INSERT INTO rules(app_hash, rule_type, match_id, action, times, sort_order) "
                "VALUES (?,?,?,?,?,?)",
                (h, "popup", pr["id"], pr["action"], pr["times"], order),
            )
            order += 1
        for k, v in params.items():
            db.execute(
                "INSERT INTO app_params(app_hash, key, value) VALUES (?,?,?) "
                "ON CONFLICT(app_hash, key) DO UPDATE SET value=excluded.value",
                (h, k, json.dumps(v)),
            )
        imported_apps += 1
        imported_rules += len(keywords) + len(keywords_append) + len(popup)
    return {
        "imported_apps": imported_apps,
        "imported_rules": imported_rules,
        "errors": errors,
        "total_records": len(records),
    }


def _collect_export_apps(scope: str) -> list[dict]:
    if scope.startswith("single:"):
        try:
            h = int(scope.split(":", 1)[1])
        except ValueError:
            return []
        apps = [{"hash": h}]
    elif scope == "enabled":
        apps = db.query("SELECT hash FROM apps WHERE enabled=1")
    else:
        apps = db.query("SELECT hash FROM apps")
    out = []
    for r in apps:
        h = r["hash"]
        rules = load_app_rules(h)
        if not (
            rules["keywords"] or rules["keywords_append"] or rules["popup_rules"]
        ):
            continue
        out.append(
            {
                "hash": h,
                "keywords": rules["keywords"],
                "keywords_append": rules["keywords_append"],
                "popup_rules": rules["popup_rules"],
                "params": rules["params"],
            }
        )
    return out


def export_rules(scope: str = "all") -> Raw:
    apps = _collect_export_apps(scope)
    text = rules_io.build_export_array(apps)
    return Raw(text, "application/json; charset=utf-8")


# --------------------------------------------------------------------------- #
# simulation
# --------------------------------------------------------------------------- #
def simulate(body: dict) -> dict:
    screen_raw = body.get("screen")
    if not isinstance(screen_raw, dict):
        raise ApiError(400, "缺少 screen（无障碍节点树）")
    screen = engine.UiNode.from_dict(screen_raw)

    app_hash: Optional[int] = body.get("hash")
    app_rules: dict = {"keywords": [], "keywords_append": [], "popup_rules": [], "params": {}}

    if app_hash is not None:
        app_hash = int(app_hash)
        stored = load_app_rules(app_hash)
        app_rules.update(stored)

    # Optional inline rule object (overrides / supplements the stored rules).
    inline = body.get("rules")
    if isinstance(inline, dict):
        kw, kwa, popup, params = rules_io.rule_obj_to_parts(inline)
        if kw:
            app_rules["keywords"] = kw
        if kwa:
            app_rules["keywords_append"] = kwa
        if popup:
            app_rules["popup_rules"] = popup
        if params:
            app_rules["params"].update(params)

    package = body.get("package")
    if not package and app_hash is not None:
        rows = db.query("SELECT package FROM apps WHERE hash=?", (app_hash,))
        if rows:
            package = rows[0]["package"]
    use_global = body.get("use_global", True)
    global_rules = load_global_rules() if use_global else None
    mini_program = load_mini_program()

    if not (
        app_rules["keywords"] or app_rules["keywords_append"] or app_rules["popup_rules"]
    ) and not global_rules:
        raise ApiError(400, "未提供规则：请指定 hash、传入 rules，或开启全局规则")

    result = engine.evaluate_screen(
        app_rules,
        screen,
        app_hash=app_hash,
        opts={
            "global_rules": global_rules,
            "package": package,
            "mini_program": mini_program,
        },
    )

    # Persist a skip log entry for each performed click (feeds the stats panel).
    ts = now_iso()
    for hit in result.hits:
        for act in hit["actions"]:
            if act["kind"] in ("node", "back", "bounds"):
                db.execute(
                    "INSERT INTO skip_logs(app_hash, rule_type, trigger_text, action_kind, action_target, created_at) "
                    "VALUES (?,?,?,?,?,?)",
                    (
                        app_hash,
                        hit["rule_type"],
                        hit["match_id"],
                        act["kind"],
                        act.get("target_text") or act.get("target_id") or act.get("bounds") or "",
                        ts,
                    ),
                )
    return asdict(result)


# --------------------------------------------------------------------------- #
# stats / logs / meta
# --------------------------------------------------------------------------- #
def stats() -> dict:
    s = db.query(
        "SELECT COUNT(*) AS apps, "
        "SUM(enabled) AS enabled_apps, "
        "(SELECT COUNT(*) FROM rules) AS rules, "
        "(SELECT COUNT(*) FROM skip_logs) AS total_skips FROM apps"
    )[0]
    return {
        "apps": s["apps"] or 0,
        "enabled_apps": s["enabled_apps"] or 0,
        "rules": s["rules"] or 0,
        "total_skips": s["total_skips"] or 0,
    }


def logs(limit: int = 50) -> list[dict]:
    limit = max(1, min(int(limit), 500))
    return [
        dict(r)
        for r in db.query(
            "SELECT * FROM skip_logs ORDER BY id DESC LIMIT ?", (limit,)
        )
    ]


def meta() -> dict:
    return META


# --------------------------------------------------------------------------- #
# dispatcher
# --------------------------------------------------------------------------- #
def handle(method: str, parts: list[str], query: dict, body: Any) -> tuple[int, Any]:
    # /api/health
    if method == "GET" and parts == ["health"]:
        return 200, {"ok": True, "version": config.VERSION}

    if method == "GET" and parts == ["meta"]:
        return 200, meta()

    # /api/settings
    if method == "GET" and parts == ["settings"]:
        return 200, get_all_settings()
    if method == "PUT" and parts == ["settings"]:
        return 200, update_settings(body or {})

    # /api/global-rules（全局规则 + 小程序）
    if parts == ["global-rules"]:
        if method == "GET":
            return 200, get_global_config()
        if method == "PUT":
            return 200, update_global_config(body or {})

    # /api/stats
    if method == "GET" and parts == ["stats"]:
        return 200, stats()

    # /api/logs
    if method == "GET" and parts == ["logs"]:
        return 200, logs(query.get("limit", 50))

    # /api/apps
    if method == "GET" and parts == ["apps"]:
        return 200, list_apps(query)
    if method == "POST" and parts == ["apps"]:
        return 200, create_app(body or {})
    if method == "POST" and parts == ["import"]:
        content = (body or {}).get("content", "")
        fmt = (body or {}).get("format", "auto")
        pkg = (body or {}).get("package")
        return 200, import_rules(content, fmt, pkg)
    if method == "GET" and parts == ["export"]:
        return 200, export_rules(query.get("scope", "all"))
    if method == "POST" and parts == ["simulate"]:
        return 200, simulate(body or {})

    # /api/apps/<hash>
    if len(parts) >= 2 and parts[0] == "apps":
        try:
            h = int(parts[1])
        except ValueError:
            raise ApiError(400, "invalid app hash")
        if len(parts) == 2:
            if method == "GET":
                return 200, get_app(h)
            if method == "PUT":
                return 200, update_app(h, body or {})
            if method == "DELETE":
                return 200, delete_app(h)
        if len(parts) == 3 and parts[2] == "rules":
            if method == "GET":
                return 200, {"items": get_app(h)["rules"]}
            if method == "POST":
                return 200, add_rule(h, body or {})
        if len(parts) == 3 and parts[2] == "export":
            if method == "GET":
                return 200, export_rules(f"single:{h}")
        if len(parts) == 3 and parts[2] == "params":
            if method == "POST":
                key = (body or {}).get("key")
                value = (body or {}).get("value")
                if not key:
                    raise ApiError(400, "key required")
                return 200, set_app_param(h, key, value)
        if len(parts) == 4 and parts[2] == "rules":
            try:
                rid = int(parts[3])
            except ValueError:
                raise ApiError(400, "invalid rule id")
            if method == "PUT":
                return 200, update_rule(h, rid, body or {})
            if method == "DELETE":
                return 200, delete_rule(h, rid)

    raise ApiError(404, f"no route for /api/{'/'.join(parts)}")
