#!/usr/bin/env python3
"""Install the top rule-covered apps into an emulator (LDPlayer / AVD).

Two sources are supported:

* ``--source file``   : install every ``*.apk`` found in ``--dir`` (you can drag
  APKs into the emulator, export them to that folder, or drop them there).
* ``--source mirror`` : download APKs from an APK mirror (APKPure by default)
  and install them. Requires a network that can reach the mirror.

Already-installed packages are skipped unless ``--reinstall`` is given.

Examples
--------
    # install APKs that already sit in ./apks
    python scripts/install_top_apps.py --source file --dir ./apks

    # download + install the top 10 apps from the Top-100 list
    python scripts/install_top_apps.py --source mirror --limit 10

    # pick a specific emulator
    python scripts/install_top_apps.py --source mirror --limit 10 --serial emulator-5554
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS_SDK = ROOT / ".buildtools" / "sdk"
ADB_CANDIDATES = [
    TOOLS_SDK / "platform-tools" / "adb.exe",
    TOOLS_SDK / "platform-tools" / "adb",
    "adb",
]
AAPT2_CANDIDATES = [
    TOOLS_SDK / "build-tools" / "34.0.0" / "aapt2.exe",
    TOOLS_SDK / "build-tools" / "34.0.0" / "aapt2",
    "aapt2",
]

DEFAULT_LIST = ROOT.parent / "docs" / "Top100-热门应用清单.csv"
DEFAULT_OUT = ROOT / "downloaded-apks"

MIRRORS = {
    "apkpure": "https://d.apkpure.com/b/APK/{pkg}?version=latest",
    "apkcombo": "https://download.apkcombo.com/apk/{pkg}",
}

APK_MAGIC = b"PK\x03\x04"
MIN_APK_BYTES = 1_000_000

_CTX = ssl.create_default_context()
_CTX.check_hostname = False
_CTX.verify_mode = ssl.CERT_NONE


def log(msg: str) -> None:
    print(msg, flush=True)


def first_executable(candidates) -> str:
    for c in candidates:
        if isinstance(c, str) or Path(c).exists():
            return str(c)
    return "adb"


def adb_bin() -> str:
    return first_executable(ADB_CANDIDATES)


def aapt2_bin() -> str:
    return first_executable(AAPT2_CANDIDATES)


def run(cmd, timeout=600) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def adb(serial: str, *args, timeout=600) -> subprocess.CompletedProcess:
    base = [adb_bin()]
    if serial:
        base += ["-s", serial]
    return run(base + list(args), timeout=timeout)


def parse_devices(output: str) -> list[str]:
    serials = []
    for line in output.splitlines()[1:]:
        line = line.strip()
        if not line or "\t" not in line:
            continue
        serial, state = line.split("\t", 1)
        if state.strip() == "device":
            serials.append(serial.strip())
    return serials


def resolve_serial(requested: str) -> str:
    if requested:
        return requested
    res = adb("", "devices")
    serials = parse_devices(res.stdout)
    if not serials:
        raise SystemExit("未发现可用设备（adb devices 为空）")
    if len(serials) > 1:
        raise SystemExit(
            "检测到多个设备，请用 --serial 指定：\n  " + "\n  ".join(serials)
        )
    return serials[0]


def list_installed(serial: str) -> set[str]:
    res = adb(serial, "shell", "pm", "list", "packages")
    pkgs = set()
    for line in res.stdout.splitlines():
        m = re.match(r"package:([^\s]+)", line.strip())
        if m:
            pkgs.add(m.group(1))
    return pkgs


def apk_package(apk: Path) -> str:
    """Read the package name from an APK via aapt2 (falls back to filename)."""
    res = run([aapt2_bin(), "dump", "badging", str(apk)], timeout=120)
    m = re.search(r"package: name='([^']+)'", res.stdout or "")
    if m:
        return m.group(1)
    return apk.stem


def looks_like_apk(path: Path) -> bool:
    if not path.exists() or path.stat().st_size < MIN_APK_BYTES:
        return False
    with open(path, "rb") as f:
        return f.read(4) == APK_MAGIC


def read_top_list(list_path: Path, limit: int) -> list[tuple[str, str, str]]:
    """Return [(package, app_name, rank), ...] from the Top-N CSV."""
    if not list_path.exists():
        raise SystemExit(f"清单文件不存在: {list_path}")
    out = []
    with open(list_path, "r", encoding="utf-8-sig", newline="") as f:
        for row in csv_rows(f):
            pkg = (row.get("package") or "").strip()
            if pkg:
                out.append((pkg, (row.get("app_name") or "").strip(), row.get("rank", "")))
    if limit and limit > 0:
        out = out[:limit]
    return out


def csv_rows(f) -> list[dict]:
    import csv as _csv

    return list(_csv.DictReader(f))


def download(url: str, dest: Path, tries: int = 5, timeout: int = 120) -> bool:
    for i in range(1, tries + 1):
        tmp = dest.with_suffix(dest.suffix + ".part")
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=timeout, context=_CTX) as r, open(tmp, "wb") as f:
                while True:
                    chunk = r.read(1024 * 512)
                    if not chunk:
                        break
                    f.write(chunk)
            tmp.replace(dest)
            if looks_like_apk(dest):
                return True
            log(f"    [警告] 下载内容不是有效 APK（尝试 {i}/{tries}）")
            dest.unlink(missing_ok=True)
        except Exception as e:
            log(f"    [重试] {i}/{tries} {type(e).__name__} {str(e)[:80]}")
            if tmp.exists():
                tmp.unlink(missing_ok=True)
            time.sleep(2 * i)
    return False


def install_apk(serial: str, apk: Path) -> bool:
    res = adb(serial, "install", "-r", "-d", str(apk), timeout=900)
    if "Success" in (res.stdout + res.stderr):
        return True
    log("    adb install: " + (res.stderr or res.stdout).strip()[:200])
    return False


def main() -> int:
    p = argparse.ArgumentParser(description="Install top rule-covered apps into an emulator")
    p.add_argument("--source", choices=["file", "mirror"], required=True)
    p.add_argument("--dir", help="APK 文件夹（--source file 时必填）")
    p.add_argument("--limit", type=int, default=10, help="安装数量（--source mirror，默认 10）")
    p.add_argument("--list", default=str(DEFAULT_LIST), help="Top-N 清单 CSV")
    p.add_argument("--out", default=str(DEFAULT_OUT), help="下载目录（--source mirror）")
    p.add_argument("--mirror", choices=list(MIRRORS), default="apkpure")
    p.add_argument("--serial", default="", help="设备序列号（默认自动探测）")
    p.add_argument("--reinstall", action="store_true", help="已安装也重装")
    p.add_argument("--dry-run", action="store_true", help="只列出计划，不安装")
    args = p.parse_args()

    serial = resolve_serial(args.serial)
    installed = set() if args.reinstall else list_installed(serial)
    log(f"设备: {serial}；已安装 {len(installed)} 个应用")

    targets: list[tuple[str, str, Path | None]] = []

    if args.source == "file":
        if not args.dir:
            raise SystemExit("--source file 需要 --dir")
        folder = Path(args.dir)
        if not folder.is_dir():
            raise SystemExit(f"文件夹不存在: {folder}")
        for apk in sorted(folder.glob("*.apk")):
            targets.append((apk_package(apk), apk.name, apk))
    else:
        out_dir = Path(args.out)
        out_dir.mkdir(parents=True, exist_ok=True)
        for pkg, name, rank in read_top_list(Path(args.list), args.limit):
            targets.append((pkg, name or pkg, out_dir / f"{pkg}.apk"))

    ok = failed = skipped = 0
    for pkg, name, apk in targets:
        if pkg in installed:
            log(f"  [跳过] {name} ({pkg}) 已安装")
            skipped += 1
            continue
        if args.dry_run:
            log(f"  [计划] {name} ({pkg}) -> {apk}")
            ok += 1
            continue

        if args.source == "mirror":
            if not looks_like_apk(apk):
                log(f"  [下载] {name} ({pkg})")
                url = MIRRORS[args.mirror].format(pkg=pkg)
                if not download(url, apk):
                    log(f"  [失败] 下载失败: {pkg}")
                    failed += 1
                    continue

        if not looks_like_apk(apk):
            log(f"  [失败] 不是有效 APK: {apk}")
            failed += 1
            continue

        log(f"  [安装] {name} ({pkg})")
        if install_apk(serial, apk):
            ok += 1
            installed.add(pkg)
        else:
            failed += 1

    log(f"\n完成：成功 {ok} / 跳过 {skipped} / 失败 {failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
