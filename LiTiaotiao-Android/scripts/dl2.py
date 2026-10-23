"""Download JDK (Microsoft) and Gradle (Tencent mirror) in parallel."""
import os, ssl, time, threading, urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BT = os.path.join(ROOT, ".buildtools")
os.makedirs(BT, exist_ok=True)
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE

FILES = {
    "jdk17.zip": "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip",
    "gradle-8.2-bin.zip": "https://mirrors.cloud.tencent.com/gradle/gradle-8.2-bin.zip",
}
lock = threading.Lock()


def log(*a):
    with lock:
        print(*a, flush=True)


def one(name, url):
    dest = os.path.join(BT, name)
    if os.path.exists(dest) and os.path.getsize(dest) > 5_000_000:
        log("[skip]", name, os.path.getsize(dest)); return True
    for i in range(1, 12):
        tmp = dest + ".part"
        try:
            log("[get]", name, "try", i)
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=120, context=CTX) as r, open(tmp, "wb") as f:
                total = int(r.headers.get("Content-Length") or 0)
                got = 0
                while True:
                    c = r.read(1024 * 512)
                    if not c:
                        break
                    f.write(c); got += len(c)
                    if total and got // (20 * 1024 * 1024) > (got - len(c)) // (20 * 1024 * 1024):
                        log(f"   {name}: {got//(1024*1024)}/{total//(1024*1024)} MB")
            os.replace(tmp, dest)
            log("[ok]", name, os.path.getsize(dest)); return True
        except Exception as e:
            log("[fail]", name, i, type(e).__name__, str(e)[:80])
            if os.path.exists(tmp):
                try: os.remove(tmp)
                except OSError: pass
            time.sleep(2 * i)
    return False


start = time.time()
with ThreadPoolExecutor(max_workers=2) as ex:
    res = list(ex.map(lambda kv: one(*kv), FILES.items()))
log("SUMMARY", dict(zip(FILES, res)), "elapsed", int(time.time() - start))
log("ALL_DONE")
