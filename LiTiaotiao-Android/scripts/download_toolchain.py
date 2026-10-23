"""Download JDK 17 + Android cmdline-tools + Gradle 8.2 for building the APK locally."""
import os, ssl, sys, time, urllib.request, urllib.error

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BT = os.path.join(ROOT, ".buildtools")
os.makedirs(BT, exist_ok=True)

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE

TARGETS = {
    "jdk17.zip": "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse",
    "cmdline-tools.zip": "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
    "gradle-8.2-bin.zip": "https://services.gradle.org/distributions/gradle-8.2-bin.zip",
}


def download(name, url):
    dest = os.path.join(BT, name)
    if os.path.exists(dest) and os.path.getsize(dest) > 1_000_000:
        print(f"[skip] {name} exists ({os.path.getsize(dest)} bytes)", flush=True)
        return
    for attempt in range(1, 8):
        tmp = dest + ".part"
        try:
            print(f"[get ] {name} attempt {attempt} ...", flush=True)
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=60, context=CTX) as r, open(tmp, "wb") as f:
                total = int(r.headers.get("Content-Length") or 0)
                got = 0
                while True:
                    chunk = r.read(1024 * 256)
                    if not chunk:
                        break
                    f.write(chunk)
                    got += len(chunk)
                    if total and got % (5 * 1024 * 1024) < 256 * 1024:
                        print(f"      {name}: {got//(1024*1024)}/{total//(1024*1024)} MB", flush=True)
            os.replace(tmp, dest)
            print(f"[ok  ] {name} -> {os.path.getsize(dest)} bytes", flush=True)
            return
        except Exception as e:
            print(f"[fail] {name} attempt {attempt}: {type(e).__name__} {str(e)[:100]}", flush=True)
            try:
                if os.path.exists(tmp):
                    os.remove(tmp)
            except OSError:
                pass
            time.sleep(3 * attempt)
    print(f"[DEAD] {name} failed after retries", flush=True)


for n, u in TARGETS.items():
    download(n, u)
print("ALL_DONE", flush=True)
