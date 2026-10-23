"""Parallel downloader for the full Android build toolchain."""
import os, ssl, time, threading, urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BT = os.path.join(ROOT, ".buildtools")
os.makedirs(BT, exist_ok=True)
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE

REPO = "https://dl.google.com/android/repository/"
FILES = {
    "jdk17.zip": "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse",
    "gradle-8.2-bin.zip": "https://services.gradle.org/distributions/gradle-8.2-bin.zip",
    "platform-34.zip": REPO + "platform-34-ext7_r03.zip",
    "build-tools-34.zip": REPO + "build-tools_r34-windows.zip",
    "platform-tools.zip": REPO + "platform-tools_r37.0.1-win.zip",
}
lock = threading.Lock()


def log(*a):
    with lock:
        print(*a, flush=True)


def one(name, url):
    dest = os.path.join(BT, name)
    if os.path.exists(dest) and os.path.getsize(dest) > 2_000_000:
        log("[skip]", name, os.path.getsize(dest)); return True
    for i in range(1, 10):
        tmp = dest + ".part"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=120, context=CTX) as r, open(tmp, "wb") as f:
                last = 0
                while True:
                    c = r.read(1024 * 512)
                    if not c:
                        break
                    f.write(c)
            os.replace(tmp, dest)
            log("[ok]", name, os.path.getsize(dest)); return True
        except Exception as e:
            log("[fail]", name, i, type(e).__name__, str(e)[:70])
            if os.path.exists(tmp):
                try: os.remove(tmp)
                except OSError: pass
            time.sleep(2 * i)
    return False


start = time.time()
with ThreadPoolExecutor(max_workers=5) as ex:
    results = list(ex.map(lambda kv: one(*kv), FILES.items()))
log("SUMMARY", dict(zip(FILES.keys(), results)), "elapsed", int(time.time() - start), "s")
log("ALL_DONE")
