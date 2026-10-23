"""Run Android lint locally with the bundled toolchain (same env as build_apk.py)."""
import os, glob, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BT = os.path.join(ROOT, ".buildtools")


def find_jdk():
    for exe in glob.glob(os.path.join(BT, "**", "bin", "java.exe"), recursive=True):
        return os.path.dirname(os.path.dirname(exe))
    return None


jdk = find_jdk()
gradle = os.path.join(BT, "gradle-8.2", "bin", "gradle.bat")
sdk = os.path.join(BT, "sdk")
if not jdk or not os.path.exists(gradle):
    print("toolchain missing"); sys.exit(1)

env = dict(os.environ)
env["JAVA_HOME"] = jdk
env["ANDROID_HOME"] = sdk
env["ANDROID_SDK_ROOT"] = sdk
env["GRADLE_USER_HOME"] = os.path.join(BT, "gradle_home")

r = subprocess.run(["cmd", "/c", gradle, "lintDebug", "--no-daemon", "--stacktrace"],
                   cwd=ROOT, env=env)
print("LINT_EXIT", r.returncode)
