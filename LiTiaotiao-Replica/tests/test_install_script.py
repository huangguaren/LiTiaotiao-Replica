import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = (
    Path(__file__).resolve().parents[2]
    / "LiTiaotiao-Android"
    / "scripts"
    / "install_top_apps.py"
)
spec = importlib.util.spec_from_file_location("install_top_apps", SCRIPT)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class TestInstallScript(unittest.TestCase):
    def test_parse_devices(self):
        out = (
            "List of devices attached\n"
            "emulator-5554\tdevice\n"
            "emulator-5556\toffline\n"
            "127.0.0.1:5555\tdevice\n"
        )
        self.assertEqual(mod.parse_devices(out), ["emulator-5554", "127.0.0.1:5555"])

    def test_looks_like_apk(self):
        with tempfile.TemporaryDirectory() as d:
            good = Path(d) / "a.apk"
            good.write_bytes(b"PK\x03\x04" + b"\x00" * 2_000_000)
            bad = Path(d) / "b.apk"
            bad.write_bytes(b"NOTAPK" + b"\x00" * 2_000_000)
            small = Path(d) / "c.apk"
            small.write_bytes(b"PK\x03\x04")
            self.assertTrue(mod.looks_like_apk(good))
            self.assertFalse(mod.looks_like_apk(bad))
            self.assertFalse(mod.looks_like_apk(small))

    def test_read_top_list(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "top.csv"
            p.write_text(
                "rank,app_name,package,rules,play\n"
                "1,微博,com.sina.weibo,16,x\n"
                "2,迅雷,com.xunlei.downloadprovider,13,y\n"
                "3,抖音,com.ss.android.ugc.aweme,8,z\n",
                encoding="utf-8",
            )
            rows = mod.read_top_list(p, 2)
            self.assertEqual(len(rows), 2)
            self.assertEqual(rows[0][0], "com.sina.weibo")
            self.assertEqual(rows[1][0], "com.xunlei.downloadprovider")

    def test_mirror_url_format(self):
        self.assertIn("com.tencent.mm", mod.MIRRORS["apkpure"].format(pkg="com.tencent.mm"))


if __name__ == "__main__":
    unittest.main()
