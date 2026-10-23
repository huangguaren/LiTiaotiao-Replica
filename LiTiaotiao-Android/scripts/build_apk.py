"""Build the debug APK: assemble a local SDK from official zips and run Gradle."""
import os, ssl, sys, time, zipfile, shutil, glob, subprocess, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BT = os.path.join(ROOT, ".buildtools")
SDK = os.path.join(BT, "sdk")

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE

REPO = "https://dl.google.com/android/repository/"
SDK_ZIPS = {
    "platform-34.zip": REPO + "platform-34-ext7_r03.zip",
    "build-tools-34.zip": REPO + "build-tools_r34-windows.zip",
    "platform-tools.zip": REPO + "platform-tools_r37.0.1-win.zip",
}


def log(*a):
    print(*a, flush=True)


def download(name, url, tries=8):
    dest = os.path.join(BT, name)
    if os.path.exists(dest) and os.path.getsize(dest) > 2_000_000:
        return dest
    for i in range(1, tries + 1):
        tmp = dest + ".part"
        try:
            log("[get ]", name, "attempt", i)
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=120, context=CTX) as r, open(tmp, "wb") as f:
                while True:
                    c = r.read(1024 * 256)
                    if not c:
                        break
                    f.write(c)
            os.replace(tmp, dest)
            log("[ok  ]", name, os.path.getsize(dest))
            return dest
        except Exception as e:
            log("[fail]", name, type(e).__name__, str(e)[:90])
            if os.path.exists(tmp):
                os.remove(tmp)
            time.sleep(3 * i)
    return None


def unzip(src, dest):
    os.makedirs(dest, exist_ok=True)
    with zipfile.ZipFile(src) as z:
        z.extractall(dest)


def find_dir(root, marker):
    for d in glob.glob(os.path.join(root, "**"), recursive=True):
        if os.path.isdir(d) and os.path.exists(os.path.join(d, marker)):
            return d
    return None


def place(zip_name, marker, target):
    if os.path.exists(os.path.join(target, marker)):
        log("[skip]", target)
        return True
    src = os.path.join(BT, zip_name)
    if not os.path.exists(src):
        log("[miss]", zip_name)
        return False
    tmp = os.path.join(BT, "_x_" + zip_name)
    shutil.rmtree(tmp, ignore_errors=True)
    unzip(src, tmp)
    d = find_dir(tmp, marker)
    if not d:
        log("[ERR] marker", marker, "not found in", zip_name)
        return False
    if os.path.isdir(target):
        shutil.rmtree(target, ignore_errors=True)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.move(d, target)
    log("[place]", target)
    return True


def find_java_home():
    for d in glob.glob(os.path.join(BT, "jdk*")):
        if os.path.exists(os.path.join(d, "bin", "java.exe")):
            return d
    src = os.path.join(BT, "jdk17.zip")
    if not os.path.exists(src):
        return None
    tmp = os.path.join(BT, "jdk_extract")
    shutil.rmtree(tmp, ignore_errors=True)
    unzip(src, tmp)
    d = find_dir(tmp, os.path.join("bin", "java.exe"))
    return os.path.dirname(d) if d and d.endswith("bin") else d


def setup_gradle():
    g = os.path.join(BT, "gradle-8.2", "bin", "gradle.bat")
    if os.path.exists(g):
        return g
    src = os.path.join(BT, "gradle-8.2-bin.zip")
    if not os.path.exists(src):
        return None
    unzip(src, BT)
    return g


def write_licenses():
    lic = os.path.join(SDK, "licenses")
    os.makedirs(lic, exist_ok=True)
    with open(os.path.join(lic, "android-sdk-license"), "w", encoding="utf-8") as f:
        f.write("24333f8a63b6825ea9c5514f83c2829b004d1fee\n")
        f.write("d56f5187479451eabf01fb78af6dfcb131a6481e\n")
        f.write("8933bad161af4178b1185d1a37fbf41ea5269c55\n")


def main():
    os.makedirs(BT, exist_ok=True)
    # SDK zips
    for name, url in SDK_ZIPS.items():
        download(name, url)
    ok1 = place("platform-34.zip", "android.jar", os.path.join(SDK, "platforms", "android-34"))
    ok2 = place("build-tools-34.zip", "aapt2.exe", os.path.join(SDK, "build-tools", "34.0.0"))
    ok3 = place("platform-tools.zip", "adb.exe", os.path.join(SDK, "platform-tools"))
    log("[sdk] platform=%s build-tools=%s platform-tools=%s" % (ok1, ok2, ok3))
    write_licenses()

    java_home = find_java_home()
    log("[jdk]", java_home)
    gradle = setup_gradle()
    log("[gradle]", gradle)
    if not (java_home and gradle and ok1 and ok2):
        log("FATAL: toolchain incomplete")
        return 2

    with open(os.path.join(ROOT, "local.properties"), "w", encoding="utf-8") as f:
        f.write("sdk.dir=" + SDK.replace("\\", "\\\\") + "\n")

    env = dict(os.environ)
    env["JAVA_HOME"] = java_home
    env["ANDROID_HOME"] = SDK
    env["ANDROID_SDK_ROOT"] = SDK
    env["GRADLE_USER_HOME"] = os.path.join(BT, "gradle_home")

    log("[build] gradle assembleDebug ...")
    r = subprocess.run(["cmd", "/c", gradle, "assembleDebug", "--no-daemon", "--stacktrace"],
                       cwd=ROOT, env=env)
    log("[build] exit", r.returncode)
    apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    if os.path.exists(apk):
        log("APK_OK", apk, os.path.getsize(apk))
        return 0
    log("APK_MISSING")
    return r.returncode or 5


if __name__ == "__main__":
    sys.exit(main())
