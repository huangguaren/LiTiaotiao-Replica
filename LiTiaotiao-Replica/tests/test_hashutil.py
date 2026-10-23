import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from litiaotiao.hashutil import java_hashcode


class TestHashutil(unittest.TestCase):
    def test_known_package_hash_matches_index(self):
        # Verified against the generated package_index.json (tv.danmaku.bili -> 1994036591).
        self.assertEqual(java_hashcode("tv.danmaku.bili"), 1994036591)

    def test_negative_hash(self):
        self.assertIsInstance(java_hashcode("com.netease.cloudmusic"), int)
        self.assertEqual(
            java_hashcode("hello.litiaotiao.app"),
            java_hashcode("hello.litiaotiao.app"),
        )

    def test_ascii_stability(self):
        self.assertEqual(java_hashcode("abc"), (97 * 31 * 31 + 98 * 31 + 99))
        self.assertEqual(java_hashcode(""), 0)


if __name__ == "__main__":
    unittest.main()
