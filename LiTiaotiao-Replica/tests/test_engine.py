import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from litiaotiao import engine as E


def node(**kw):
    return E.UiNode(**kw)


class TestEngine(unittest.TestCase):
    def test_match_contains(self):
        self.assertTrue(E.match_condition("跳过", "跳过广告", ""))
        self.assertFalse(E.match_condition("跳过", "立即更新", ""))

    def test_match_startswith(self):
        self.assertTrue(E.match_condition("+检测到", "检测到更新弹窗", ""))
        self.assertFalse(E.match_condition("+检测到", "已检测到", ""))

    def test_match_endswith(self):
        self.assertTrue(E.match_condition("-用户体验", "优化用户体验", ""))
        self.assertFalse(E.match_condition("-用户体验", "用户体验计划", ""))

    def test_match_exact(self):
        self.assertTrue(E.match_condition("=以后再说", "以后再说", ""))
        self.assertFalse(E.match_condition("=以后再说", "以后", ""))

    def test_match_and(self):
        cond = "+检测到&-用户体验"
        self.assertTrue(E.match_condition(cond, "检测到优化用户体验", ""))
        self.assertFalse(E.match_condition(cond, "检测到弹窗", ""))
        self.assertFalse(E.match_condition(cond, "优化用户体验计划", ""))

    def test_match_against_resource_id(self):
        n = node(text="", resource_id="com.xxx:id/tv_close")
        self.assertTrue(E.match_condition("tv_close", n.text, n.resource_id))

    def test_bounds_action(self):
        screen = node(text="x", children=[node(text="天天神券", resource_id="t")])
        res = E.evaluate_screen(
            {"popup_rules": [{"id": "=天天神券", "action": "100,200,300,400"}], "params": {}},
            screen,
        )
        a = res.hits[0]["actions"][0]
        self.assertEqual(a["kind"], "bounds")
        self.assertEqual(a["bounds"], "100,200,300,400")

    def test_back_action(self):
        screen = node(text="x", children=[node(text="天天神券", resource_id="t")])
        res = E.evaluate_screen(
            {"popup_rules": [{"id": "=天天神券", "action": "GLOBAL_ACTION_BACK"}], "params": {}},
            screen,
        )
        self.assertEqual(res.hits[0]["actions"][0]["kind"], "back")

    def test_keyword_splash(self):
        screen = node(text="跳过广告", resource_id="tv_skip", clickable=True)
        res = E.evaluate_screen({"keywords": ["跳过"], "popup_rules": []}, screen)
        self.assertEqual(res.total_clicks, 1)
        self.assertEqual(res.matched_keywords, ["跳过"])

    def test_times_clicks(self):
        screen = node(text="x", children=[node(text="弹窗", resource_id="p")])
        res = E.evaluate_screen(
            {"popup_rules": [{"id": "弹窗", "action": "弹窗", "times": 3}], "params": {}}, screen
        )
        self.assertEqual(res.total_clicks, 3)
        self.assertEqual(len(res.hits[0]["actions"]), 3)

    def test_unite_times_zero_is_detection_only(self):
        screen = node(text="x", children=[node(text="还剩5秒", resource_id="p")])
        res = E.evaluate_screen(
            {"popup_rules": [{"id": "还剩&秒", "action": "还剩&秒", "times": 0}], "params": {}},
            screen,
        )
        self.assertEqual(len(res.hits), 1)
        self.assertEqual(res.total_clicks, 0)

    def test_search_times_popup_repeats(self):
        screen = node(text="x", children=[node(text="a", resource_id="p1"), node(text="b", resource_id="p2")])
        res = E.evaluate_screen(
            {"popup_rules": [{"id": "a", "action": "a"}], "params": {"search_times_popup": 2}},
            screen,
        )
        self.assertEqual(res.total_clicks, 1)

    def test_force_click_falls_back_to_trigger(self):
        screen = node(text="关闭广告", resource_id="c", clickable=True)
        res = E.evaluate_screen(
            {"popup_rules": [{"id": "关闭广告", "action": "不存在的按钮"}], "params": {"click_way_popup": 1}},
            screen,
        )
        self.assertEqual(res.hits[0]["actions"][0]["kind"], "node")

    def test_unite_gate_waits_for_intermediate_state(self):
        rules = {
            "popup_rules": [
                {"id": "倒计时", "action": "倒计时", "times": 0},
                {"id": "关闭", "action": "关闭", "times": 1},
            ],
            "params": {"unite_popup_rules": True},
        }
        # 中间态未出现：不点击
        only_close = node(text="关闭", resource_id="close", clickable=True)
        self.assertEqual(E.evaluate_screen(rules, only_close).total_clicks, 0)
        # 中间态出现：允许点击
        with_gate = node(text="x", children=[
            node(text="倒计时 5 秒", resource_id="cd"),
            node(text="关闭", resource_id="close", clickable=True),
        ])
        self.assertEqual(E.evaluate_screen(rules, with_gate).total_clicks, 1)

    def test_filter_young_skips_young_popup(self):
        screen = node(text="青少年模式", resource_id="young")
        rules = {"popup_rules": [{"id": "青少年模式", "action": "我知道了"}], "params": {}}
        self.assertEqual(len(E.evaluate_screen(rules, screen, opts={"filter_young": True}).hits), 0)
        self.assertEqual(len(E.evaluate_screen(rules, screen, opts={"filter_young": False}).hits), 1)


if __name__ == "__main__":
    unittest.main()
