"""Run the JVM unit tests for the rule engine (no emulator / device needed).

规则引擎（RuleMatcher / RuleEngine / RuleSet / JavaHash / NodeSnapshot）是纯 Java，
不依赖 Android 框架，因此可以直接在 JVM 上跑，作为发布前的语义回归门禁。
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_apk import ROOT, BT, SDK, find_java_home, setup_gradle, log


def main():
    java_home = find_java_home()
    gradle = setup_gradle()
    if not (java_home and gradle):
        log("FATAL: JDK or Gradle missing; run scripts/build_apk.py first")
        return 2

    # local.properties 由 build_apk 写入；这里确保存在，便于单独跑测试
    lp = os.path.join(ROOT, "local.properties")
    if not os.path.exists(lp):
        with open(lp, "w", encoding="utf-8") as f:
            f.write("sdk.dir=" + SDK.replace("\\", "\\\\") + "\n")

    env = dict(os.environ)
    env["JAVA_HOME"] = java_home
    env["ANDROID_HOME"] = SDK
    env["ANDROID_SDK_ROOT"] = SDK
    env["GRADLE_USER_HOME"] = os.path.join(BT, "gradle_home")

    log("[test] gradle testDebugUnitTest ...")
    r = subprocess.run(["cmd", "/c", gradle, "testDebugUnitTest", "--no-daemon", "--stacktrace"],
                       cwd=ROOT, env=env)
    log("[test] gradle exit", r.returncode)

    # 汇总 XML 结果，给出人类可读的结论
    res_dir = os.path.join(ROOT, "app", "build", "test-results", "testDebugUnitTest")
    total = failed = skipped = 0
    suites = []
    if os.path.isdir(res_dir):
        import xml.etree.ElementTree as ET
        for name in sorted(os.listdir(res_dir)):
            if not name.endswith(".xml"):
                continue
            try:
                root = ET.parse(os.path.join(res_dir, name)).getroot()
            except ET.ParseError:
                continue
            suites.append(root.get("name", name))
            total += int(root.get("tests", 0))
            failed += int(root.get("failures", 0)) + int(root.get("errors", 0))
            skipped += int(root.get("skipped", 0))

    log("[test] suites=%d tests=%d failed=%d skipped=%d" % (len(suites), total, failed, skipped))
    for s in suites:
        log("[test]   -", s)

    if r.returncode != 0 or failed:
        log("TESTS_FAILED")
        return 1
    log("TESTS_OK %d" % total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
