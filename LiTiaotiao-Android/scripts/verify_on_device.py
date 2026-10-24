"""在已连接的模拟器/真机上自动验证李跳跳的跳过效果。

用法：
    python scripts/verify_on_device.py [-s 设备序列号]

前置条件：
  * 设备上已安装李跳跳，且无障碍服务已开启；
  * 设备上已安装测试专用 App（LiTiaotiao-TestTarget，包名 hello.litiaotiao.testapp）。

脚本会依次验证三个场景，并给出 PASS / FAIL：
  1. 开屏跳过：启动测试 App，「跳过广告」按钮应被自动点击一次；
  2. 弹窗跳过：点开「打开更新弹窗」，弹窗应被自动关闭（点掉「以后再说」）；
  3. 白名单生效：把测试 App 加入白名单后，同样的弹窗应原样保留、且无任何跳过记录。

判定不依赖人眼看截图，而是读取无障碍节点树（uiautomator dump）
和 App 数据库里的 skip_logs，因此结果可复现、可进 CI。
"""
import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.path.join(ROOT, ".buildtools", "sdk", "platform-tools", "adb.exe")
if not os.path.exists(ADB):
    ADB = "adb"  # 回退到 PATH 里的 adb

TEST_PKG = "hello.litiaotiao.testapp"
LTT_PKG = "hello.litiaotiao.app"
TEST_HASH = -326074944  # java hashCode("hello.litiaotiao.testapp")
DIALOG_MARK = '检测到更新'


