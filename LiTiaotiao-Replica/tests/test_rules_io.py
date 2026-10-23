import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from litiaotiao import rules_io as R


class TestRulesIO(unittest.TestCase):
    def test_parse_all_rules_basic(self):
        text = (
            '[{"-330301876": "{\\"popup_rules\\":[{\\"id\\":\\"a\\",\\"action\\":\\"b\\"}]}"},'
            '{"1994036591": "{\\"popup_rules\\":[{\\"id\\":\\"c\\",\\"action\\":\\"d\\"}]}"}]'
        )
        recs = R.parse_all_rules_text(text)
        self.assertEqual(len(recs), 2)
        self.assertEqual(recs[0]["hash"], -330301876)
        obj = recs[0]["rule_obj"]
        self.assertEqual(obj["popup_rules"][0]["id"], "a")
        self.assertEqual(obj["popup_rules"][0]["action"], "b")

    def test_parse_tolerates_trailing_comma(self):
        text = '[{"-1": "{\\"popup_rules\\":[{\\"id\\":\\"x\\",\\"action\\":\\"y\\"},]}"}]'
        recs = R.parse_all_rules_text(text)
        self.assertIsNotNone(recs[0]["rule_obj"])
        self.assertEqual(recs[0]["rule_obj"]["popup_rules"][0]["id"], "x")

    def test_parts_round_trip(self):
        obj = {
            "keywords": ["跳过"],
            "keywords_append": ["关闭"],
            "popup_rules": [{"id": "a", "action": "b", "times": 2}],
            "click_way_popup": 1,
        }
        kw, kwa, popup, params = R.rule_obj_to_parts(obj)
        self.assertEqual(kw, ["跳过"])
        self.assertEqual(kwa, ["关闭"])
        self.assertEqual(popup[0]["times"], 2)
        self.assertEqual(params["click_way_popup"], 1)
        rebuilt = R.parts_to_rule_obj(kw, kwa, popup, params)
        self.assertEqual(rebuilt["popup_rules"][0]["action"], "b")
        self.assertEqual(rebuilt["click_way_popup"], 1)

    def test_build_export_array_format(self):
        apps = [
            {
                "hash": -330301876,
                "keywords": [],
                "keywords_append": [],
                "popup_rules": [{"id": "a", "action": "b", "times": 1}],
                "params": {"click_way_popup": 1},
            }
        ]
        out = R.build_export_array(apps)
        parsed = json.loads(out)
        self.assertIsInstance(parsed, list)
        self.assertIn("-330301876", parsed[0])
        inner = json.loads(parsed[0]["-330301876"])
        self.assertEqual(inner["popup_rules"][0]["id"], "a")
        self.assertEqual(inner["click_way_popup"], 1)

    def test_export_skips_empty_app(self):
        out = R.build_export_array(
            [{"hash": 1, "keywords": [], "keywords_append": [], "popup_rules": [], "params": {}}]
        )
        self.assertEqual(out, "[]")


if __name__ == "__main__":
    unittest.main()
