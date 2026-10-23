"""Java-compatible ``String.hashCode()`` used by LiTiaotiao to key rules.

In the original app and in the community rule repository, every app's rules are
stored under ``Java String.hashCode(packageName)`` which may be negative. This
module reproduces that exact algorithm so we can map a package name to its rule
hash (and recognise hashes coming from imported rule files).
"""
from __future__ import annotations


def java_hashcode(text: str) -> int:
    """Return the 32-bit signed Java hashCode of *text* (matches ``String.hashCode``)."""
    h = 0
    for ch in text:
        code = ord(ch)
        if code > 0xFFFF:  # surrogate pair (rare for ASCII package names)
            h = (h * 31 + ((code >> 10) + 0xD800)) & 0xFFFFFFFF
            h = (h * 31 + ((code & 0x3FF) + 0xDC00)) & 0xFFFFFFFF
        else:
            h = (h * 31 + code) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def hash_to_str(value: int) -> str:
    return str(value)
