"""LiTiaotiao rule engine.

This is a faithful, dependency-free re-implementation of the matching / execution
logic that powers LiTiaotiao's Android ``AccessibilityService``. Instead of a live
accessibility node tree, it operates on an in-memory representation of a screen
(a tree of :class:`UiNode`), which lets us unit-test the engine and expose a
"simulator" in the web UI.

Rule semantics (see https://juejin.cn/post/6938590373740544007 ):

* ``id``   – the *trigger*: text/resource-id to look for on screen.
* ``action`` – what to click when the trigger is found. May be:

  * a text/resource-id pattern (click the matching control),
  * ``GLOBAL_ACTION_BACK`` (press the system Back key),
  * a ``bounds`` string ``x1,y1,x2,y2`` (or ``x1,y1,x2,y2,0|1`` for a CheckBox state).

* Matching prefixes on the ``id`` / ``action`` strings:

  * ``+foo``  – startsWith ``foo``
  * ``-foo``  – endsWith   ``foo``
  * ``=foo``  – equals     ``foo``
  * bare ``foo`` – fuzzy *contains* ``foo``
  * ``a&b``   – both conditions must hold (AND)

* ``times`` – how many times to click (``0`` = detection only, used by
  ``unite_popup_rules`` continuous tasks).
* ``click_way_popup`` – ``1`` forces a click even when the usual path fails.
* ``search_times_popup`` – how many search passes to perform.
* ``unite_popup_rules`` – treat the popup rules as one continuous-task group.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Iterable, Optional

ACTION_BACK = "GLOBAL_ACTION_BACK"
_BOUNDS_RE = re.compile(
    r"^\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*(?:,\s*[01]\s*)?$"
)


def is_bounds(value: str) -> bool:
    return bool(_BOUNDS_RE.match(value or ""))


def normalize_bounds(value: str) -> str:
    return ",".join(part.strip() for part in value.split(","))


@dataclass
class UiNode:
    text: str = ""
    resource_id: str = ""
    bounds: str = ""
    clazz: str = ""
    clickable: bool = False
    visible: bool = True
    checked: Optional[bool] = None
    children: list["UiNode"] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: Any) -> "UiNode":
        if not isinstance(data, dict):
            return cls()
        return cls(
            text=data.get("text") or data.get("contentDescription") or "",
            resource_id=(
                data.get("resource_id")
                or data.get("id")
                or data.get("viewIdResourceName")
                or ""
            ),
            bounds=data.get("bounds") or "",
            clazz=data.get("class") or data.get("className") or "",
            clickable=bool(data.get("clickable", False)),
            visible=bool(data.get("visible", True)),
            checked=data.get("checked"),
            children=[cls.from_dict(c) for c in (data.get("children") or [])],
        )


def node_summary(node: UiNode) -> dict:
    return {
        "text": node.text,
        "resource_id": node.resource_id,
        "bounds": node.bounds,
        "class": node.clazz,
        "clickable": node.clickable,
        "checked": node.checked,
    }


# --------------------------------------------------------------------------- #
# Matching
# --------------------------------------------------------------------------- #
def _single(cond: str, value: str) -> bool:
    if not cond:
        return False
    if cond[0] == "=":
        return value == cond[1:]
    if cond[0] == "+":
        return value.startswith(cond[1:])
    if cond[0] == "-":
        return value.endswith(cond[1:])
    # No recognised operator (e.g. leading '|') -> fuzzy contains (literal text).
    return cond in value


def match_condition(condition: str, text: str, resource_id: str = "") -> bool:
    """Return True if *condition* matches either ``text`` or ``resource_id``.

    ``&`` splits the condition into AND-combined sub-conditions.
    """
    if "&" in condition:
        parts = condition.split("&")
        return all(
            _single(p, text) or _single(p, resource_id) for p in parts
        )
    return _single(condition, text) or _single(condition, resource_id)


def iter_nodes(root: UiNode, reverse: bool = False) -> Iterable[UiNode]:
    stack = [root]
    while stack:
        node = stack.pop()
        yield node
        children = list(node.children)
        if reverse:
            children = list(reversed(children))
        stack.extend(children)


def find_matching_nodes(
    root: UiNode,
    condition: str,
    *,
    require_clickable: bool = False,
    require_visible: bool = True,
    reverse: bool = False,
) -> list[UiNode]:
    out: list[UiNode] = []
    for node in iter_nodes(root, reverse=reverse):
        if require_visible and not node.visible:
            continue
        if require_clickable and not node.clickable:
            continue
        if match_condition(condition, node.text, node.resource_id):
            out.append(node)
    return out


# --------------------------------------------------------------------------- #
# Actions
# --------------------------------------------------------------------------- #
@dataclass
class ClickAction:
    kind: str  # 'node' | 'back' | 'bounds' | 'none'
    target_text: str = ""
    target_id: str = ""
    bounds: str = ""
    note: str = ""

    def to_dict(self) -> dict:
        return {
            "kind": self.kind,
            "target_text": self.target_text,
            "target_id": self.target_id,
            "bounds": self.bounds,
            "note": self.note,
        }


def resolve_action(
    action: str, trigger: UiNode, root: UiNode, *, force: bool = False
) -> ClickAction:
    if action == ACTION_BACK:
        return ClickAction(kind="back", note="系统返回键 (GLOBAL_ACTION_BACK)")

    if is_bounds(action):
        return ClickAction(
            kind="bounds", bounds=normalize_bounds(action), note=f"点击坐标 {action}"
        )

    # Click the trigger node itself when the action refers to the same control.
    if action == trigger.text or action == trigger.resource_id:
        return ClickAction(
            kind="node",
            target_text=trigger.text,
            target_id=trigger.resource_id,
            bounds=trigger.bounds,
            note="点击命中的控件本身",
        )

    # Otherwise locate the target by pattern, preferring clickable controls.
    targets = find_matching_nodes(root, action, require_clickable=True)
    if not targets:
        targets = find_matching_nodes(root, action)
    if targets:
        t = targets[0]
        return ClickAction(
            kind="node",
            target_text=t.text,
            target_id=t.resource_id,
            bounds=t.bounds,
            note="点击匹配控件",
        )

    if force:
        return ClickAction(
            kind="node",
            target_text=trigger.text,
            target_id=trigger.resource_id,
            bounds=trigger.bounds,
            note="强制点击命中控件",
        )
    return ClickAction(kind="none", note="未找到可点击目标")


# --------------------------------------------------------------------------- #
# Screen evaluation
# --------------------------------------------------------------------------- #
@dataclass
class RuleHit:
    rule_type: str
    match_id: str
    action: str
    times: int
    trigger: dict
    actions: list
    note: str = ""


@dataclass
class ScreenResult:
    app_hash: Optional[int]
    total_clicks: int
    hits: list
    matched_keywords: list


def _evaluate_one_rule(
    rule_type: str,
    match_id: str,
    action: str,
    times: int,
    root: UiNode,
    *,
    force: bool,
    reverse: bool,
) -> Optional[RuleHit]:
    triggers = find_matching_nodes(root, match_id, reverse=reverse)
    if not triggers:
        return None

    trigger = triggers[0]
    if rule_type == "keyword":
        # Splash keyword: click the node that matched.
        acts = [resolve_action(action, trigger, root, force=force)]
    else:
        if times == 0:
            # Detection-only marker (unite_popup_rules intermediate state).
            return RuleHit(
                rule_type=rule_type,
                match_id=match_id,
                action=action,
                times=times,
                trigger=node_summary(trigger),
                actions=[ClickAction(kind="none", note="检测态命中 (times=0)").to_dict()],
                note="持续任务的中间态",
            )
        acts = [
            resolve_action(action, trigger, root, force=force)
            for _ in range(max(times, 1))
        ]

    return RuleHit(
        rule_type=rule_type,
        match_id=match_id,
        action=action,
        times=times,
        trigger=node_summary(trigger),
        actions=[a.to_dict() for a in acts],
        note="命中",
    )


def evaluate_screen(
    app_rules: dict,
    screen: UiNode,
    *,
    app_hash: Optional[int] = None,
    opts: Optional[dict] = None,
) -> ScreenResult:
    """Run rule sets against one screen snapshot.

    Besides the per-app ``app_rules``, ``opts`` may carry:

    * ``global_rules`` – a rule set applied to **all packages**. This is what lets
      us catch ads that open inside a mini-program / a different package (e.g.
      splash ads delivered as mini-programs), where the foreground package has no
      per-app rule of its own.
    * ``package`` – the current foreground package.
    * ``mini_program`` – ``{"hosts": [...], "auto_back": bool}``; when the package is
      a configured mini-program host and no skip button was found, press Back.
    """
    opts = opts or {}
    global_rules = opts.get("global_rules") or None
    package = opts.get("package")
    mini_program = opts.get("mini_program") or {}

    hits: list[RuleHit] = []
    matched_keywords: list[str] = []
    seen: set[tuple] = set()

    def run(rule_set: Optional[dict], scope: str) -> None:
        if not rule_set:
            return
        params = rule_set.get("params") or {}
        force = str(params.get("click_way_popup", 0)) in ("1", "true", "True")
        reverse = bool(params.get("reverse_traversal", False))
        search_times = max(int(params.get("search_times_popup", 1) or 1), 1)
        suffix = " [全局]" if scope == "global" else ""

        for kw in rule_set.get("keywords", []) or []:
            res = _evaluate_one_rule("keyword", kw, kw, 1, screen, force=force, reverse=reverse)
            if not res:
                continue
            # 按「命中的控件」去重：避免多个关键词（如 跳过 / 跳过广告）
            # 命中同一个按钮而重复点击。
            trig = res.trigger
            nkey = (
                "node",
                trig.get("text", ""),
                trig.get("resource_id", ""),
                trig.get("bounds", ""),
            )
            if nkey in seen:
                continue
            seen.add(nkey)
            res.note = (res.note or "") + suffix
            hits.append(res)
            matched_keywords.append(kw)

        popup = rule_set.get("popup_rules", []) or []
        for _ in range(search_times):
            for r in popup:
                rid = r.get("id", "")
                if not rid:
                    continue
                raction = r.get("action", rid)
                key = ("popup", rid, raction)
                if key in seen:
                    continue
                res = _evaluate_one_rule(
                    "popup", rid, raction, int(r.get("times", 1)),
                    screen, force=force, reverse=reverse,
                )
                if res:
                    seen.add(key)
                    res.note = (res.note or "") + suffix
                    hits.append(res)

    if global_rules:
        run(global_rules, "global")
    run(app_rules, "app")

    total_clicks = sum(
        1 for h in hits for a in h.actions if a["kind"] in ("node", "back", "bounds")
    )

    # 小程序开屏广告兜底：命中宿主包但没找到可点击的跳过按钮时自动返回。
    if (
        total_clicks == 0
        and package
        and package in (mini_program.get("hosts") or [])
        and mini_program.get("auto_back")
    ):
        hits.append(
            RuleHit(
                rule_type="mini_program",
                match_id=package,
                action="GLOBAL_ACTION_BACK",
                times=1,
                trigger={
                    "text": package,
                    "resource_id": "",
                    "bounds": "",
                    "class": "",
                    "clickable": False,
                    "checked": None,
                },
                actions=[
                    ClickAction(
                        kind="back",
                        note="小程序开屏广告：未找到跳过按钮，自动返回",
                    ).to_dict()
                ],
                note="小程序广告自动返回",
            )
        )
        total_clicks = 1

    return ScreenResult(
        app_hash=app_hash,
        total_clicks=total_clicks,
        hits=[h.__dict__ for h in hits],
        matched_keywords=matched_keywords,
    )
