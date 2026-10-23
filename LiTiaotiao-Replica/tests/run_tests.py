#!/usr/bin/env python3
"""Run the full backend test suite (no third-party deps required)."""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import test_hashutil  # noqa: E402
import test_engine  # noqa: E402
import test_rules_io  # noqa: E402

if __name__ == "__main__":
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    for mod in (test_hashutil, test_engine, test_rules_io):
        suite.addTests(loader.loadTestsFromModule(mod))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
