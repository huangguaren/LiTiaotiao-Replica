#!/usr/bin/env python3
"""Entry point: initialise the database (if needed) and start the server."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from litiaotiao.seed import seed_from_file  # noqa: E402
from litiaotiao.server import run  # noqa: E402


def main() -> None:
    # Seed the bundled community rules on first run so the app is useful immediately.
    seed_from_file()
    run()


if __name__ == "__main__":
    main()