class Dev:
    def __init__(self, serial=None):
        self.base = [ADB]
        if serial:
            self.base += ["-s", serial]

    def sh(self, *args, timeout=30):
        r = subprocess.run(self.base + ["shell"] + [str(a) for a in args],
                           capture_output=True, text=True, timeout=timeout,
                           errors="replace")
        return r.stdout.strip()

    def sql(self, query):
        return self.sh("run-as", LTT_PKG, "sqlite3", "databases/litiaotiao.db",
                       '"%s"' % query.replace('"', '\\"'))

    def tap(self, x, y):
        self.sh("input", "tap", x, y)

    def tree(self):
        """返回当前屏幕的节点列表 [(resource_id, text, bounds_str)]。"""
        self.sh("uiautomator", "dump", "/sdcard/_ltt_verify.xml")
        xml = self.sh("cat", "/sdcard/_ltt_verify.xml")
        if not xml.startswith("<?xml"):
            return []
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            return []
        out = []
        for n in root.iter("node"):
            out.append((n.get("resource-id") or "", n.get("text") or "",
                        n.get("bounds") or ""))
        return out

    def center_of(self, rid_contains):
        """按 resource id 片段找到控件，返回其屏幕中心坐标。"""
        for rid, _text, bounds in self.tree():
            if rid_contains in rid and bounds:
                m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", bounds)
                if m:
                    x1, y1, x2, y2 = map(int, m.groups())
                    return (x1 + x2) // 2, (y1 + y2) // 2
        return None

    def has_text(self, text):
        return any(text == t for _r, t, _b in self.tree())

    def foreground(self):
        out = self.sh("dumpsys", "window")
        m = re.search(r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^/]+)/", out)
        return m.group(1) if m else ""

    def start_test_app(self, settle=6):
        self.sh("am", "force-stop", TEST_PKG)
        time.sleep(1)
        self.sh("am", "start", "-n", "%s/.MainActivity" % TEST_PKG)
        # 等到测试 App 真正回到前台（启动动画期间坐标会漂移）
        for _ in range(20):
            time.sleep(0.5)
            if self.foreground() == TEST_PKG:
                break
        time.sleep(settle)

    def clear_logs(self):
        self.sql("delete from skip_logs")

    def logs(self):
        rows = self.sql(
            "select rule_type||' | '||coalesce(trigger_text,'')||' -> '||"
            "coalesce(action_target,'') from skip_logs order by id")
        return [r for r in rows.splitlines() if r.strip()]

    def count_logs(self):
        v = self.sql("select count(*) from skip_logs")
        try:
            return int(v)
        except ValueError:
            return -1

    def set_whitelist(self, on):
        self.sql("update apps set whitelisted=%d where hash=%d" % (1 if on else 0, TEST_HASH))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-s", "--serial", default=None, help="设备序列号，多设备时必填")
    args = ap.parse_args()

    d = Dev(args.serial)
    print("设备:", d.sh("getprop", "ro.product.model") or "(未知)")
    svc = d.sh("settings", "get", "secure", "enabled_accessibility_services")
    if LTT_PKG not in (svc or ""):
        print("FAIL 前置条件：李跳跳无障碍服务未开启（%s）" % svc)
        return 2
    print("无障碍服务: 已开启")

    results = []

    # ---------- 场景 1：开屏跳过 ----------
    print("\n[场景1] 开屏跳过 —— 启动测试 App，「跳过广告」应被自动点击")
    d.set_whitelist(False)
    # 先停掉并静置，超过「严格防重复点击」的 5 秒冷却窗口。
    # 注意：clear_logs() 只清数据库，清不掉内存里的去重表，
    # 若紧跟着上一次点击重新启动，本次点击会被冷却抑制，造成误判。
    d.sh("am", "force-stop", TEST_PKG)
    time.sleep(6)
    d.clear_logs()
    d.sh("am", "start", "-n", "%s/.MainActivity" % TEST_PKG)
    time.sleep(5)
    logs = d.logs()
    kw = [l for l in logs if l.startswith("keyword")]
    ok = len(kw) == 1 and "btnSkip" in " ".join(kw)
    print("   跳过记录 %d 条（期望 1 条）: %s" % (len(kw), kw))
    results.append(("开屏跳过", ok))

    # ---------- 场景 2：弹窗跳过 ----------
    print("\n[场景2] 弹窗跳过 —— 点开更新弹窗，应被自动点掉「以后再说」")
    d.start_test_app(settle=6)
    pos = d.center_of("btnPopup")
    if not pos:
        print("   FAIL 未找到「打开更新弹窗」按钮")
        results.append(("弹窗跳过", False))
    else:
        print("   按钮中心:", pos)
        d.clear_logs()
        d.tap(*pos)
        time.sleep(3)
        still = d.has_text(DIALOG_MARK)
        pop = [l for l in d.logs() if l.startswith("popup")]
        ok = (not still) and len(pop) >= 1 and "button2" in " ".join(pop)
        print("   弹窗是否仍在: %s（期望 False）" % still)
        print("   弹窗记录: %s" % pop)
        results.append(("弹窗跳过", ok))

    # ---------- 场景 3：白名单 ----------
    print("\n[场景3] 白名单 —— 加入白名单后，同样的弹窗应保留且无跳过记录")
    d.start_test_app(settle=6)
    pos = d.center_of("btnPopup")
    if not pos:
        print("   FAIL 未找到「打开更新弹窗」按钮")
        results.append(("白名单", False))
    else:
        d.set_whitelist(True)
        d.clear_logs()
        d.tap(*pos)
        time.sleep(3)
        still = d.has_text(DIALOG_MARK)
        n = d.count_logs()
        ok = still and n == 0
        print("   弹窗是否仍在: %s（期望 True）" % still)
        print("   跳过记录: %d 条（期望 0）" % n)
        results.append(("白名单", ok))
        # 收尾：关掉弹窗并复原白名单
        d.sh("input", "keyevent", "KEYCODE_BACK")
        d.set_whitelist(False)

    print("\n" + "=" * 46)
    all_ok = True
    for name, ok in results:
        print("  %-10s %s" % (name, "PASS" if ok else "FAIL"))
        all_ok = all_ok and ok
    print("=" * 46)
    print("总体: %s" % ("全部通过" if all_ok else "存在失败项"))
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
