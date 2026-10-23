"""Stand-alone HTTP server (Python stdlib only).

Serves the static SPA from ``static/`` and routes ``/api/*`` requests to
:mod:`litiaotiao.api`. No third-party dependencies are required, so the whole
project runs with a single ``python run.py``.
"""
from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from . import api as api_module
from .config import config
from .api import ApiError, Raw
from .logging_setup import get_logger

log = get_logger("server")

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"

_CONTENT_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".ico": "image/x-icon",
    ".woff2": "font/woff2",
}


class Handler(BaseHTTPRequestHandler):
    server_version = f"LiTiaotiao-Replica/{config.VERSION}"

    # ----- boilerplate -----
    def log_message(self, fmt, *args):  # quieter default logging
        log.info("%s - %s", self.address_string(), fmt % args)

    def _send(self, status: int, body: bytes, content_type: str):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header(
            "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"
        )
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _send_json(self, status: int, payload: dict):
        self._send(status, json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                   "application/json; charset=utf-8")

    # ----- routing -----
    def do_OPTIONS(self):
        self._send(204, b"", "application/json; charset=utf-8")

    def do_GET(self):
        self._route("GET")

    def do_POST(self):
        self._route("POST")

    def do_PUT(self):
        self._route("PUT")

    def do_DELETE(self):
        self._route("DELETE")

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", 0) or 0)
        if length <= 0:
            return b""
        return self.rfile.read(length)

    def _route(self, method: str):
        parsed = urlparse(self.path)
        path = parsed.path
        query = {k: v[0] for k, v in parse_qs(parsed.query).items()}
        body = self._read_body()

        # API
        if path.startswith("/api/"):
            self._handle_api(method, path[len("/api/"):], query, body)
            return

        if method != "GET":
            self._send_json(405, {"error": "method not allowed"})
            return
        self._serve_static(path)

    def _handle_api(self, method, sub: str, query, body: bytes):
        try:
            if body:
                ctype = self.headers.get("Content-Type", "")
                if "application/json" in ctype:
                    try:
                        payload = json.loads(body.decode("utf-8"))
                    except json.JSONDecodeError as e:
                        self._send_json(400, {"error": f"invalid JSON body: {e}"})
                        return
                else:
                    payload = {"content": body.decode("utf-8", "ignore")}
            else:
                payload = {}
            parts = [p for p in sub.split("/") if p != ""]
            status, result = api_module.handle(method, parts, query, payload)
            if isinstance(result, Raw):
                self._send(status, result.text.encode("utf-8"), result.content_type)
            else:
                self._send_json(status, result)
        except ApiError as e:
            self._send_json(e.status, {"error": e.message})
        except json.JSONDecodeError as e:
            self._send_json(400, {"error": f"JSON 解析失败: {e}"})
        except Exception as e:  # noqa: BLE001 - last-resort guard
            log.exception("unhandled error in API %s %s", method, sub)
            self._send_json(500, {"error": f"internal error: {e}"})

    def _serve_static(self, path: str):
        rel = path.lstrip("/")
        if rel in ("", "index.html"):
            rel = "index.html"
        target = (STATIC_DIR / rel).resolve()
        # Prevent path traversal.
        if not str(target).startswith(str(STATIC_DIR.resolve())):
            self._send_json(403, {"error": "forbidden"})
            return
        if not target.exists() or not target.is_file():
            # SPA fallback
            target = (STATIC_DIR / "index.html").resolve()
        ext = target.suffix.lower()
        ctype = _CONTENT_TYPES.get(ext, "application/octet-stream")
        try:
            data = target.read_bytes()
        except OSError:
            self._send_json(404, {"error": "not found"})
            return
        self._send(200, data, ctype)


def run(host: str | None = None, port: int | None = None) -> None:
    from .db import init_db

    host = host or config.HOST
    port = port or config.PORT
    init_db()
    api_module.ensure_default_settings()
    httpd = ThreadingHTTPServer((host, port), Handler)
    url = f"http://{host}:{port}"
    log.info("LiTiaotiao replica serving at %s", url)
    print(f"\n  李跳跳 复刻版 已启动 →  {url}\n  (按 Ctrl+C 停止)\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        log.info("server stopped")
    finally:
        httpd.server_close()


if __name__ == "__main__":
    run()
