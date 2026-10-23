"""Logging configuration (file + console) shared across the backend."""
from __future__ import annotations

import logging
import sys

from .config import config

_logger_ready = False


def setup_logging() -> None:
    global _logger_ready
    if _logger_ready:
        return
    handlers: list[logging.Handler] = [logging.StreamHandler(sys.stdout)]
    try:
        fh = logging.FileHandler(config.LOG_PATH, encoding="utf-8")
        handlers.append(fh)
    except OSError:
        pass
    logging.basicConfig(
        level=logging.DEBUG if config.DEBUG else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        handlers=handlers,
        force=True,
    )
    _logger_ready = True


def get_logger(name: str) -> logging.Logger:
    setup_logging()
    return logging.getLogger(name)
